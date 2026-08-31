# `coverage/` — aggregate JaCoCo coverage

A single sink module with no source of its own. It depends on every
`lib/*` module and runs `jacoco:report-aggregate` at `verify`.

There used to be two sub-modules, `coverage/dev` and `coverage/main`,
one per build profile. Those profiles are gone (see the root pom's
top-level `<modules>`: they were `activeByDefault` and therefore never
actually active), so there is one reactor and one aggregate.

## Updating the module list

⚠ **The `<dependencies>` in `coverage/pom.xml` ARE the report.**
`jacoco:report-aggregate` builds from that list — it does *not* scan the
reactor. Both former sub-modules had an **empty** `<dependencies>`, so
the aggregate they produced held zero classes while the per-module
reports held real data, and `sonar.coverage.jacoco.xmlReportPaths`
pointed straight at that empty file.

So: whenever you add or remove a `lib/*` module in the root pom's
`<modules>`, mirror it here. The `coverage-covers-every-module`
enforcer rule checks both directions and fails the build naming the
artifact — without it, forgetting is silent.

## Output path

After `mvn -T1C verify`:

```
coverage/target/site/jacoco-aggregate/jacoco.xml
```

To confirm it has content, count occurrences — **not** lines:

```bash
grep -o '<class ' coverage/target/site/jacoco-aggregate/jacoco.xml | wc -l
```

JaCoCo writes the whole XML on one line, so `grep -c` reports `1`
regardless of what the report contains.
