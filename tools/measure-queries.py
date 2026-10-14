#!/usr/bin/env python3
"""D153: every DAO query, against a made-up store the size of a year of a band's record.

Builds a database from an exported schema (`app/schemas/.../<version>.json`, the newest by default),
fills it with INVENTED data the size of a year of a wrist band's record plus a year of food logging,
then runs every `@Query` of every DAO — extracted from the Kotlin sources, never retyped — and the
four child queries Room generates for `@Relation` reads, each with representative arguments. For each
it prints `EXPLAIN QUERY PLAN` and the time it took here.

THE SIZES (all invented, chosen to be generous for one wrist band; none is taken from anyone's record)
  - 366 days, ending 2026-09-03 (the tests' TEST_EPOCH_DAY = 20699).
  - health_readings, two writing apps:
      band app:  HEART_RATE one record an hour holding a sample a minute (1,440 rows a day);
                 STEPS, DISTANCE, ACTIVE_KCAL, TOTAL_KCAL one record per 15 minutes (96 a day each);
                 OXYGEN_SATURATION 24 a day, HRV_RMSSD 6, RESPIRATORY_RATE 6, RESTING_HEART_RATE 1;
      phone app: STEPS one record an hour (24 a day); WEIGHT and BODY_FAT one a week.
    About 1,882 rows a day, ~689,000 in the year. Record ids are 32-character hex, as long as real ids.
  - workouts 5 a week (~261), 1 in 10 typed by hand; sleep 1 night a day with 28 stages (10,248);
    health_days one a day; archive_months 13; health_sync one per kind.
  - food: 250 foods with 320 names, 4 meals a day of 3 items (1,464 meals, 4,392 items), 25 saved
    meals with 80 components, 60 products, 300 weighings.
  - trainer: 60 suggestions, 150 reviews, 15 weekly plans, 60 answers, 52 letters, 400 messages,
    30 sheet versions, 4 splits, 10 corrections.

WHAT IT REPORTS
  Every query whose plan SCANs a table that grows with the record (health_readings, workouts,
  sleep_sessions, sleep_stages, health_days, meals, food_items, weights) or that takes longer than
  --slow-ms (default 50) here. A query reading a whole table by design (the backup, a restore) is
  listed as such, with its time, and does not fail the run.

WHAT IT DOES NOT PROVE
  - The phone's timing. This is CPython's sqlite3 on the development box, with the file in memory;
    a phone's flash and slower cores make every figure larger. Use the times to rank, not to promise.
  - The plan SQLite on the phone chooses: its version may differ, and Room never runs ANALYZE, so
    neither does this script.
  - How often a query runs. A 5 ms query run once per deleted id inside the copy's transaction is
    worse than a 40 ms one run once a day; that judgement is made by reading the callers.

Exit status: 1 when a query outside ACCEPTED scans a growing table or is slow, else 0.

  --dense-distance writes DISTANCE a record a minute instead (about 1,350 more rows a day), the
  pessimistic case for the queries that look for a distance reading during a workout.

Usage:  python3 -I tools/measure-queries.py [--version 15] [--slow-ms 50] [--only Dao.function] [--dense-distance]
"""

import argparse
import json
import pathlib
import random
import re
import sqlite3
import sys
import time

ROOT = pathlib.Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / "app/schemas/com.metaself.app.data.day.MetaSelfDatabase"
SOURCES = ROOT / "app/src/main/java/com/metaself/app/data"

LAST_DAY = 20_699          # 2026-09-03, the tests' TEST_EPOCH_DAY
DAYS = 366
FIRST_DAY = LAST_DAY - DAYS + 1
DAY_MS = 86_400_000
BAND = "com.example.band"   # invented package names
PHONE = "com.example.phone"
GROWING = ("health_readings", "workouts", "sleep_sessions", "sleep_stages", "health_days",
           "meals", "food_items", "weights")
DENSE_DISTANCE = False      # --dense-distance: DISTANCE a record a minute instead of every 15 minutes
READING_KINDS = ("STEPS", "DISTANCE", "ACTIVE_KCAL", "TOTAL_KCAL", "HEART_RATE", "RESTING_HEART_RATE",
                 "HRV_RMSSD", "OXYGEN_SATURATION", "RESPIRATORY_RATE", "WEIGHT", "BODY_FAT")

