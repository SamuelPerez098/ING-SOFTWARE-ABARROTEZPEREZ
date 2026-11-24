# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Users

- **Usuario principal (confirmado en entrevista): el dueño/gerente de una tienda de abarrotes pequeña.** Ejecuta y supervisa la operación diaria: ventas, inventario, clientes de fiado, mermas y reportes.
- No se confirmaron otros roles como audiencia separada. En la entrevista se descartó tratar al proyecto como una demo académica: el usuario de referencia es el dueño/gerente real de la tienda.

## Product Purpose

Aplicación de punto de venta y gestión de inventario para "Abarrotes Pérez". Permite registrar ventas y compras, controlar stock y caducidades, administrar fiados (crédito a clientes), registrar mermas, consultar reportes con gráficas, respaldar la información en Google Drive y compartir tickets en PDF por WhatsApp. El éxito es que el dueño pueda operar y entender su negocio desde el teléfono, incluso sin cobertura.

## Positioning

**Pérez AI es el diferencial clave (confirmado):** un asistente que responde preguntas en lenguaje natural sobre ventas, inventario y caducidades usando un modelo de lenguaje local (LiteRT-LM / Qwen3-0.6B) que corre en el propio dispositivo. La app funciona sin Internet: el modelo es opcional y se instala copiando un archivo `.litertlm`; sin él, un intérprete de reglas ("modo básico") sigue respondiendo. Ninguna función de la operación diaria depende de la nube; el respaldo en Drive es explícito y configurable con credenciales propias.

## Operating Context

- **Punto de venta en el mostrador:** lectura de códigos de barras por cámara, lector HID por cable/OTG y Bluetooth; el conector del lector puede cambiar la configuración del teclado de Android, por lo que las pantallas de Ventas e Inventario declaran `configChanges` para no perder la venta en curso.
- **Pantallas principales:** Login (lanzador), Inventario, Venta, Compra, Fiado + DetalleFiado, Mermas, Reportes, Configuración, Perfil, Almacenamiento, Chat (Pérez AI), ListaInventario y escáner por cámara.
- **Salidas y respaldo:** ticket PDF compartido por WhatsApp; respaldo en Google Drive ejecutado con WorkManager; notificaciones/recordatorios en Reportes.
- **Asistente:** historial de chat persistente, herramientas de consulta de ventas/inventario/caducidades/estadísticas, umbral de "pocas existencias" configurable (por defecto 5) y opción de excluir productos de prueba `PRUEBA-n`.

## Capabilities and Constraints

**Restricciones duraderas (confirmadas, respetar siempre):**
- Kotlin + layouts XML (Android Views / Material 3). **No usar Compose.**
- UI íntegramente en español.
- SQLite mediante `DatabaseHelper`. **No modificar el esquema de la BD ni la lógica de ventas/compras sin pedirlo explícitamente.**
- `minSdk 30`, `targetSdk 34`.
- Animaciones con `ObjectAnimator`/`ViewPropertyAnimator`, 150–300 ms, nunca desde escala 0; respetar `ValueAnimator.areAnimatorsEnabled()` y el ajuste de "quitar animaciones" del sistema; ninguna animación infinita sin pausa.

**Capacidades confirmadas por el código:** alta/edición de productos y clientes, control de caducidades, ventas con selección de clientes y teléfonos, compras, fiados, mermas, reportes con gráficas, personalización de umbrales del asistente, respaldo/restauración en Drive.

** decisiones abiertas / sin confirmar:**
- Despliegue comercial real: la entrevista confirma al dueño/gerente como usuario de referencia, pero el repositorio conserva señales de proyecto de curso (paquete `com.example`, `versionName 1.0`, volcado `todo_mi_codigo.txt`). No se confirmó distribución ni uso en una tienda concreta; no inventar clientes, métricas ni testimonios.
- El archivo del modelo local no se distribuye en el APK; se instala manualmente con `adb`. No se requiere que la app lo descargue.
- Alcance de tabletas: el código declara `android:supportsRtl="true"` pero no hay restricciones confirmadas de tamaño; Material 3 exige navegación adecuada a cada tamaño.

## Brand Commitments

- Nombre de la tienda/producto: **Abarrotes Pérez**; asistente: **Pérez AI**. El `app_name` del recurso es `ING-SOFTWARE-ABARROTEZPEREZ` (identificador técnico heredado, no renombrar sin pedirlo).
- Voz en español, directa y cercana, con términos del negocio (fiado, merma, caducidad, existencias). Ejemplo real: «¿Es producto perecedero?», «Sin Internet · IA local lista».
- Identidad visual existente: tema Material 3 DayNight con azul `#3A5BA0` como primary y fondo `#F4F5F9` (definido en `themes.xml`; no es un encargo de rediseño).

## Evidence on Hand

- Código fuente completo en `app/src/main/java/` y `app/src/main/res/`; pantallas, layout XML, tema y colores.
- `docs/INSTALAR_MODELO.txt`: procedimiento real de instalación del modelo local y estados del encabezado del asistente.
- `pruebas/`: suites de prueba (`ParserTest.kt`, `Phase5Test.kt`, `E2E.kt`, `Harness.kt`) y scripts `seed.py`/`sqlrun.py`; `todo_mi_codigo.txt`: volcado histórico de código.
- Historial de Git con funcionalidades entregadas: escáner HID/Bluetooth/cámara, ticket PDF por WhatsApp, campana de notificaciones, respaldo Drive, módulo de inventario, conjuntos difusos.
- **Ausencias que no deben inventarse:** no hay testimonios, métricas de uso, clientes externos, precios, licencias, medial real del negocio ni Materials visuales de marca más allá del tema y los drawables vectoriales incluidos.

## Product Principles

1. **Sin Internet no es un modo de emergencia, es la operación normal.** Vender, consultar y preguntar deben funcionar con conectividad limitada; la nube solo para el respaldo explícito.
2. **El mostrador no se interrumpe.** Una venta en curso no se pierde por conectar un lector, abrir el teclado o cambiar de pantalla.
3. **El dato del negocio es sagrado.** Esquema de BD y lógica de ventas/compras solo cambian con solicitud explícita.
4. **El dueño entiende lo que ve.** Español, vocabulario del abarrotero y jerarquía clara por encima de expresión visual.
5. **El asistente amplía, nunca bloquea.** Pérez AI es el diferencial, pero el modo básico por reglas garantiza respuesta siempre.

## Accessibility & Inclusion

- El sistema de animaciones del dispositivo manda: respetar `ValueAnimator.areAnimatorsEnabled()` y la opción de "quitar animaciones"; ofrecer corte directo o crossfade en lugar de movimiento (ver restricciones duraderas).
- Plataforma Android: Material 3 rige estructura, navegación e interacción; se deben cumplir las garantías del sistema (Back predictivo, insets de barra de estado/navegación/IME, objetivos táctiles de 48 dp, tipografía en sp, tema oscuro de primera clase y escala de fuente). No se registró un requisito de accesibilidad específico del negocio más allá de estos.
