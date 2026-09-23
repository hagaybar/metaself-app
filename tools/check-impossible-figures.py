#!/usr/bin/env python3
"""Run the repair of issue #7's own SQL, in the order `RoomFoodRepository.clearImpossibleFigures`
runs it, against real SQLite — and read the result back the way the app reads a food.

`RoomFoodRepositoryTest` is where the repair is tested, and it stands aside on the development box
(Robolectric's SQLite has no aarch64 build) and runs in CI. This narrows the gap. It extracts the
statements from `FoodDao.kt` — rather than retyping them, which would test a copy — builds a database
from the latest committed exported schema, and runs the repair's sequence: read every food row, then
clear each group the rule names, with the food's own edit stamp.

WHICH groups are cleared is decided in Kotlin by `ImpossibleFigures`, and tested by
`ImpossibleFiguresTest`, which runs anywhere. Here the decision is written out by hand for each case,
as the comment beside it says.

A food is read back through a model of `FoodWithNames.toDomain` (FoodMapping.kt), because that is
what decides what the owner sees: a food needs a name, a group needs all four figures, none negative,
and a source, and a food with neither number group reads as no food at all.

WHAT IT PROVES
  - every statement the repair uses parses and binds against the latest schema;
  - SQLite keeps an infinite figure as one (so such a food really is on a phone), and stores a
    not-a-number figure as NULL (so that one never was);
  - a cleared group is gone with its source, rank, confidence and date; the food's other groups,
    names, brand, barcode and edit stamp do not move;
  - a food left with no group is not deleted: its names, its logged rows and its meal part remain,
    no foreign key breaks, and it reads as no food;
  - no logged row's figures, meal part's amount or name row is touched;
  - running the sequence again changes nothing.

WHAT IT DOES NOT PROVE
  - Room's transaction, or anything Room validates. That is CI's.
  - the Kotlin that chooses the groups (ImpossibleFiguresTest) or the real mapping (FoodMapping).

Usage:  python3 tools/check-impossible-figures.py
"""

import json
import math
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DAO = ROOT / "app/src/main/java/com/metaself/app/data/food/FoodDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
INF = float("inf")


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
    db = sqlite3.connect(":memory:", isolation_level=None)
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
    columns = {"brand": "NA", "createdAtMillis": 1000, "updatedAtMillis": 1500, **columns}
    keys = ", ".join(columns)
    marks = ", ".join("?" for _ in columns)
    food_id = db.execute(f"INSERT INTO foods ({keys}) VALUES ({marks})", list(columns.values())).lastrowid
    db.execute(
        "INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
        "VALUES (?, ?, ?, 'na', 1, 1000)",
        (food_id, name, name.lower()),
    )
    return food_id


PER_100G = dict(kcalPer100g=422.0, proteinPer100g=33.0, carbsPer100g=38.0, fatPer100g=14.0,
                per100gSource="LABEL", per100gSourceRank=3, per100gConfidence=None,
                per100gSetAtMillis=700)
PER_BAR = dict(unitName="bar", kcalPerUnit=190.0, proteinPerUnit=15.0, carbsPerUnit=17.0,
               fatPerUnit=6.0, perUnitSource="TYPED", perUnitSourceRank=2, perUnitConfidence=None,
               perUnitSetAtMillis=700)
WEIGHS_45 = dict(gramsPerUnit=45.0, gramsPerUnitSource="TYPED", gramsPerUnitSourceRank=2,
                 gramsPerUnitConfidence=None, gramsPerUnitSetAtMillis=700)


def repair(q, db, decide):
    """The repair's sequence, in one transaction: read every row, clear what `decide` names.

    `decide` stands in for `ImpossibleFigures.of`, written out by hand per case. Returns how many
    groups were cleared, as the Kotlin does.
    """
    cleared = 0
    db.execute("BEGIN")
    for stored in db.execute(q["everyFood"]).fetchall():
        groups = decide(dict(stored))
        stamp = stored["updatedAtMillis"]
        if "PER_100G" in groups:
            db.execute(q["clearPer100g"], dict(id=stored["id"], nowMillis=stamp))
        if "PER_UNIT" in groups:
            db.execute(q["clearPerUnit"], dict(id=stored["id"], nowMillis=stamp))
        if "GRAMS_PER_UNIT" in groups:
            db.execute(q["clearGramsPerUnit"], dict(id=stored["id"], nowMillis=stamp))
        cleared += len(groups)
    db.execute("COMMIT")
    return cleared


