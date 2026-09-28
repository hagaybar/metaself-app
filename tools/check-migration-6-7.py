#!/usr/bin/env python3
"""Run the version 6 -> 7 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-5-6.py`, this extracts every statement from
`WorkoutFileMigration.kt` (rather than retyping them, which would test a copy), builds a version 6
database from the committed `6.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - every statement is an ADD COLUMN on `workouts` whose column definition is, character for
    character, one `7.json` declares for `workouts`, and together they add every column 7.json has
    that 6.json does not;
  - `workouts` after the migration has the same columns, types, nullability, defaults, keys, indices
    and foreign keys as a `workouts` built fresh from `7.json`;
  - every other table is declared identically in 6.json and 7.json;
  - every table keeps exactly the rows it had, and the new columns of the existing workout are null.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-6-7.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/WorkoutFileMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
TABLE = "workouts"
ADD = re.compile(r"^ALTER TABLE `workouts` ADD COLUMN (`(\w+)` .+)$")


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


def declared(version):
    """Every CREATE statement the exported schema declares, tables first within each entity."""
    out = []
    for table, entity in entities(version).items():
        out.append(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            out.append(index["createSql"].replace("${TABLE_NAME}", table))
    return out


def column_definitions(create_sql):
    """The column definitions inside a CREATE TABLE, split at top-level commas."""
    body = create_sql[create_sql.index("(") + 1:create_sql.rindex(")")]
    out, depth, current = [], 0, ""
    for ch in body:
        if ch == "(":
            depth += 1
        elif ch == ")":
            depth -= 1
        if ch == "," and depth == 0:
            out.append(current.strip())
            current = ""
        else:
            current += ch
    out.append(current.strip())
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
    old, new = entities(6), entities(7)

    if set(old) != set(new):
        failures.append(f"7.json has different tables: {sorted(set(old) ^ set(new))}")
    for table in sorted(set(old) & set(new) - {TABLE}):
        if old[table] != new[table]:
            failures.append(f"`{table}` is declared differently in 6.json and 7.json")

    old_columns = column_definitions(old[TABLE]["createSql"])
    new_columns = column_definitions(new[TABLE]["createSql"])
    if new_columns[:len(old_columns)] != old_columns:
        failures.append("7.json's `workouts` does not begin with 6.json's columns")
    wanted = new_columns[len(old_columns):]

    migration = statements()
    added = []
    for sql in migration:
        match = ADD.match(sql)
        if not match:
            failures.append(f"not an ADD COLUMN on `workouts`: {sql}")
            continue
        added.append(match.group(1))
    if added != wanted:
        failures.append(f"the columns added are not 7.json's new ones:\n  migration: {added}\n  7.json:    {wanted}")

    db = build(6)
    tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-6 table, so "untouched" means something for each.
    for table in tables:
        columns = db.execute(f"PRAGMA table_info(`{table}`)").fetchall()
        values = [1 if kind.upper() in ("INTEGER", "REAL") else f"{table}.{name}"
                  for _, name, kind, _, _, _ in columns]
        db.execute(f"INSERT INTO `{table}` VALUES ({', '.join('?' for _ in values)})", values)
    before = everything(db, tables)

    for sql in migration:
        db.execute(sql)

    after = everything(db, tables)
    for table in tables:
        if table == TABLE:
            kept = [row[:len(old_columns)] for row in after[table]]
            if kept != before[table]:
                failures.append("an existing workout changed")
            if any(any(v is not None for v in row[len(old_columns):]) for row in after[table]):
                failures.append("an existing workout's new columns are not null")
        elif after[table] != before[table]:
            failures.append(f"`{table}` changed")

    fresh = build(7)
    if shape(db, TABLE) != shape(fresh, TABLE):
        failures.append(f"`{TABLE}` after the migration differs from 7.json:\n"
                        f"  migrated: {shape(db, TABLE)}\n  7.json:   {shape(fresh, TABLE)}")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statements, {len(tables) - 1} other tables untouched, "
          f"`{TABLE}` matches 7.json and keeps its row")


if __name__ == "__main__":
    main()
