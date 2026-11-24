---
name: Abarrotes Pérez
description: Punto de venta Android donde cada módulo del negocio lleva su propio letrero de color.
colors:
  azul-sistema: "#3A5BA0"
  azul-contenedor: "#D6E3FF"
  fondo: "#F4F5F9"
  superficie: "#FFFFFF"
  superficie-variante: "#E1E2EC"
  texto-principal: "#1A1C20"
  texto-secundario: "#44464F"
  contorno: "#74767F"
  error: "#BA1A1A"
  venta: "#00B259"
  inventario: "#C45B20"
  reportes: "#2563EB"
  fiado: "#5E17EB"
  mermas: "#B71C1C"
  asistente: "#7A513E"
  configuracion: "#737373"
  menu: "#0F1123"
  dinero: "#4CAF50"
typography:
  display:
    fontFamily: "Roboto, sans-serif"
    fontSize: "34sp"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "0em"
  headline:
    fontFamily: "Roboto, sans-serif"
    fontSize: "22sp"
    fontWeight: 700
    lineHeight: 1.2
    letterSpacing: "0em"
  title:
    fontFamily: "Roboto, sans-serif"
    fontSize: "18sp"
    fontWeight: 700
    lineHeight: 1.3
    letterSpacing: "0em"
  body:
    fontFamily: "Roboto, sans-serif"
    fontSize: "16sp"
    fontWeight: 400
    lineHeight: 1.4
    letterSpacing: "0em"
  label:
    fontFamily: "Roboto, sans-serif"
    fontSize: "12sp"
    fontWeight: 500
    lineHeight: 1.2
    letterSpacing: "0.02em"
rounded:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "24dp"
  pill: "30dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "20dp"
  2xl: "24dp"
  3xl: "32dp"
components:
  letrero-modulo:
    backgroundColor: "{colors.venta}"
    textColor: "{colors.superficie}"
    rounded: "0dp"
    height: "60dp"
    padding: "0 16dp"
  mosaico-modulo:
    backgroundColor: "{colors.venta}"
    textColor: "{colors.superficie}"
    rounded: "{rounded.lg}"
    padding: "16dp"
  tarjeta-lista:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto-principal}"
    rounded: "{rounded.md}"
    padding: "16dp"
  boton-modulo:
    backgroundColor: "{colors.venta}"
    textColor: "{colors.superficie}"
    rounded: "20dp"
    height: "40dp"
    padding: "0 24dp"
  campo-contorno:
    backgroundColor: "transparent"
    textColor: "{colors.texto-principal}"
    rounded: "{rounded.xs}"
    height: "56dp"
  burbuja-asistente:
    backgroundColor: "{colors.superficie}"
    textColor: "{colors.texto-principal}"
    rounded: "20dp"
    padding: "12dp"
  burbuja-usuario:
    backgroundColor: "{colors.asistente}"
    textColor: "{colors.superficie}"
    rounded: "20dp"
    padding: "12dp"
---

# Design System: Abarrotes Pérez

## Overview

**Creative North Star: "El Mostrador Rotulado"**

La app se comporta como un mostrador de abarrotes bien organizado: cada área del negocio tiene su propio letrero pintado y su color de pasillo. Ventas es verde, inventario es terracota, reportes es azul, fiado es violeta, mermas es rojo, el asistente es café. El dueño no navega un menú abstracto: camina hasta el pasillo que ya conoce.

El sistema es de densidad alta y lectura inmediata. Se opera de pie, con una mano, a veces con un cliente esperando, así que los números son grandes y en negritas, los títulos van en mayúsculas y las etiquetas de acción son cortas. El cromo —barras de estado, iconos, adornos— es mínimo y siempre blanco sobre el color del módulo.

La base estructural es Material 3 con tema claro/oscuro real (`themes.xml`), y sobre esa base se permite una sola audacia: la saturación categórica de los letreros. No es una app expresiva; es una app que se lee de reojo. Los recursos de marca disponibles son los logos PNG por módulo y el café de Pérez AI; no hay fotografía ni ilustración propia, y no se inventa.

