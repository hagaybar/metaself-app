#!/usr/bin/env python3
"""Run the version 5 -> 6 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside here and runs in CI.
In the pattern of `check-migration-4-5.py`, this extracts every statement from
`HealthRecordMigration.kt` (rather than retyping them, which would test a copy), builds a version 5
database from the committed `5.json`, puts one row in every table, and runs the migration.

WHAT IT PROVES
  - every statement parses and executes;
  - the statements are, character for character, the ones `6.json` declares for the new tables;
  - each new table has the same columns, types, nullability, defaults, keys, indices and foreign
    keys as a database built fresh from `6.json`;
  - every table that existed has exactly the rows it had before;
  - the unique indices refuse a second copy of a sample, a session and a night, and allow any number
    of typed workouts with no origin;
  - deleting a night deletes its stages once foreign keys are on, as Room turns them on.

WHAT IT DOES NOT PROVE
  - Room's own validation (`runMigrationsAndValidate`) — CI only.

Usage:  python3 tools/check-migration-5-6.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/HealthRecordMigration.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
NEW_TABLES = (
    "health_readings", "workouts", "sleep_sessions", "sleep_stages", "health_days",
    "movement_corrections", "health_sync", "archive_months",
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


def declared(version, tables=None):
    """Every CREATE statement the exported schema declares, tables first within each entity."""
    schema = json.loads((SCHEMAS / f"{version}.json").read_text())
    out = []
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
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
    expected = declared(6, NEW_TABLES)
    if sorted(migration) != sorted(expected):
        missing = sorted(set(expected) - set(migration))
        extra = sorted(set(migration) - set(expected))
        failures.append(
            "the migration's statements are not the ones 6.json declares:\n"
            + "".join(f"  only in 6.json:    {s}\n" for s in missing)
            + "".join(f"  only in migration: {s}\n" for s in extra)
        )

    db = build(5)
    old_tables = [t for (t,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'")]
    # Invented values: one row in every version-5 table, so "untouched" means something for each.
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

    fresh = build(6)
    for table in NEW_TABLES:
        if shape(db, table) != shape(fresh, table):
            failures.append(f"`{table}` after the migration differs from 6.json:\n"
                            f"  migrated: {shape(db, table)}\n  6.json:   {shape(fresh, table)}")

    sample = ("INSERT INTO health_readings (kind, startMillis, value, unit, origin, recordId, "
              "sampleIndex, epochDay) VALUES ('HEART_RATE', 1000, 60, 'bpm', 'o', 'hr-1', 0, 20699)")
    db.execute(sample)
    if not refused(db, sample):
        failures.append("a second copy of the same sample was accepted")

    typed = ("INSERT INTO workouts (epochDay, startedAtMillis, durationMinutes, kind, energySource, "
             "source) VALUES (20699, 1000, 45, 'STRENGTH', 'NONE', 'TYPED')")
    db.execute(typed)
    db.execute(typed)
    synced = ("INSERT INTO workouts (epochDay, startedAtMillis, durationMinutes, kind, energySource, "
              "source, origin, originId) VALUES (20699, 1000, 30, 'RUN', 'NONE', 'SYNCED', 'o', 'w-1')")
    db.execute(synced)
    if not refused(db, synced):
        failures.append("a second copy of the same synced session was accepted")

    # foreign_keys is a no-op while a transaction is open, and the inserts above left one pending.
    db.commit()
    db.execute("PRAGMA foreign_keys = ON")
    night = ("INSERT INTO sleep_sessions (id, epochDay, startMillis, endMillis, origin, recordId) "
             "VALUES (1, 20699, 1000, 3000, 'o', 's-1')")
    db.execute(night)
    if not refused(db, night.replace("(1,", "(2,")):
        failures.append("a second copy of the same night was accepted")
    db.execute("INSERT INTO sleep_stages (sessionId, stage, startMillis, endMillis) "
               "VALUES (1, 'DEEP', 1000, 2000)")
    db.execute("DELETE FROM sleep_sessions WHERE id = 1")
    if db.execute("SELECT COUNT(*) FROM sleep_stages").fetchone() != (0,):
        failures.append("deleting a night left its stages behind")

    if failures:
        print("FAIL")
        for f in failures:
            print(" -", f)
        sys.exit(1)
    print(f"OK: {len(migration)} statements, {len(old_tables)} existing tables untouched, "
          f"{len(NEW_TABLES)} new tables match 6.json")


if __name__ == "__main__":
    main()
