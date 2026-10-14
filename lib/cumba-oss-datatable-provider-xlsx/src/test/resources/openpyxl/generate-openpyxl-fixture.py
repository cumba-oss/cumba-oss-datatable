#!/usr/bin/env python3
"""Writes uncalculated-formulas.xlsx for PLAN-reader-value-semantics-alignment (P2, Q37).

An EXTERNAL producer, deliberately not POI: openpyxl -- and therefore pandas.DataFrame.to_excel --
never evaluates formulas, so every formula cell is written with an EMPTY cached value element
(<c r="B2"><f>1+1</f><v></v></c>), and a string that is one of Excel's error codes is written as
an error cell (<c r="D1" t="e"><v>#REF!</v></c>). Those are the two shapes the owner rulings
Q37(i) (a formula without a cached value reads MIS_ERROR, logged once per column) and Q37(ii)
(an unreadable header keeps its column under a positional name, logged) are about.

Sheet DATA:
    ID  | CALC (formulas) | NUM (1.5, blank, 3) | #REF! (10, 20, 30) | SITE
Run: python3 generate-openpyxl-fixture.py   (openpyxl 3.1.5; writes next to itself; the
cell content is deterministic, the zip timestamps are not)
"""
import datetime
from pathlib import Path

import openpyxl

wb = openpyxl.Workbook()
wb.properties.created = datetime.datetime(2026, 10, 14)
wb.properties.modified = datetime.datetime(2026, 10, 14)
ws = wb.active
ws.title = "DATA"
ws.append(["ID", "CALC", "NUM", "#REF!", "SITE"])
ws.append(["r1", "=1+1", 1.5, 10, "S1"])
ws.append(["r2", "=B2*2", None, 20, "S2"])
ws.append(["r3", "=B3+1", 3, 30, "S3"])
wb.save(Path(__file__).with_name("uncalculated-formulas.xlsx"))