**Key Characteristics:**
- Un color saturado por módulo, con texto e iconografía siempre en blanco.
- Títulos de pantalla en mayúsculas, 22sp, negritas, dentro de una banda de color de 60–75dp.
- Tarjetas de superficie blanca con radio de 12–16dp sobre un fondo gris frío `#F4F5F9`.
- Números primero: precio, total, stock y saldo van en 16–22sp negritas.
- Todo el sistema tipográfico es Roboto; no hay segunda familia.
- El modo oscuro y el tamaño de letra del sistema son ciudadanos de primera clase.

## Colors

Un azul de sistema sereno para los estados y los controles, y encima una paleta categórica de alta saturación que funciona como señalización de pasillo.

**Modo oscuro:** la app tiene tema oscuro real (`themes-night.xml`); todo cambio de color o estilo debe revisarse también en modo oscuro.

### Primary
- **Azul de Mostrador** (`#3A5BA0`, en oscuro `#5C8AE6`): color primario del tema. Aparece en los controles Material que no pertenecen a un módulo, en el foco de los campos y en los acentos del sistema. No compite con los colores de módulo.
- **Azul Contenedor** (`#D6E3FF`): fondo suave de contenedores informativos cuando se necesita un tinte sin gritar.

### Secondary
La familia categórica. Un color por módulo, elegido para distinguirse a un vistazo, no para combinar entre sí. Un módulo usa su color en la banda superior, en sus tarjetas destacadas y en sus botones; nunca dos colores de módulo en la misma pantalla.

El mosaico del menú usa un tono hermano del mismo pasillo (por ejemplo, el mosaico de Venta es un verde más claro que la banda de Venta). Lo que se conserva es la familia de tono y su lectura, no el hex exacto: la banda de la pantalla siempre manda.

- **Verde Venta** (`#00B259`): ventas, cobro, total a pagar, línea de totales. Es el color del dinero que entra.
- **Terracota Inventario** (`#C45B20`): lista de inventario, búsqueda, alta de producto, botones de la ficha de producto.
- **Azul Reportes** (`#2563EB`, franja `#1E40AF`): reportes, gráficas, campanita de caducidades. Único azul del módulo; contraste 5.2:1 sobre superficie clara, 8.7:1 con texto blanco sobre la franja y 6.3:1 en oscuro (`#60A5FA`).
- **Violeta Fiado** (`#5E17EB`): clientes, fiados, saldos, detalle de cliente.
- **Rojo Merma** (`#B71C1C`): mermas, pérdidas, y el número negativo de la pérdida.
- **Café Pérez AI** (`#7A513E`): asistente, burbuja del propietario, botón de enviar. Es el único módulo con identidad cálida.
- **Gris Configuración** (`#737373`): configuración y almacenamiento; son pantallas de servicio, no de operación.
- **Azul Noche Menú** (`#0F1123`, login `#001845`): barra del menú y cabecera de inicio de sesión.

### Neutral
- **Fondo Frío** (`#F4F5F9`): fondo de todas las pantallas operativas, también `windowBackground`.
- **Superficie** (`#FFFFFF`, en oscuro `#1E2128`): tarjetas, listas, campos.
- **Superficie Variante** (`#E1E2EC`): relleno interno de tarjetas de lista y pestañas inactivas.
- **Tinta** (`#1A1C20`, en oscuro `#E2E2E9`): texto principal, nombres de producto, cifras.
- **Tinta Suave** (`#44464F`, en oscuro `#C4C6D0`): segunda línea de una fila, códigos, horas, notas.
- **Contorno** (`#74767F`): bordes de campo, separadores, iconos secundarios.
- **Error** (`#BA1A1A`): validación de formularios y mensajes de fallo.
- **Verde Dinero** (`#4CAF50`): precio unitario en listas de producto y el botón de sumar cantidad. Es el mismo idioma que el Verde Venta, un paso más apagado.

