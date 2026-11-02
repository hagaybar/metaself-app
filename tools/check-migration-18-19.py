#!/usr/bin/env python3
"""Run the version 18 -> 19 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI. This
extracts every statement from `ProposalMigration.kt`, builds a version 18 database from the committed
`18.json`, puts invented messages in `trainer_messages`, and runs the migration under TZ=UTC.

WHAT IT PROVES
  - every statement parses and executes;
  - `trainer_messages` then has the columns `19.json` declares, the new one nullable with no default,
    and every other table is declared identically in `18.json` and `19.json`;
  - no message is lost, no word, conversation or Read line changes, and every older message has no card.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  TZ=UTC python3 tools/check-migration-18-19.py
"""

import json
import os
import pathlib
import re
import sqlite3
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/ProposalMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"


def statements():
    source = MIGRATION.read_text()
    calls = re.findall(r"db\.execSQL\(\s*((?:\"(?:[^\"\\]|\\.)*\"\s*\+?\s*)+),?\s*\)", source)
    return ["".join(re.findall(r"\"((?:[^\"\\]|\\.)*)\"", call)) for call in calls]


def tables(version):
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    return {e["tableName"]: e for e in schema["database"]["entities"]}


def main():
    os.environ["TZ"] = "UTC"
    time.tzset()
    old, new = tables(18), tables(19)
    assert set(old) == set(new), set(old) ^ set(new)
    for name in old:
        if name != "trainer_messages":
            assert old[name] == new[name], f"{name} differs between 18.json and 19.json"
    db = sqlite3.connect(":memory:")
    db.execute(old["trainer_messages"]["createSql"].replace("${TABLE_NAME}", "trainer_messages"))
    rows = [
        (1, 1_000, 1, "Invented.", None, 1, None, "talk:1", None),
        (2, 2_000, 0, "Invented reply.", "m", 0, 1, "talk:1", ""),
    ]
    db.executemany("INSERT INTO trainer_messages (id, atMillis, fromOwner, text, model, answered, replyTo, conversation, opened) VALUES (?,?,?,?,?,?,?,?,?)", rows)
    found = statements()
    assert len(found) == 1, found
    for statement in found:
        db.execute(statement)
    columns = {row[1]: row for row in db.execute("PRAGMA table_info(trainer_messages)")}
    declared = {f["columnName"] for f in new["trainer_messages"]["fields"]}
    assert set(columns) == declared, (set(columns), declared)
    field = next(f for f in new["trainer_messages"]["fields"] if f["columnName"] == "proposal")
    assert not field.get("notNull") and field.get("affinity") == "TEXT", field
    assert columns["proposal"][3] == 0 and columns["proposal"][4] is None, columns["proposal"]
    got = list(db.execute("SELECT id, atMillis, fromOwner, text, model, answered, replyTo, conversation, opened FROM trainer_messages ORDER BY id"))
    assert got == rows, got
    assert [row[0] for row in db.execute("SELECT proposal FROM trainer_messages")] == [None, None]
    print("ok: 1 statement; columns as 19.json; other tables unchanged; messages kept; older messages have no card")


if __name__ == "__main__":
    sys.exit(main())
