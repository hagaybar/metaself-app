#!/usr/bin/env python3
"""Run the version 15 -> 16 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-14-15.py`, this extracts every statement from
`RecordIdIndexMigration.kt` (rather than retyping them, which would test a copy), builds a version 15
database from the committed `15.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the index statements `16.json` adds to `workouts`
    and `sleep_sessions` (with `${TABLE_NAME}` filled in);
  - both tables then have `16.json`'s shape, and every table is otherwise declared identically in
    `15.json` and `16.json` and keeps exactly its rows;
  - the query plans (D153): `SleepDao.daysOf(recordId)`, `deleteByRecordId`, `daysBetween`,
    `deleteBetween`, `WorkoutDao.daysOfSynced`, `deleteSynced` and `visibleSyncedBetween` scan their
    table before the migration and search one of the new indexes after it. Each statement is
    extracted from the DAO, not retyped.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.
  - The plan SQLite on the phone chooses: that build may differ from this one, and a database that has
    been ANALYZEd may plan differently. Room does not run ANALYZE. With one row a table, as here, the
    planner has no statistics either way, which is also the phone's state.

Usage:  python3 tools/check-migration-15-16.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/RecordIdIndexMigration.kt"
DAOS = ROOT / "app/src/main/java/com/metaself/app/data/health"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
TABLES = ("workouts", "sleep_sessions")

# (DAO file, function, the parameter list that tells overloads apart, plan before, plan after)
PLANS = (
    ("SleepDao", "daysOf", "recordId: String", "SCAN sleep_sessions",
     "SEARCH sleep_sessions USING INDEX index_sleep_sessions_recordId (recordId=?)"),
    ("SleepDao", "deleteByRecordId", "recordId: String", "SCAN sleep_sessions",
     "SEARCH sleep_sessions USING INDEX index_sleep_sessions_recordId (recordId=?)"),
    ("SleepDao", "daysBetween", "from: Long, to: Long", "SCAN sleep_sessions",
     "SEARCH sleep_sessions USING INDEX index_sleep_sessions_startMillis (startMillis>? AND startMillis<?)"),
    ("SleepDao", "deleteBetween", "from: Long, to: Long", "SCAN sleep_sessions",
     "SEARCH sleep_sessions USING INDEX index_sleep_sessions_startMillis (startMillis>? AND startMillis<?)"),
    ("WorkoutDao", "daysOfSynced", "originId: String", "SCAN workouts",
     "SEARCH workouts USING INDEX index_workouts_originId (originId=?)"),
    ("WorkoutDao", "deleteSynced", "originId: String", "SCAN workouts",
     "SEARCH workouts USING INDEX index_workouts_originId (originId=?)"),
    ("WorkoutDao", "visibleSyncedBetween", "from: Long, to: Long", "SCAN workouts",
     "SEARCH workouts USING INDEX index_workouts_startedAtMillis (startedAtMillis>? AND startedAtMillis<?)"),
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


def dao_query(dao, function, params):
    """The @Query string on [dao]'s function named [function] whose parameters are exactly [params]."""
    src = (DAOS / f"{dao}.kt").read_text()
    pattern = (r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),?\s*\)\s*suspend fun '
               + function + r'\(' + re.escape(params) + r'\)')
    match = re.search(pattern, src)
    if not match:
        sys.exit(f"FAIL\n - no @Query found for {dao}.{function}({params})")
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
    added = [s for t in TABLES for s in indices(16, t) if s not in indices(15, t)]
    if sorted(migration) != sorted(added):
        failures.append(
            "the migration's statements are not the indexes 16.json adds:\n"
            + "".join(f"  only in 16.json:   {s}\n" for s in sorted(set(added) - set(migration)))
            + "".join(f"  only in migration: {s}\n" for s in sorted(set(migration) - set(added)))
        )

    db = build(15)
    tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-15 table, so "untouched" means something for each.
    # Foreign keys are off (sqlite3's default, and the state a Room migration runs in).
    for table in tables:
        columns = db.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        values = [1 if kind.upper() in ("INTEGER", "REAL") else f"{table}.{name}"
                  for _, name, kind, _, _, _ in columns]
        db.execute(f"INSERT INTO `{table}` VALUES ({', '.join('?' for _ in values)})", values)
    before = everything(db, tables)

    queries = [(f"{d}.{f}", dao_query(d, f, p), old, new) for d, f, p, old, new in PLANS]
    for name, sql, old, _ in queries:
        got = plan(db, sql)
        if not any(step.startswith(old) for step in got):
            failures.append(f"{name} before the migration: expected {old!r}, got {got}")

    for sql in migration:
        db.execute(sql)

    if everything(db, tables) != before:
        failures.append("a row changed in the migration")

    fresh = build(16)
    for table in TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 16.json:\n"
                            f"  migrated: {shape(db, table)}\n  16.json:  {shape(fresh, table)}")
        a, b = dict(entities(15)[table]), dict(entities(16)[table])
        a.pop("indices"), b.pop("indices")
        if a != b:
            failures.append(f"`{table}` differs between 15.json and 16.json in more than its indexes")
    if set(entities(15)) != set(entities(16)):
        failures.append("15.json and 16.json do not declare the same tables")
    for table in tables:
        if table not in TABLES and entities(15)[table] != entities(16)[table]:
            failures.append(f"`{table}` is declared differently in 15.json and 16.json")

    for name, sql, _, new in queries:
        got = plan(db, sql)
        print(f"  {name}: {sql}\n    -> {' | '.join(got)}")
        if new not in got:
            failures.append(f"{name} after the migration: expected {new!r}, got {got}")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statement(s), {len(tables)} tables' rows untouched, {', '.join(TABLES)} match "
          f"16.json, {len(queries)} queries now search an index instead of scanning")


if __name__ == "__main__":
    main()