### Named Rules
**La Regla del Letrero Único.** Una pantalla pertenece a un módulo y usa un solo color categórico. Si dos colores de módulo aparecen en la misma vista, uno de ellos está mal.

**La Regla del Blanco Roto.** Sobre cualquier relleno saturado, el texto y el icono son blancos. Nunca se escribe un color de módulo encima de otro color de módulo.

**La Regla del Semáforo.** El color significa antes que la etiqueta: verde = entra dinero, rojo = sale o se pierde, violeta = se fía, magenta `#E42566` = existencias en riesgo. El texto confirma, no explica.

## Typography

**Display Font:** Roboto (con la sans del sistema como respaldo)
**Body Font:** Roboto
**Label/Mono Font:** monospace del sistema, sólo para el ticket

**Character:** Una sola familia, sin adornos, jerarquizada por peso y tamaño. La voz tipográfica es la de un letrero de tienda: pocas palabras, todas legibles a un brazo de distancia.

### Hierarchy
- **Display** (700, 34sp, 1.15): sólo el nombre de la tienda en el inicio de sesión.
- **Headline** (700, 22sp, 1.2, mayúsculas): título dentro de la banda de color de cada pantalla.
- **Title** (700, 18–24sp, 1.3): nombre del producto en una tarjeta (18sp), etiqueta del mosaico de módulos (24sp), y cifras destacadas.
- **Body** (400, 16sp, 1.4): texto de listas, descripciones, contenido del chat y de los campos.
- **Label** (500, 11–12sp, mayúsculas en acciones): botones (`EDITAR`, `PROMOCIÓN`, `COBRAR`), insignias y metadatos; en 12sp va la hora y las notas del chat.

La escala anterior es por roles, no un inventario cerrado: existen tamaños puntuales fuera de ella (36sp, 32sp, 30sp y algunos intermedios como 20–21sp o 13–15sp) para cifras, encabezados y metadatos concretos.

### Named Rules
**La Regla del Puño Firme.** Todo lo que el dueño necesita leer de reojo —total, precio, stock, saldo, nombre de producto— va en negritas. Lo secundario va regular y en Tinta Suave.

**La Regla del Mono.** El monoespaciado se reserva al ticket impreso en pantalla y a la pantalla de diagnóstico de lectores (`mostrarDiagnostico()` en `VentaActivity.kt`), donde alinea la salida técnica. Fuera de esos dos casos, toda la app es Roboto.

## Layout

Rejilla de módulos de 2 columnas × 3 filas en el menú principal, con celdas de peso igual, 8dp de padding exterior y 8dp de margen por tarjeta: el resultado es un ritmo de 16dp entre bloques. Cada celda lleva el logo del módulo a 70dp, 12dp de aire y su etiqueta en 24sp.

Las pantallas internas siguen siempre la misma pila vertical: banda de color (60–75dp, 16dp de padding horizontal) → contenido con 16dp de padding → lista o formulario → barra de totales anclada al fondo. Las listas usan `paddingHorizontal` 16dp con 8–12dp entre filas y `clipToPadding="false"`. Los formularios viven dentro de un `ScrollView` con 16dp de padding y campos separados 16dp.

La densidad es de teléfono vertical; no hay variante para tablet ni para horizontal. No se introduce una navegación inferior: el menú principal es el hub.

## Elevation & Depth

Sistema híbrido. Las bandas de color son completamente planas: el color, no la sombra, es lo que separa la cabecera del contenido. Las tarjetas usan una elevación real de 4dp sobre el fondo frío, y dentro de ellas el contenido se separa por capas tonales (`colorSurface` sobre `colorSurfaceVariant`) con 0dp de elevación. La única superficie que sube más es la barra de total anclada al pie (Mermas), que usa 10dp para flotar sobre la lista que hace scroll. Los diálogos van sobre una tarjeta blanca de 24dp de radio.

