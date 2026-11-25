package com.example.ing_software_abarrotezperez.ai.repository

/**
 * ÚNICO lugar con SQL del asistente. Reglas:
 *  - Solo SELECT / WITH (el repositorio lo verifica antes de ejecutar).
 *  - Todo valor variable va como parámetro (?N); nunca se concatena texto del usuario.
 *  - Tokens que el repositorio expande con texto FIJO (no controlado por el usuario):
 *      {TEST}  -> " AND p.codigo_barras NOT LIKE 'PRUEBA-%'"  (o vacío)
 *      {CAD}   -> CAD_CTE (caducidades normalizadas a ISO)
 *  - Fechas de venta/merma: ISO "yyyy-MM-dd ..." -> se comparan con límites "yyyy-MM-dd" (>= desde, < hasta).
 *  - Fechas de caducidad: se guardan como "dd/MM/yy" y se convierten a ISO con substr() (sin cambiar el esquema).
 */
object AssistantSql {

    const val TEST_FILTER = " AND p.codigo_barras NOT LIKE 'PRUEBA-%'"

    /** Caducidades: una fila por lote con existencias; si el producto no tiene lotes, usa su propia fecha y stock. */
    const val CAD_CTE = """
WITH cad AS (
  SELECT p.id_producto AS id, p.codigo_barras AS codigo, p.nombre AS nombre,
         l.cantidad AS cantidad, p.precio_compra AS costo,
         ('20' || substr(l.fecha_caducidad,7,2) || '-' || substr(l.fecha_caducidad,4,2) || '-' || substr(l.fecha_caducidad,1,2)) AS iso,
         l.fecha_caducidad AS original, 'lote' AS origen
  FROM lote l JOIN producto p ON p.id_producto = l.id_producto
  WHERE l.cantidad > 0 AND p.stock > 0
    AND length(l.fecha_caducidad) = 8 AND substr(l.fecha_caducidad,3,1) = '/' AND substr(l.fecha_caducidad,6,1) = '/'
    {TEST}
  UNION ALL
  SELECT p.id_producto, p.codigo_barras, p.nombre,
         p.stock, p.precio_compra,
         ('20' || substr(p.fecha_caducidad,7,2) || '-' || substr(p.fecha_caducidad,4,2) || '-' || substr(p.fecha_caducidad,1,2)),
         p.fecha_caducidad, 'producto'
  FROM producto p
  WHERE p.stock > 0
    AND length(p.fecha_caducidad) = 8 AND substr(p.fecha_caducidad,3,1) = '/' AND substr(p.fecha_caducidad,6,1) = '/'
    AND NOT EXISTS (SELECT 1 FROM lote l2 WHERE l2.id_producto = p.id_producto AND l2.cantidad > 0)
    {TEST}
)
"""

    // ?1 = desde ISO, ?2 = hasta ISO (inclusive), ?3 = límite
    const val EXPIRING = """{CAD}
SELECT nombre, codigo, cantidad, iso, original, origen FROM cad
WHERE iso >= ?1 AND iso <= ?2 ORDER BY iso ASC, nombre ASC LIMIT ?3"""

    // ?1, ?2 igual que arriba
    const val EXPIRING_TOTAL = """{CAD}
SELECT COUNT(*), COALESCE(SUM(cantidad),0), COALESCE(SUM(cantidad * costo),0) FROM cad
WHERE iso >= ?1 AND iso <= ?2"""

    // ?1 = hoy ISO, ?2 = límite (caducados = fecha < hoy)
    const val EXPIRED = """{CAD}
SELECT nombre, codigo, cantidad, iso, original, origen FROM cad
WHERE iso < ?1 ORDER BY iso ASC, nombre ASC LIMIT ?2"""

    const val EXPIRED_TOTAL = """{CAD}
SELECT COUNT(*), COALESCE(SUM(cantidad),0), COALESCE(SUM(cantidad * costo),0) FROM cad
WHERE iso < ?1"""

    // ?1 = umbral, ?2 = límite
    const val LOW_STOCK = """SELECT p.nombre, p.codigo_barras, p.stock, COALESCE(p.precio_venta,0)
FROM producto p WHERE p.stock > 0 AND p.stock <= ?1 {TEST}
ORDER BY p.stock ASC, p.nombre ASC LIMIT ?2"""

    const val LOW_STOCK_TOTAL = """SELECT COUNT(*) FROM producto p WHERE p.stock > 0 AND p.stock <= ?1 {TEST}"""

    // ?1 = límite
    const val OUT_OF_STOCK = """SELECT p.nombre, p.codigo_barras, p.stock, COALESCE(p.precio_venta,0)
FROM producto p WHERE p.stock <= 0 {TEST}
ORDER BY p.nombre ASC LIMIT ?1"""

    const val OUT_OF_STOCK_TOTAL = """SELECT COUNT(*) FROM producto p WHERE p.stock <= 0 {TEST}"""

