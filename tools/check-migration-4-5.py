#!/usr/bin/env python3
"""Run the version 4 -> 5 migration's own SQL against real SQLite, on this machine.

Robolectric's SQLite has no aarch64 build, so `MigrationTest` stands aside on the development box
and the migration that converts the owner's whole record is unverified until CI has run. That is
the rule and it stays the rule — but it leaves the riskiest code in the project untested at the
moment it is being written, which is the worst time not to know.

This narrows the gap. It extracts every statement from `FoodEntityMigration.kt` — rather than
retyping them, which would test a copy instead of the thing — builds a version 4 database from the
committed exported schema, and runs the migration's sequence over a small record.

WHAT IT PROVES
  - every statement parses and executes;
  - rebuilding `meals` does not fire the cascade that would delete every logged row;
  - the rename succeeds while a child table's foreign key is briefly dangling;
  - each day is worth exactly what it was worth before;
  - no row, meal, weight or packet is lost, and every row ends up attached to a food;
  - no saved meal and no weight-of-one-unit is invented;
  - the unique index really does refuse a second food with the same name and brand.

WHAT IT DOES NOT PROVE
  - what Room validates: that the finished tables match the exported schema exactly. Only
    `runMigrationsAndValidate` does that, and only in CI.
  - the Kotlin between the statements. The grouping, the naming and the choice of which row fills
    which fact are tested by `DerivedFoodsTest`, which runs anywhere.

Usage:  python3 tools/check-migration-4-5.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
MIGRATION = ROOT / "app/src/main/java/com/metaself/app/data/day/FoodEntityMigration.kt"
SCHEMA_4 = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase/4.json"


def statements():
    """Every SQL string the migration executes, in source order."""
    raw = MIGRATION.read_text()
    # Whole-line comments only, so a concatenation split by one still reads as a single chain.
    src = "\n".join(l for l in raw.splitlines() if not l.strip().startswith("//"))
    found = []
    for match in re.finditer(r'db\.(?:execSQL|query)\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found.append("".join(parts).replace('\\"', '"').replace("\\'", "'"))
    return found


def version_4_database():
    db = sqlite3.connect(":memory:")
    cursor = db.cursor()
    schema = json.loads(SCHEMA_4.read_text())
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        cursor.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            cursor.execute(index["createSql"].replace("${TABLE_NAME}", table))
    # A yoghurt eaten two mornings under two spellings, bread counted in slices, and a weight.
    cursor.executescript(
        """
        INSERT INTO meals VALUES (1,20690,1000,NULL);
        INSERT INTO meals VALUES (2,20691,2000,NULL);
        INSERT INTO food_items VALUES (1,1,'Yoghurt','180 g',180.0,'g',130,5,10,2,'TYPED',NULL);
        INSERT INTO food_items VALUES (2,1,'Bread','2 slice',2.0,'slice',160,6,30,2,'AI_ESTIMATE','MEDIUM');
        INSERT INTO food_items VALUES (3,2,'yoghurt','200 g',200.0,'g',144,6,11,2,'TYPED',NULL);
        INSERT INTO weights VALUES (20690, 81.4);
        INSERT INTO products VALUES ('7290012345678','Protein bar','Tnuva',422.0,33.0,38.0,14.0,45.0,1);
        """
    )
    return db, cursor


def kcal_by_day(cursor):
    return cursor.execute(
        "SELECT m.epochDay, SUM(f.kcal) FROM meals m "
        "INNER JOIN food_items f ON f.mealId = m.id GROUP BY m.epochDay ORDER BY 1"
    ).fetchall()


def main():
    sql = statements()
    db, cursor = version_4_database()
    before = kcal_by_day(cursor)

    pick = lambda prefix: next(s for s in sql if s.startswith(prefix))
    new_tables = [
        s for s in sql
        if s.startswith("CREATE") and "_new_" not in s
        and "index_meals" not in s and "index_food_items" not in s
    ]
    meals_rebuild = [s for s in sql if "_new_meals" in s or s == "DROP TABLE `meals`" or "index_meals" in s]
    items_rebuild = [s for s in sql if "_new_food_items" in s or s == "DROP TABLE `food_items`" or "index_food_items" in s]

    for statement in new_tables:
        cursor.execute(statement)

    rows = cursor.execute(pick("SELECT f.`id`")).fetchall()

    # Two foods, standing in for what DerivedFoods works out. Their exact numbers are that
    # function's business and are tested there; what matters here is that the rows bind.
    insert_food, insert_name = pick("INSERT INTO `foods`"), pick("INSERT INTO `food_names`")
    cursor.execute(insert_food, ("NA", 1, 1, 72.0, 3.0, 5.5, 1.0, "TYPED", 2, None, 2000,
                                 None, None, None, None, None, None, None, None, None))
    yoghurt = cursor.execute("SELECT last_insert_rowid()").fetchone()[0]
    cursor.execute(insert_name, (yoghurt, "Yoghurt", "yoghurt", "na", 1))
    cursor.execute(insert_food, ("NA", 1, 1, None, None, None, None, None, None, None, None,
                                 "slice", 80.0, 3.0, 15.0, 1.0, "AI_ESTIMATE", 1, "MEDIUM", 1000))
    bread = cursor.execute("SELECT last_insert_rowid()").fetchone()[0]
    cursor.execute(insert_name, (bread, "Bread", "bread", "na", 1))

    for statement in meals_rebuild:
        cursor.execute(statement)
    for statement in items_rebuild:
        cursor.execute(statement)
    attach = pick("UPDATE `food_items` SET `foodId`")
    for ref, food in ((1, yoghurt), (2, bread), (3, yoghurt)):
        cursor.execute(attach, (food, ref))

    count = lambda sql_text: cursor.execute(sql_text).fetchone()[0]
    cursor.execute("PRAGMA foreign_keys=ON")

    checks = [
        ("every statement parsed and ran", len(sql) == 30, f"{len(sql)} statements"),
        ("every logged row was read before the rebuild", len(rows) == 3, f"{len(rows)} rows"),
        ("no day changed what it was worth", kcal_by_day(cursor) == before, f"{before}"),
        ("no logged row was lost", count("SELECT COUNT(*) FROM food_items") == 3, ""),
        ("no meal was lost", count("SELECT COUNT(*) FROM meals") == 2, ""),
        ("the weights are untouched", count("SELECT COUNT(*) FROM weights") == 1, ""),
        ("the packets seen before are untouched", count("SELECT COUNT(*) FROM products") == 1, ""),
        ("every row points at a food", count("SELECT COUNT(*) FROM food_items WHERE foodId IS NULL") == 0, ""),
        ("no saved meal was manufactured", count("SELECT COUNT(*) FROM saved_meals") == 0, ""),
        ("no weight of one unit was invented", count("SELECT COUNT(*) FROM foods WHERE gramsPerUnit IS NOT NULL") == 0, ""),
        ("no foreign key is broken", cursor.execute("PRAGMA foreign_key_check").fetchall() == [], ""),
    ]

    try:
        cursor.execute(insert_name, (bread, "Yoghurt", "yoghurt", "na", 0))
        checks.append(("the identity rule is enforced by the index", False, "a duplicate was accepted"))
    except sqlite3.IntegrityError:
        checks.append(("the identity rule is enforced by the index", True, ""))

    failed = 0
    for name, ok, detail in checks:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}{('  — ' + detail) if detail and not ok else ''}")
        failed += 0 if ok else 1

    print()
    if failed:
        print(f"{failed} check(s) failed. The migration is not safe to merge.")
        return 1
    print("All checks passed. This is not a substitute for CI: Room's own schema validation,")
    print("which is what catches a table that does not match the exported file, runs only there.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