# Queries that read or clear a whole table on purpose; reported, never failed. Name -> why.
ACCEPTED = {
    # The backup file and the restore: every row of a table, by definition.
    **{f"{dao}.{fn}": "backup or restore: the whole table by design" for dao, fn in [
        ("MealDao", "allMeals"), ("MealDao", "deleteAllMeals"), ("WeightDao", "all"), ("WeightDao", "deleteAll"),
        ("FoodDao", "everyFood"), ("HealthDayDao", "all"), ("HealthDayDao", "deleteAll"),
        ("SleepDao", "allSessions"), ("SleepDao", "allStages"), ("SleepDao", "allNights"), ("SleepDao", "deleteAll"),
        ("WorkoutDao", "all"), ("WorkoutDao", "deleteAll"), ("MovementCorrectionDao", "all"),
        ("TrainerDao", "allPlans"), ("TrainerDao", "allReviews"), ("TrainerDao", "allProgrammes"),
        ("TrainerDao", "allConfirmations"), ("TrainerDao", "allLetters"), ("TrainerDao", "allMessages"),
        ("TrainerDao", "allSheets"), ("SessionSplitDao", "all"),
    ]},
    "WeightDao.observeAll": "the weight trend walks every weighing; one row a day at most",
    "MealDao.observeLoggedDays": "every day holding food, for the streak; walks the meals' epochDay index",
    "MealDao.deleteEmptyMeals": "after a delete or a gather; NOT IN builds one ephemeral table of food_items.mealId",
    "MealDao.rowsWithNoFood": "the on-open repair; searches food_items by the foodId index (IS NULL)",
    "HealthDayDao.observeEarliest": "MIN over the primary key: one step, not a walk",
    "HealthDayDao.observeCount": "COUNT over 366 rows at most a year",
    "HealthDayDao.observeEarliestWithSteps": "first non-null in key order: stops at the first day with steps",
    "WorkoutDao.observeEarliest": "MIN over the epochDay index: one step",
    "WorkoutDao.observeWeeklyRunning": "no caller in the app (its tests only)",
    "HealthReadingDao.ofKindInDays": "the monthly archive: one kind's month through the index; the time is the "
                                     "rows themselves (D153 report: read inside a transaction, not changed here)",
}

