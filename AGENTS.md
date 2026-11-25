# Abarrotes Pérez (Android nativo)

- Kotlin + layouts XML (Views), Material 3, SQLite (DatabaseHelper). NO usar Compose.
- minSdk 30. Textos de la UI en español.
- Nunca modificar el esquema de la BD ni la lógica de ventas/compras sin pedirlo.
- Transiciones de UI: ObjectAnimator/ViewPropertyAnimator, 150-300 ms con easing, nunca desde escala 0. Excepción: animaciones de atención (p. ej. campanita) pueden usar ciclos de ~600 ms, siempre finitas y con pausa.
- Respetar ValueAnimator.areAnimatorsEnabled(). Nada de animaciones infinitas sin pausa.
- Las skills de Emil/Impeccable/Taste son de web: aplica sus principios, no su código CSS/React.
- Cambios mínimos y por archivo; explicar qué se tocó.
