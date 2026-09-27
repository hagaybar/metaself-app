"""Run a rename's own SQL, in the order `RoomFoodRepository.rename` runs it, against real SQLite.

`RoomFoodRepositoryTest` holds the test of this — renaming a food to a name it absorbed in a join —
and it stands aside on the development box (Robolectric's SQLite has no aarch64 build) and runs in
CI. This narrows the gap. It extracts the statements from `FoodDao.kt` rather than retyping them,
builds a database from the latest committed exported schema, joins one food into another, and
renames the one that stayed to the absorbed food's name: once in the order the rename used to run,
once in the order it runs now.

WHAT IT PROVES
  - without the drop, the rename is refused by the unique index with exactly
    "UNIQUE constraint failed: food_names.nameKey, food_names.brandKey", although the check before
    it finds no OTHER food holding the name;
  - with `dropJoinedName` first, the rename lands and the food is left with one name;
  - every statement used parses and binds against the latest schema.

WHAT IT DOES NOT PROVE
  - Room's transaction, or anything Room validates. That is CI's.
  - the Kotlin around the statements (which row is preferred, the refusal). That is CI's too.

Usage:  python3 tools/check-rename-own-alias.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DAO = ROOT / "app/src/main/java/com/metaself/app/data/food/FoodDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"


def queries():
    """Every `@Query` in the DAO, by the name of the function it annotates."""
    src = DAO.read_text()
    found = {}
    pattern = r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),?\s*\)\s*(?:suspend\s+)?fun\s+(\w+)'
    for match in re.finditer(pattern, src):
        parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
        found[match.group(2)] = "".join(parts).replace('\\"', '"')
    return found


def database():
    latest = max(SCHEMAS.glob("*.json"), key=lambda p: int(p.stem))
    schema = json.loads(latest.read_text())["database"]
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys=ON")
    for entity in schema["entities"]:
        db.execute(entity["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", entity["tableName"]))
    return db


def run(db, sql, **args):
    """A DAO statement with its `:name` parameters bound by name."""
    return db.execute(re.sub(r":(\w+)", "?", sql), [args[n] for n in re.findall(r":(\w+)", sql)])


def joined(db, q):
    """Two foods of no brand, the second joined into the first. Returns the first's id."""
    ids = []
    for display, key in (("Oat bar", "oat bar"), ("Granola bar", "granola bar")):
        db.execute("INSERT INTO foods (brand, createdAtMillis, updatedAtMillis) VALUES ('NA', 0, 0)")
        food = db.execute("SELECT last_insert_rowid()").fetchone()[0]
        db.execute(
            "INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
            "VALUES (?, ?, ?, 'na', 1, 0)",
            (food, display, key),
        )
        ids.append(food)
    kept, absorbed = ids
    run(db, q["moveNames"], winner=kept, loser=absorbed)
    run(db, q["deleteFood"], id=absorbed)
    return kept


def rename(db, q, food, drop_first):
    taken = run(db, q["foodIdNamed"], nameKey="granola bar", brandKey="na").fetchone()[0]
    assert taken == food, "the check finds only the food itself, so nothing refuses"
    shown = db.execute("SELECT id FROM food_names WHERE foodId = ? AND isPreferred = 1", (food,)).fetchone()[0]
    if drop_first:
        run(db, q["dropJoinedName"], foodId=food, nameKey="granola bar", brandKey="na")
    run(db, q["renameNameRow"], id=shown, displayName="Granola bar", nameKey="granola bar")


def main():
    q = queries()
    failures = []

    db = database()
    food = joined(db, q)
    try:
        rename(db, q, food, drop_first=False)
        failures.append("without the drop the rename was expected to be refused, and was not")
    except sqlite3.IntegrityError as error:
        if str(error) != "UNIQUE constraint failed: food_names.nameKey, food_names.brandKey":
            failures.append(f"refused with another message: {error}")

    db = database()
    food = joined(db, q)
    rename(db, q, food, drop_first=True)
    names = db.execute("SELECT nameKey, brandKey, isPreferred FROM food_names WHERE foodId = ?", (food,)).fetchall()
    if names != [("granola bar", "na", 1)]:
        failures.append(f"after the rename the food holds {names}")

    for failure in failures:
        print("FAIL:", failure)
    print("ok" if not failures else f"{len(failures)} failure(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