def to_domain(db, food_id):
    """A model of `FoodWithNames.toDomain`: what the app shows for this food, or None."""
    stored = db.execute("SELECT * FROM foods WHERE id = ?", (food_id,)).fetchone()
    if stored is None:
        return None
    names = db.execute("SELECT * FROM food_names WHERE foodId = ?", (food_id,)).fetchall()
    preferred = next((n for n in names if n["isPreferred"]), None) or \
        min(names, key=lambda n: n["addedAtMillis"], default=None)
    if preferred is None:
        return None

    def nutrients(*figures):
        if any(f is None for f in figures) or any(f < 0 for f in figures):
            return None
        return figures

    per100g = nutrients(stored["kcalPer100g"], stored["proteinPer100g"],
                        stored["carbsPer100g"], stored["fatPer100g"])
    if stored["per100gSource"] is None:
        per100g = None
    per_unit = nutrients(stored["kcalPerUnit"], stored["proteinPerUnit"],
                         stored["carbsPerUnit"], stored["fatPerUnit"])
    if not (stored["unitName"] or "").strip() or stored["perUnitSource"] is None:
        per_unit = None
    if per100g is None and per_unit is None:
        return None
    weight = stored["gramsPerUnit"] if (stored["gramsPerUnit"] or 0) > 0 and \
        stored["gramsPerUnitSource"] is not None else None
    return dict(name=preferred["displayName"], per100g=per100g, perUnit=per_unit, weight=weight)


def listed(db):
    """What the manager lists: `observeAll`, each row read through `to_domain`, nothing dropped kept."""
    ids = [r["id"] for r in db.execute("SELECT id FROM foods ORDER BY updatedAtMillis DESC, id DESC")]
    return [f for f in (to_domain(db, i) for i in ids) if f is not None]


def logged(db, food_id, kcal, amount):
    meal = db.execute("INSERT INTO meals (epochDay, loggedAtMillis) VALUES (20699, 1000)").lastrowid
    return db.execute(
        "INSERT INTO food_items (mealId, name, portion, portionAmount, portionUnit, kcal, proteinG, "
        "carbsG, fatG, source, confidence, foodId) "
        "VALUES (?, 'Halva', 'some', ?, 'g', ?, 0, 0, 0, 'TYPED', NULL, ?)",
        (meal, amount, kcal, food_id),
    ).lastrowid


def snapshot(db, table):
    return [tuple(r) for r in db.execute(f"SELECT * FROM {table} ORDER BY 1")]


