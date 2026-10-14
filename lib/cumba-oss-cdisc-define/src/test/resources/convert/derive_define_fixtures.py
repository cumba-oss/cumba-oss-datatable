#!/usr/bin/env python3
"""Derive the Define-XML 2.0 and 1.0 conversion-test fixtures from CDISC's MIT-licensed 2.1 example.

Input : cdisc-org/DataExchange-DatasetJson ``examples/sdtm/define.xml`` at commit a379f49a (the CDISC MSG v2 example
        Define-XML 2.1, MIT License, "Copyright (c) 2022 cdisc"). The md5 of that file is checked before anything runs.
Output: ``define-v20-sdtm.xml`` (Define-XML 2.0.0) and ``define-v10-sdtm.xml`` (Define-XML 1.0.0 / CRT-DDS), written
        beside this script unless ``--out-dir`` says otherwise.

This is a hand-written DOWN-conversion, deliberately independent of the Java converter under test
(``Step10To20`` / ``Step20To21`` in net.cumba.cdisc.define): the fixtures are that converter's oracle inputs, so they
must not be produced by running it backwards. Each mapping below follows the Define-XML 2.0 / 2.1 specifications'
"deprecated components" and "changes from 2.0" tables, read in the direction 2.1 -> 2.0 -> 1.0:

2.1 -> 2.0
  * namespaces def/v2.1 -> def/v2.0; def:DefineVersion 2.0.0; def:Context dropped (2.1 only);
  * def:Standards -> MetaDataVersion/@def:StandardName + @def:StandardVersion taken from the first Type="IG" standard
    (Name SDTMIG -> "SDTM-IG", the 2.0 spelling; the source spells it "STDTMIG", which is read as the same name);
    every @def:StandardOID / @def:IsNonStandard / @def:HasNoData dropped; MetaDataVersion/@def:CommentOID dropped
    (2.1 only);
  * datasets with @def:HasNoData="Yes" are dropped (2.0 cannot declare a dataset without data: a dataset listed in a
    2.0 define is a submitted one), and everything only they referenced is pruned;
  * <def:Class Name="X"/> element -> @def:Class="X" attribute;
  * def:Origin Type/Source -> 2.0 Type: Collected+Vendor -> eDT, any other Collected -> CRF; Derived, Assigned,
    Protocol, Predecessor keep their Type; @Source dropped; def:PDFPageRef/@Title dropped (2.1 only);
  * children re-ordered to the 2.0 / ODM 1.3.2 content models.

2.0 -> 1.0 (CRT-DDS; the 1.0 constructs are the ones the CDISC 2.0 schema lists as deprecated)
  * namespaces odm/v1.3 -> odm/v1.2, def/v2.0 -> def/v1.0; ODMVersion 1.2; def:DefineVersion 1.0.0;
  * Description/TranslatedText on ItemGroupDef / ItemDef -> @def:Label; ItemRef/@KeySequence -> ItemGroupDef/
    @def:DomainKeys; def:Class title-cased (the 1.0 spelling, e.g. "Trial Design");
  * def:Origin element -> free-text @Origin ("CRF Page 22, 23", "eDT", "Derived", the predecessor's text, ...);
  * def:CommentDef + @def:CommentOID -> inline @Comment text;
  * MethodDef + ItemRef/@MethodOID -> def:ComputationMethod (text) + ItemDef/@def:ComputationMethodOID;
  * Rank -> @def:Rank; ExternalCodeList keeps only @Dictionary / @Version (ODM 1.2.1); EnumeratedItem ->
    CodeListItem whose Decode is the coded value (ODM 1.2 has no EnumeratedItem); Alias, OrderNumber on code-list
    items, def:ExtendedValue, Description on CodeList dropped;
  * DataType values ODM 1.2.1 does not know (partialDate, partialDatetime, durationDatetime, ...) -> "text";
  * dataset OIDs IG.<DS> -> <DS>, item OIDs IT.<DS>.<VAR> -> <DS>.<VAR> (the 1.0 OID style);
  * value-level metadata: 1.0 has no where-clauses; conditions live only in the OID naming convention
    ValueList.<DS>.<VAR> / <DS>.<VAR>.<value>, the value list hanging off the variable whose VALUE selects the row
    (e.g. LBTESTCD). A 2.0 where-clause is carried over iff it is a single RangeCheck "VAR EQ value" with an
    OID-safe value; IN / NE conditions and values with spaces or dots cannot be expressed and are dropped; when
    several 2.0 value lists key on the same VAR and value, the first in document order wins.

Oracles (all must pass, or nothing is written):
  * 2.0: ``xmllint --schema`` against the CDISC Define-XML 2.0 schema vendored in cumba-corej / cumba-oss-corej
    (``.../xsd/define-2-0/cdisc-define-2.0/define2-0-0.xsd``; found by walking up from this script, or
    ``--define20-xsd``). No copy is vendored here.
  * 1.0: no CDISC-hosted Define-XML 1.0 schema under a usable licence was found, so the 1.0 file is checked
    (a) for its ODM core: every def:-namespace element and attribute is stripped and the rest is validated with
        ``xmllint --schema`` against CDISC's ODM 1.2.1 schema (cdisc-org/DataExchange-ODM, MIT,
        archive/ODM1-2-1/ODM1-2-1-foundation.xsd at 6cb00a9c, sha256-pinned; downloaded unless
        ``--odm121-foundation`` names a local copy), and
    (b) structurally, by the checks in ``check_v10`` (S1-S6), written down there.
  ``--self-test`` plants a defect per check and proves each oracle reports it.

Usage:
  python3 derive_define_fixtures.py --source <DataExchange-DatasetJson>/examples/sdtm/define.xml
  python3 derive_define_fixtures.py --source ... --check       # regenerate into a temp dir, compare to the files here
  python3 derive_define_fixtures.py --source ... --self-test   # plant defects, prove every oracle reds

Needs Python >= 3.9 (stdlib only) and xmllint (libxml2).
"""

