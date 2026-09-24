#!/usr/bin/env python3
"""Run the food form's Save, in the order `RoomFoodRepository.saveForm` runs it, against real SQLite.

`RoomFoodRepositoryTest` is where a Save is tested, and it stands aside on the development box
(Robolectric's SQLite has no aarch64 build) and runs in CI. This narrows the gap for D54, where
`correct()` stopped clearing all three groups and started leaving an unchanged group alone. It
extracts the statements from `FoodDao.kt` — rather than retyping them, which would test a copy —
builds a database from the latest committed exported schema, and runs the Save's sequence.

THE READ PATH IS MODELLED, NOT ASSUMED. `correct()` decides each group from what the food READS as —
`dao.byId(...)?.toDomain()` — not from its columns. `toDomain` returns nothing for a food with no
name, and drops a group it cannot believe (half its figures, no source, a blank unit). So each case
here reads the food through `to_domain` (a model of `FoodWithNames.toDomain`), plans from that read
with `plan` (a model of `Correction.plan`, whose Kotlin is tested by `CorrectionTest`), and every
case also states the plan it expects by hand, so the model cannot quietly drift from the intent.
A check of the SQL alone once passed while the app did nothing; this one asserts what the app would
READ afterwards.

WHAT IT PROVES
  - every statement the Save uses parses and binds against the latest schema;
  - an unchanged group gets no statement: source, rank, confidence and date stay, byte for byte;
  - a changed group is cleared and then lands through the guard's IS NULL arm, even an estimate
    over a label — and without the clear the guard would refuse it;
  - a group the read drops is swept away when nothing arrives for it;
  - a food with no name reads as nothing, so the Save keeps none of its groups — why `correct()`
    must read the food while it is named (the rename step keeps the name row; it never removes it);
  - the saved-meal refusal is asked before any statement, and a refused Save leaves the food as it
    was, rename included.

WHAT IT DOES NOT PROVE
  - Room's transaction, or anything Room validates. That is CI's.
  - The Kotlin that plans (CorrectionTest) or reads (FoodMapping). Those are modelled here.

Every figure is invented. The Oat biscuit is D54's own invented example.

Usage:  python3 tools/check-correct-keeps.py
"""

import json
import pathlib
import re
import sqlite3
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DAO = ROOT / "app/src/main/java/com/metaself/app/data/food/FoodDao.kt"
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"

RANK = {"LABEL": 3, "TYPED": 2, "AI_ESTIMATE": 1, "REPEATED": 0, "UNRECOGNISED": 0}


def queries():
    """Every `@Query` in the DAO, by the name of the function it annotates.

    A statement is either written in the annotation or named there by a `const val` in the same
    file (`MEALS_USING`, shared by the delete refusal and the food page, D55 §3).
    """
    src = DAO.read_text()
    literal = r'((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)'
    joined = lambda body: "".join(re.findall(r'"((?:[^"\\]|\\.)*)"', body)).replace('\\"', '"')
    constants = {name: joined(body)
                 for name, body in re.findall(r'const\s+val\s+(\w+)\s*=\s*' + literal, src)}
    found = {}
    pattern = r'@Query\(\s*(?:' + literal + r'|(\w+)),?\s*\)\s*(?:suspend\s+)?fun\s+(\w+)'
    for body, constant, fun in re.findall(pattern, src):
        if body:
            found[fun] = joined(body)
        elif constant in constants:
            found[fun] = constants[constant]
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
    """A food and, unless `name` is None, its preferred name."""
    columns = {"brand": "NA", "createdAtMillis": 1000, "updatedAtMillis": 1000, **columns}
    keys = ", ".join(columns)
    marks = ", ".join("?" for _ in columns)
    food_id = db.execute(f"INSERT INTO foods ({keys}) VALUES ({marks})", list(columns.values())).lastrowid
    if name is not None:
        db.execute(
            "INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
            "VALUES (?, ?, ?, 'na', 1, 1000)",
            (food_id, name, name.lower()),
        )
    return food_id


