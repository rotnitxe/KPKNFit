# Banco de búsqueda · resultado pendiente de dispositivo

200 consultas únicas escritas a mano: 140 palabras de 3–10 letras, 20 consultas de varios términos, 8 con tildes, 6 marcas, 6 faltas y 20 ausentes esperadas contra una fixture pequeña (no se garantiza ausencia en un catálogo real).

Usa NutritionRepository.searchFoodCandidates, loggerFilter=true y limit=20. El cajón actual usa la misma función/filtro con límite 15: el banco fija 20 por el criterio de esta ola; no modifica el cajón ni el ranking. warm=true realiza 20 búsquedas previas descartadas y después mide las 200; repeat admite 1–3 pasadas. Percentiles de rango más cercano ceil(p*n)-1, sin interpolación. Mide hasta recibir la lista; cuenta catálogo global y PSS antes/después. Si el catálogo está vacío o se está importando, no entrega cifras engañosas.

## Medición física

- Dispositivo / versión / commit:
- Catálogo (filas):
- top-k: 20
- warm / repeat:
- p50 / p95 / p99 (ms):
- n:
- Cinco consultas más lentas:
- PSS antes / después (KB):

Sin cifras de emulador para tomar la decisión. Top-20 caliente p95 ≤120 ms: conservar búsqueda; >120 ms: proponer FTS a la persona. Nunca cambiar automáticamente índices, Room, importador o búsqueda. El Z Flip está temporalmente indisponible según la coordinación recibida; no se ha medido aún.