import argparse
import copy
import hashlib
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import xml.etree.ElementTree as ET

SOURCE_MD5 = "1d20c8a430457af2c50a820b22e40e1e"
SOURCE_ID = "cdisc-org/DataExchange-DatasetJson examples/sdtm/define.xml @ a379f49a"

ODM121_URL = ("https://raw.githubusercontent.com/cdisc-org/DataExchange-ODM/"
              "6cb00a9cb38f245069758a7c5a6823cdf3ea48be/archive/ODM1-2-1/ODM1-2-1-foundation.xsd")
ODM121_SHA256 = "09e415d74a7ac0de8f0bbd93334fe2617717b892e551184e19057aea69105846"

DEFINE20_XSD_CANDIDATES = (
    "cumba-corej/lib/cumba-corej-define-conformance/src/main/resources/xsd/define-2-0/cdisc-define-2.0/"
    "define2-0-0.xsd",
    "cumba-oss-corej/lib/cumba-oss-corej-define-conformance/src/main/resources/xsd/define-2-0/cdisc-define-2.0/"
    "define2-0-0.xsd",
)

ODM13 = "http://www.cdisc.org/ns/odm/v1.3"
ODM12 = "http://www.cdisc.org/ns/odm/v1.2"
DEF21 = "http://www.cdisc.org/ns/def/v2.1"
DEF20 = "http://www.cdisc.org/ns/def/v2.0"
DEF10 = "http://www.cdisc.org/ns/def/v1.0"
XLINK = "http://www.w3.org/1999/xlink"
XSI = "http://www.w3.org/2001/XMLSchema-instance"
XML = "http://www.w3.org/XML/1998/namespace"

OUT20 = "define-v20-sdtm.xml"
OUT10 = "define-v10-sdtm.xml"

ODM121_DATATYPES = {"integer", "float", "date", "datetime", "time", "text", "string"}
OID_SAFE_VALUE = re.compile(r"[A-Za-z0-9_\-]+")

# The 1.0 vocabulary of the def namespace: what the CDISC Define-XML 2.0 schema (define-extension.xsd / define-ns.xsd)
# keeps from 1.0 plus what it lists as "deprecated" 1.0 constructs.
V10_DEF_ELEMENTS = {"AnnotatedCRF", "SupplementalDoc", "DocumentRef", "leaf", "title", "ComputationMethod",
                    "ValueListDef", "ValueListRef"}
V10_DEF_ATTRIBUTES = {"DefineVersion", "StandardName", "StandardVersion", "Label", "Structure", "Class",
                      "DomainKeys", "ArchiveLocationID", "ComputationMethodOID", "Rank"}


def q(ns, local):
    return "{%s}%s" % (ns, local)


def local(tag):
    return tag.rsplit("}", 1)[-1]


def ns_of(tag):
    return tag[1:].split("}", 1)[0] if tag.startswith("{") else ""


def text_of(el, odm):
    """The joined TranslatedText of el's Description child, or None."""
    d = el.find(q(odm, "Description"))
    if d is None:
        return None
    parts = [(t.text or "").strip() for t in d.findall(q(odm, "TranslatedText"))]
    joined = " ".join(p for p in parts if p)
    return joined or None


def reorder(el, order):
    """Stable re-order of el's children by the position of their tag in order (unknown tags last)."""
    rank = {t: i for i, t in enumerate(order)}
    kids = list(el)
    kids.sort(key=lambda c: rank.get(c.tag, len(order)))
    for c in list(el):
        el.remove(c)
    el.extend(kids)


# ---------------------------------------------------------------------------------------------------------------
# 2.1 -> 2.0
# ---------------------------------------------------------------------------------------------------------------

def rename_namespace(root, old, new):
    for el in root.iter():
        if ns_of(el.tag) == old:
            el.tag = q(new, local(el.tag))
        for k in list(el.attrib):
            if ns_of(k) == old:
                el.attrib[q(new, local(k))] = el.attrib.pop(k)


def prune_unreferenced(mdv, odm, dfn):
    """Remove definitions nothing references any more (fix point). Returns {kind: count}."""
    removed = {}
    kinds = [
        (q(odm, "ItemDef"), "OID"), (q(odm, "CodeList"), "OID"), (q(odm, "MethodDef"), "OID"),
        (q(dfn, "CommentDef"), "OID"), (q(dfn, "ValueListDef"), "OID"), (q(dfn, "WhereClauseDef"), "OID"),
        (q(dfn, "leaf"), "ID"),
    ]
    ref_attrs = {"ItemOID", "CodeListOID", "MethodOID", "CommentOID", "ValueListOID", "WhereClauseOID",
                 "ArchiveLocationID", "leafID"}
    while True:
        refs = set()
        for el in mdv.iter():
            for k, v in el.attrib.items():
                if local(k) in ref_attrs:
                    refs.add(v)
        changed = False
        for tag, key in kinds:
            for parent in [mdv] + list(mdv.iter(q(odm, "ItemGroupDef"))):
                for el in list(parent.findall(tag)):
                    if el.get(key) not in refs:
                        parent.remove(el)
                        removed[local(tag)] = removed.get(local(tag), 0) + 1
                        changed = True
        if not changed:
            return removed