def main():
    q = queries()
    needed = ["everyFood", "clearPer100g", "clearPerUnit", "clearGramsPerUnit"]
    missing = [name for name in needed if name not in q]
    if missing:
        print(f"Could not find these statements in FoodDao.kt: {missing}")
        return 1

    checks = []
    check = lambda name, ok: checks.append((name, bool(ok)))

    # 0. What a phone can hold at all.
    db, schema_name = latest_database()
    inf_id = food(db, "Protein bar", **{**PER_100G, "kcalPer100g": INF})
    nan_id = food(db, "Oil", **{**PER_100G, "fatPer100g": float("nan")})
    check("an infinite figure is stored and read back as infinite",
          math.isinf(db.execute(q["everyFood"]).fetchall()[0]["kcalPer100g"]))
    check("a not-a-number figure is stored as NULL, so no phone holds one",
          db.execute("SELECT fatPer100g FROM foods WHERE id = ?", (nan_id,)).fetchone()[0] is None)
    check("the app reads an infinite group as a food like any other before the repair",
          to_domain(db, inf_id)["per100g"][0] == INF)

    # 1. Infinite calories per 100 g beside a believable bar and weight.
    #    ImpossibleFigures: PER_100G only.
    db, _ = latest_database()
    bar = food(db, "Protein bar", barcode="2000012345678", **{**PER_100G, "kcalPer100g": INF},
               **PER_BAR, **WEIGHS_45)
    fine = food(db, "Yoghurt", **PER_100G)
    names_before = snapshot(db, "food_names")
    fine_before = snapshot(db, "foods")[1]
    cleared = repair(q, db, lambda r: {"PER_100G"} if r["id"] == bar else set())
    after = dict(db.execute("SELECT * FROM foods WHERE id = ?", (bar,)).fetchone())
    check("one group cleared", cleared == 1)
    check("the per-100 g group is gone with its source, rank, confidence and date",
          all(after[k] is None for k in PER_100G))
    check("the bar and its weight stay, provenance and all",
          all(after[k] == v for k, v in {**PER_BAR, **WEIGHS_45}.items()))
    check("the edit stamp is the food's own, so it does not move up the list",
          after["updatedAtMillis"] == 1500)
    check("barcode and brand stay", after["barcode"] == "2000012345678" and after["brand"] == "NA")
    check("the other food is untouched", snapshot(db, "foods")[1] == fine_before)
    check("no name row moves", snapshot(db, "food_names") == names_before)
    shown = to_domain(db, bar)
    check("the app still shows it, counted in bars, with no per-100 g figure",
          shown is not None and shown["per100g"] is None and shown["perUnit"] == (190.0, 15.0, 17.0, 6.0))

    # 2. A food whose only group is impossible, with history: a logged row holding the saturated
    #    figure and an infinite amount, and a meal part. ImpossibleFigures: PER_100G.
    db, _ = latest_database()
    halva = food(db, "Halva", **{**PER_100G, "fatPer100g": 1e12})
    row = logged(db, halva, 2_147_483_647, INF)
    saved = db.execute("INSERT INTO saved_meals (name, nameKey, createdAtMillis, updatedAtMillis) "
                       "VALUES ('Dessert', 'dessert', 1000, 1000)").lastrowid
    db.execute("INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
               "VALUES (?, ?, 0, 30.0, 'GRAMS')", (saved, halva))
    rows_before = snapshot(db, "food_items")
    parts_before = snapshot(db, "saved_meal_components")
    cleared = repair(q, db, lambda r: {"PER_100G"})
    check("the food row is not deleted", db.execute(
        "SELECT COUNT(*) FROM foods WHERE id = ?", (halva,)).fetchone()[0] == 1)
    check("its name stays, so logging it again finds the same food", db.execute(
        "SELECT foodId FROM food_names WHERE nameKey = 'halva'").fetchone()[0] == halva)
    check("it reads as no food, so it leaves every list", to_domain(db, halva) is None and listed(db) == [])
    check("the logged row keeps every figure and its link, the saturated one included",
          snapshot(db, "food_items") == rows_before and db.execute(
              "SELECT kcal, portionAmount, foodId FROM food_items WHERE id = ?", (row,)).fetchone()[:]
          == (2_147_483_647, INF, halva))
    check("the meal part keeps its food and amount", snapshot(db, "saved_meal_components") == parts_before)
    check("no foreign key is broken", db.execute("PRAGMA foreign_key_check").fetchall() == [])

    # 3. Every group impossible at once, then the whole repair run twice.
    #    ImpossibleFigures: all three the first time; nothing the second.
    db, _ = latest_database()
    worst = food(db, "Bar", **{**PER_100G, "kcalPer100g": INF},
                 **{**PER_BAR, "proteinPerUnit": 9000.0}, **{**WEIGHS_45, "gramsPerUnit": INF})
    first = repair(q, db, lambda r: {"PER_100G", "PER_UNIT", "GRAMS_PER_UNIT"}
                   if r["kcalPer100g"] is not None else set())
    once = snapshot(db, "foods")
    second = repair(q, db, lambda r: {"PER_100G", "PER_UNIT", "GRAMS_PER_UNIT"}
                    if r["kcalPer100g"] is not None else set())
    check("all three groups are cleared together", first == 3 and all(
        v is None for k, v in dict(db.execute("SELECT * FROM foods WHERE id = ?", (worst,)).fetchone()).items()
        if k not in ("id", "brand", "barcode", "createdAtMillis", "updatedAtMillis", "hiddenAtMillis")))
    check("a second run finds nothing and changes nothing", second == 0 and snapshot(db, "foods") == once)

    failed = 0
    for name, ok in checks:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}")
        failed += 0 if ok else 1
    print()
    print(f"Schema: {schema_name}")
    if failed:
        print(f"{failed} check(s) failed.")
        return 1
    print("All checks passed. This is not a substitute for CI: the repair's transaction and Room's")
    print("own validation are exercised only by RoomFoodRepositoryTest, which runs there.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
