#!/usr/bin/env python3
"""Run a food page's *Where it's used* reads (D55 §3) against real SQLite.

`RoomFoodRepositoryTest` is where `observeUse` is tested, and it stands aside on the development box
(Robolectric's SQLite has no aarch64 build) and runs in CI. This narrows the gap. It extracts the
statements from `FoodDao.kt` — rather than retyping them, which would test a copy — resolving a
statement held in a `const val` the way the Kotlin compiler does, builds a database from the latest
committed exported schema with foreign keys on, and reads the two answers as
`RoomFoodRepository.observeUse` combines them: the logged count, and the saved meals by name.

THE READ PATH IS MODELLED, NOT ASSUMED. The page shows a food only while the app READS it —
`FoodWithNames.toDomain`, which returns nothing for a food with no name or one knowing neither way
of counting — and closes when it does not (D55 §7). The count itself never looks at `foods`, so a
food the app cannot read may still have rows pointing at it; `reads` models that read so each case
states both what is counted and whether there is a page to show it on.

WHAT IT PROVES
  - both statements parse and bind against the latest schema, and the delete refusal's statement is
    the very one the section reads (one constant, two annotations);
  - "logged" counts every row of this food on every day and nothing else: not another food's row,
    not a row attached to no food; two rows of it in one meal are two;
  - a row deleted from a day, or a whole meal deleted (the cascade), lowers the count;
  - deleting a food nothing holds leaves its rows (ON DELETE SET NULL) and counts 0 for its id;
  - after a join's own sequence the food kept counts both foods' rows and names both foods' meals,
    and the absorbed one counts nothing and reads as nothing;
  - the saved meals come in name order, a meal's own hidden stamp is not consulted, and a food in
    no meal gets none;
  - the count is answered from `index_food_items_foodId`, not a scan of every row;
  - no version-6 schema has been exported: this change stores nothing new.

WHAT IT DOES NOT PROVE
  - Room's `Flow` invalidation — that each answer is emitted again when the tables move. That is
    CI's `RoomFoodRepositoryTest`.
  - The Kotlin that combines the two flows, or the page that shows them.

Every food, meal and figure is invented; the foods and meals are D55's own examples.

Usage:  python3 tools/check-food-use.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
FOOD_DAO = ROOT / "app/src/main/java/com/metaself/app/data/food/FoodDao.kt"
MEAL_DAO = ROOT / "app/src/main/java/com/metaself/app/data/day/MealDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"

STRING = r'"((?:[^"\\]|\\.)*)"'
CONCAT = r'((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)'


def joined(literals):
    return "".join(re.findall(STRING, literals)).replace('\\"', '"')


def queries(path):
    """Every `@Query` in a DAO file, by the name of the function it annotates.

    A statement is either written in the annotation or named there by a `const val` in the same
    file, which is how one statement is shared by two annotations.
    """
    src = path.read_text()
    constants = {name: joined(body) for name, body in
                 re.findall(r'const\s+val\s+(\w+)\s*=\s*' + CONCAT, src)}
    found = {}
    pattern = r'@Query\(\s*(?:' + CONCAT + r'|(\w+)),?\s*\)\s*(?:suspend\s+)?fun\s+(\w+)'
    for literals, constant, fun in re.findall(pattern, src):
        if literals:
            found[fun] = joined(literals)
        elif constant in constants:
            found[fun] = constants[constant]
    return found


def latest_database():
    schema_file = max(SCHEMAS.glob("*.json"), key=lambda p: int(p.stem))
    db = sqlite3.connect(":memory:")
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA foreign_keys=ON")
    schema = json.loads(schema_file.read_text())
    for entity in schema["database"]["entities"]:
        table = entity["tableName"]
        db.execute(entity["createSql"].replace("${TABLE_NAME}", table))
        for index in entity.get("indices", []):
            db.execute(index["createSql"].replace("${TABLE_NAME}", table))
    return db, schema_file.name


def food(db, name, **columns):
    """A food knowing what 100 g of it are worth, and its preferred name."""
    columns = {"brand": "NA", "createdAtMillis": 1000, "updatedAtMillis": 1000,
               "kcalPer100g": 100.0, "proteinPer100g": 8.0, "carbsPer100g": 4.0,
               "fatPer100g": 5.0, "per100gSource": "LABEL", "per100gSourceRank": 3,
               "per100gSetAtMillis": 1000, **columns}
    keys = ", ".join(columns)
    marks = ", ".join("?" for _ in columns)
    food_id = db.execute(f"INSERT INTO foods ({keys}) VALUES ({marks})", list(columns.values())).lastrowid
    db.execute(
        "INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
        "VALUES (?, ?, ?, 'na', 1, 1000)",
        (food_id, name, name.lower()),
    )
    return food_id


def reads(db, food_id):
    """Whether the app reads this food at all: `toDomain` needs a name and one way of counting."""
    stored = db.execute("SELECT * FROM foods WHERE id = ?", (food_id,)).fetchone()
    if stored is None:
        return False
    named = db.execute("SELECT COUNT(*) FROM food_names WHERE foodId = ?", (food_id,)).fetchone()[0]
    return named > 0 and (stored["kcalPer100g"] is not None or stored["kcalPerUnit"] is not None)


def meal(db, epoch_day):
    return db.execute("INSERT INTO meals (epochDay, loggedAtMillis) VALUES (?, 1000)",
                      (epoch_day,)).lastrowid


def logged(db, meal_id, name, food_id):
    return db.execute(
        "INSERT INTO food_items (mealId, name, portion, portionAmount, portionUnit, kcal, proteinG, "
        "carbsG, fatG, source, confidence, foodId) "
        "VALUES (?, ?, '150 g', 150.0, 'g', 150, 12, 6, 8, 'LABEL', NULL, ?)",
        (meal_id, name, food_id),
    ).lastrowid


def saved_meal(db, name, *food_ids, hidden_at=None):
    saved = db.execute(
        "INSERT INTO saved_meals (name, nameKey, createdAtMillis, updatedAtMillis, hiddenAtMillis) "
        "VALUES (?, ?, 1000, 1000, ?)", (name, name.lower(), hidden_at)).lastrowid
    for position, food_id in enumerate(food_ids):
        db.execute(
            "INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
            "VALUES (?, ?, ?, 1.0, 'UNITS')", (saved, food_id, position))
    return saved


def use(q, db, food_id):
    """`RoomFoodRepository.observeUse`: the two answers, combined into one."""
    count = db.execute(q["observeLoggedCount"], dict(foodId=food_id)).fetchone()[0]
    meals = [r[0] for r in db.execute(q["observeMealsUsing"], dict(foodId=food_id))]
    return count, meals


def join(q, db, winner, loser, now=9000):
    """A join's statements, in the order `RoomFoodRepository.merge` runs them (no fills needed)."""
    assert db.execute(q["mealsHoldingBoth"], dict(winner=winner, loser=loser)).fetchall() == []
    db.execute(q["movePastRows"], dict(winner=winner, loser=loser))
    db.execute(q["moveMealComponents"], dict(winner=winner, loser=loser))
    db.execute(q["moveNames"], dict(winner=winner, loser=loser))
    db.execute(q["deleteFood"], dict(id=loser))
    db.execute(q["touch"], dict(id=winner, nowMillis=now))