def to_v20(src_root, log):
    root = copy.deepcopy(src_root)
    rename_namespace(root, DEF21, DEF20)
    odm, dfn = ODM13, DEF20

    root.attrib.pop(q(dfn, "Context"), None)
    root.set(q(XSI, "schemaLocation"), ODM13 + " define2-0-0.xsd")

    mdv = root.find(f"{{{odm}}}Study/{{{odm}}}MetaDataVersion")
    mdv.set(q(dfn, "DefineVersion"), "2.0.0")
    mdv.attrib.pop(q(dfn, "CommentOID"), None)
    standards = mdv.find(q(dfn, "Standards"))
    ig_std = next(s for s in standards.findall(q(dfn, "Standard")) if s.get("Type") == "IG")
    std_name = {"SDTMIG": "SDTM-IG", "STDTMIG": "SDTM-IG"}.get(ig_std.get("Name"), ig_std.get("Name"))
    mdv.set(q(dfn, "StandardName"), std_name)
    mdv.set(q(dfn, "StandardVersion"), ig_std.get("Version"))
    mdv.remove(standards)

    for el in root.iter():
        el.attrib.pop(q(dfn, "StandardOID"), None)
        el.attrib.pop(q(dfn, "IsNonStandard"), None)

    dropped = []
    for ig in list(mdv.findall(q(odm, "ItemGroupDef"))):
        if ig.get(q(dfn, "HasNoData")) == "Yes":
            mdv.remove(ig)
            dropped.append(ig.get("Name"))
            continue
        cls = ig.find(q(dfn, "Class"))
        if cls is not None:
            ig.remove(cls)
            ig.set(q(dfn, "Class"), cls.get("Name"))
        reorder(ig, [q(odm, "Description"), q(odm, "ItemRef"), q(odm, "Alias"), q(dfn, "leaf")])
    log.append("2.0: dropped %d HasNoData datasets: %s" % (len(dropped), ", ".join(dropped)))
    for el in root.iter():
        el.attrib.pop(q(dfn, "HasNoData"), None)

    origin_counts = {}
    for it in mdv.findall(q(odm, "ItemDef")):
        for o in list(it.findall(q(dfn, "Origin"))):
            t, s = o.get("Type"), o.get("Source")
            if t == "Collected":
                new = "eDT" if s == "Vendor" else "CRF"
            elif t in ("Derived", "Assigned", "Protocol", "Predecessor"):
                new = t
            else:
                it.remove(o)  # "Not Available" and anything unknown has no 2.0 type
                origin_counts["dropped " + str(t)] = origin_counts.get("dropped " + str(t), 0) + 1
                continue
            o.attrib.clear()
            o.set("Type", new)
            key = "%s/%s -> %s" % (t, s, new)
            origin_counts[key] = origin_counts.get(key, 0) + 1
        reorder(it, [q(odm, "Description"), q(odm, "Question"), q(odm, "ExternalQuestion"),
                     q(odm, "MeasurementUnitRef"), q(odm, "RangeCheck"), q(odm, "CodeListRef"), q(odm, "Role"),
                     q(odm, "Alias"), q(dfn, "Origin"), q(dfn, "ValueListRef")])
    log.append("2.0: origins " + "; ".join("%s x%d" % kv for kv in sorted(origin_counts.items())))

    for p in root.iter(q(dfn, "PDFPageRef")):
        p.attrib.pop("Title", None)
    for cl in mdv.findall(q(odm, "CodeList")):
        cl.attrib.pop(q(dfn, "CommentOID"), None)

    removed = prune_unreferenced(mdv, odm, dfn)
    log.append("2.0: pruned after the drops: " + (", ".join("%s x%d" % kv for kv in sorted(removed.items()))
                                                  or "nothing"))
    reorder(mdv, [q(dfn, "AnnotatedCRF"), q(dfn, "SupplementalDoc"), q(dfn, "ValueListDef"),
                  q(dfn, "WhereClauseDef"), q(odm, "ItemGroupDef"), q(odm, "ItemDef"), q(odm, "CodeList"),
                  q(odm, "MethodDef"), q(dfn, "CommentDef"), q(dfn, "leaf")])
    return root


# ---------------------------------------------------------------------------------------------------------------
# 2.0 -> 1.0
# ---------------------------------------------------------------------------------------------------------------