# Representative arguments, by parameter name; a per-query entry in OVERRIDES wins.
DEFAULTS = {
    "epochDay": LAST_DAY - 1, "fromEpochDay": LAST_DAY - 6, "toEpochDay": LAST_DAY,
    "fromDay": LAST_DAY - 8, "toDay": LAST_DAY,
    "kind": "HEART_RATE", "kinds": list(READING_KINDS), "origin": BAND,
    "weeks": 12, "count": 5, "days": list(range(LAST_DAY - 6, LAST_DAY + 1)),
    "weekMonday": LAST_DAY - 3, "status": "STOPPED", "day": LAST_DAY, "start": LAST_DAY,
    "month": "2026-08", "changedAtSeen": 1, "atMillis": 1, "nowMillis": 1,
    "barcode": "0000000000017", "nameKey": "food 17", "brandKey": "na", "brand": "NA",
    "displayName": "Food 17", "unitName": "portion", "source": "AI", "rank": 1, "confidence": None,
    "kcal": 100.0, "protein": 1.0, "carbs": 1.0, "fat": 1.0, "grams": 50.0, "amount": 1.0,
    "countedAs": "GRAMS", "position": 3, "name": "Meal 3", "hidden": 0, "hiddenAtMillis": None,
    "kept": 1, "suggestion": "{}", "exceptId": 1, "exceptWorkoutId": 1, "keepId": 1,
    "from": "", "to": "", "winner": 1, "loser": 2,
}
MS = lambda day: day * DAY_MS  # noqa: E731
OVERRIDES = {
    # day ranges
    "HealthDayDao.observeBetween": {"from": LAST_DAY - 83, "to": LAST_DAY},
    "WorkoutDao.observeBetween": {"from": LAST_DAY - 83, "to": LAST_DAY},
    "SleepDao.sessionsBetween": {"from": LAST_DAY - 6, "to": LAST_DAY},
    # Its one caller since D153's fix: the band report in Settings, over 30 days, once a visit. (Before
    # it, Your movement -> Year asked ~370 days and every trainer request ~13 months, on every write.)
    "WorkoutDao.idsWithOwnDistance": {"from": LAST_DAY - 29, "to": LAST_DAY},
    # What replaced it on every screen: only the workouts overlapping another (invented: three)
    "WorkoutDao.idsWithOwnDistanceAmong": {"ids": [1, 2, 3]},
    "WorkoutDao.syncedIdsBetween": {"from": LAST_DAY - 9, "to": LAST_DAY},
    "WorkoutDao.dropSyncedNotIn": {"from": LAST_DAY - 9, "to": LAST_DAY, "keep": ["w5", "w6"]},
    # millisecond windows: the copy's re-read of the last ten days
    "SleepDao.daysBetween": {"from": MS(LAST_DAY - 9), "to": MS(LAST_DAY + 1)},
    "SleepDao.deleteBetween": {"from": MS(LAST_DAY - 9), "to": MS(LAST_DAY + 1)},
    "WorkoutDao.visibleSyncedBetween": {"from": MS(LAST_DAY - 9), "to": MS(LAST_DAY + 1)},
    "HealthReadingDao.recordsOfKindBetween": {"fromDay": LAST_DAY - 10, "toDay": LAST_DAY + 1,
                                              "from": MS(LAST_DAY - 9), "to": MS(LAST_DAY + 1)},
    # one workout's heart rate: a 45-minute window
    "HealthReadingDao.ofKindBetween": {"fromDay": LAST_DAY - 2, "toDay": LAST_DAY,
                                       "from": MS(LAST_DAY - 1) + 18 * 3_600_000,
                                       "to": MS(LAST_DAY - 1) + 18 * 3_600_000 + 45 * 60_000},
    # the monthly archive: one kind over one month
    "HealthReadingDao.ofKindInDays": {"fromDay": LAST_DAY - 33, "toDay": LAST_DAY - 3},
    "FoodDao.renameNameRow": {"nameKey": "food renamed"},
    # ids: filled in once the rows exist (see representative())
}


# --- extracting the SQL ----------------------------------------------------------------------------

STRING = r'"((?:[^"\\]|\\.)*)"'


def literals(text):
    return "".join(re.findall(STRING, text)).replace('\\"', '"')


def queries():
    """(name, params, sql) for every @Query in every DAO under data/, in source order."""
    found = []
    for path in sorted(SOURCES.rglob("*.kt")):
        src = path.read_text()
        if "@Query" not in src:
            continue
        consts = {m.group(1): literals(m.group(2)) for m in re.finditer(
            r'(?:private )?const val (\w+)\s*=\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+)', src)}
        dao = None
        for m in re.finditer(r'interface (\w+Dao)\b|@Query\(\s*((?:"(?:[^"\\]|\\.)*"\s*\+?\s*)+|\w+),?\s*\)'
                             r'(?:\s*@[\w.]+)*\s*(?:suspend )?fun (\w+)\(([^)]*)\)', src):
            if m.group(1):
                dao = m.group(1)
                continue
            body = m.group(2)
            sql = consts[body] if re.fullmatch(r"\w+", body) else literals(body)
            params = [p.split(":")[0].strip() for p in m.group(4).split(",") if p.strip()]
            found.append((f"{dao}.{m.group(3)}", params, sql))
    return found