def main():
    q = queries(FOOD_DAO)
    m = queries(MEAL_DAO)
    needed = ["observeLoggedCount", "observeMealsUsing", "mealsUsing", "mealsHoldingBoth",
              "movePastRows", "moveMealComponents", "moveNames", "deleteFood", "touch"]
    missing = [name for name in needed if name not in q]
    missing += [name for name in ["deleteItemRow", "deleteEmptyMeals"] if name not in m]
    if missing:
        print(f"Could not find these statements in the DAOs: {missing}")
        return 1

    checks = []
    check = lambda name, ok: checks.append((name, bool(ok)))

    check("the delete refusal and the section read one statement",
          q["mealsUsing"] == q["observeMealsUsing"])

    # (a) Greek yoghurt on two days and in two saved meals; another food's row and a row attached
    #     to no food on the same days.
    db, schema_name = latest_database()
    yoghurt = food(db, "Greek yoghurt")
    biscuit = food(db, "Oat biscuit", brand="Examplebrand")
    soup = food(db, "Lentil soup")
    first_day, second_day = meal(db, 20_698), meal(db, 20_699)
    logged(db, first_day, "Greek yoghurt", yoghurt)
    doomed = logged(db, second_day, "Greek yoghurt", yoghurt)
    logged(db, second_day, "Oat biscuit", biscuit)
    logged(db, second_day, "Greek yoghurt", None)
    saved_meal(db, "Snack plate", yoghurt, biscuit)
    saved_meal(db, "Breakfast bowl", yoghurt)
    check("(a) every row of the food, on every day, is counted", use(q, db, yoghurt)[0] == 2)
    check("(a) another food's row is that food's", use(q, db, biscuit)[0] == 1)
    check("(a) a row attached to no food is nobody's",
          db.execute("SELECT COUNT(*) FROM food_items WHERE foodId IS NULL").fetchone()[0] == 1
          and use(q, db, yoghurt)[0] + use(q, db, biscuit)[0] == 3)

    # (b) Two rows of it in one meal are two.
    logged(db, first_day, "Greek yoghurt", yoghurt)
    check("(b) two rows of the same food in one meal count two", use(q, db, yoghurt)[0] == 3)

    # (c) A row deleted from a day (the app's statement), then a whole meal (the cascade).
    db.execute(m["deleteItemRow"], dict(itemId=doomed))
    db.execute(m["deleteEmptyMeals"])
    check("(c) a row deleted from a day lowers the count", use(q, db, yoghurt)[0] == 2)
    db.execute("DELETE FROM meals WHERE id = ?", (first_day,))
    check("(c) a meal deleted takes its rows, and the count, with it", use(q, db, yoghurt)[0] == 0)

    # (f) The saved meals, from the refusal's own statement.
    check("(f) the saved meals holding it, in name order",
          use(q, db, yoghurt)[1] == ["Breakfast bowl", "Snack plate"])
    check("(f) a food in no saved meal gets none", use(q, db, soup)[1] == [])
    db.execute("UPDATE saved_meals SET hiddenAtMillis = 5000 WHERE name = 'Breakfast bowl'")
    check("(f) a saved meal's own hidden stamp is not consulted",
          use(q, db, yoghurt)[1] == ["Breakfast bowl", "Snack plate"])

    # (d) A food nothing holds is deleted: its rows stay, attached to nothing.
    lone = meal(db, 20_699)
    logged(db, lone, "Lentil soup", soup)
    check("(d) nothing holds the food, so the delete's refusal finds nothing",
          db.execute(q["mealsUsing"], dict(foodId=soup)).fetchall() == [])
    db.execute(q["deleteFood"], dict(id=soup))
    check("(d) its row stays, attached to no food",
          db.execute("SELECT foodId FROM food_items WHERE name = 'Lentil soup'").fetchone()[0] is None)
    check("(d) and its id counts nothing, with no page to show it on",
          use(q, db, soup) == (0, []) and not reads(db, soup))
    check("no foreign key is broken", db.execute("PRAGMA foreign_key_check").fetchall() == [])

    # (e) A join: the food kept counts both foods' rows and names both foods' meals.
    db, _ = latest_database()
    kept = food(db, "Greek yoghurt")
    absorbed = food(db, "Strained yoghurt")
    day = meal(db, 20_699)
    for _ in range(3):
        logged(db, day, "Greek yoghurt", kept)
    for _ in range(2):
        logged(db, day, "Strained yoghurt", absorbed)
    saved_meal(db, "Snack plate", absorbed)
    check("(e) before the join each counts its own", use(q, db, kept)[0] == 3 and use(q, db, absorbed)[0] == 2)
    join(q, db, kept, absorbed)
    check("(e) after it the food kept counts both foods' rows and names their meal",
          use(q, db, kept) == (5, ["Snack plate"]) and reads(db, kept))
    check("(e) the absorbed food counts nothing and reads as nothing",
          use(q, db, absorbed) == (0, []) and not reads(db, absorbed))
    check("no foreign key is broken by the join", db.execute("PRAGMA foreign_key_check").fetchall() == [])

    # (g) The count is answered from the index on food_items.foodId.
    plan = " ".join(r["detail"] for r in db.execute(
        "EXPLAIN QUERY PLAN " + q["observeLoggedCount"], dict(foodId=kept)))
    check("(g) the count uses index_food_items_foodId", "index_food_items_foodId" in plan)

    # (h) Nothing new is stored.
    check("(h) no version-6 schema has been exported", not (SCHEMAS / "6.json").exists())

    failed = 0
    for name, ok in checks:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}")
        failed += 0 if ok else 1
    print()
    print(f"Schema: {schema_name}")
    if failed:
        print(f"{failed} check(s) failed.")
        return 1
    print("All checks passed. This is not a substitute for CI: that each answer is emitted again when")
    print("the tables move is exercised only by RoomFoodRepositoryTest, which runs there.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
