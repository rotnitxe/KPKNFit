# Integración local, sin publicación

Base feat/macrofactor-parity `026a8c1e3`, worktree C:/kw/mfpi, rama mfp/i. Se fusionan mfp/n, mfp/d, mfp/t5a y mfp/h preservando rutas, entradas de Perfil y actividades de depuración. Conflictos aditivos en manifiesto, navegación, MainActivity, Perfil y documentación; doble guarda de render mantenida.

Contrato de datos: se copian únicamente entidad/DAO, migración y esquema exportado de calibración v30 ya presentes en master; no se integra master ni su runtime. Nutrición pasa a 30→31. 31.json debe generarse con KSP, nunca a mano. Las pruebas abren bases completas v27, v28, v29 y v30; la fixture v30 incluye las decisiones de calibración y comprueba que sobrevivan. No hay versión 32 ni cambio de tablas nutricionales publicadas.

La integración a master sigue pendiente de decisión del usuario. La orden posterior de AGENTS sustituye la antigua app .mfp por KPKN Dev com.example.kpkn.dbg, sin borrar datos ni instalar sobre Room posterior.

La clasificación de copias incluye calibration_decisions como datos del usuario. Extensión opcional de JSON v6: exporta las filas completas (incluido su JSON opaco), restaura sólo si la sección existe, y una copia anterior sin sección conserva los datos locales. Regresión exportar/importar/exportar con una decisión real y round-trip JSON. Esto acompaña exclusivamente la conservación del esquema v30 prestado; no añade el runtime de otra ola.
