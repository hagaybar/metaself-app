#!/usr/bin/env python3
"""Run a restore's database writes, in the order `BackupRepository.restore` runs them, as ONE
transaction against real SQLite — and make it fail at every step.

`BackupRoundTripTest` is where a restore is tested against Room, and it stands aside on the
development box (Robolectric's SQLite has no aarch64 build) and runs in CI. This narrows the gap for
public issue #32, where a restore became all or nothing. The `@Query` statements are extracted from
the DAOs rather than retyped, which would test a copy; the `@Insert` statements Room generates are
written out below from the latest committed exported schema's columns, because there is no source
text to extract them from.

The sequence, for a file holding one food counted in slices, one built meal of it, one logged meal
pointing at both, and one weight — replacing a record holding a different food, built meal, logged
meal and weight:

  DELETE meals (cascading to their rows) · DELETE weights · find-or-create the food (name lookup,
  insert, preferred name, the per-unit offer) · create the built meal (name lookup, insert) and put
  the food in it (component lookup, next position, insert, touch) · insert the meal and its row ·
  upsert the weight.

WHAT IT PROVES
  - every statement the restore's database half uses parses and binds against the latest schema;
  - with foreign keys on, the whole sequence runs inside one transaction: rows created in it are
    pointed at by rows created later in it, and the committed result has no dangling key;
  - a failure after ANY step, followed by ROLLBACK, leaves every table exactly as it was — the
    rows the delete cascaded away included, ids and stamps included;
  - a real constraint failure midway (a second food with the same name and brand, which the unique
    index refuses) does the same;
  - the built meals and foods already here are merged into, not deleted: only meals and weights are
    emptied.

WHAT IT DOES NOT PROVE
  - Room's transaction. Android nests transactions without SAVEPOINTs: an inner `withTransaction`
    that ends unmarked makes the outer one roll back when it closes, however the exception was
    handled. That is why `restore` catches nothing inside its transaction, and it is Android's
    behaviour, not SQLite's, so it cannot be reproduced here; `BackupRoundTripTest` checks it in CI
    (a food and a built meal named "!!!" and "?!" are skipped and everything else is restored).
  - the settings half. The DataStore writes happen inside the same block, last, and are put back
    from a snapshot; that is `BackupRestoreOrderTest` and `DataStoreSettingsSnapshotTest`, which
    run anywhere.
  - the Kotlin that decides what to write (`prepare`), which `BackupRestoreOrderTest` covers.

Usage:  python3 tools/check-restore-transaction.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DAOS = [
    ROOT / "app/src/main/java/com/metaself/app/data/day/MealDao.kt",
    ROOT / "app/src/main/java/com/metaself/app/data/weight/WeightDao.kt",
    ROOT / "app/src/main/java/com/metaself/app/data/food/FoodDao.kt",
    ROOT / "app/src/main/java/com/metaself/app/data/food/SavedMealDao.kt",
]
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"


def queries():
    """Every `@Query` in the four DAOs, by DAO file and the name of the function it annotates."""
    found = {}
    pattern = r'@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+),?\s*\)\s*(?:suspend\s+)?fun\s+(\w+)'
    for dao in DAOS:
        for match in re.finditer(pattern, dao.read_text()):
            parts = re.findall(r'"((?:[^"\\]|\\.)*)"', match.group(1))
            found[f"{dao.stem}.{match.group(2)}"] = "".join(parts).replace('\\"', '"')
    return found


def latest_database():
    schema_file = max(SCHEMAS.glob("*.json"), key=lambda p: int(p.stem))
    # Autocommit, so BEGIN / COMMIT / ROLLBACK below are exactly the ones issued.
    db = sqlite3.connect(":memory:", isolation_level=None)
    db.execute("PRAGMA foreign_keys=ON")
    schema = json.loads(schema_file.read_text())
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table))
    return db, schema_file.name


TABLES = ["meals", "food_items", "weights", "foods", "food_names", "saved_meals",
          "saved_meal_components", "products"]


def dump(db):
    """Every row of every table, ids included, so "identical" means identical."""
    return {t: db.execute(f"SELECT * FROM {t} ORDER BY rowid").fetchall() for t in TABLES}


def insert(db, table, **columns):
    """What Room's generated `@Insert` does: every column named, the id left to SQLite."""
    keys = ", ".join(f"`{k}`" for k in columns)
    marks = ", ".join("?" for _ in columns)
    return db.execute(f"INSERT INTO `{table}` ({keys}) VALUES ({marks})", list(columns.values())).lastrowid


