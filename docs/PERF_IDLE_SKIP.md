# slimefun.idle-skip (perf.yml)

**Que hace:** `AContainer#findNextRecipe` (Horno electrico, triturador, prensa, etc.) en una maquina sin operacion y con
todas las ranuras de entrada vacias recorria todas las recetas, creando 2 HashMap y llamando `isItemSimilar` por
receta x entrada x ranura, en cada tick de cada maquina ociosa. Con el flag, si las entradas estan vacias (y ninguna
receta tiene 0 entradas) devuelve `null` directo: es el mismo resultado que el bucle. No toca energia, salidas ni
maquinas con items dentro.

**Activar:** en `drakes-data/perf.yml`
```yaml
slimefun:
  idle-skip: true   # default false
```
El plugin vigila la fecha de `drakes-data/perf.yml` en cada ciclo del TickerTask y solo reparsea si cambio:
activar/desactivar no requiere reinicio (y no depende del kernel; sin archivo queda false). Estado en vivo: `PerfFlags.idleSkip`.

**Medido** (microbenchmark `TestIdleSkip`, `mvn -o test -Dtest=TestIdleSkip -DargLine=-Dsf.bench=true`, JDK 25, MockBukkit,
solo `findNextRecipe` de una maquina ociosa):

| recetas | off | on |
|---|---|---|
| 11 | 307 ns | 18 ns |
| 41 | 1188 ns | 18 ns |
| 151 | 4641 ns | 17 ns |

Ojo: es el costo de la funcion aislada; el tick completo de una maquina ociosa incluye ademas `BlockStorage.getLocationInfo`,
`getInventory` y el profiler, que no cambian. La ganancia real en vivo = (maquinas ociosas) x (ns ahorrados) por ciclo, en el
hilo async del ticker; no se midio en el servidor.

**Riesgos:** ninguno de comportamiento previsto (la prueba `sameResult` compara ociosa / sin receta / receta de 1 y 2
entradas / salida llena). Una subclase que anade recetas a `recipes` sin cambiar su tamano no se reevalua (el chequeo de
"receta sin entradas" se cachea por tamano). Maquinas de addon que sobreescriben `findNextRecipe` no se benefician.