LABEL_100G = dict(kcalPer100g=480.0, proteinPer100g=7.0, carbsPer100g=62.0, fatPer100g=22.0,
                  per100gSource="LABEL", per100gSourceRank=3, per100gConfidence=None,
                  per100gSetAtMillis=700)
TYPED_BISCUIT = dict(unitName="biscuit", kcalPerUnit=90.0, proteinPerUnit=1.0, carbsPerUnit=12.0,
                     fatPerUnit=1.0, perUnitSource="TYPED", perUnitSourceRank=2,
                     perUnitConfidence=None, perUnitSetAtMillis=700)
WEIGHS_18 = dict(gramsPerUnit=18.0, gramsPerUnitSource="TYPED", gramsPerUnitSourceRank=2,
                 gramsPerUnitConfidence=None, gramsPerUnitSetAtMillis=700)


# --- The read path: a model of `FoodWithNames.toDomain` --------------------------------------------

def to_domain(db, food_id):
    """What the app reads this food as, or None — with each group's figures and provenance."""
    stored = db.execute("SELECT * FROM foods WHERE id = ?", (food_id,)).fetchone()
    if stored is None:
        return None
    names = db.execute("SELECT * FROM food_names WHERE foodId = ?", (food_id,)).fetchall()
    preferred = next((n for n in names if n["isPreferred"]), None) or \
        min(names, key=lambda n: n["addedAtMillis"], default=None)
    if preferred is None:
        return None

    def figures(*values):
        if any(v is None for v in values) or any(v < 0 for v in values):
            return None
        return tuple(values)

    def provenance(source, confidence, set_at):
        if source is None:
            return None
        if source not in RANK:
            source, confidence = "UNRECOGNISED", None
        return (source, confidence if source == "AI_ESTIMATE" else None, set_at or 0)

    per100g = None
    f = figures(stored["kcalPer100g"], stored["proteinPer100g"], stored["carbsPer100g"],
                stored["fatPer100g"])
    p = provenance(stored["per100gSource"], stored["per100gConfidence"], stored["per100gSetAtMillis"])
    if f and p:
        per100g = dict(figures=f, provenance=p)

    per_unit = None
    f = figures(stored["kcalPerUnit"], stored["proteinPerUnit"], stored["carbsPerUnit"],
                stored["fatPerUnit"])
    p = provenance(stored["perUnitSource"], stored["perUnitConfidence"], stored["perUnitSetAtMillis"])
    if (stored["unitName"] or "").strip() and f and p:
        per_unit = dict(unit=stored["unitName"], figures=f, provenance=p)

    if per100g is None and per_unit is None:
        return None

    weight = None
    p = provenance(stored["gramsPerUnitSource"], stored["gramsPerUnitConfidence"],
                   stored["gramsPerUnitSetAtMillis"])
    if (stored["gramsPerUnit"] or 0) > 0 and p:
        weight = dict(grams=stored["gramsPerUnit"], provenance=p)

    return dict(name=preferred["displayName"], per100g=per100g, perUnit=per_unit, weight=weight)


# --- The plan: a model of `Correction.plan` ---------------------------------------------------------

def same_figure(a, b):
    """D45's comparison: nine significant figures, with a floor at zero."""
    if a == b:
        return True
    apart = abs(a - b)
    return apart <= 1e-9 or apart <= 1e-9 * max(abs(a), abs(b))


def step(held, arriving, same):
    if arriving is None:
        return "clear"
    if held is not None and same(held, arriving):
        return "keep"
    return "replace"


def plan(stored, incoming):
    figs = lambda h, a: all(same_figure(x, y) for x, y in zip(h["figures"], a["figures"]))
    return (
        step(stored and stored["per100g"], incoming.get("per100g"), figs),
        step(stored and stored["perUnit"], incoming.get("perUnit"),
             lambda h, a: h["unit"] == a["unit"] and figs(h, a)),
        step(stored and stored["weight"], incoming.get("weight"),
             lambda h, a: same_figure(h["grams"], a["grams"])),
    )


