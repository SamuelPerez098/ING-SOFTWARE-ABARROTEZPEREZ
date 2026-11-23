import sqlite3, sys, json
db, payload = sys.argv[1], json.loads(sys.stdin.read())
c = sqlite3.connect("file:%s?mode=ro" % db, uri=True)   # solo lectura, como garantía extra
sql = payload["sql"]
assert sql.lstrip().upper().startswith(("SELECT", "WITH")), "no es lectura"
rows = c.execute(sql, payload["args"]).fetchall()
out = []
for r in rows:
    out.append("\x1f".join("\x00N" if v is None else str(v) for v in r))
sys.stdout.write("\x1e".join(out))
