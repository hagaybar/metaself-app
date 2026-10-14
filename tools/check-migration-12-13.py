#!/usr/bin/env python3
"""Run the version 12 -> 13 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-11-12.py`, this extracts every statement from
`ConversationMigration.kt` (rather than retyping them, which would test a copy), builds a version 12
database from the committed `12.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the ones `13.json` declares for the new table;
  - the new table has `13.json`'s shape;
  - every table that existed is declared identically in `12.json` and `13.json` and keeps exactly its
    rows;
  - D145's delete, `TrainerDao.deleteOwnMessage`'s statement (extracted from the DAO, not retyped):
    deleting the owner's message deletes it and the reply it got and nothing else, and a reply's id
    deletes nothing.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-12-13.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/ConversationMigration.kt"
DAO = ROOT / "app/src/main/java/com/metaself/app/data/trainer/TrainerDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
NEW_TABLES = ("trainer_messages",)


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
    expected = declared(13, NEW_TABLES)
    if sorted(migration) != sorted(expected):
        missing = sorted(set(expected) - set(migration))
        extra = sorted(set(migration) - set(expected))
        failures.append(
            "the migration's statements are not the ones 13.json declares:\n"
            + "".join(f"  only in 13.json:   {s}\n" for s in missing)
            + "".join(f"  only in migration: {s}\n" for s in extra)
        )

    db = build(12)
    old_tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-12 table, so "untouched" means something for each.
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

    fresh = build(13)
    for table in NEW_TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 13.json:\n"
                            f"  migrated: {shape(db, table)}\n  13.json:  {shape(fresh, table)}")

    for table in old_tables:
        if entities(12)[table] != entities(13)[table]:
            failures.append(f"`{table}` is declared differently in 12.json and 13.json")

    # Invented: two of his messages, each answered; deleting the first takes its reply and nothing else.
    columns = "(id, atMillis, fromOwner, text, model, answered, replyTo)"
    rows = [
        (1, 1000, 1, "a", None, 1, None),
        (2, 2000, 0, "b", "m", 0, 1),
        (3, 3000, 1, "c", None, 1, None),
        (4, 4000, 0, "d", "m", 0, 3),
    ]
    db.executemany(f"INSERT INTO trainer_messages {columns} VALUES (?, ?, ?, ?, ?, ?, ?)", rows)
    delete = dao_query("deleteOwnMessage").replace(":id", "?")
    db.execute(delete, (4, 4))
    if [r[0] for r in db.execute("SELECT id FROM trainer_messages ORDER BY id")] != [1, 2, 3, 4]:
        failures.append("deleting by a reply's id deleted something")
    db.execute(delete, (1, 1))
    if [r[0] for r in db.execute("SELECT id FROM trainer_messages ORDER BY id")] != [3, 4]:
        failures.append("deleting his message did not delete exactly it and its reply")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statement(s), {len(old_tables)} tables untouched, the new table matches 13.json, "
          "the delete takes a message and its reply only")


if __name__ == "__main__":
    main()
