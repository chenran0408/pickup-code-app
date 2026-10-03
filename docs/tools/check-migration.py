"""用真实 SQLite 检查 6→7→8 / 7→8 的数据保留及 schema，项目根目录执行。"""
from pathlib import Path
import json
import re
import sqlite3

root = Path(__file__).resolve().parents[2]
schema_dir = root / 'app/schemas/com.pickupcode.app.data.AppDatabase'
source = (root / 'app/src/main/java/com/pickupcode/app/data/CodeHistoryDao.kt').read_text(encoding='utf-8')

def sql_for_migration(start, end):
    part = source.split(f'MIGRATION_{start}_{end} =', 1)[1]
    part = part.split('\n        }', 1)[0]
    # 迁移语句只由 Kotlin 字符串常量及 + 组成，按原顺序拼接后执行。
    calls = re.findall(r'db\.execSQL\((.*?)\)\s*(?:\n|$)', part, re.S)
    assert calls, f'No SQL for {start}→{end}'
    return [''.join(json.loads('"' + s + '"') for s in re.findall(r'"((?:\\.|[^"\\])*)"', call)) for call in calls]

def schema(version):
    return json.loads((schema_dir / f'{version}.json').read_text(encoding='utf-8'))['database']['entities'][0]

def create(db, version):
    db.execute(schema(version)['createSql'].replace('${TABLE_NAME}', 'code_history'))

def check(start):
    db = sqlite3.connect(':memory:')
    create(db, start)
    fields = [f['columnName'] for f in schema(start)['fields']]
    values = {f['columnName']: 0 if f['affinity'] in ('INTEGER','REAL') else '' for f in schema(start)['fields']}
    values.update(id=41, code='3-7-4162', type='pickup_parcel', source='中通', pickupAddress='长兴路合成站点',
        screenshotPath='/synthetic/shared.jpg', timestamp=100_000, isActive=1, expiryTime=300_000)
    if start >= 8:
        values.update(recognitionOrigin='local', addressOrigin='local')
    db.execute(f"INSERT INTO code_history ({','.join(fields)}) VALUES ({','.join('?' for _ in fields)})", [values[f] for f in fields])
    if start == 6:
        for ghost in ['stationName','stationType','codeConfirmed','sourceConfirmed']:
            db.execute(f"ALTER TABLE code_history ADD COLUMN {ghost} TEXT NOT NULL DEFAULT ''")
    for version in range(start, 9):
        for sql in sql_for_migration(version, version+1):
            db.execute(sql)
    actual = dict(zip([d[0] for d in db.execute('SELECT * FROM code_history').description], db.execute('SELECT * FROM code_history').fetchone()))
    assert all(actual[k] == value for k,value in values.items()), 'Existing data changed'
    assert actual['trackingNumber'] == '' and actual['userEditedFields'] == 0
    assert actual['recognitionOrigin'] == 'local' and actual['addressOrigin'] == 'local'
    expected = sqlite3.connect(':memory:')
    create(expected, 9)
    # ALTER 新列位于表尾，Room 按列名检查字段，物理列序不影响兼容性。
    actual_columns = sorted(row[1:] for row in db.execute('PRAGMA table_info(code_history)'))
    expected_columns = sorted(row[1:] for row in expected.execute('PRAGMA table_info(code_history)'))
    assert actual_columns == expected_columns, 'Schema mismatch'
    # 实际执行 DAO 的条件 SQL，验证旧地址回填无法给人工修改后的地址盖上已验证标签。
    conditional = re.search(r'@Query\("(UPDATE code_history SET geoVerified = :verified[^"\n]*expectedAddress[^"\n]*)"\)', source).group(1)
    db.execute(conditional, dict(verified=1,confidence=0.9,formatted='旧地址',id=41,expectedAddress='不匹配地址'))
    assert db.execute('SELECT geoVerified FROM code_history').fetchone()[0] == 0
    # 已取历史不随回收站过期删除，撤销批次不能复活后来删除的记录。
    new_fields = [f['columnName'] for f in schema(9)['fields']]
    def archived_row(identifier, kind):
        row = {f['columnName']: 0 if f['affinity'] in ('INTEGER','REAL') else '' for f in schema(9)['fields']}
        row.update(id=identifier,code='7-3-5268',type='pickup_parcel',source='圆通',rawTextSnippet='合成记录',isActive=0,doneAt=100,archiveKind=kind)
        db.execute(f"INSERT INTO code_history ({','.join(new_fields)}) VALUES ({','.join('?' for _ in new_fields)})",[row[f] for f in new_fields])
    completed_id = 42; deleted_id = 43
    archived_row(completed_id, 'done'); archived_row(deleted_id, 'deleted')
    cleanup = re.search(r'@Query\("(DELETE FROM code_history WHERE isActive = 0[^"\n]*:before[^"\n]*)"\)', source).group(1)
    db.execute(cleanup, dict(before=200))
    assert db.execute('SELECT COUNT(*) FROM code_history WHERE id=?',(completed_id,)).fetchone()[0] == 1
    assert db.execute('SELECT COUNT(*) FROM code_history WHERE id=?',(deleted_id,)).fetchone()[0] == 0
    db.execute("UPDATE code_history SET archiveKind='deleted' WHERE id=?",(completed_id,))
    undo = re.search(r'@Query\("(UPDATE code_history SET isActive = 1[^"\n]*id IN[^"\n]*:doneAt[^"\n]*)"\)', source).group(1)
    db.execute(undo.replace(':ids',str(completed_id)), dict(doneAt=100))
    assert db.execute('SELECT isActive FROM code_history WHERE id=?',(completed_id,)).fetchone()[0] == 0
    print(f'{start}→9: data preserved, schema matched, stale verification rejected')

if __name__ == '__main__':
    check(6)
    check(7)
    check(8)
