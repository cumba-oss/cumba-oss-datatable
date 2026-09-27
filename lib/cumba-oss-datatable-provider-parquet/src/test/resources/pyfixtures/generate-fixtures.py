#!/usr/bin/env python3
"""Generates the Parquet fixture(s) in this directory, for the Parquet provider module's tests.

WHY THIS EXISTS
---------------
PLAN-bare-nan-is-mis (owner ruling E5, 2026-09-25: "NaN should get mis"). The one REAL source of a
bare NaN in the stack is a pandas / pyarrow NaN in a Parquet DOUBLE or FLOAT column. The module's
other tests write Parquet through parquet-java, i.e. the same library family the reader uses; this
fixture is produced by pyarrow instead, so the reader meets the bit patterns a real Python export
writes rather than ones a Java writer chose. Written once and committed as a binary: Python is not
part of the build and no test shells out to it.

Regenerate with (pyarrow 24.0.0 / numpy 2.4.3 at the time of writing):

    python3 generate-fixtures.py

The script asserts the stored bits after writing, so a platform that produces a different NaN
pattern fails here rather than silently committing a fixture that tests something else.

bare_nan.parquet -- 4 rows, two nullable columns, no dictionary encoding (a dictionary would merge
the two NaN patterns into one entry), no compression, no statistics:

    row  DBL (DOUBLE)                        FLT (FLOAT)
    0    np.nan      0x7FF8000000000000      np.float32 nan  0x7FC00000
    1    0.0 / 0.0   0xFFF8000000000000      0.0f / 0.0f     0xFFC00000
    2    null                                null
    3    1.25                                1.25

Rows 0 and 1 are the two canonical quiet NaNs of ruling N1 (the arithmetic NaN of x86 carries the
sign bit); row 2 is a Parquet null, which the reader stores as MissingValue.MIS (the format has one
kind of null); row 3 is a present value.

negative_zero.parquet -- PLAN-negative-zero-on-load (NZL O1, owner 2026-09-27: "we drop the sign from
any 0.0 so a -0.0 gets read as 0.0"). pyarrow writes a signed zero as its raw IEEE bits, so this is
the real-world source of a -0.0 in a Parquet DOUBLE or FLOAT column. 3 rows, two non-null columns,
same writer options as above (no dictionary, which would merge the two zeros into one entry):

    row  DBL (DOUBLE)                        FLT (FLOAT)
    0    -0.0        0x8000000000000000      -0.0f       0x80000000
    1     0.0        0x0000000000000000       0.0f       0x00000000
    2    -1.5        0xBFF8000000000000      -1.5f       0xBFC00000

Row 0 is the negative zero, row 1 the positive-zero control, row 2 a non-zero control.
"""

import struct
import warnings

import numpy as np
import pyarrow as pa
import pyarrow.parquet as pq

OUT = "bare_nan.parquet"


def bits64(x):
    return struct.unpack("<Q", struct.pack("<d", float(x)))[0]


with warnings.catch_warnings():
    warnings.simplefilter("ignore", RuntimeWarning)
    neg_nan64 = np.float64(0.0) / np.float64(0.0)
    neg_nan32 = np.float32(0.0) / np.float32(0.0)

dbl = np.array([np.nan, neg_nan64, 0.0, 1.25], dtype=np.float64)
flt = np.array([np.float32(np.nan), neg_nan32, 0.0, 1.25], dtype=np.float32)
null_mask = np.array([False, False, True, False])

assert dbl.view(np.uint64)[0] == 0x7FF8000000000000, hex(dbl.view(np.uint64)[0])
assert dbl.view(np.uint64)[1] == 0xFFF8000000000000, hex(dbl.view(np.uint64)[1])
assert flt.view(np.uint32)[0] == 0x7FC00000, hex(flt.view(np.uint32)[0])
assert flt.view(np.uint32)[1] == 0xFFC00000, hex(flt.view(np.uint32)[1])

table = pa.table({
    "DBL": pa.array(dbl, type=pa.float64(), mask=null_mask),
    "FLT": pa.array(flt, type=pa.float32(), mask=null_mask),
})
pq.write_table(table, OUT, use_dictionary=False, compression="NONE", write_statistics=False)

# Verify what was actually written, from the file.
back = pq.read_table(OUT)
d = back.column("DBL").combine_chunks()
f = back.column("FLT").combine_chunks()
assert d.null_count == 1 and f.null_count == 1
assert not d.is_valid()[2].as_py() and not f.is_valid()[2].as_py()
d_bits = np.frombuffer(d.buffers()[1], dtype=np.uint64, count=len(d), offset=d.offset * 8)
f_bits = np.frombuffer(f.buffers()[1], dtype=np.uint32, count=len(f), offset=f.offset * 4)
assert d_bits[0] == 0x7FF8000000000000 and d_bits[1] == 0xFFF8000000000000, [hex(b) for b in d_bits]
assert f_bits[0] == 0x7FC00000 and f_bits[1] == 0xFFC00000, [hex(b) for b in f_bits]
assert d[3].as_py() == 1.25 and f[3].as_py() == 1.25
print(OUT, "written and verified:", [hex(b) for b in d_bits], [hex(b) for b in f_bits])


# --- negative_zero.parquet (PLAN-negative-zero-on-load) -------------------------------------------

OUT_NZ = "negative_zero.parquet"

nz_dbl = np.array([-0.0, 0.0, -1.5], dtype=np.float64)
nz_flt = np.array([-0.0, 0.0, -1.5], dtype=np.float32)
assert nz_dbl.view(np.uint64)[0] == 0x8000000000000000, hex(nz_dbl.view(np.uint64)[0])
assert nz_flt.view(np.uint32)[0] == 0x80000000, hex(nz_flt.view(np.uint32)[0])

nz_table = pa.table({
    "DBL": pa.array(nz_dbl, type=pa.float64()),
    "FLT": pa.array(nz_flt, type=pa.float32()),
})
pq.write_table(nz_table, OUT_NZ, use_dictionary=False, compression="NONE", write_statistics=False)

nz_back = pq.read_table(OUT_NZ)
nd = nz_back.column("DBL").combine_chunks()
nf = nz_back.column("FLT").combine_chunks()
assert nd.null_count == 0 and nf.null_count == 0
nd_bits = np.frombuffer(nd.buffers()[1], dtype=np.uint64, count=len(nd), offset=nd.offset * 8)
nf_bits = np.frombuffer(nf.buffers()[1], dtype=np.uint32, count=len(nf), offset=nf.offset * 4)
assert list(nd_bits) == [0x8000000000000000, 0, 0xBFF8000000000000], [hex(b) for b in nd_bits]
assert list(nf_bits) == [0x80000000, 0, 0xBFC00000], [hex(b) for b in nf_bits]
print(OUT_NZ, "written and verified:", [hex(b) for b in nd_bits], [hex(b) for b in nf_bits])