    const val INVENTORY_VALUE = """SELECT COUNT(*), COALESCE(SUM(p.stock),0),
       COALESCE(SUM(p.stock * p.precio_compra),0), COALESCE(SUM(p.stock * COALESCE(p.precio_venta,0)),0),
       COALESCE(SUM(CASE WHEN p.precio_compra <= 0 THEN 1 ELSE 0 END),0)
FROM producto p WHERE p.stock > 0 {TEST}"""

    const val TEST_PRODUCT_COUNT = """SELECT COUNT(*) FROM producto p WHERE p.codigo_barras LIKE 'PRUEBA-%'"""

    const val ALL_PRODUCTS = """SELECT p.nombre, p.codigo_barras, p.stock, COALESCE(p.precio_venta,0), p.precio_compra
FROM producto p WHERE 1 = 1 {TEST} ORDER BY p.nombre ASC"""

    // ?1 = desde, ?2 = hasta (exclusivo)
    const val SALES_TOTALS = """SELECT COALESCE(SUM(v.total),0), COUNT(*) FROM venta v
WHERE v.fecha >= ?1 AND v.fecha < ?2"""

    const val SALES_FIADO = """SELECT COALESCE(SUM(v.total),0) FROM venta v
JOIN fiado f ON f.id_venta = v.id_venta WHERE v.fecha >= ?1 AND v.fecha < ?2"""

    const val SALES_UNITS_PROFIT = """SELECT COALESCE(SUM(dv.cantidad),0),
       COALESCE(SUM((dv.precio_unitario - p.precio_compra) * dv.cantidad),0)
FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
JOIN producto p ON p.id_producto = dv.id_producto
WHERE v.fecha >= ?1 AND v.fecha < ?2"""

    // ?1 = desde, ?2 = hasta (exclusivo), ?3 = límite
    const val TOP_SELLING = """SELECT p.nombre, SUM(dv.cantidad) AS u, SUM(dv.cantidad * dv.precio_unitario) AS m
FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
JOIN producto p ON p.id_producto = dv.id_producto
WHERE v.fecha >= ?1 AND v.fecha < ?2 {TEST}
GROUP BY p.id_producto ORDER BY u DESC, m DESC, p.nombre ASC LIMIT ?3"""

    const val LEAST_SELLING = """SELECT p.nombre, SUM(dv.cantidad) AS u, SUM(dv.cantidad * dv.precio_unitario) AS m
FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
JOIN producto p ON p.id_producto = dv.id_producto
WHERE v.fecha >= ?1 AND v.fecha < ?2 {TEST}
GROUP BY p.id_producto ORDER BY u ASC, m ASC, p.nombre ASC LIMIT ?3"""

    // ?1 = desde, ?2 = hasta (exclusivo), ?3 = límite
    const val NO_MOVEMENT = """SELECT p.nombre, p.codigo_barras, p.stock, p.stock * p.precio_compra AS capital
FROM producto p WHERE p.stock > 0 {TEST}
AND NOT EXISTS (SELECT 1 FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
                WHERE dv.id_producto = p.id_producto AND v.fecha >= ?1 AND v.fecha < ?2)
ORDER BY capital DESC, p.nombre ASC LIMIT ?3"""

    const val NO_MOVEMENT_TOTAL = """SELECT COUNT(*), COALESCE(SUM(p.stock * p.precio_compra),0)
FROM producto p WHERE p.stock > 0 {TEST}
AND NOT EXISTS (SELECT 1 FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
                WHERE dv.id_producto = p.id_producto AND v.fecha >= ?1 AND v.fecha < ?2)"""

    // ?1 = desde, ?2 = hasta (exclusivo), ?3 = días del período, ?4 = cobertura mínima (días), ?5 = límite
    const val LOW_ROTATION = """SELECT p.nombre, p.codigo_barras, p.stock, s.u AS vendidas,
       (p.stock * 1.0 * ?3 / s.u) AS cobertura
FROM producto p
JOIN (SELECT dv.id_producto AS id, SUM(dv.cantidad) AS u
      FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
      WHERE v.fecha >= ?1 AND v.fecha < ?2 GROUP BY dv.id_producto) s ON s.id = p.id_producto
WHERE p.stock > 0 AND s.u > 0 AND p.stock * ?3 >= s.u * ?4 {TEST}
ORDER BY cobertura DESC, p.nombre ASC LIMIT ?5"""

    // ?1 = desde, ?2 = hasta (exclusivo), ?3 = días del período, ?4 = umbral de stock,
    // ?5 = cobertura objetivo (días), ?6 = límite
    private const val RESTOCK_FROM_WHERE = """FROM producto p
LEFT JOIN (SELECT dv.id_producto AS id, SUM(dv.cantidad) AS u
           FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
           WHERE v.fecha >= ?1 AND v.fecha < ?2 GROUP BY dv.id_producto) s ON s.id = p.id_producto
WHERE 1 = 1 {TEST}
  AND ( (COALESCE(s.u,0) > 0 AND (p.stock <= ?4 OR p.stock * ?3 < s.u * ?5))
     OR (COALESCE(s.u,0) = 0 AND p.stock > 0 AND p.stock <= ?4) )"""