### Shadow Vocabulary
- **Tarjeta en reposo** (`app:cardElevation="4dp"`): tarjetas de módulo, tarjetas de lista y contenedores de sección. Es la elevación por defecto y el techo.
- **Tarjeta anidada** (`app:cardElevation="0dp"`): cualquier tarjeta dentro de otra tarjeta o dentro de una banda coloreada; la separación la da el relleno `colorSurfaceVariant`.
- **Barra de total anclada** (`app:cardElevation="10dp"`): sólo la barra de resumen fijo al pie que flota sobre una lista con scroll (pérdida total en Mermas).
- **Diálogo** (elevación del sistema): sólo en `AlertDialog` y hojas modales.

### Named Rules
**La Regla del Techo de 4dp.** Ninguna superficie supera 4dp de elevación, salvo la barra de total anclada al pie (10dp). La profundidad se compra con color y relleno, no con sombra.

**La Regla del Marco Pintado.** La estructura externa de un bloque puede ser una tarjeta de color del módulo con otra tarjeta de superficie blanca incrustada a 8dp. El marco da identidad, el interior da legibilidad.

## Shapes

Lenguaje de esquinas generoso y uniforme, sin chaflanes ni cortes rectos. Radio 16dp para lo que representa un módulo (mosaicos, tarjetas contenedoras, tarjetas de totales), 12dp para lo que representa un registro (filas de lista, chips, bloques de ganancia) y 24dp para diálogos y contenedores de conversación. Las burbujas del chat usan 20dp con una esquina de 4dp del lado del emisor: la cola indica quién habla.

Los botones y campos que el dueño toca en la operación diaria se vuelven píldora: el campo del chat (26dp), el botón `VER` del cliente (15dp) y los botones Material por defecto (20dp). Los botones de icono son círculos de 36–50dp. Los iconos de acción dentro de filas se dibujan entre 28 y 40dp, pero el contenedor tocable debe medir al menos 48dp (`minWidth`/`minHeight` y `padding`).

## Components

### Buttons
- **Shape:** píldora de 20dp por defecto; 15dp en el botón `VER` de la lista de clientes.
- **Primary:** relleno del color del módulo (Verde Venta, Terracota, Violeta), texto blanco, 12sp en mayúsculas y negritas, altura 40dp, padding horizontal 24dp.
- **Secondary / Ghost:** `MaterialButton` con `?attr/materialButtonOutlinedStyle` y `app:strokeColor` del color del módulo; se usa para alternar modos (Bluetooth / Cable USB / Cámara).
- **Destructive:** botón de texto o icono con tinte `#D32F2F` / `#F44336`; nunca un botón relleno de rojo junto a otro relleno.
- **States:** ripple del tema (`selectableItemBackground`), sin cambios de color en hover porque no hay hover.

### Chips
- **Style:** `Chip` Material por defecto, 8dp de radio, superficie del tema, texto Body 14sp.
- **Use:** preguntas sugeridas de Pérez AI en una fila con desplazamiento horizontal, 8dp entre chips. Son acciones, no filtros: al tocarlas envían la pregunta.

### Cards / Containers
- **Corner Style:** 16dp contenedores y mosaicos; 12dp filas de lista.
- **Background:** `colorSurface` por defecto, `colorSurfaceVariant` para el relleno interno de una fila, color de módulo para el marco de un bloque destacado.
- **Shadow Strategy:** 4dp en reposo, 0dp cuando está anidada (ver Elevation).
- **Border:** sin borde; el contraste lo da el fondo frío o el relleno variante. Sólo el campo del chat usa contorno de 1dp con `colorOutlineVariant`.
- **Internal Padding:** 16dp (tarjeta), 12dp (fila), 8dp (marco incrustado).

