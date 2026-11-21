#!/usr/bin/env python3
"""Room 자동 마이그레이션이 폰의 저장 데이터를 지키는지 확인한다.

이전 버전 스키마(app/schemas/.../N.json)로 SQLite DB 를 만들고 데이터를 넣은 뒤,
KSP 가 만든 AutoMigration SQL 을 실행하고, 결과 테이블이 새 스키마 JSON 과
Room 의 검증 방식(컬럼 이름, 타입, NOT NULL, 기본값, PK)대로 일치하는지 본다.

사용법: ./gradlew assembleDebug 후 python3 tools/check_migration.py <from> <to>
"""
import json
import re
import sqlite3
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCHEMAS = ROOT / "app/schemas/com.kungdeok.game.data.AppDatabase"
GEN = ROOT / "app/build/generated/ksp/debug/kotlin/com/kungdeok/game/data"


def schema(v):
    return json.loads((SCHEMAS / f"{v}.json").read_text())["database"]


def main(a, b):
    old, new = schema(a), schema(b)
    db = sqlite3.connect(":memory:")
    for e in old["entities"]:
        db.execute(e["createSql"].replace("${TABLE_NAME}", e["tableName"]))
        # 모든 NOT NULL 컬럼에 값을 넣은 한 줄
        cols = [f["columnName"] for f in e["fields"]]
        vals = ["'x'" if f["affinity"] == "TEXT" else "7" for f in e["fields"]]
        db.execute(f"INSERT INTO {e['tableName']} ({','.join(cols)}) VALUES ({','.join(vals)})")
    impl = (GEN / f"AppDatabase_AutoMigration_{a}_{b}_Impl.kt").read_text()
    for sql in re.findall(r'execSQL\("(.*?)"\)', impl):
        db.execute(sql.replace('\\"', '"'))

    ok = True
    for e in new["entities"]:
        t = e["tableName"]
        info = {r[1]: r for r in db.execute(f"PRAGMA table_info(`{t}`)")}
        want = {f["columnName"]: f for f in e["fields"]}
        if set(info) != set(want):
            print(f"[X] {t}: 컬럼 다름 {set(info) ^ set(want)}"); ok = False; continue
        pk = e["primaryKey"]["columnNames"]
        for name, f in want.items():
            _, _, typ, notnull, dflt, ispk = info[name]
            if typ != f["affinity"] or bool(notnull) != f["notNull"] or (ispk > 0) != (name in pk):
                print(f"[X] {t}.{name}: {typ},{notnull},{ispk}"); ok = False
            if "defaultValue" in f and (dflt or "").strip("'") != f["defaultValue"].strip("'"):
                print(f"[X] {t}.{name}: 기본값 {dflt} != {f['defaultValue']}"); ok = False
        n = db.execute(f"SELECT COUNT(*) FROM `{t}`").fetchone()[0]
        print(f"[{'O' if n else 'X'}] {t}: 기존 데이터 {n}줄 유지")
        ok &= n > 0
    print("통과" if ok else "실패")
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main(int(sys.argv[1]), int(sys.argv[2]))