# Room's own child reads for @Relation (as in the generated *_Impl), with the parent ids bound.
RELATIONS = [
    ("@Relation MealWithItems", "SELECT `id`,`mealId`,`name`,`portion`,`portionAmount`,`portionUnit`,`kcal`,"
     "`proteinG`,`carbsG`,`fatG`,`source`,`confidence`,`foodId` FROM `food_items` WHERE `mealId` IN (:parents)",
     "SELECT id FROM meals WHERE epochDay = %d" % (LAST_DAY - 1)),
    ("@Relation FoodWithNames", "SELECT `id`,`foodId`,`displayName`,`nameKey`,`brandKey`,`isPreferred`,"
     "`addedAtMillis` FROM `food_names` WHERE `foodId` IN (:parents)", "SELECT id FROM foods"),
    ("@Relation SavedMealWithComponents", "SELECT `id`,`savedMealId`,`foodId`,`position`,`amount`,`countedAs` "
     "FROM `saved_meal_components` WHERE `savedMealId` IN (:parents)", "SELECT id FROM saved_meals"),
    ("@Relation SleepNight", "SELECT `id`,`sessionId`,`stage`,`startMillis`,`endMillis` FROM `sleep_stages` "
     "WHERE `sessionId` IN (:parents)", "SELECT id FROM sleep_sessions WHERE epochDay = %d" % (LAST_DAY - 1)),
]


def check_relations():
    """The child SQL above, compared with the generated code when a build has left it behind."""
    generated = ROOT / "app/build/generated/ksp/debug/java/com/metaself/app/data"
    if not generated.exists():
        return "generated code absent; relation SQL taken from this script"
    text = "".join(p.read_text() for p in generated.rglob("*_Impl.java"))
    missing = [name for name, sql, _ in RELATIONS if sql.split(" IN (")[0] + " IN (" not in text]
    if missing:
        sys.exit(f"FAIL\n - relation SQL differs from the generated code: {missing}")
    return "relation SQL matches the generated code"


# --- the made-up store -----------------------------------------------------------------------------

def schema(version):
    return json.loads((SCHEMAS / f"{version}.json").read_text())["database"]


def build(version):
    db = sqlite3.connect(":memory:", isolation_level=None)
    for e in schema(version)["entities"]:
        db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        for i in e.get("indices", []):
            db.execute(i["createSql"].replace("${TABLE_NAME}", e["tableName"]))
    return db


