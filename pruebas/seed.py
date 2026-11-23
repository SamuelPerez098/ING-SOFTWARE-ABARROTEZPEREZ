import re, sqlite3, sys
KT = "/home/claude/proj/ING-SOFTWARE-ABARROTEZPEREZ/app/src/main/java/com/example/ing_software_abarrotezperez/data/DatabaseHelper.kt"
src = open(KT, encoding="utf-8").read()
creates = re.findall(r'db\.execSQL\("""\s*(CREATE TABLE.*?)\s*"""\.trimIndent\(\)\)', src, re.S)
assert len(creates) == 12, len(creates)
path = sys.argv[1]
import os
if os.path.exists(path): os.remove(path)
c = sqlite3.connect(path)
c.execute("PRAGMA foreign_keys = ON")
for s in creates: c.execute(s)
P = [  # id, nombre, codigo, venta, compra, stock, cad
 (1,"Coca-Cola 600ml","7501",18,12,24,"15/12/26"),(2,"Galletas Marinela","7502",12,8,3,"05/12/26"),
 (3,"Leche Lala 1L","7503",26,20,10,"10/11/26"),(4,"Sabritas Original","7504",17,11,0,None),
 (5,"Jabón Zote","7505",15,9,4,None),(6,"Arroz 1kg","7506",25,15,80,None),
 (7,"Atún Dolores","7507",22,16,2,None),(8,"Frijol Sin Costo","7508",30,0,7,None),
 (9,"Prueba 1","PRUEBA-1",50,30,100,None),(10,"Prueba 2","PRUEBA-2",50,30,100,None),(11,"Prueba 3","PRUEBA-3",50,30,100,None)]
for p in P:
    c.execute("INSERT INTO producto(id_producto,nombre,codigo_barras,precio_venta,precio_compra,stock,fecha_caducidad) VALUES(?,?,?,?,?,?,?)", p)
for l in [(1,"25/11/26",6),(1,"15/12/26",18),(3,"10/11/26",10),(4,"30/11/26",5)]:
    c.execute("INSERT INTO lote(id_producto,fecha_caducidad,cantidad) VALUES(?,?,?)", l)
c.execute("INSERT INTO cliente(id_cliente,nombre) VALUES(1,'Doña Mary')")
V = [ # id, fecha, total, [(prod,cant,precio)]
 (1,"2026-11-22 09:10:00",54,[(1,3,18)]),
 (2,"2026-11-21 10:00:00",146,[(7,5,22),(1,2,18)]),
 (3,"2026-11-20 11:00:00",63,[(2,4,12),(5,1,15)]),
 (4,"2026-11-10 12:00:00",66,[(7,3,22)]),
 (5,"2026-10-28 18:00:00",232,[(1,10,18),(3,2,26)]),
 (6,"2026-10-05 08:00:00",88,[(7,4,22)]),
 (7,"2026-11-18 16:00:00",30,[(5,2,15)]),
 (8,"2026-11-19 10:00:00",100,[]),          # fiado manual sin detalle (DetalleFiadoActivity)
 (9,"2026-11-21 13:00:00",50,[(9,1,50)])]  # venta de producto de prueba
for vid,f,t,items in V:
    c.execute("INSERT INTO venta(id_venta,fecha,total) VALUES(?,?,?)",(vid,f,t))
    for pr,q,pu in items:
        c.execute("INSERT INTO detalle_venta(id_venta,id_producto,cantidad,precio_unitario) VALUES(?,?,?,?)",(vid,pr,q,pu))
c.execute("INSERT INTO fiado(id_cliente,id_venta,saldo_pendiente) VALUES(1,7,30)")
c.execute("INSERT INTO fiado(id_cliente,id_venta,saldo_pendiente) VALUES(1,8,100)")
for m in [(3,2,"Caducado","2026-11-20 14:30"),(2,1,"Dañado","2026-11-05 09:00"),(1,1,"Roto","2026-10-30 10:00")]:
    c.execute("INSERT INTO merma(id_producto,cantidad,motivo,fecha) VALUES(?,?,?,?)", m)
c.commit(); print("seed ok")