def group(figures, source, confidence=None, unit=None):
    g = dict(figures=figures, source=source, confidence=confidence)
    if unit is not None:
        g["unit"] = unit
    return g


def weight(grams, source="TYPED"):
    return dict(grams=grams, source=source)


# --- The Save: rename, setBrand, correct, in `saveForm`'s order, as one transaction ------------------

class Refused(Exception):
    pass


def correct(q, db, food_id, incoming, now, expect_plan):
    """`RoomFoodRepository.correct`, statement for statement. Returns the plan it ran."""
    stored = to_domain(db, food_id)
    if stored is not None and stored["perUnit"] is not None and incoming.get("perUnit") is None:
        if db.execute(q["mealsUsing"], dict(foodId=food_id)).fetchall():
            raise Refused("NeededBySavedMeals")
    decided = plan(stored, incoming)
    assert decided == expect_plan, f"plan {decided} is not the expected {expect_plan}"
    clears = ("clearPer100g", "clearPerUnit", "clearGramsPerUnit")
    for name, fate in zip(clears, decided):
        if fate != "keep":
            db.execute(q[name], dict(id=food_id, nowMillis=now))
    written = []
    per100g, per_unit, grams = (incoming.get(k) for k in ("per100g", "perUnit", "weight"))
    if decided[0] == "replace":
        k, p, c, f = per100g["figures"]
        written.append(db.execute(q["writePer100g"], dict(
            id=food_id, kcal=k, protein=p, carbs=c, fat=f, source=per100g["source"],
            rank=RANK[per100g["source"]], confidence=per100g["confidence"], nowMillis=now)).rowcount)
    if decided[1] == "replace":
        k, p, c, f = per_unit["figures"]
        written.append(db.execute(q["writePerUnit"], dict(
            id=food_id, unitName=per_unit["unit"], kcal=k, protein=p, carbs=c, fat=f,
            source=per_unit["source"], rank=RANK[per_unit["source"]],
            confidence=per_unit["confidence"], nowMillis=now)).rowcount)
    if decided[2] == "replace":
        written.append(db.execute(q["writeGramsPerUnit"], dict(
            id=food_id, grams=grams["grams"], source=grams["source"], rank=RANK[grams["source"]],
            confidence=None, nowMillis=now)).rowcount)
    return decided, written


def save_form(q, db, food_id, name, incoming, now, expect_plan):
    """`saveForm`: the three steps in one transaction; a refusal rolls all of it back."""
    db.execute("SAVEPOINT save_form")
    try:
        names = db.execute("SELECT * FROM food_names WHERE foodId = ? ORDER BY isPreferred DESC, "
                           "addedAtMillis", (food_id,)).fetchall()
        if names:
            db.execute(q["renameNameRow"], dict(id=names[0]["id"], displayName=name,
                                                nameKey=name.lower()))
            db.execute(q["touch"], dict(id=food_id, nowMillis=now))
        db.execute(q["setBrand"], dict(id=food_id, brand="NA", nowMillis=now))
        result = correct(q, db, food_id, incoming, now, expect_plan)
    except Refused:
        db.execute("ROLLBACK TO save_form")
        db.execute("RELEASE save_form")
        return "refused"
    db.execute("RELEASE save_form")
    return result


def row(db, food_id):
    return dict(db.execute("SELECT * FROM foods WHERE id = ?", (food_id,)).fetchone())


def holds(stored, columns):
    return all(stored[k] == v for k, v in columns.items())