def fill(db):
    rng = random.Random(153)
    hexid = lambda: "%032x" % rng.getrandbits(128)  # noqa: E731
    db.execute("BEGIN")
    readings = []

    def series(kind, unit, origin, day, per_day, samples_each, value):
        for r in range(per_day):
            rid = hexid()
            start = MS(day) + r * (DAY_MS // per_day)
            step = (DAY_MS // per_day) // samples_each
            for s in range(samples_each):
                t = start + s * step
                readings.append((kind, t, None if samples_each > 1 else t + step, value(), unit, origin, rid, s, day))

    for day in range(FIRST_DAY, LAST_DAY + 1):
        series("HEART_RATE", "bpm", BAND, day, 24, 60, lambda: rng.randint(50, 150))
        for kind, unit in (("STEPS", "count"), ("DISTANCE", "m"), ("ACTIVE_KCAL", "kcal"), ("TOTAL_KCAL", "kcal")):
            per_day = 1440 if kind == "DISTANCE" and DENSE_DISTANCE else 96
            series(kind, unit, BAND, day, per_day, 1, lambda: rng.randint(0, 500))
        series("OXYGEN_SATURATION", "%", BAND, day, 24, 1, lambda: 97.0)
        series("HRV_RMSSD", "ms", BAND, day, 6, 1, lambda: 40.0)
        series("RESPIRATORY_RATE", "breaths/min", BAND, day, 6, 1, lambda: 14.0)
        series("RESTING_HEART_RATE", "bpm", BAND, day, 1, 1, lambda: 60)
        series("STEPS", "count", PHONE, day, 24, 1, lambda: rng.randint(0, 900))
        if day % 7 == 0:
            series("WEIGHT", "kg", PHONE, day, 1, 1, lambda: 80.0)
            series("BODY_FAT", "%", PHONE, day, 1, 1, lambda: 20.0)
        if len(readings) > 200_000:
            flush_readings(db, readings)
    flush_readings(db, readings)

    kinds = ["WALK", "RUN", "CYCLE", "STRENGTH", "SWIM"]
    for day in range(FIRST_DAY, LAST_DAY + 1):
        db.execute("INSERT INTO health_days (epochDay, computedAtMillis, steps, stepsSource) VALUES (?,?,?,?)",
                   (day, MS(day), 8000, "TOTAL"))
        sid = db.execute("INSERT INTO sleep_sessions (epochDay, startMillis, endMillis, origin, recordId) "
                         "VALUES (?,?,?,?,?)", (day, MS(day) - 7_200_000, MS(day) + 21_600_000, BAND, hexid())).lastrowid
        db.executemany("INSERT INTO sleep_stages (sessionId, stage, startMillis, endMillis) VALUES (?,?,?,?)",
                       [(sid, "LIGHT", MS(day) + s * 1_000_000, MS(day) + (s + 1) * 1_000_000) for s in range(28)])
        if day % 7 < 5:
            typed = day % 10 == 0
            db.execute("INSERT INTO workouts (epochDay, startedAtMillis, durationMinutes, kind, energySource, source, "
                       "origin, originId, distanceM) VALUES (?,?,?,?,?,?,?,?,?)",
                       (day, MS(day) + 18 * 3_600_000, 45, kinds[day % 5], "NONE", "TYPED" if typed else "SYNCED",
                        None if typed else BAND, None if typed else ("w%d" % day), 5000 if day % 5 == 1 else None))
    for m in range(13):
        db.execute("INSERT INTO archive_months VALUES (?,?,?)", ("2025-%02d" % (m + 9) if m < 4 else "2026-%02d" % (m - 3),
                                                                  2, 1 if m < 12 else None))
    for k in READING_KINDS + ("SLEEP", "EXERCISE"):
        db.execute("INSERT INTO health_sync (kind, changesToken, catchUpDone) VALUES (?,?,1)", (k, "token"))
    for i in range(10):
        db.execute("INSERT INTO movement_corrections (epochDay, steps, setAtMillis) VALUES (?,?,?)",
                   (LAST_DAY - 30 * i, 5000, 1))

    for f in range(1, 251):
        db.execute("INSERT INTO foods (id, barcode, createdAtMillis, updatedAtMillis, kcalPer100g, unitName, "
                   "hiddenAtMillis) VALUES (?,?,?,?,?,?,?)",
                   (f, "%013d" % f if f % 3 == 0 else None, 1, MS(FIRST_DAY) + f, 100.0 if f % 2 else None,
                    "portion", 5 if f % 25 == 0 else None))
        db.execute("INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
                   "VALUES (?,?,?,?,1,1)", (f, "Food %d" % f, "food %d" % f, "na"))
        if f % 4 == 0:
            for extra in ("a", "b") if f % 8 == 0 else ("a",):
                db.execute("INSERT INTO food_names (foodId, displayName, nameKey, brandKey, isPreferred, addedAtMillis) "
                           "VALUES (?,?,?,?,0,1)", (f, "Food %d%s" % (f, extra), "food %d%s" % (f, extra), "na"))
    for s in range(1, 26):
        db.execute("INSERT INTO saved_meals VALUES (?,?,?,?,?,NULL)", (s, "Meal %d" % s, "meal %d" % s, 1, 1))
    comp = 0
    for s in range(1, 26):
        for c in range(4 if s <= 5 else 3):
            comp += 1
            if comp > 80:
                break
            db.execute("INSERT INTO saved_meal_components (savedMealId, foodId, position, amount, countedAs) "
                       "VALUES (?,?,?,?,?)", (s, (s * 7 + c * 13) % 250 + 1, c, 1.0, "GRAMS"))
    for day in range(FIRST_DAY, LAST_DAY + 1):
        for n in range(4):
            meal = db.execute("INSERT INTO meals (epochDay, loggedAtMillis, savedMealId) VALUES (?,?,?)",
                              (day, MS(day) + (7 + 4 * n) * 3_600_000,
                               rng.randint(1, 25) if n == 1 else None)).lastrowid
            db.executemany("INSERT INTO food_items (mealId, name, kcal, proteinG, carbsG, fatG, source, foodId) "
                           "VALUES (?,?,?,?,?,?,?,?)",
                           [(meal, "Food", 200, 10, 20, 5, "AI", None if rng.random() < 0.02 else rng.randint(1, 250))
                            for _ in range(3)])
    for p in range(60):
        db.execute("INSERT INTO products VALUES (?,?,NULL,100,1,1,1,NULL,1)", ("%013d" % (1000 + p), "Product"))
    for day in range(LAST_DAY - 299, LAST_DAY + 1):
        db.execute("INSERT INTO weights VALUES (?,?)", (day, 80.0))

    workout_ids = [r[0] for r in db.execute("SELECT id FROM workouts ORDER BY id")]
    for i in range(60):
        db.execute("INSERT INTO trainer_plans (createdAtMillis, activity, minutes, feeling, wish, suggestion, model, kept) "
                   "VALUES (?,?,?,?,?,?,?,?)", (i, "RUN", 30, "OK", "EASY", "{}", "m", i % 4 == 0))
    for i, w in enumerate(workout_ids[-150:]):
        db.execute("INSERT INTO trainer_reviews (workoutId, feedback, feedbackAtMillis) VALUES (?,?,?)",
                   (w, "Feedback", i))
    for i in range(15):
        db.execute("INSERT INTO trainer_programmes (createdAtMillis, weeks, perWeek, plan, model, startEpochDay, status) "
                   "VALUES (?,?,?,?,?,?,?)", (i, 4, 3, "{}", "m", LAST_DAY - 7 * (15 - i), "RUNNING" if i == 14 else "REPLACED"))
    for i in range(60):
        db.execute("INSERT INTO plan_confirmations VALUES (?,?,?,?)", (1 + i // 4, workout_ids[i], 1, 1))
    for i in range(52):
        db.execute("INSERT INTO weekly_letters (weekMonday, createdAtMillis, figures, letter, model) VALUES (?,?,?,?,?)",
                   (LAST_DAY - 3 - 7 * i, i, "{}", "Letter", "m"))
    for i in range(400):
        db.execute("INSERT INTO trainer_messages (atMillis, fromOwner, text, answered, replyTo) VALUES (?,?,?,?,?)",
                   (i, i % 2 == 0, "Message", 1, None if i % 2 == 0 else i))
    for i in range(30):
        db.execute("INSERT INTO trainer_sheets (atMillis, facts, madeBy) VALUES (?,?,?)", (i, "[]", "TRAINER"))
    for i in range(4):
        db.execute("INSERT INTO session_splits VALUES (?,?)", (workout_ids[i], workout_ids[i + 1]))
    db.execute("COMMIT")


def flush_readings(db, readings):
    db.executemany("INSERT INTO health_readings (kind, startMillis, endMillis, value, unit, origin, recordId, "
                   "sampleIndex, epochDay) VALUES (?,?,?,?,?,?,?,?,?)", readings)
    readings.clear()


def representative(db):
    """Ids that exist in the store, so a lookup finds what it would find on a phone."""
    one = lambda sql: db.execute(sql).fetchone()[0]  # noqa: E731
    hr = one("SELECT recordId FROM health_readings WHERE kind = 'HEART_RATE' AND epochDay = %d LIMIT 1" % (LAST_DAY - 1))
    night = one("SELECT recordId FROM sleep_sessions WHERE epochDay = %d" % (LAST_DAY - 1))
    workout = one("SELECT id FROM workouts WHERE source = 'SYNCED' ORDER BY id DESC LIMIT 1")
    origin_id = one("SELECT originId FROM workouts WHERE id = %d" % workout)
    meal = one("SELECT id FROM meals ORDER BY id DESC LIMIT 1")
    item = one("SELECT id FROM food_items ORDER BY id DESC LIMIT 1")
    return {
        "recordId": hr, "originId": origin_id, "workoutId": workout, "id": 3, "mealId": meal, "itemId": item,
        "itemIds": [item, item - 1], "ids": [1, 2, 3], "foodId": 17, "savedMealId": 3, "sessionId": 1,
        "programmeId": 1, "parents": None,
        "_night": night,
    }


# --- running ---------------------------------------------------------------------------------------

def bind(sql, params, values):
    """Room's own expansion: a list parameter becomes one `?` per element."""
    args = []

    def sub(m):
        name = m.group(1)
        if name not in values:
            sys.exit(f"FAIL\n - no representative value for :{name} in {sql}")
        v = values[name]
        if isinstance(v, list):
            args.extend(v)
            return ", ".join("?" for _ in v)
        args.append(v)
        return "?"

    return re.sub(r":(\w+)", sub, sql), args


def measure(db, sql, args):
    plan = [row[3] for row in db.execute("EXPLAIN QUERY PLAN " + sql, args)]
    write = not sql.lstrip().upper().startswith("SELECT")
    best = None
    for _ in range(1 if write else 3):
        if write:
            db.execute("SAVEPOINT measure")
        t = time.perf_counter()
        try:
            rows = len(db.execute(sql, args).fetchall())
        finally:
            took = (time.perf_counter() - t) * 1000
            if write:
                db.execute("ROLLBACK TO measure")
                db.execute("RELEASE measure")
        best = took if best is None else min(best, took)
    return plan, best, rows


def scanned(plan):
    """The growing tables a plan walks whole (a SCAN, with or without an index)."""
    out = []
    for step in plan:
        m = re.match(r"SCAN (\w+)", step)
        if m and m.group(1) in GROWING:
            out.append(step)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--version", type=int, default=max(int(p.stem) for p in SCHEMAS.glob("*.json")))
    ap.add_argument("--slow-ms", type=float, default=50.0)
    ap.add_argument("--only")
    ap.add_argument("--dense-distance", action="store_true",
                    help="DISTANCE one record a minute (1,440 a day) instead of 96: how a band app writing "
                         "distance per minute would fill the table; the per-minute granularity is not known")
    a = ap.parse_args()
    global DENSE_DISTANCE
    DENSE_DISTANCE = a.dense_distance

    relation_note = check_relations()
    t = time.perf_counter()
    db = build(a.version)
    fill(db)
    counts = {name: db.execute(f"SELECT COUNT(*) FROM `{name}`").fetchone()[0] for (name,) in db.execute(
        "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name")}
    print(f"schema {a.version}; store built in {time.perf_counter() - t:.1f} s; {relation_note}")
    print("rows: " + ", ".join(f"{k} {v:,}" for k, v in counts.items() if v))
    ids = representative(db)
    night = ids.pop("_night")

    found, accepted = [], []
    work = [(n, p, s) for n, p, s in queries()]
    for name, sql, parents in RELATIONS:
        work.append((name, ["parents"], sql.replace(":parents", ":parents")))
    for name, params, sql in work:
        if a.only and not name.startswith(a.only):
            continue
        values = {**DEFAULTS, **ids, **OVERRIDES.get(name, {})}
        if name.startswith("SleepDao.") and "recordId" in params:
            values["recordId"] = night
        if name.startswith("@Relation"):
            values["parents"] = [r[0] for r in db.execute(next(q for n, _, q in RELATIONS if n == name))]
        bound, args = bind(sql, params, values)
        plan, ms, rows = measure(db, bound, args)
        scans = scanned(plan)
        flag = ""
        if scans or ms > a.slow_ms:
            if name in ACCEPTED:
                flag = "  [accepted: " + ACCEPTED[name] + "]"
                accepted.append(name)
            else:
                flag = "  <-- " + ("scans " + ", ".join(scans) if scans else "") + \
                       (f" slow ({ms:.1f} ms)" if ms > a.slow_ms else "")
                found.append((name, plan, ms))
        print(f"{ms:8.2f} ms {rows:7d} rows  {name}{flag}\n             {' | '.join(plan)}")

    print()
    if found:
        print(f"FOUND {len(found)} quer{'y' if len(found) == 1 else 'ies'} scanning a growing table or slower "
              f"than {a.slow_ms:g} ms:")
        for name, plan, ms in found:
            print(f"  {name}: {ms:.2f} ms — {' | '.join(plan)}")
        sys.exit(1)
    print(f"OK: {len(work)} queries, none scanning a growing table or slower than {a.slow_ms:g} ms "
          f"outside the {len(accepted)} accepted")


if __name__ == "__main__":
    main()