### Inputs / Fields
- **Style:** `TextInputLayout` con `Widget.MaterialComponents.TextInputLayout.OutlinedBox`, hint flotante, radio de 4dp del estilo Material.
- **Focus:** borde y label en `colorPrimary`; en pantallas de módulo se admite `backgroundTint` del color del módulo para el subrayado (el campo `RECIBIDO` de la venta va en verde).
- **Error:** texto debajo del campo en `#D32F2F`, 13sp, sin icono.
- **Search:** el buscador de inventario usa el campo de plataforma (`@android:drawable/editbox_background`) con icono de lupa al inicio y 12dp de padding; es la única excepción deliberada al campo contorneado.

### Navigation
- **Style:** no hay barra inferior ni drawer. El hub es el mosaico de módulos del menú principal.
- **Top app bar:** banda de color propia de cada pantalla, 60–75dp, icono de módulo a 32dp, título centrado o a la izquierda en 22sp mayúsculas blancas, acciones a la derecha en iconos de 32–40dp.
- **Back:** icono de regreso dentro de la banda, además del gesto/Back del sistema, que nunca se intercepta.

### Module Band (signature component)
La banda de 60–75dp en el color del módulo, con el logo del módulo en blanco y su título en 22sp. Es lo que hace reconocible cada pantalla al instante y lo que da al sistema su carácter de señalización. En el menú su equivalente es el mosaico de 16dp de radio con logo de 70dp y etiqueta de 24sp.

### Chat Bubbles (signature component)
Burbuja del asistente: superficie del tema, contorno de 1dp, esquinas 20dp con la inferior izquierda a 4dp. Burbuja del propietario: café `#7A513E`, esquinas 20dp con la inferior derecha a 4dp. Hora en 11sp Tinta Suave debajo de cada burbuja, alineada al emisor. Las tablas de resultados dentro de la burbuja van en 12–13sp con separadores de 1dp.

### Ticket
Panel de `#FAFAFA` con texto monoespaciado de 12sp en negro, alineado con guiones y signos de igual dibujados con caracteres. Es una pieza funcional: reproduce la impresora térmica, no el sistema visual.

## Motion

El movimiento confirma; no se luce. Es un punto de venta de alta frecuencia y la velocidad manda sobre el espectáculo.

**Base única:** `ui/Motion.kt` concentra duraciones, interpoladores y helpers (`pressFeedback`, `fadeInUp`, `fadeOut`, `fadeErrorIn`, `setEnabledAnimated`, `CampanaAnimator`). Ninguna pantalla crea animadores a mano.

**Duraciones:** 100 ms (feedback rápido), 150 ms (fundidos de salida y errores), 180 ms (entradas de contenido), 300 ms (tope de transición). La campanita es la única excepción: 600 ms por ciclo, 5 ciclos, finita.

**Curvas:** entrada `DecelerateInterpolator()`; salida `AccelerateInterpolator()`.

**Reglas:**
- Todo se condiciona a `ValueAnimator.areAnimatorsEnabled()`; si el sistema las apaga, se aplica el estado final sin animar.
- Acciones de alta frecuencia (escaneo, agregar al carrito, cobro) no animan o se quedan en ≤100 ms.
- Máximo una animación protagonista por pantalla.
- No se animan cifras de dinero que el dueño está leyendo.

## Feedback

Los avisos transitorios son un componente propio (`ui/Aviso.kt`), no un Toast: un Snackbar de Material con vista propia (`view_aviso.xml`).

**Tipos y color (Semáforo):** ÉXITO verde `#00793A` (texto blanco, 5.5:1); ERROR rojo `#B71C1C` (blanco, 6.6:1); ADVERTENCIA ámbar `#FFC107` (texto oscuro `#1A1C20`, 10.5:1); INFO azul `#3A5BA0` (blanco, 6.6:1). Todos los pares cumplen ≥ 4.5:1.

**Blanco Roto (con excepción):** icono y texto blancos sobre el relleno saturado; la única excepción es ADVERTENCIA, que va en ámbar con texto e icono oscuros para asegurar el contraste. El relleno es el mismo en claro y oscuro, así que el aviso no depende del tema.