def main():
    q = queries()
    needed = ["writePer100g", "writePerUnit", "writeGramsPerUnit", "clearPer100g", "clearPerUnit",
              "clearGramsPerUnit", "mealsUsing", "renameNameRow", "touch", "setBrand"]
    missing = [name for name in needed if name not in q]
    if missing:
        print(f"Could not find these statements in FoodDao.kt: {missing}")
        return 1

    checks = []
    check = lambda name, ok: checks.append((name, bool(ok)))

    # The form hands over every group TYPED unless accepted from a review, so an untouched Oat
    # biscuit arrives as its own figures, all TYPED.
    as_typed = dict(per100g=group((480.0, 7.0, 62.0, 22.0), "TYPED"),
                    perUnit=group((90.0, 1.0, 12.0, 1.0), "TYPED", unit="biscuit"),
                    weight=weight(18.0))

    # 1. D54's example: per biscuit accepted (fat 1 -> 4, MEDIUM); per 100 g and the weight equal.
    db, schema_name = latest_database()
    oat = food(db, "Oat biscuit", **LABEL_100G, **TYPED_BISCUIT, **WEIGHS_18)
    accepted = {**as_typed, "perUnit": group((90.0, 1.0, 12.0, 4.0), "AI_ESTIMATE", "MEDIUM",
                                              unit="biscuit")}
    _, written = save_form(q, db, oat, "Oat biscuit", accepted, 2000, ("keep", "replace", "keep"))
    after, seen = row(db, oat), to_domain(db, oat)
    check("an unchanged label group keeps source, rank, confidence and date", holds(after, LABEL_100G))
    check("an unchanged weight keeps its source and date", holds(after, WEIGHS_18))
    check("the accepted group is cleared and lands through the IS NULL arm", written == [1])
    check("and the app reads it as an estimate, MEDIUM, dated by the Save",
          seen["perUnit"] == dict(unit="biscuit", figures=(90.0, 1.0, 12.0, 4.0),
                                  provenance=("AI_ESTIMATE", "MEDIUM", 2000)))
    check("and still reads the label as a label", seen["per100g"]["provenance"] == ("LABEL", None, 700))

    # 2. Nothing changed: the Save's figures are the stored ones, so correct() writes nothing.
    db, _ = latest_database()
    oat = food(db, "Oat biscuit", **LABEL_100G, **TYPED_BISCUIT, **WEIGHS_18)
    before = row(db, oat)
    save_form(q, db, oat, "Oat biscuit", as_typed, 2000, ("keep", "keep", "keep"))
    after = row(db, oat)
    check("an untouched Save leaves every figure column byte for byte",
          {k: v for k, v in after.items() if k != "updatedAtMillis"} ==
          {k: v for k, v in before.items() if k != "updatedAtMillis"})
    check("the edit stamp still moves, by the rename and brand steps", after["updatedAtMillis"] == 2000)

    # 3. An echo of the label to nine significant figures, arriving as an estimate: kept.
    db, _ = latest_database()
    oat = food(db, "Oat biscuit", **LABEL_100G, **TYPED_BISCUIT, **WEIGHS_18)
    echoed = {**as_typed, "per100g": group((480.0 * (1 + 1e-12), 7.0, 62.0, 22.0), "AI_ESTIMATE",
                                           "LOW")}
    correct(q, db, oat, echoed, 2000, ("keep", "keep", "keep"))
    check("an estimate equal to a label leaves the label", holds(row(db, oat), LABEL_100G))

    # 4. An accepted estimate that changes a label: the clear is what lets it land.
    db, _ = latest_database()
    impossible = {**LABEL_100G, "kcalPer100g": 120.0, "proteinPer100g": 30.0, "carbsPer100g": 40.0,
                  "fatPer100g": 10.0}
    lab = food(db, "Invented label", **impossible)
    guarded_alone = db.execute(q["writePer100g"], dict(
        id=lab, kcal=370.0, protein=30.0, carbs=40.0, fat=10.0, source="AI_ESTIMATE", rank=1,
        confidence="MEDIUM", nowMillis=1500)).rowcount
    check("without the clear the guard refuses an estimate over a label", guarded_alone == 0)
    fixed = dict(per100g=group((370.0, 30.0, 40.0, 10.0), "AI_ESTIMATE", "MEDIUM"))
    _, written = correct(q, db, lab, fixed, 2000, ("replace", "clear", "clear"))
    seen = to_domain(db, lab)
    check("with the clear it lands, and reads as the estimate",
          written == [1] and seen["per100g"] == dict(figures=(370.0, 30.0, 40.0, 10.0),
                                                     provenance=("AI_ESTIMATE", "MEDIUM", 2000)))

    # 5. A per-one group the read drops (no source): the app sees none, nothing arrives, so it is
    #    cleared — the Save before D54 swept it, and keeping it would leave it standing unseen.
    db, _ = latest_database()
    junk = {**TYPED_BISCUIT, "perUnitSource": None, "perUnitSourceRank": None}
    odd = food(db, "Oat biscuit", **LABEL_100G, **junk)
    check("the read drops a per-one group with no source", to_domain(db, odd)["perUnit"] is None)
    correct(q, db, odd, dict(per100g=as_typed["per100g"]), 2000, ("keep", "clear", "clear"))
    after = row(db, odd)
    check("and the Save clears its columns", after["unitName"] is None and after["kcalPerUnit"] is None)
    check("while the label beside it is not touched", holds(after, LABEL_100G))

    # 6. The read-path dependency: a food with no name reads as nothing, so nothing is kept — its
    #    label would be rewritten TYPED. The editor only opens a food it could read, and saveForm's
    #    rename edits the name row in place, so correct() always reads a named food.
    db, _ = latest_database()
    nameless = food(db, None, **LABEL_100G)
    check("a food with no name reads as nothing", to_domain(db, nameless) is None)
    correct(q, db, nameless, dict(per100g=as_typed["per100g"]), 2000, ("replace", "clear", "clear"))
    check("so its unchanged label is rewritten, which is why the read must see the name",
          row(db, nameless)["per100gSource"] == "TYPED")
    db, _ = latest_database()
    named = food(db, "Oat biscuit", **LABEL_100G)
    save_form(q, db, named, "Oat cracker", dict(per100g=as_typed["per100g"]), 2000,
              ("keep", "clear", "clear"))
    check("after saveForm's rename the food still reads, and its label stays",
          to_domain(db, named)["name"] == "Oat cracker" and holds(row(db, named), LABEL_100G))

    # 7. The refusal: emptying a group a saved meal counts in, asked before any statement.
    db, _ = latest_database()
    oat = food(db, "Oat biscuit", **LABEL_100G, **TYPED_BISCUIT, **WEIGHS_18)
    saved = db.execute("INSERT INTO saved_meals (name, nameKey, createdAtMillis, updatedAtMillis) "
                       "VALUES ('Snack', 'snack', 1000, 1000)").lastrowid
    db.execute("INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
               "VALUES (?, ?, 0, 1.0, 'UNITS')", (saved, oat))
    before = row(db, oat)
    result = save_form(q, db, oat, "Oat cracker", dict(per100g=as_typed["per100g"]), 2000, None)
    check("emptying a group a saved meal counts in is refused", result == "refused")
    check("and the refused Save leaves the food and its name as they were",
          row(db, oat) == before and to_domain(db, oat)["name"] == "Oat biscuit")

    check("no foreign key is broken", db.execute("PRAGMA foreign_key_check").fetchall() == [])

    failed = 0
    for name, ok in checks:
        print(f"  {'ok  ' if ok else 'FAIL'}  {name}")
        failed += 0 if ok else 1
    print()
    print(f"Schema: {schema_name}")
    if failed:
        print(f"{failed} check(s) failed.")
        return 1
    print("All checks passed. This is not a substitute for CI: the Save's transaction and Room's own")
    print("validation are exercised only by RoomFoodRepositoryTest, which runs there.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