def a_record(db):
    """What is here before: a food, a built meal of it, a meal logged from both, a weight."""
    db.execute("BEGIN")
    food = insert(db, "foods", brand="NA", barcode=None, createdAtMillis=1000, updatedAtMillis=1000,
                  hiddenAtMillis=None, kcalPer100g=72.0, proteinPer100g=4.0, carbsPer100g=6.0,
                  fatPer100g=2.0, per100gSource="TYPED", per100gSourceRank=2, per100gConfidence=None,
                  per100gSetAtMillis=1000)
    insert(db, "food_names", foodId=food, displayName="Yoghurt", nameKey="yoghurt", brandKey="na",
           isPreferred=1, addedAtMillis=1000)
    built = insert(db, "saved_meals", name="Lunch", nameKey="lunch", createdAtMillis=1000,
                   updatedAtMillis=1000, hiddenAtMillis=None)
    insert(db, "saved_meal_components", savedMealId=built, foodId=food, position=0, amount=150.0,
           countedAs="GRAMS")
    meal = insert(db, "meals", epochDay=20699, loggedAtMillis=1000, note=None, savedMealId=built,
                  savedMealAdjusted=0)
    insert(db, "food_items", mealId=meal, name="Yoghurt", portion=None, portionAmount=0.0,
           portionUnit="", kcal=108, proteinG=6, carbsG=9, fatG=3, source="TYPED", confidence=None,
           foodId=food)
    insert(db, "weights", epochDay=20699, kg=80.0)
    db.execute("COMMIT")


class Stop(Exception):
    """A failure injected after a numbered step."""


def restore(q, db, fail_after=None, collide=False):
    """The restore's database writes, in `restore`'s order, raising [Stop] after step [fail_after].

    Invented figures throughout.
    """
    step = 0

    def done():
        nonlocal step
        step += 1
        if fail_after == step:
            raise Stop(step)

    now = 2000
    db.execute(q["MealDao.deleteAllMeals"]); done()
    db.execute(q["WeightDao.deleteAll"]); done()

    # findOrCreate("Bread", perUnit = 80 kcal a slice)
    assert db.execute(q["FoodDao.foodIdNamed"], dict(nameKey="bread", brandKey="na")).fetchone() is None
    food = insert(db, "foods", brand="NA", barcode=None, createdAtMillis=now, updatedAtMillis=now)
    done()
    insert(db, "food_names", foodId=food, displayName="Bread",
           # The unique index on (nameKey, brandKey) is the lock `findOrCreate` relies on; a second
           # "yoghurt|na" is the real failure it raises.
           nameKey="yoghurt" if collide else "bread", brandKey="na", isPreferred=1, addedAtMillis=now)
    done()
    written = db.execute(q["FoodDao.writePerUnit"], dict(
        id=food, unitName="slice", kcal=80.0, protein=3.0, carbs=15.0, fat=1.0, source="TYPED",
        rank=2, confidence=None, nowMillis=now)).rowcount
    assert written == 1, "a new food's first offer must land"
    done()

    # create("Supper"), then put(bread, 2 slices)
    assert db.execute(q["SavedMealDao.idNamed"], dict(nameKey="supper")).fetchone() is None
    built = insert(db, "saved_meals", name="Supper", nameKey="supper", createdAtMillis=now,
                   updatedAtMillis=now, hiddenAtMillis=None)
    done()
    assert db.execute(q["SavedMealDao.componentFor"], dict(mealId=built, foodId=food)).fetchone() is None
    position = db.execute(q["SavedMealDao.nextPosition"], dict(mealId=built)).fetchone()[0]
    insert(db, "saved_meal_components", savedMealId=built, foodId=food, position=position,
           amount=2.0, countedAs="UNITS")
    done()
    db.execute(q["SavedMealDao.touch"], dict(id=built, nowMillis=now)); done()

    meal = insert(db, "meals", epochDay=20700, loggedAtMillis=now, note=None, savedMealId=built,
                  savedMealAdjusted=0)
    done()
    insert(db, "food_items", mealId=meal, name="Bread", portion="2 slice", portionAmount=2.0,
           portionUnit="slice", kcal=160, proteinG=6, carbsG=30, fatG=2, source="TYPED",
           confidence=None, foodId=food)
    done()
    # WeightDao.upsert is @Insert(onConflict = REPLACE).
    db.execute("INSERT OR REPLACE INTO `weights` (`epochDay`, `kg`) VALUES (?, ?)", (20700, 79.0))
    done()
    return step