def to_v10(v20_root, log):
    odm, dfn = ODM13, DEF20
    o, d = ODM12, DEF10
    src_study = v20_root.find(q(odm, "Study"))
    smdv = src_study.find(q(odm, "MetaDataVersion"))

    comments = {c.get("OID"): text_of(c, odm) for c in smdv.findall(q(dfn, "CommentDef"))}
    methods = {m.get("OID"): m for m in smdv.findall(q(odm, "MethodDef"))}
    items = {i.get("OID"): i for i in smdv.findall(q(odm, "ItemDef"))}
    wcs = {w.get("OID"): w for w in smdv.findall(q(dfn, "WhereClauseDef"))}
    igs = smdv.findall(q(odm, "ItemGroupDef"))

    # dataset-level item OIDs IT.<DS>.<VAR> -> <DS>.<VAR>; the item's method comes from its (single) ItemRef
    item_oid = {}
    item_ds = {}
    item_method = {}
    for ig in igs:
        for ir in ig.findall(q(odm, "ItemRef")):
            it = items[ir.get("ItemOID")]
            item_oid[it.get("OID")] = "%s.%s" % (ig.get("Name"), it.get("Name"))
            item_ds[it.get("OID")] = ig.get("Name")
            if ir.get("MethodOID"):
                item_method[it.get("OID")] = ir.get("MethodOID")

    # ---- value-level metadata, re-keyed to the 1.0 naming convention
    vl_owner = {}  # 2.0 VLD OID -> dataset of the item that carries it
    for it in items.values():
        vr = it.find(q(dfn, "ValueListRef"))
        if vr is not None and it.get("OID") in item_ds:
            vl_owner[vr.get("ValueListOID")] = item_ds[it.get("OID")]
    value_lists = {}  # 1.0 VLD OID -> list of (value item OID, src ItemRef, src value ItemDef, value)
    param_of = {}  # 1.0 VLD OID -> 2.0 dataset-level ItemDef OID of the selecting variable
    seen = set()
    kept = dropped = 0
    drop_reasons = {}
    for vld in smdv.findall(q(dfn, "ValueListDef")):
        ds = vl_owner.get(vld.get("OID"))
        for ir in vld.findall(q(odm, "ItemRef")):
            wref = ir.find(q(dfn, "WhereClauseRef"))
            rcs = wcs[wref.get("WhereClauseOID")].findall(q(odm, "RangeCheck"))
            reason = None
            if ds is None:
                reason = "value list not owned by a dataset variable"
            elif len(rcs) != 1:
                reason = "%d conditions" % len(rcs)
            else:
                rc = rcs[0]
                vals = [cv.text or "" for cv in rc.findall(q(odm, "CheckValue"))]
                if rc.get("Comparator") != "EQ" or len(vals) != 1:
                    reason = "comparator %s" % rc.get("Comparator")
                elif not OID_SAFE_VALUE.fullmatch(vals[0]):
                    reason = "value not OID-safe"
                elif item_ds.get(rc.get(q(dfn, "ItemOID"))) != ds:
                    reason = "condition on another dataset"
            if reason:
                dropped += 1
                drop_reasons[reason] = drop_reasons.get(reason, 0) + 1
                continue
            param = rc.get(q(dfn, "ItemOID"))
            value = vals[0]
            vl_oid = "ValueList.%s" % item_oid[param]
            vi_oid = "%s.%s" % (item_oid[param], value)
            if vi_oid in seen:
                dropped += 1
                drop_reasons["same VAR/value as an earlier list"] = drop_reasons.get(
                    "same VAR/value as an earlier list", 0) + 1
                continue
            seen.add(vi_oid)
            param_of[vl_oid] = param
            value_lists.setdefault(vl_oid, []).append((vi_oid, ir, items[ir.get("ItemOID")], value))
            kept += 1
    log.append("1.0: value-level items kept %d, dropped %d (%s)" % (
        kept, dropped, "; ".join("%s x%d" % kv for kv in sorted(drop_reasons.items()))))
    vl_of_param = {p: vl for vl, p in param_of.items()}

    def origin_text(it):
        org = it.find(q(dfn, "Origin"))
        if org is None:
            return None
        t = org.get("Type")
        if t == "CRF":
            pages = []
            for p in org.iter(q(dfn, "PDFPageRef")):
                if p.get("PageRefs"):
                    pages.extend(p.get("PageRefs").split())
                elif p.get("FirstPage"):
                    pages.append("%s-%s" % (p.get("FirstPage"), p.get("LastPage")))
            return "CRF Page " + ", ".join(pages) if pages else "CRF"
        if t == "Predecessor":
            return text_of(org, odm) or "Predecessor"
        return t

    used_methods = []
    used_codelists = set()

    def item10(src, oid, name, method_oid):
        e = ET.Element(q(o, "ItemDef"))
        e.set("OID", oid)
        e.set("Name", name)
        dt = src.get("DataType")
        e.set("DataType", dt if dt in ODM121_DATATYPES else "text")
        for a in ("Length", "SignificantDigits", "SASFieldName"):
            if src.get(a):
                e.set(a, src.get(a))
        origin = origin_text(src)
        if origin:
            e.set("Origin", origin)
        comment = comments.get(src.get(q(dfn, "CommentOID")))
        if comment:
            e.set("Comment", comment)
        label = text_of(src, odm)
        if label:
            e.set(q(d, "Label"), label)
        if method_oid:
            e.set(q(d, "ComputationMethodOID"), method_oid)
            if method_oid not in used_methods:
                used_methods.append(method_oid)
        clr = src.find(q(odm, "CodeListRef"))
        if clr is not None:
            ET.SubElement(e, q(o, "CodeListRef"), {"CodeListOID": clr.get("CodeListOID")})
            used_codelists.add(clr.get("CodeListOID"))
        return e

    # ---- assemble
    root = ET.Element(q(o, "ODM"))
    for a in ("FileOID", "FileType", "CreationDateTime", "AsOfDateTime", "Originator", "SourceSystem",
              "SourceSystemVersion"):
        if v20_root.get(a):
            root.set(a, v20_root.get(a))
    root.set("ODMVersion", "1.2")
    root.set(q(XSI, "schemaLocation"), ODM12 + " define1-0-0.xsd")
    study = ET.SubElement(root, q(o, "Study"), {"OID": src_study.get("OID")})
    gv = ET.SubElement(study, q(o, "GlobalVariables"))
    for c in src_study.find(q(odm, "GlobalVariables")):
        ET.SubElement(gv, q(o, local(c.tag))).text = c.text
    mdv = ET.SubElement(study, q(o, "MetaDataVersion"))
    for a in ("OID", "Name", "Description"):
        if smdv.get(a):
            mdv.set(a, smdv.get(a))
    mdv.set(q(d, "DefineVersion"), "1.0.0")
    mdv.set(q(d, "StandardName"), smdv.get(q(dfn, "StandardName")))
    mdv.set(q(d, "StandardVersion"), smdv.get(q(dfn, "StandardVersion")))

    doc_leaves = []
    for tag in ("AnnotatedCRF", "SupplementalDoc"):
        src = smdv.find(q(dfn, tag))
        if src is not None:
            el = ET.SubElement(mdv, q(d, tag))
            for dr in src.findall(q(dfn, "DocumentRef")):
                ET.SubElement(el, q(d, "DocumentRef"), {"leafID": dr.get("leafID")})
                doc_leaves.append(dr.get("leafID"))
    for lf in smdv.findall(q(dfn, "leaf")):
        if lf.get("ID") in doc_leaves:
            mdv.append(leaf10(lf, dfn, d))
    cm_anchor = len(list(mdv))

    for vl_oid, rows in value_lists.items():
        vld = ET.SubElement(mdv, q(d, "ValueListDef"), {"OID": vl_oid})
        for n, (vi_oid, ir, _src, _value) in enumerate(rows, 1):
            ref = ET.SubElement(vld, q(o, "ItemRef"))
            ref.set("ItemOID", vi_oid)
            ref.set("Mandatory", ir.get("Mandatory"))
            ref.set("OrderNumber", str(n))

    for ig in igs:
        g = ET.SubElement(mdv, q(o, "ItemGroupDef"))
        g.set("OID", ig.get("Name"))
        for a in ("Name", "Repeating", "IsReferenceData", "SASDatasetName", "Domain", "Purpose"):
            if ig.get(a):
                g.set(a, ig.get(a))
        comment = comments.get(ig.get(q(dfn, "CommentOID")))
        if comment:
            g.set("Comment", comment)
        g.set(q(d, "Label"), text_of(ig, odm) or ig.get("Name"))
        g.set(q(d, "Structure"), ig.get(q(dfn, "Structure")))
        if ig.get(q(dfn, "Class")):
            g.set(q(d, "Class"), ig.get(q(dfn, "Class")).title())
        refs = ig.findall(q(odm, "ItemRef"))
        keys = sorted((int(r.get("KeySequence")), items[r.get("ItemOID")].get("Name"))
                      for r in refs if r.get("KeySequence"))
        if keys:
            g.set(q(d, "DomainKeys"), ", ".join(k[1] for k in keys))
        if ig.get(q(dfn, "ArchiveLocationID")):
            g.set(q(d, "ArchiveLocationID"), ig.get(q(dfn, "ArchiveLocationID")))
        for r in refs:
            ref = ET.SubElement(g, q(o, "ItemRef"))
            ref.set("ItemOID", item_oid[r.get("ItemOID")])
            ref.set("Mandatory", r.get("Mandatory"))
            if r.get("OrderNumber"):
                ref.set("OrderNumber", r.get("OrderNumber"))
            if r.get("Role"):
                ref.set("Role", r.get("Role"))
        lf = ig.find(q(dfn, "leaf"))
        if lf is not None:
            g.append(leaf10(lf, dfn, d))

    for ig in igs:
        for r in ig.findall(q(odm, "ItemRef")):
            src = items[r.get("ItemOID")]
            e = item10(src, item_oid[src.get("OID")], src.get("Name"), item_method.get(src.get("OID")))
            vl = vl_of_param.get(src.get("OID"))
            if vl:
                ET.SubElement(e, q(d, "ValueListRef"), {"ValueListOID": vl})
            mdv.append(e)
    for vl_oid, rows in value_lists.items():
        for vi_oid, ir, src, value in rows:
            mdv.append(item10(src, vi_oid, value, ir.get("MethodOID")))

    for cl in smdv.findall(q(odm, "CodeList")):
        if cl.get("OID") not in used_codelists:
            continue
        c = ET.SubElement(mdv, q(o, "CodeList"))
        for a in ("OID", "Name", "DataType", "SASFormatName"):
            if cl.get(a):
                c.set(a, cl.get(a))
        for ci in cl:
            tag = local(ci.tag)
            if tag in ("CodeListItem", "EnumeratedItem"):
                item = ET.SubElement(c, q(o, "CodeListItem"), {"CodedValue": ci.get("CodedValue")})
                if ci.get("Rank"):
                    item.set(q(d, "Rank"), ci.get("Rank"))
                dec = ET.SubElement(item, q(o, "Decode"))
                src_tt = ci.findall(f"{{{odm}}}Decode/{{{odm}}}TranslatedText")
                if src_tt:
                    for tt in src_tt:
                        t = ET.SubElement(dec, q(o, "TranslatedText"))
                        if tt.get(q(XML, "lang")):
                            t.set(q(XML, "lang"), tt.get(q(XML, "lang")))
                        t.text = tt.text
                else:
                    t = ET.SubElement(dec, q(o, "TranslatedText"), {q(XML, "lang"): "en"})
                    t.text = ci.get("CodedValue")
            elif tag == "ExternalCodeList":
                ET.SubElement(c, q(o, "ExternalCodeList"),
                              {a: ci.get(a) for a in ("Dictionary", "Version") if ci.get(a)})

    # def:ComputationMethod in first-use order, placed after the document leaves (1.0 MetaDataVersion order)
    for n, m_oid in enumerate(used_methods):
        cm = ET.Element(q(d, "ComputationMethod"), {"OID": m_oid})
        cm.text = text_of(methods[m_oid], odm) or ""
        mdv.insert(cm_anchor + n, cm)
    log.append("1.0: %d datasets, %d ItemDefs, %d value lists, %d computation methods, %d code lists" % (
        len(mdv.findall(q(o, "ItemGroupDef"))), len(mdv.findall(q(o, "ItemDef"))),
        len(mdv.findall(q(d, "ValueListDef"))), len(used_methods), len(mdv.findall(q(o, "CodeList")))))
    return root


