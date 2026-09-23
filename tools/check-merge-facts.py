#!/usr/bin/env python3
"""Run a join's own SQL, in the order `RoomFoodRepository.merge` runs it, against real SQLite.

`RoomFoodRepositoryTest` is where a join is tested, and it stands aside on the development box
(Robolectric's SQLite has no aarch64 build) and runs in CI. This narrows the gap for issue #19, where
a join started carrying across the figures only the absorbed food knew. It extracts the statements
from `FoodDao.kt` — rather than retyping them, which would test a copy — builds a database from the
latest committed exported schema, and runs the sequence the merge runs.

WHICH groups are carried is decided in Kotlin by `JoinedFacts`, and tested by `JoinedFactsTest`,
which runs anywhere. Here the decision is written out by hand for each case, as the comment beside
it says.

WHAT IT PROVES
  - every statement the join uses parses and binds against the version 5 schema;
  - a filled group lands with the absorbed food's source, rank, confidence and date unchanged;
  - the winner's own groups, its rows' numbers and its unit name do not move;
  - the absorbed food's rows, meal parts and names reach the winner before it is deleted, so the
    delete cascades nothing away and breaks no foreign key;
  - the edit stamp the join leaves is the join's, not the copied figure's date;
  - the question that refuses a join finds a meal holding both;
  - why `JoinedFacts` must not offer a figure where the winner has one: at equal rank the guard's
    `>=` would let it through.

WHAT IT DOES NOT PROVE
  - Room's transaction, or anything Room validates. That is CI's.
  - the Kotlin that chooses the groups (JoinedFactsTest) or reads the rows back (FoodMapping).

Usage:  python3 tools/check-merge-facts.py
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
    """A food and its preferred name. Invented figures throughout."""
    columns = {"brand": "NA", "createdAtMillis": 1000, "updatedAtMillis": 1000, **columns}
    keys = ", ".join(columns)
    marks = ", ".join("?" for _ in columns)
    food_id = db.execute(f"INSERT INTO foods ({keys}) VALUES ({marks})", list(columns.values())).lastrowid
    db.execute(
        "INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
        "VALUES (?, ?, ?, 'na', 1, 1000)",
        (food_id, name, name.lower()),
    )
    return food_id


PER_100G = dict(kcalPer100g=120.0, proteinPer100g=5.0, carbsPer100g=10.0, fatPer100g=2.0,
                per100gSource="AI_ESTIMATE", per100gSourceRank=1, per100gConfidence="MEDIUM",
                per100gSetAtMillis=700)
PER_BAR = dict(unitName="bar", kcalPerUnit=190.0, proteinPerUnit=15.0, carbsPerUnit=17.0,
               fatPerUnit=6.0, perUnitSource="LABEL", perUnitSourceRank=3, perUnitConfidence=None,
               perUnitSetAtMillis=700)
PER_SLICE = dict(unitName="slice", kcalPerUnit=80.0, proteinPerUnit=3.0, carbsPerUnit=15.0,
                 fatPerUnit=1.0, perUnitSource="TYPED", perUnitSourceRank=2, perUnitConfidence=None,
                 perUnitSetAtMillis=1000)
WEIGHS_45 = dict(gramsPerUnit=45.0, gramsPerUnitSource="TYPED", gramsPerUnitSourceRank=2,
                 gramsPerUnitConfidence=None, gramsPerUnitSetAtMillis=700)


def offer_per100g(q, db, food_id, g):
    return db.execute(q["writePer100g"], dict(
        id=food_id, kcal=g["kcalPer100g"], protein=g["proteinPer100g"], carbs=g["carbsPer100g"],
        fat=g["fatPer100g"], source=g["per100gSource"], rank=g["per100gSourceRank"],
        confidence=g["per100gConfidence"], nowMillis=g["per100gSetAtMillis"])).rowcount


def offer_per_unit(q, db, food_id, g):
    return db.execute(q["writePerUnit"], dict(
        id=food_id, unitName=g["unitName"], kcal=g["kcalPerUnit"], protein=g["proteinPerUnit"],
        carbs=g["carbsPerUnit"], fat=g["fatPerUnit"], source=g["perUnitSource"],
        rank=g["perUnitSourceRank"], confidence=g["perUnitConfidence"],
        nowMillis=g["perUnitSetAtMillis"])).rowcount


def offer_weight(q, db, food_id, g):
    return db.execute(q["writeGramsPerUnit"], dict(
        id=food_id, grams=g["gramsPerUnit"], source=g["gramsPerUnitSource"],
        rank=g["gramsPerUnitSourceRank"], confidence=g["gramsPerUnitConfidence"],
        nowMillis=g["gramsPerUnitSetAtMillis"])).rowcount


def join(q, db, winner, loser, fills, now=9000):
    """The merge's sequence, after the refusal is asked: read, move, fill, delete, touch.

    The absorbed food is read FIRST. The app reads a food through its names (`toDomain` returns
    nothing for a food with no name), and `moveNames` leaves the loser with none — a read after it
    finds nothing to fill from. Asserted here because this script once modelled the fills as given
    and so passed while the app filled nothing.
    """
    assert db.execute(q["mealsHoldingBoth"], dict(winner=winner, loser=loser)).fetchall() == []
    names = "SELECT COUNT(*) FROM food_names WHERE foodId = ?"
    assert db.execute(names, (loser,)).fetchone()[0] > 0, "the absorbed food must be read while named"
    db.execute(q["movePastRows"], dict(winner=winner, loser=loser))
    db.execute(q["moveMealComponents"], dict(winner=winner, loser=loser))
    db.execute(q["moveNames"], dict(winner=winner, loser=loser))
    assert db.execute(names, (loser,)).fetchone()[0] == 0, "after moveNames the loser reads as nothing"
    written = [fill(q, db, winner) for fill in fills]
    db.execute(q["deleteFood"], dict(id=loser))
    db.execute(q["touch"], dict(id=winner, nowMillis=now))
    return written


def row(db, food_id):
    return dict(db.execute("SELECT * FROM foods WHERE id = ?", (food_id,)).fetchone())


def holds(stored, group):
    return all(stored[k] == v for k, v in group.items())


def with_history(db, food_id):
    """A logged row and a saved meal part pointing at the food, so a delete would show."""
    meal = db.execute("INSERT INTO meals (epochDay, loggedAtMillis) VALUES (20699, 1000)").lastrowid
    db.execute(
        "INSERT INTO food_items (mealId, name, portion, portionAmount, portionUnit, kcal, proteinG, "
        "carbsG, fatG, source, confidence, foodId) "
        "VALUES (?, 'Protein bar', '1 bar', 1.0, 'bar', 190, 15, 17, 6, 'LABEL', NULL, ?)",
        (meal, food_id),
    )
    saved = db.execute(
        "INSERT INTO saved_meals (name, nameKey, createdAtMillis, updatedAtMillis) "
        "VALUES ('Snack', 'snack', 1000, 1000)").lastrowid
    db.execute(
        "INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
        "VALUES (?, ?, 0, 1.0, 'UNITS')", (saved, food_id))
    return saved


def main():
    q = queries()
    needed = ["writePer100g", "writePerUnit", "writeGramsPerUnit", "mealsHoldingBoth",
              "movePastRows", "moveMealComponents", "moveNames", "deleteFood", "touch"]
    missing = [name for name in needed if name not in q]
    if missing:
        print(f"Could not find these statements in FoodDao.kt: {missing}")
        return 1

    checks = []
    check = lambda name, ok: checks.append((name, bool(ok)))

    # 1. The winner counts in slices; the absorbed food knows per-100 g, per-bar and a bar's weight.
    #    JoinedFacts: per-100 g is a blank, so it is filled; per-one is held, so it stays; the weight
    #    was measured against a bar and the winner counts in slices, so it is left behind.
    db, schema_name = latest_database()
    winner = food(db, "Protein bar", **PER_SLICE)
    loser = food(db, "Chocolate bar", **PER_100G, **PER_BAR, **WEIGHS_45)
    saved = with_history(db, loser)
    before = row(db, winner)
    written = join(q, db, winner, loser, [lambda q, db, w: offer_per100g(q, db, w, PER_100G)])
    after = row(db, winner)
    check("a blank per-100 g group is written through the guard's IS NULL arm", written == [1])
    check("it lands with source, rank, confidence and date unchanged", holds(after, PER_100G))
    check("the winner's own per-one group and unit name do not move", holds(after, PER_SLICE))
    check("a bar's weight is not set against a slice", after["gramsPerUnit"] is None)
    check("the edit stamp is the join's, not the copied date", after["updatedAtMillis"] == 9000
          and before["updatedAtMillis"] == 1000)
    check("the absorbed food is gone", db.execute("SELECT COUNT(*) FROM foods").fetchone()[0] == 1)
    check("its logged row now points at the winner, numbers intact", db.execute(
        "SELECT COUNT(*) FROM food_items WHERE foodId = ? AND kcal = 190 AND source = 'LABEL'",
        (winner,)).fetchone()[0] == 1)
    check("its meal part now points at the winner", db.execute(
        "SELECT foodId FROM saved_meal_components WHERE savedMealId = ?", (saved,)).fetchone()[0] == winner)
    check("the winner answers to both names", db.execute(
        "SELECT COUNT(*) FROM food_names WHERE foodId = ?", (winner,)).fetchone()[0] == 2)
    check("no foreign key is broken", db.execute("PRAGMA foreign_key_check").fetchall() == [])

    # 2. The winner knows only per-100 g; the absorbed food knows per-bar and a bar's weight.
    #    JoinedFacts: per-one is a blank and is filled, so the winner will count in bars, and the
    #    bar's weight comes with it.
    db, _ = latest_database()
    winner = food(db, "Protein bar", **{**PER_100G, "per100gSetAtMillis": 1000})
    loser = food(db, "Chocolate bar", **PER_BAR, **WEIGHS_45)
    written = join(q, db, winner, loser, [
        lambda q, db, w: offer_per_unit(q, db, w, PER_BAR),
        lambda q, db, w: offer_weight(q, db, w, WEIGHS_45),
    ])
    after = row(db, winner)
    check("a per-one group is carried with its unit name and provenance",
          written == [1, 1] and holds(after, PER_BAR))
    check("the weight comes with the per-one group it was measured against", holds(after, WEIGHS_45))
    check("the winner's per-100 g stays exactly as it was", after["per100gSetAtMillis"] == 1000
          and after["kcalPer100g"] == 120.0)

    # 3. Why JoinedFacts offers nothing where the winner holds a group: the guard alone would let an
    #    equal-rank figure replace the winner's, and the join question promises it keeps its numbers.
    db, _ = latest_database()
    winner = food(db, "Protein bar", **{**PER_SLICE, "unitName": "bar", "kcalPerUnit": 200.0})
    equal = offer_per_unit(q, db, winner, {**PER_BAR, "perUnitSource": "TYPED", "perUnitSourceRank": 2})
    check("the guard alone lets an equal-rank figure through, so the Kotlin must not offer it",
          equal == 1 and row(db, winner)["kcalPerUnit"] == 190.0)

    # 4. The refusal: a meal holding both is found before anything moves.
    db, _ = latest_database()
    winner = food(db, "Protein bar", **PER_SLICE)
    loser = food(db, "Chocolate bar", **PER_100G)
    saved = with_history(db, loser)
    db.execute("INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
               "VALUES (?, ?, 1, 1.0, 'UNITS')", (saved, winner))
    names = [r[0] for r in db.execute(q["mealsHoldingBoth"], dict(winner=winner, loser=loser))]
    check("a meal holding both is found, so the join is refused before anything is written",
          names == ["Snack"])

    failed = 0
    for name, ok in checks:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}")
        failed += 0 if ok else 1
    print()
    print(f"Schema: {schema_name}")
    if failed:
        print(f"{failed} check(s) failed.")
        return 1
    print("All checks passed. This is not a substitute for CI: the join's transaction and Room's own")
    print("validation are exercised only by RoomFoodRepositoryTest, which runs there.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
