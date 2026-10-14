#!/usr/bin/env python3
"""Run the version 14 -> 15 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-13-14.py`, this extracts every statement from
`ReadingIndexMigration.kt` (rather than retyping them, which would test a copy), builds a version 14
database from the committed `14.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the index statements `15.json` adds to
    `health_readings` (with `${TABLE_NAME}` filled in);
  - `health_readings` then has `15.json`'s shape, and every table is otherwise declared identically in
    `14.json` and `15.json` and keeps exactly its rows;
  - the query plans: `HealthReadingDao.daysOf(recordId)` and `deleteByRecordId` scan the whole table
    before the migration and search the `recordId` index after it; `onDay` walks the whole
    `(kind, epochDay, startMillis)` index before and searches the `epochDay` index after. Each statement
    is extracted from the DAO, not retyped.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.
  - The plan SQLite on the phone chooses: that build may differ from this one, and a database that has
    been ANALYZEd may plan differently. Room does not run ANALYZE.

Usage:  python3 tools/check-migration-14-15.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/ReadingIndexMigration.kt"
DAO = ROOT / "app/src/main/java/com/metaself/app/data/health/HealthReadingDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
TABLE = "health_readings"

# (DAO function, the parameter list that tells overloads apart, plan before, plan after)
PLANS = (
    ("daysOf", "recordId: String", "SCAN health_readings",
     "SEARCH health_readings USING INDEX index_health_readings_recordId (recordId=?)"),
    ("deleteByRecordId", "recordId: String", "SCAN health_readings",
     "SEARCH health_readings USING INDEX index_health_readings_recordId (recordId=?)"),
    ("onDay", "epochDay: Long", "SCAN health_readings USING INDEX index_health_readings_kind_epochDay_startMillis",
     "SEARCH health_readings USING INDEX index_health_readings_epochDay (epochDay=?)"),
)


def statements():
    """Every SQL string the migration executes, in source order."""
    raw = MIGRATION.read_text()
    src = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("//"))
    found = []
    for match in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found.append("".join(parts).replace('\\"', '"'))
    return found


def dao_query(function, params):
    """The @Query string on the DAO function named [function] whose parameters are exactly [params]."""
    src = DAO.read_text()
    pattern = (r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),?\s*\)\s*suspend fun '
               + function + r'\(' + re.escape(params) + r'\)')
    match = re.search(pattern, src)
    if not match:
        sys.exit(f"FAIL\n - no @Query found for {function}({params})")
    return "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1)))


def entities(version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    return {e["tableName"]: e for e in schema["database"]["entities"]}


def indices(version, table):
    return [i["createSql"].replace("${TABLE_NAME}", table) for i in entities(version)[table].get("indices", [])]


def build(version):
    db = sqlite3.connect(":memory:")
    for table, entity in entities(version).items():
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for sql in indices(version, table):
            db.execute(sql)
    return db


def shape(db, table):
    """Columns, indices (without the creation-order number) and foreign keys."""
    return (
        db.execute(f"PRAGMA table_info(`{table}`)").fetchall(),
        sorted(row[1:] for row in db.execute(f"PRAGMA index_list(`{table}`)").fetchall()),
        db.execute(f"PRAGMA foreign_key_list(`{table}`)").fetchall(),
    )


def everything(db, tables):
    return {t: sorted(db.execute(f"SELECT * FROM `{t}`").fetchall(), key=repr) for t in tables}


def plan(db, sql):
    bound = re.sub(r":\w+", "?", sql)
    return [row[3] for row in db.execute("EXPLAIN QUERY PLAN " + bound, [1] * bound.count("?"))]


def main():
    failures = []

    migration = statements()
    added = [s for s in indices(15, TABLE) if s not in indices(14, TABLE)]
    if sorted(migration) != sorted(added):
        failures.append(
            "the migration's statements are not the indexes 15.json adds:\n"
            + "".join(f"  only in 15.json:   {s}\n" for s in sorted(set(added) - set(migration)))
            + "".join(f"  only in migration: {s}\n" for s in sorted(set(migration) - set(added)))
        )

    db = build(14)
    tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-14 table, so "untouched" means something for each.
    # Foreign keys are off (sqlite3's default, and the state a Room migration runs in).
    for table in tables:
        columns = db.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        values = [1 if kind.upper() in ("INTEGER", "REAL") else f"{table}.{name}"
                  for _, name, kind, _, _, _ in columns]
        db.execute(f"INSERT INTO `{table}` VALUES ({', '.join('?' for _ in values)})", values)
    before = everything(db, tables)

    queries = [(f, dao_query(f, p), old, new) for f, p, old, new in PLANS]
    for function, sql, old, _ in queries:
        got = plan(db, sql)
        if old not in got:
            failures.append(f"{function} before the migration: expected {old!r}, got {got}")

    for sql in migration:
        db.execute(sql)

    if everything(db, tables) != before:
        failures.append("a row changed in the migration")

    fresh = build(15)
    if shape(db, TABLE) != shape(fresh, TABLE):
        failures.append(f"`{TABLE}` after the migration differs from 15.json:\n"
                        f"  migrated: {shape(db, TABLE)}\n  15.json:  {shape(fresh, TABLE)}")
    if set(entities(14)) != set(entities(15)):
        failures.append("14.json and 15.json do not declare the same tables")
    for table in tables:
        if table != TABLE and entities(14)[table] != entities(15)[table]:
            failures.append(f"`{table}` is declared differently in 14.json and 15.json")
    a, b = dict(entities(14)[TABLE]), dict(entities(15)[TABLE])
    a.pop("indices"), b.pop("indices")
    if a != b:
        failures.append(f"`{TABLE}` differs between 14.json and 15.json in more than its indexes")

    for function, sql, _, new in queries:
        got = plan(db, sql)
        print(f"  {function}: {sql}\n    -> {' | '.join(got)}")
        if new not in got:
            failures.append(f"{function} after the migration: expected {new!r}, got {got}")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statement(s), {len(tables)} tables' rows untouched, `{TABLE}` matches 15.json, "
          f"{len(queries)} queries now search an index instead of scanning")


if __name__ == "__main__":
    main()