def leaf10(lf, dfn, d):
    e = ET.Element(q(d, "leaf"), {"ID": lf.get("ID"), q(XLINK, "href"): lf.get(q(XLINK, "href"))})
    t = lf.find(q(dfn, "title"))
    ET.SubElement(e, q(d, "title")).text = t.text if t is not None else lf.get(q(XLINK, "href"))
    return e


# ---------------------------------------------------------------------------------------------------------------
# serialisation
# ---------------------------------------------------------------------------------------------------------------

def serialise(root, odm, dfn, stylesheet, version_label):
    for prefix, uri in (("", odm), ("def", dfn), ("xlink", XLINK), ("xsi", XSI)):
        ET.register_namespace(prefix, uri)
    root = copy.deepcopy(root)
    ET.indent(root, space="  ")
    body = ET.tostring(root, encoding="unicode")
    header = ('<?xml version="1.0" encoding="UTF-8"?>\n'
              '<?xml-stylesheet type="text/xsl" href="%s"?>\n'
              "<!-- Test fixture, Define-XML %s. Derived from %s (MIT License, Copyright (c) 2022 cdisc;\n"
              "     see README.md beside this file), down-converted by derive_define_fixtures.py.\n"
              "     Generated: do not edit by hand, re-run the script. -->\n") % (stylesheet, version_label, SOURCE_ID)
    return (header + body + "\n").encode("utf-8")


# ---------------------------------------------------------------------------------------------------------------
# oracles
# ---------------------------------------------------------------------------------------------------------------

