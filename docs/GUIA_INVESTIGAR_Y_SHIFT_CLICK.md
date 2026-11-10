# Guia: investigar ingrediente bloqueado + shift-click a entradas de maquina

## Ingrediente bloqueado (Guia, modo supervivencia)
En la pagina de receta de un item Slimefun, un ingrediente bloqueado solo por una investigacion pendiente (con permiso y
investigaciones activas) muestra "Click to unlock / Cost". El clic usa `Research#unlockFromGuide` (nueva sobrecarga con
callback): mismo evento `PlayerPreResearchEvent`, mismo coste de XP, mismo aviso `not-enough-xp`, y al aprender vuelve a la
MISMA receta. Sin XP no cobra ni cambia de pagina. Ingredientes sin investigacion pendiente: comportamiento anterior.
Tests: `TestGuideResearchIngredient`. (No se encontro trabajo previo de Codex en ramas, worktrees ni stashes.)

## Shift-click hacia maquinas
`MenuListener`: un shift-click desde el inventario del jugador a un menu de maquina (BlockMenu) coloca el item SOLO en las
ranuras que el preset declara para INSERT (`getSlotsAccessedByItemTransport`, con el item, asi respeta filtros de addons):
primero completa stacks parciales, luego ranuras vacias. Lo que no cabe se queda en el jugador (se reduce el stack pulsado;
el cursor no se toca). Si el preset no declara ranuras de entrada, queda el comportamiento vanilla. Shift-click de salida
hacia el jugador no cambia. Flag `slimefun.shift-click-input` en `drakes-data/perf.yml`, **default true**
(se relee por fecha de archivo; `false` = vanilla). Tests: `TestShiftClickInput`.
Riesgo residual: addons con ranuras INSERT declaradas que ademas aceptan items a mano en otras ranuras via shift-click.