    const val RESTOCK = """SELECT p.nombre, p.codigo_barras, p.stock, COALESCE(s.u,0) AS vendidas,
       CASE WHEN COALESCE(s.u,0) > 0 THEN p.stock * 1.0 * ?3 / s.u ELSE NULL END AS cobertura
""" + RESTOCK_FROM_WHERE + """
ORDER BY CASE WHEN COALESCE(s.u,0) > 0 THEN 0 ELSE 1 END ASC,
         CASE WHEN COALESCE(s.u,0) > 0 THEN p.stock * 1.0 / s.u ELSE 0 END ASC,
         p.stock ASC, p.nombre ASC LIMIT ?6"""

    const val RESTOCK_TOTAL = """SELECT COUNT(*) """ + RESTOCK_FROM_WHERE

    // ?1 = desde, ?2 = hasta (exclusivo); la fecha de merma es "yyyy-MM-dd HH:mm"
    const val SHRINK_TOTAL = """SELECT COUNT(*), COALESCE(SUM(m.cantidad),0), COALESCE(SUM(m.cantidad * p.precio_compra),0)
FROM merma m JOIN producto p ON p.id_producto = m.id_producto
WHERE m.fecha >= ?1 AND m.fecha < ?2 {TEST}"""

    // ?3 = límite
    const val SHRINK_BY_PRODUCT = """SELECT p.nombre, SUM(m.cantidad) AS u, SUM(m.cantidad * p.precio_compra) AS perdida,
       GROUP_CONCAT(DISTINCT m.motivo)
FROM merma m JOIN producto p ON p.id_producto = m.id_producto
WHERE m.fecha >= ?1 AND m.fecha < ?2 {TEST}
GROUP BY p.id_producto ORDER BY perdida DESC, u DESC LIMIT ?3"""

    const val SHRINK_BY_REASON = """SELECT COALESCE(m.motivo,'Sin motivo'), SUM(m.cantidad), SUM(m.cantidad * p.precio_compra) AS perdida
FROM merma m JOIN producto p ON p.id_producto = m.id_producto
WHERE m.fecha >= ?1 AND m.fecha < ?2 {TEST}
GROUP BY m.motivo ORDER BY perdida DESC LIMIT 5"""

    // ── Resumen de tienda (solo lectura) ──
    const val FIADO_TOTAL = """SELECT COALESCE(SUM(f.saldo_pendiente),0), COUNT(f.id_fiado)
FROM fiado f WHERE f.saldo_pendiente > 0"""

    // ?1 = fecha límite ISO; deuda cuya venta es anterior a ese día (fiado no guarda fecha propia).
    const val FIADO_ANTIGUO = """SELECT c.id_cliente, c.nombre, COALESCE(SUM(f.saldo_pendiente),0)
FROM fiado f JOIN venta v ON v.id_venta = f.id_venta
JOIN cliente c ON c.id_cliente = f.id_cliente
WHERE f.saldo_pendiente > 0 AND v.fecha < ?1
GROUP BY f.id_cliente ORDER BY 3 DESC"""

    const val MAYOR_DEUDOR = """SELECT c.id_cliente, c.nombre, COALESCE(SUM(f.saldo_pendiente),0)
FROM fiado f JOIN cliente c ON c.id_cliente = f.id_cliente
WHERE f.saldo_pendiente > 0 GROUP BY f.id_cliente ORDER BY 3 DESC LIMIT 1"""

    // ?1 = límite; todos los clientes con saldo pendiente, del mayor al menor.
    const val DEUDORES = """SELECT c.id_cliente, c.nombre, COALESCE(SUM(f.saldo_pendiente),0)
FROM fiado f JOIN cliente c ON c.id_cliente = f.id_cliente
WHERE f.saldo_pendiente > 0 GROUP BY f.id_cliente ORDER BY 3 DESC LIMIT ?1"""

    // ?1 = desde, ?2 = hasta (exclusivo); pérdida de vender bajo el costo en el periodo.
    const val MARGEN_NEGATIVO = """SELECT COALESCE(SUM((p.precio_compra - dv.precio_unitario) * dv.cantidad),0)
FROM detalle_venta dv JOIN venta v ON v.id_venta = dv.id_venta
JOIN producto p ON p.id_producto = dv.id_producto
WHERE v.fecha >= ?1 AND v.fecha < ?2 AND dv.precio_unitario < p.precio_compra"""

    /** Expande tokens con texto fijo. */
    fun expand(sql: String, excludeTest: Boolean): String =
        sql.replace("{CAD}", CAD_CTE)
            .replace("{TEST}", if (excludeTest) TEST_FILTER else "")
}