**Contenido:** título corto (16sp, 700) y detalle opcional (14sp). Español, sin emojis, tono directo; solo datos que ya existan en el ámbito de la función.

**Comportamiento:** entra con `Motion.fadeInUp` (8 dp) y sale con `Motion.fadeOut`; permanece 2.2 s. Se puede anclar sobre una vista (`setAnchorView`) para no tapar barras fijas, como el total de Venta.

**Háptica:** vibración leve solo en ÉXITO y ERROR.

**Accesibilidad:** anuncia título y detalle con `announceForAccessibility`; respeta `areAnimatorsEnabled()` a través de `Motion`.

## Fichas de lista

Tarjetas de fila para las listas (producto, cliente, fiado, merma, carrito):

- **Contenedor:** radio 16dp, margen 16dp horizontal y 6dp vertical, padding interno 16dp.
- **Profundidad:** sin sombra; borde de 1dp (`colorOutlineVariant`) o elevación ≤ 2dp. El Techo de 4dp sigue mandando.
- **Tipografía:** título 16sp bold, texto secundario 13sp, cifra destacada 16–18sp bold.
- **Acento por módulo:** Inventario `#C45B20`, Fiado `#5E17EB`, Merma `#B71C1C`, Venta `#00B259`, Reportes `#2563EB`, Pérez AI `#7A513E`. Un solo acento por ficha; el resto usa roles del tema.
- **Claro y oscuro:** fondo `?attr/colorSurface`, textos con `?attr/colorOnSurface` / `colorOnSurfaceVariant`.
- **Acciones:** `Widget.Abarrotes.IconAccion` (MaterialButton IconButton, táctil 48dp, icono 20dp, `contentDescription` en español). Estados con `Widget.Abarrotes.Estado.{Peligro,Advertencia,Neutro}`.

## Do's and Don'ts

### Do:
- **Do** asignar a cada pantalla el color de su módulo y usarlo en la banda, en sus botones y en sus tarjetas destacadas.
- **Do** escribir títulos de pantalla en mayúsculas, 22sp, 700, blanco sobre la banda.
- **Do** poner las cifras que importan en 16–22sp negritas, alineadas a la derecha cuando son dinero.
- **Do** mantener 4dp de elevación como máximo (10dp sólo en la barra de total anclada al pie) y 0dp en tarjetas anidadas.
- **Do** usar 16dp de padding como ritmo base y 8–12dp entre elementos de una misma lista.
- **Do** dar a cada objetivo táctil al menos 48dp, aunque el icono dibujado sea de 28–36dp.
- **Do** verificar cada pantalla nueva en modo oscuro y con `font_scale` 1.3 antes de darla por terminada.
- **Do** respetar `ValueAnimator.areAnimatorsEnabled()` y mantener cualquier animación entre 150 y 300 ms, con la campanita como única excepción (600 ms por ciclo, finita, con pausa).

### Don't:
- **Don't** introducir un segundo color de módulo en una misma pantalla, ni un degradado: el sistema es de rellenos planos.
- **Don't** escribir texto gris o del color del módulo sobre un relleno saturado; ahí sólo va blanco.
- **Don't** inventar hex nuevos para un botón o una tarjeta: se usa el color del módulo o un rol del tema (`?attr/colorSurface`, `?attr/colorOnSurfaceVariant`).
- **Don't** superar 16sp en un texto secundario ni bajar de 11sp en ningún texto.
- **Don't** usar monoespaciado fuera del ticket ni una segunda familia tipográfica.
- **Don't** añadir una barra de navegación inferior, un drawer o un segundo FAB: el mosaico de módulos es la navegación.
- **Don't** poner sombras encima de las bandas de color; la banda es plana por definición.
- **Don't** animar desde escala 0 ni dejar animaciones infinitas sin pausa.