def xmllint(xsd, xml_path):
    p = subprocess.run(["xmllint", "--nonet", "--noout", "--schema", xsd, xml_path],
                       capture_output=True, text=True)
    errors = [ln for ln in p.stderr.splitlines() if "Schemas parser warning" not in ln and ln.strip()
              and not ln.endswith(" validates")]
    return p.returncode, errors


def check_v10(data):
    """Structural checks for the 1.0 output; returns a list of failures (empty = pass).

    S1 root: namespaces odm/v1.2 + def/v1.0 only (no v1.3 / v2.x anywhere), ODMVersion 1.2,
       MetaDataVersion/@def:DefineVersion 1.0.0 with @def:StandardName and @def:StandardVersion.
    S2 every def-namespace element and attribute is in the 1.0 vocabulary (V10_DEF_ELEMENTS / V10_DEF_ATTRIBUTES).
    S3 no 2.x construct survives: no Description under ItemGroupDef / ItemDef, no ItemRef/@KeySequence or @MethodOID,
       no EnumeratedItem, no Alias.
    S4 every ItemGroupDef carries @def:Label, @def:Structure, @def:Class, @def:ArchiveLocationID (resolving to its own
       def:leaf with a def:title); every @def:DomainKeys entry names a variable of that dataset; every ItemDef carries
       @def:Label.
    S5 references resolve: ItemRef/@ItemOID -> ItemDef, CodeListRef -> CodeList, def:ValueListRef -> def:ValueListDef,
       @def:ComputationMethodOID -> def:ComputationMethod, def:DocumentRef/@leafID -> def:leaf; and nothing is
       orphaned (every ItemDef, CodeList, ValueListDef, ComputationMethod is referenced).
    S6 value lists follow the 1.0 naming convention: OID ValueList.<DS>.<VAR> where <DS>.<VAR> is an ItemDef that
       carries def:ValueListRef to it, and every child ItemOID is <DS>.<VAR>.<value> with an OID-safe value.
    """
    fails = []
    try:
        root = ET.fromstring(data)
    except ET.ParseError as e:
        return ["not well-formed: %s" % e]
    o, d = ODM12, DEF10
    text = data.decode("utf-8")
    # S1
    if root.tag != q(o, "ODM"):
        fails.append("S1 root is %s" % root.tag)
    for bad in (ODM13, DEF20, DEF21):
        if bad in text:
            fails.append("S1 namespace %s present" % bad)
    if root.get("ODMVersion") != "1.2":
        fails.append("S1 ODMVersion %r" % root.get("ODMVersion"))
    mdv = root.find(f"{{{o}}}Study/{{{o}}}MetaDataVersion")
    if mdv is None:
        return fails + ["S1 no Study/MetaDataVersion"]
    if mdv.get(q(d, "DefineVersion")) != "1.0.0":
        fails.append("S1 def:DefineVersion %r" % mdv.get(q(d, "DefineVersion")))
    for a in ("StandardName", "StandardVersion"):
        if not mdv.get(q(d, a)):
            fails.append("S1 MetaDataVersion lacks def:%s" % a)
    # S2
    for el in root.iter():
        if ns_of(el.tag) == d and local(el.tag) not in V10_DEF_ELEMENTS:
            fails.append("S2 def element %s is not 1.0" % local(el.tag))
        for k in el.attrib:
            if ns_of(k) == d and local(k) not in V10_DEF_ATTRIBUTES:
                fails.append("S2 def attribute %s on %s is not 1.0" % (local(k), local(el.tag)))
    # S3
    for tag in ("ItemGroupDef", "ItemDef"):
        for el in mdv.findall(q(o, tag)):
            if el.find(q(o, "Description")) is not None:
                fails.append("S3 %s %s has a Description" % (tag, el.get("OID")))
    for ir in mdv.iter(q(o, "ItemRef")):
        for a in ("KeySequence", "MethodOID"):
            if ir.get(a) is not None:
                fails.append("S3 ItemRef %s has @%s" % (ir.get("ItemOID"), a))
    for tag in ("EnumeratedItem", "Alias"):
        if any(True for _ in root.iter(q(o, tag))):
            fails.append("S3 %s present" % tag)
    # S4
    items = {i.get("OID"): i for i in mdv.findall(q(o, "ItemDef"))}
    for ig in mdv.findall(q(o, "ItemGroupDef")):
        for a in ("Label", "Structure", "Class", "ArchiveLocationID"):
            if not ig.get(q(d, a)):
                fails.append("S4 ItemGroupDef %s lacks def:%s" % (ig.get("OID"), a))
        lf = ig.find(q(d, "leaf"))
        if lf is None or lf.get("ID") != ig.get(q(d, "ArchiveLocationID")) or lf.find(q(d, "title")) is None:
            fails.append("S4 ItemGroupDef %s: def:ArchiveLocationID does not resolve to its def:leaf" % ig.get("OID"))
        names = {items[r.get("ItemOID")].get("Name") for r in ig.findall(q(o, "ItemRef")) if r.get("ItemOID") in items}
        for key in (ig.get(q(d, "DomainKeys")) or "").split(","):
            if key.strip() and key.strip() not in names:
                fails.append("S4 ItemGroupDef %s: domain key %s is not one of its variables" % (ig.get("OID"), key))
    for it in items.values():
        if not it.get(q(d, "Label")):
            fails.append("S4 ItemDef %s lacks def:Label" % it.get("OID"))
    # S5
    codelists = {c.get("OID") for c in mdv.findall(q(o, "CodeList"))}
    vlds = {v.get("OID"): v for v in mdv.findall(q(d, "ValueListDef"))}
    cms = {c.get("OID") for c in mdv.findall(q(d, "ComputationMethod"))}
    leaves = {lf.get("ID") for lf in root.iter(q(d, "leaf"))}
    referenced = set()
    for ir in mdv.iter(q(o, "ItemRef")):
        referenced.add(ir.get("ItemOID"))
        if ir.get("ItemOID") not in items:
            fails.append("S5 ItemRef -> missing ItemDef %s" % ir.get("ItemOID"))
    for clr in mdv.iter(q(o, "CodeListRef")):
        referenced.add(clr.get("CodeListOID"))
        if clr.get("CodeListOID") not in codelists:
            fails.append("S5 CodeListRef -> missing CodeList %s" % clr.get("CodeListOID"))
    for vr in mdv.iter(q(d, "ValueListRef")):
        referenced.add(vr.get("ValueListOID"))
        if vr.get("ValueListOID") not in vlds:
            fails.append("S5 def:ValueListRef -> missing def:ValueListDef %s" % vr.get("ValueListOID"))
    for it in items.values():
        cm = it.get(q(d, "ComputationMethodOID"))
        if cm:
            referenced.add(cm)
            if cm not in cms:
                fails.append("S5 ItemDef %s -> missing def:ComputationMethod %s" % (it.get("OID"), cm))
    for dr in root.iter(q(d, "DocumentRef")):
        if dr.get("leafID") not in leaves:
            fails.append("S5 def:DocumentRef -> missing def:leaf %s" % dr.get("leafID"))
    for kind, oids in (("ItemDef", items), ("CodeList", codelists), ("def:ValueListDef", vlds),
                       ("def:ComputationMethod", cms)):
        for oid in oids:
            if oid not in referenced:
                fails.append("S5 orphaned %s %s" % (kind, oid))
    # S6
    for oid, vld in vlds.items():
        m = re.fullmatch(r"ValueList\.([^.]+)\.([^.]+)", oid)
        if not m:
            fails.append("S6 value list OID %s does not follow ValueList.<DS>.<VAR>" % oid)
            continue
        owner = items.get("%s.%s" % m.groups())
        vr = owner.find(q(d, "ValueListRef")) if owner is not None else None
        if vr is None or vr.get("ValueListOID") != oid:
            fails.append("S6 value list %s does not hang off ItemDef %s.%s" % ((oid,) + m.groups()))
        for ir in vld.findall(q(o, "ItemRef")):
            iv = ir.get("ItemOID")
            prefix = "%s.%s." % m.groups()
            if not (iv.startswith(prefix) and OID_SAFE_VALUE.fullmatch(iv[len(prefix):])):
                fails.append("S6 value item %s in %s does not follow <DS>.<VAR>.<value>" % (iv, oid))
    return fails


