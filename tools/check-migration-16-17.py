#!/usr/bin/env python3
"""Run the version 16 -> 17 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI. This
extracts every statement from `ConversationsMigration.kt`, builds a version 16 database from the
committed `16.json`, puts invented messages in `trainer_messages`, and runs the migration under TZ=UTC.

WHAT IT PROVES
  - every statement parses and executes;
  - `trainer_messages` then has the columns `17.json` declares, and every other table is declared
    identically in `16.json` and `17.json`;
  - no message is lost and no word changes;
  - each message is in its local day's conversation, and a reply is with the message it answers, even
    after midnight; a reply whose message is gone is in its own day's.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  TZ=UTC python3 tools/check-migration-16-17.py
"""

import json
import os
import pathlib
import re
import sqlite3
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/ConversationsMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
DAY = 86_400_000
BEFORE = DAY * 20_699 + 1_000  # 2026-09-03, invented


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
    old, new = tables(16), tables(17)
    for name in old:
        if name != "trainer_messages":
            assert old[name] == new[name], f"{name} differs between 16.json and 17.json"
    db = sqlite3.connect(":memory:")
    db.execute(old["trainer_messages"]["createSql"].replace("${TABLE_NAME}", "trainer_messages"))
    rows = [
        (1, BEFORE, 1, "Invented.", None, 1, None),
        (2, BEFORE + DAY, 0, "Invented, after midnight.", "m", 0, 1),
        (3, BEFORE + DAY, 1, "Invented next day.", None, 0, None),
        (4, BEFORE + 2 * DAY, 0, "Invented, its message gone.", "m", 0, 99),
    ]
    db.executemany("INSERT INTO trainer_messages (id, atMillis, fromOwner, text, model, answered, replyTo) VALUES (?,?,?,?,?,?,?)", rows)
    found = statements()
    assert len(found) == 3, found
    for statement in found:
        db.execute(statement)
    columns = {row[1]: row for row in db.execute("PRAGMA table_info(trainer_messages)")}
    declared = {f["columnName"] for f in new["trainer_messages"]["fields"]}
    assert set(columns) == declared, (set(columns), declared)
    field = next(f for f in new["trainer_messages"]["fields"] if f["columnName"] == "conversation")
    assert field["notNull"] and columns["conversation"][4] == field.get("defaultValue"), (field, columns["conversation"])
    got = dict(db.execute("SELECT id, conversation FROM trainer_messages"))
    assert got == {1: "day:2026-09-03", 2: "day:2026-09-03", 3: "day:2026-09-04", 4: "day:2026-09-05"}, got
    words = [row[0] for row in db.execute("SELECT text FROM trainer_messages ORDER BY id")]
    assert words == [row[3] for row in rows], words
    print("ok: 3 statements; columns as 17.json; other tables unchanged; words kept; conversations by local day, replies with their message")


if __name__ == "__main__":
    sys.exit(main())