def main():
    q = queries()
    needed = ["MealDao.deleteAllMeals", "WeightDao.deleteAll", "FoodDao.foodIdNamed",
              "FoodDao.writePerUnit", "SavedMealDao.idNamed", "SavedMealDao.componentFor",
              "SavedMealDao.nextPosition", "SavedMealDao.touch"]
    missing = [n for n in needed if n not in q]
    if missing:
        print(f"FAIL: could not extract {missing}")
        return 1

    failures = []

    def check(ok, what):
        print(("  ok    " if ok else "  FAIL  ") + what)
        if not ok:
            failures.append(what)

    db, schema = latest_database()
    print(f"schema {schema}")
    a_record(db)
    before = dump(db)
    check(len(before["food_items"]) == 1 and len(before["meals"]) == 1, "the record before is in place")

    # How many steps there are, from a run that is rolled back at the end.
    db.execute("BEGIN")
    steps = restore(q, db)
    db.execute("ROLLBACK")
    check(dump(db) == before, f"all {steps} steps, rolled back: every table as it was")

    for k in range(1, steps + 1):
        db.execute("BEGIN")
        try:
            restore(q, db, fail_after=k)
            check(False, f"step {k} was meant to fail")
        except Stop:
            if k == 1:
                check(db.execute("SELECT COUNT(*) FROM food_items").fetchone()[0] == 0,
                      "inside the transaction, deleting the meals cascades their rows away")
            db.execute("ROLLBACK")
        check(dump(db) == before, f"a failure after step {k} of {steps}, rolled back: every table as it was")

    db.execute("BEGIN")
    try:
        restore(q, db, collide=True)
        check(False, "a second food with the same name and brand was accepted")
    except sqlite3.IntegrityError as refused:
        db.execute("ROLLBACK")
        check("UNIQUE" in str(refused), f"the unique index refuses a second yoghurt|na ({refused})")
    check(dump(db) == before, "a real constraint failure midway, rolled back: every table as it was")

    db.execute("BEGIN")
    restore(q, db)
    db.execute("COMMIT")
    after = dump(db)
    check(db.execute("PRAGMA foreign_key_check").fetchall() == [], "committed: no dangling foreign key")
    check([r[1] for r in after["meals"]] == [20700] and len(after["food_items"]) == 1,
          "committed: the file's one meal and its row, the old ones gone")
    check(db.execute("SELECT COUNT(*) FROM food_items WHERE foodId IS NULL").fetchone()[0] == 0,
          "committed: every row attached to a food")
    check(db.execute("SELECT savedMealId FROM meals").fetchone()[0] ==
          db.execute("SELECT id FROM saved_meals WHERE nameKey='supper'").fetchone()[0],
          "committed: the meal points at the built meal created in the same transaction")
    check([r[1] for r in after["weights"]] == [79.0], "committed: the file's weight, the old one gone")
    check(len(after["foods"]) == 2 and len(after["saved_meals"]) == 2,
          "committed: foods and built meals merged into, not emptied")

    print("PASS" if not failures else f"FAIL ({len(failures)})")
    return 0 if not failures else 1


if __name__ == "__main__":
    sys.exit(main())