def strip_def(data):
    """The ODM core of a 1.0 document: every def-namespace element and attribute (and xsi:schemaLocation) removed."""
    root = ET.fromstring(data)
    for parent in root.iter():
        for c in list(parent):
            if ns_of(c.tag) == DEF10:
                parent.remove(c)
        for k in list(parent.attrib):
            if ns_of(k) in (DEF10, XSI):
                del parent.attrib[k]
    ET.register_namespace("", ODM12)
    return ET.tostring(root, encoding="utf-8", xml_declaration=True)


class Oracles:
    def __init__(self, define20_xsd, odm121_foundation, work):
        self.define20_xsd = define20_xsd
        core = os.path.normpath(os.path.join(os.path.dirname(define20_xsd), "..", "core"))
        self.odm121_driver = os.path.join(work, "odm121-driver.xsd")
        with open(self.odm121_driver, "w", encoding="utf-8") as f:
            f.write('<?xml version="1.0" encoding="UTF-8"?>\n'
                    '<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema" targetNamespace="%s"'
                    ' elementFormDefault="qualified">\n'
                    '  <xs:import namespace="%s" schemaLocation="%s"/>\n'
                    '  <xs:import namespace="http://www.w3.org/2000/09/xmldsig#" schemaLocation="%s"/>\n'
                    '  <xs:include schemaLocation="%s"/>\n'
                    '</xs:schema>\n' % (ODM12, XML, uri(os.path.join(core, "xml.xsd")),
                                         uri(os.path.join(core, "xmldsig-core-schema.xsd")), uri(odm121_foundation)))
        self.work = work

    def v20(self, data):
        path = os.path.join(self.work, "probe-v20.xml")
        with open(path, "wb") as f:
            f.write(data)
        rc, errors = xmllint(self.define20_xsd, path)
        return [] if rc == 0 else ["xmllint rc=%d: %s" % (rc, e) for e in (errors or ["(no message)"])][:20]

    def v10(self, data):
        fails = check_v10(data)
        path = os.path.join(self.work, "probe-v10-odm-core.xml")
        with open(path, "wb") as f:
            f.write(strip_def(data))
        rc, errors = xmllint(self.odm121_driver, path)
        if rc != 0:
            fails += ["S7 ODM 1.2.1 core: xmllint rc=%d: %s" % (rc, e) for e in (errors or ["(no message)"])][:20]
        return fails


def uri(path):
    return "file://" + os.path.abspath(path)


# ---------------------------------------------------------------------------------------------------------------
# self-test: plant one defect per check, prove the oracle reports it
# ---------------------------------------------------------------------------------------------------------------

