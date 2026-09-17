import sqlite3
from pathlib import Path

db = Path(__file__).with_name("kpkn-pull.db")
c = sqlite3.connect(str(db))
tables = [r[0] for r in c.execute("select name from sqlite_master where type='table'").fetchall()]
print("tables:", tables)
for t in tables:
    try:
        n = c.execute(f"select count(*) from [{t}]").fetchone()[0]
        print(f"  {t}: {n}")
    except Exception as e:
        print(f"  {t}: ERR {e}")
if "programs" in tables:
    print("programs:", c.execute("select id, name, length(data) from programs").fetchall())
if "active_program" in tables:
    print("active_program rows:", c.execute("select rowId, length(data) from active_program").fetchall())
for name in tables:
    if "active" in name.lower() or "setting" in name.lower() or "state" in name.lower():
        print("peek", name, c.execute(f"select * from [{name}] limit 1").fetchall())
