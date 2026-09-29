#!/usr/bin/env python3
"""Run the version 11 -> 12 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-4-5.py`, this extracts every statement from
`LetterMigration.kt` (rather than retyping them, which would test a copy), builds a version 11
database from the committed `11.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the ones `12.json` declares for the new table and
    its index;
  - the new table has `12.json`'s shape;
  - every table that existed is declared identically in `11.json` and `12.json` and keeps exactly its
    rows;
  - one letter a week: a second row for the same week is refused by the unique index, while two
    different weeks are accepted.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-11-12.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/LetterMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
NEW_TABLES = ("weekly_letters",)


def statements():
    """Every SQL string the migration executes, in source order."""
    raw = MIGRATION.read_text()
    src = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("//"))
    found = []
    for match in re.finditer(r'db\.execSQL\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found.append("".join(parts).replace('\\"', '"'))
    return found


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


def refused(db, sql):
    try:
        db.execute(sql)
        return False
    except sqlite3.IntegrityError:
        return True


def main():
    failures = []

    migration = statements()
    expected = declared(12, NEW_TABLES)
    if sorted(migration) != sorted(expected):
        missing = sorted(set(expected) - set(migration))
        extra = sorted(set(migration) - set(expected))
        failures.append(
            "the migration's statements are not the ones 12.json declares:\n"
            + "".join(f"  only in 12.json:   {s}\n" for s in missing)
            + "".join(f"  only in migration: {s}\n" for s in extra)
        )

    db = build(11)
    old_tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-11 table, so "untouched" means something for each.
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

    fresh = build(12)
    for table in NEW_TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 12.json:\n"
                            f"  migrated: {shape(db, table)}\n  12.json:  {shape(fresh, table)}")

    for table in old_tables:
        if entities(11)[table] != entities(12)[table]:
            failures.append(f"`{table}` is declared differently in 11.json and 12.json")

    # Invented values: two different weeks, then the first week again.
    columns = "(weekMonday, createdAtMillis, figures, letter, model)"
    db.execute(f"INSERT INTO weekly_letters {columns} VALUES (20696, 1000, '{{}}', '{{}}', 'm')")
    db.execute(f"INSERT INTO weekly_letters {columns} VALUES (20703, 1000, '{{}}', '{{}}', 'm')")
    if not refused(db, f"INSERT INTO weekly_letters {columns} VALUES (20696, 2000, '{{}}', '{{}}', 'm')"):
        failures.append("a second letter for the same week was accepted")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statements, {len(old_tables)} tables untouched, the new table matches 12.json")

if __name__ == "__main__":
    main()