def self_test(oracles, v20, v10):
    def sub(data, old, new, count=1):
        s = data.decode("utf-8")
        assert old in s, "self-test pattern not found: %r" % old
        return s.replace(old, new, count).encode("utf-8")

    plants20 = {
        "unknown element in 2.0": sub(v20, "<ItemGroupDef ", "<def:Bogus/><ItemGroupDef "),
        "2.1 def:Class element left in 2.0": sub(v20, "</ItemGroupDef>", '<def:Class Name="EVENTS"/></ItemGroupDef>'),
        "2.1 Origin/@Source left in 2.0": sub(v20, '<def:Origin Type="Assigned"',
                                              '<def:Origin Type="Assigned" Source="Sponsor"'),
    }
    s10 = v10.decode("utf-8")
    first_vl = re.search(r'<def:ValueListDef OID="([^"]+)"', s10).group(1)
    plants10 = {
        "S1 wrong DefineVersion": sub(v10, 'def:DefineVersion="1.0.0"', 'def:DefineVersion="2.0.0"'),
        "S2 2.x def attribute": sub(v10, "<ItemDef ", '<ItemDef def:CommentOID="COM.X" '),
        "S3 Description on ItemDef": sub(v10, "</ItemDef>", "<Description/></ItemDef>"),
        "S4 domain key not a variable": sub(v10, 'def:DomainKeys="', 'def:DomainKeys="NOSUCHVAR, '),
        "S5 dangling CodeListRef": sub(v10, '<CodeListRef CodeListOID="', '<CodeListRef CodeListOID="X'),
        "S6 value list OID off-convention": sub(v10, 'OID="%s"' % first_vl, 'OID="VL.%s"' % first_vl),
        "S7 ODM core: ItemDef without DataType": sub(v10, ' DataType="text"', ""),
        "S7 ODM core: ODM 1.3 element": sub(v10, "</CodeListItem>",
                                            '<Alias Context="nci:ExtCodeID" Name="C1"/></CodeListItem>'),
    }
    failures = []
    for name, data in plants20.items():
        got = oracles.v20(data)
        print("  self-test 2.0 %-40s -> %s" % (name, "RED (%s)" % got[0][:90] if got else "GREEN  <-- MISSED"))
        if not got:
            failures.append(name)
    for name, data in plants10.items():
        got = oracles.v10(data)
        tag = name.split()[0]
        hit = [g for g in got if g.startswith(tag)] if tag.startswith("S") else got
        print("  self-test 1.0 %-40s -> %s" % (name, "RED (%s)" % hit[0][:90] if hit else "GREEN  <-- MISSED"))
        if not hit:
            failures.append(name)
    return failures


# ---------------------------------------------------------------------------------------------------------------

def find_define20_xsd(start):
    d = os.path.abspath(start)
    while True:
        for cand in DEFINE20_XSD_CANDIDATES:
            p = os.path.join(d, cand)
            if os.path.isfile(p):
                return p
        parent = os.path.dirname(d)
        if parent == d:
            return None
        d = parent


def fetch_odm121(work):
    path = os.path.join(work, "ODM1-2-1-foundation.xsd")
    with urllib.request.urlopen(ODM121_URL, timeout=60) as r, open(path, "wb") as f:
        f.write(r.read())
    return path


def sha256(path):
    with open(path, "rb") as f:
        return hashlib.sha256(f.read()).hexdigest()


def main(argv):
    here = os.path.dirname(os.path.abspath(__file__))
    ap = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    ap.add_argument("--source", required=True, help="DataExchange-DatasetJson examples/sdtm/define.xml @ a379f49a")
    ap.add_argument("--out-dir", default=here)
    ap.add_argument("--define20-xsd", help="CDISC define2-0-0.xsd (default: found in cumba-corej / cumba-oss-corej)")
    ap.add_argument("--odm121-foundation", help="local CDISC ODM1-2-1-foundation.xsd (default: download, pinned)")
    ap.add_argument("--check", action="store_true", help="do not write; fail if the files in --out-dir differ")
    ap.add_argument("--self-test", action="store_true", help="also plant one defect per oracle and prove it reds")
    args = ap.parse_args(argv)

    with open(args.source, "rb") as f:
        src_bytes = f.read()
    if hashlib.md5(src_bytes).hexdigest() != SOURCE_MD5:
        sys.exit("source md5 %s != %s (%s)" % (hashlib.md5(src_bytes).hexdigest(), SOURCE_MD5, SOURCE_ID))
    if shutil.which("xmllint") is None:
        sys.exit("xmllint not found (libxml2-utils)")
    define20_xsd = args.define20_xsd or find_define20_xsd(here)
    if not define20_xsd or not os.path.isfile(define20_xsd):
        sys.exit("CDISC define2-0-0.xsd not found; pass --define20-xsd")

    with tempfile.TemporaryDirectory(prefix="derive-define-") as work:
        foundation = args.odm121_foundation or fetch_odm121(work)
        if sha256(foundation) != ODM121_SHA256:
            sys.exit("ODM1-2-1-foundation.xsd sha256 %s != %s" % (sha256(foundation), ODM121_SHA256))
        oracles = Oracles(define20_xsd, foundation, work)

        log = []
        src_root = ET.fromstring(src_bytes)
        v20_root = to_v20(src_root, log)
        v20 = serialise(v20_root, ODM13, DEF20, "define2-0-0.xsl", "2.0.0")
        v10 = serialise(to_v10(v20_root, log), ODM12, DEF10, "define1-0-0.xsl", "1.0.0 (CRT-DDS)")
        for line in log:
            print(line)

        failures = ["2.0 " + f for f in oracles.v20(v20)] + ["1.0 " + f for f in oracles.v10(v10)]
        print("oracle 2.0 (xmllint vs %s): %s" % (define20_xsd, "PASS" if not any(
            f.startswith("2.0") for f in failures) else "FAIL"))
        print("oracle 1.0 (S1-S6 structural + S7 ODM 1.2.1 core): %s" % ("PASS" if not any(
            f.startswith("1.0") for f in failures) else "FAIL"))
        for f in failures:
            print("  " + f)
        if failures:
            return 1
        if args.self_test:
            missed = self_test(oracles, v20, v10)
            if missed:
                print("self-test: %d planted defect(s) NOT detected: %s" % (len(missed), ", ".join(missed)))
                return 1
            print("self-test: every planted defect detected")

    outputs = {OUT20: v20, OUT10: v10}
    if args.check:
        differ = []
        for name, data in outputs.items():
            path = os.path.join(args.out_dir, name)
            with open(path, "rb") as f:
                if f.read() != data:
                    differ.append(name)
        print("check: " + ("identical" if not differ else "DIFFERS: " + ", ".join(differ)))
        return 1 if differ else 0
    for name, data in outputs.items():
        with open(os.path.join(args.out_dir, name), "wb") as f:
            f.write(data)
        print("wrote %s (%d bytes, md5 %s)" % (name, len(data), hashlib.md5(data).hexdigest()))
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
