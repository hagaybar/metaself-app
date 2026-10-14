#!/usr/bin/env python3
"""Run the version 13 -> 14 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-12-13.py`, this extracts every statement from
`SheetMigration.kt` (rather than retyping them, which would test a copy), builds a version 13
database from the committed `13.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the ones `14.json` declares for the new table;
  - the new table has `14.json`'s shape;
  - every table that existed is declared identically in `13.json` and `14.json` and keeps exactly its
    rows;
  - D150's versions: a rewrite and an owner's version restoring it are stored and read back in order by
    `TrainerDao.allSheets`'s statement (extracted from the DAO, not retyped).

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-13-14.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/SheetMigration.kt"
DAO = ROOT / "app/src/main/java/com/metaself/app/data/trainer/TrainerDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
NEW_TABLES = ("trainer_sheets",)


def statements():
    """Every SQL string the migration executes, in source order."""
    raw = MIGRATION.read_text()
    src = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("//"))
    found = []
    for match in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found.append("".join(parts).replace('\\"', '"'))
    return found


def dao_query(function):
    """The @Query string on the DAO function named [function]."""
    src = DAO.read_text()
    match = re.search(r'@Query\(\s*"((?:[^"\\]|\\.)*)"\s*\)\s*suspend fun ' + function + r'\(', src)
    if not match:
        sys.exit(f"FAIL\n - no @Query found for {function}")
    return match.group(1)


def entities(version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    return {e["tableName"]: e for e in schema["database"]["entities"]}


def declared(version, tables=None):
    """Every CREATE statement the exported schema declares, tables first within each entity."""
    out = []
    for table, entity in entities(version).items():
        if tables and table not in tables:
            continue
        out.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            out.append(index["createSql"].replace("${TABLE_NAME}", table))
    return out


def build(version):
    db = sqlite3.connect(":memory:")
    for sql in declared(version):
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


def main():
    failures = []

    migration = statements()
    expected = declared(14, NEW_TABLES)
    if sorted(migration) != sorted(expected):
        missing = sorted(set(expected) - set(migration))
        extra = sorted(set(migration) - set(expected))
        failures.append(
            "the migration's statements are not the ones 14.json declares:\n"
            + "".join(f"  only in 14.json:   {s}\n" for s in missing)
            + "".join(f"  only in migration: {s}\n" for s in extra)
        )

    db = build(13)
    old_tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-13 table, so "untouched" means something for each.
    # Foreign keys are off (sqlite3's default, and the state a Room migration runs in).
    for table in old_tables:
        columns = db.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        values = [1 if kind.upper() in ("INTEGER", "REAL") else f"{table}.{name}"
                  for _, name, kind, _, _, _ in columns]
        db.execute(f"INSERT INTO `{table}` VALUES ({', '.join('?' for _ in values)})", values)
    before = everything(db, old_tables)

    for sql in migration:
        db.execute(sql)

    if everything(db, old_tables) != before:
        failures.append("a table that existed before the migration changed")

    fresh = build(14)
    for table in NEW_TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 14.json:\n"
                            f"  migrated: {shape(db, table)}\n  14.json:  {shape(fresh, table)}")

    for table in old_tables:
        if entities(13)[table] != entities(14)[table]:
            failures.append(f"`{table}` is declared differently in 13.json and 14.json")

    # Invented: a rewrite, then a version of his going back to before it; read back oldest first.
    columns = "(id, atMillis, facts, madeBy, model, restoredFrom)"
    rows = [(2, 2000, "[]", "OWNER", None, 1), (1, 1000, "[]", "REWRITE", "m", None)]
    db.executemany(f"INSERT INTO trainer_sheets {columns} VALUES (?, ?, ?, ?, ?, ?)", rows)
    read = [r[0] for r in db.execute(dao_query("allSheets"))]
    if read != [1, 2]:
        failures.append(f"the versions did not read back oldest first: {read}")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statement(s), {len(old_tables)} tables untouched, the new table matches 14.json, "
          "the versions read back oldest first")


if __name__ == "__main__":
    main()
