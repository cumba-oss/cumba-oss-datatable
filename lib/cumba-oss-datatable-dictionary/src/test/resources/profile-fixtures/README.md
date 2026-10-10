# Profile fixtures (PLAN-dictionary-backed-formats B1)

Hand-written house files in the shapes the module's tests write — a MedDRA file with and without
`hierarchy_by_level`, an all-uppercase PT level, WHODrug, LOINC (a code without a name), UNII (a code
that also sits in another registry with another decode — the registry-choice case), SNOMED, MED-RT
with a `medrt-base` registry that must never serve a decode, a file breaking the key contract, and a
house-format-2 file with a `decodes` section. Moved here from the retired Phase A cross-reader drift
test (`cumba-datatable-manager-local-core`, `dictionary-drift/synthetic/`), byte-identical.

`ProfileEquivalenceTest` reads every file here and every engine dummy file
(`../engine-dictionaries/`) in both load profiles of `ValueMapDictionary` — `FULL` (the engine's tree
parse) and `FORMAT` (the streaming format subset) — and pins that they answer the format-path read
API identically.
