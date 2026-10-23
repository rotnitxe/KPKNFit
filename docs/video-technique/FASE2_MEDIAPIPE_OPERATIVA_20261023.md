# Fase 2 MediaPipe v51: procedimiento operativo

Esta guía prepara la publicación del banco y su medición con el motor 9. No iniciar los pasos hasta que la extracción cierre y el conductor termine. Consultar el contador y la cuota en el estado vigente; las fases originales de entrega mantienen prioridad.

## Condiciones de entrada y pins

- Confirmar que el conductor, el servicio de extracción y el candado estén detenidos; no reutilizar los 72 recibos v50. El estado global debe ser `selected-clips-complete`, no basta el contador 616.
- Comprobar cuota semanal menor al94% y que no haya otra Gradle propia corriendo o esperando. Usar la cola compartida y el carril JVM; no incrementar prioridad ni detener procesos ajenos.
- El constructor valida los 616 recibos, tamaños, hashes, metadatos y timing antes de publicar. La fuente `C:\kw\vt-mmfit\bank` es inmutable y el destino `C:\kw\vt-mmfit\bank-mp-v51` debe no existir.
- Confirmar que `C:\kw\vt-mmfit\tools\build_mmfit_mp_bank_v51.py` conserva SHA-256 `db99b961b5ee1159dc96cb013e8c1f1eed0bd5132de55b9ce863f0bccff8fa51`.
- La atestación fijada es APK `8c2c233ccdfc77bc66700e2c9fa7c75d63be7f3f63d0ca17dc0fdee74bb186f0`, paquete `com.example.kpkn.dbg`, versionCode 51, Pre Release 17, Room 29 y motor 9. Modelo `assets/mediapipe/pose_landmarker_full.task`: SHA `5134a3aad27a58b93da0088d431f366da362b44e3ccfbe3462b3827a839011b1`; JNI arm64: `af5da2251aae340a8ac9d225f0780ecfc50a5fb36ea17e17218f362b1e90dceb`. Se exige delegate GPU y stride 1.
- El extractor y árbol Android fijados son commit `50d6e104b8f54d2b7b45335472a1cc88a3cc666f` y árbol `c63c5adbcb2f66b3d7c885c88b4cf89a1813a8da`; el constructor valida además manifiesto del teléfono, conductor, validador y certificado contra sus pins internos.

## Comprobar y publicar el banco

En PowerShell 7, desde cualquier directorio:

```powershell
$Python = 'C:\Users\valen\AppData\Local\Python\pythoncore-3.14-64\python.exe'
$Builder = 'C:\kw\vt-mmfit\tools\build_mmfit_mp_bank_v51.py'
$Destination = 'C:\kw\vt-mmfit\bank-mp-v51'
$ExpectedBuilderSha = 'db99b961b5ee1159dc96cb013e8c1f1eed0bd5132de55b9ce863f0bccff8fa51'
if ((Get-FileHash -LiteralPath $Builder -Algorithm SHA256).Hash.ToLowerInvariant() -ne $ExpectedBuilderSha) { throw 'Cambió el constructor fijado.' }
if (Test-Path -LiteralPath $Destination) { throw 'El destino ya existe; no sobrescribir ni borrar.' }
& $Python -B -I $Builder --check-only
if ($LASTEXITCODE -ne 0) { throw "Preflight falló ($LASTEXITCODE); no publicar." }
& $Python -B -I $Builder
if ($LASTEXITCODE -ne 0) { throw "Publicación falló ($LASTEXITCODE); conservar evidencia y revisar." }
$Manifest = Join-Path $Destination 'manifest.json'
$Actual = (Get-FileHash -LiteralPath $Manifest -Algorithm SHA256).Hash.ToLowerInvariant()
$Receipt = ((Get-Content -LiteralPath (Join-Path $Destination 'manifest.sha256') -Raw).Trim() -split '\s+')[0]
if ($Actual -ne $Receipt) { throw 'El recibo del manifiesto no coincide.' }
$Actual
```

`--check-only` debe terminar correctamente antes de la publicación. Un retorno 2 por extracción incompleta o cualquier error de procedencia detiene el proceso. No se edita el banco fuente. Guardar el SHA real impreso y el recibo; ese valor será el pin del empaquetador M5, después de revisión independiente.

## Medir el motor 9 sobre 616 filas

Usar una sola corrida JVM, sin prioridad y con salida nueva. Si cualquiera de las dos carpetas de salida ya existe, elegir un sufijo nuevo y revisar que siga vacía. No repetir `report-1` ni enviar JSON por clip al ajustador.

```powershell
$GitBash = 'C:\Program Files\Git\bin\bash.exe'
$StartedUtc = [DateTime]::UtcNow
$Out = 'C:\kw\vt-mmfit\report-mp-v51-motor9-20261023'
$Results = 'C:\kw\logs\vt-mmfit-v51-motor9-20261023-results'
$Log = 'C:\kw\logs\vt-mmfit-v51-motor9-20261023.log'
$Xml = 'C:\kw\vt-jvm\build\test-results\test\TEST-com.example.kpkn.domain.biomechanics.golden.FullBankReportTest.xml'
if (Test-Path -LiteralPath $Out) { throw 'El informe ya existe; seleccionar salida nueva.' }
if (Test-Path -LiteralPath $Results) { throw 'El archivo de resultados ya existe; seleccionar carpeta nueva.' }
if (Test-Path -LiteralPath $Log) { throw 'El log ya existe; seleccionar nombre nuevo.' }
New-Item -ItemType Directory -Path $Results | Out-Null
if (Test-Path -LiteralPath $Xml) { Copy-Item -LiteralPath $Xml -Destination (Join-Path $Results 'xml-anterior-preservado.xml') }
$env:KPKN_FULL_BANK_OUT = $Out.Replace('\', '/')
$env:KPKN_VT_MOTOR9_LOG = $Log.Replace('C:\', '/c/').Replace('\', '/')
$BashScript = @'
unset KPKN_SLOT_PRIORITY
export KPKN_FULL_BANK_DIR=C:/kw/vt-mmfit/bank-mp-v51
export KPKN_FULL_BANK_OUT KPKN_VT_MOTOR9_LOG
export KPKN_FULL_BANK_EXAM=0
export KPKN_SLOT_LANE=jvm
export KPKN_SLOT_FORCE=1
/c/kw/tools/gradle_slot.sh /c/kw/vt-jvm test --rerun --offline --console=plain '-Dorg.gradle.jvmargs=-Xmx1g -XX:MaxMetaspaceSize=512m -Dfile.encoding=UTF-8' -PappRoot=C:/Users/valen/Documents/KPKNFit/.claude/worktrees/video-technique-rep-counter-ee5b0d/android-native/app --tests 'com.example.kpkn.domain.biomechanics.golden.FullBankReportTest' > "$KPKN_VT_MOTOR9_LOG" 2>&1
rc=$?
printf '\nEXIT=%s\n' "$rc" >> "$KPKN_VT_MOTOR9_LOG"
exit "$rc"
'@
& $GitBash -lc $BashScript
$GradleExit = $LASTEXITCODE
if ((Test-Path -LiteralPath $Xml) -and (Get-Item -LiteralPath $Xml).LastWriteTimeUtc -ge $StartedUtc) {
    Copy-Item -LiteralPath $Xml -Destination (Join-Path $Results 'FullBankReportTest.xml')
} else { throw 'No apareció XML nuevo de la corrida; preservar log y detenerse.' }
Copy-Item -LiteralPath $Log -Destination $Results
if ($GradleExit -ne 0) { Write-Warning "Gradle terminó $GradleExit; inspeccionar el XML preservado, no repetir automáticamente." }
```

El filtro único es `FullBankReportTest`; `--rerun` fuerza esa tarea después de `test`, y `KPKN_SLOT_FORCE=1` impide reutilizar una corrida anterior al cambiar el banco por entorno. El heap de Gradle y el del test quedan en1g cada uno; el wrapper usa compilación Kotlin in-process. No lanzar otra Gradle propia mientras esta espera o corre.

El test histórico contiene dos discrepancias conocidas para este manifiesto: `optionalFullBankReport` conserva expectativas antiguas de 475 filas y validaciones VT10; `historicalAutomaticPlateIsNowRejectedAndPoseExplainsTheDifference` busca una fila histórica que no pertenece a MM-Fit. `FullBankReport.run` escribe el informe antes de esas comprobaciones. Conservar intactos log, XML y carpeta de informe incluso con ese exit no cero. Aceptar solo esas dos entradas conocidas para continuar; cualquier otra falla, error, compilación fallida, XML viejo o recuento incompleto requiere triage. No repetir la corrida para limpiar el resultado.

Revisar solamente agregados en `summary.json`: `engineVersion == 9`, `selected == 616`, `processed == 616`, `groups.G1.n == 494` y los agregados `G1/profile/*`. Para una lectura acotada:

```powershell
$Summary = Get-Content -LiteralPath (Join-Path $Out 'summary.json') -Raw | ConvertFrom-Json
[pscustomobject]@{ engineVersion=$Summary.engineVersion; selected=$Summary.selected; processed=$Summary.processed; G1n=$Summary.groups.G1.n; G1processed=$Summary.groups.G1.processed }
$Summary.groups.PSObject.Properties | Where-Object Name -Like 'G1/profile/*' | ForEach-Object { [pscustomobject]@{ group=$_.Name; n=$_.Value.n; processed=$_.Value.processed } }
```

`development-clips` contiene las salidas individuales del banco completo y es exclusivo del orquestador. El paso de informe de PLAN se ejecuta con `C:/kw/vt-bench/exam_report.py`, redirigiendo su stdout completo a un archivo privado nuevo: imprime identificadores que no deben llegar al ajustador. Revisar/publicar solo agregados; no enviar predicciones por fila. Comparar con el `report-1` ya existente, sin repetir la línea base.

```powershell
$PrivateReportLog = Join-Path $Results 'exam-report.stdout.txt'
if (Test-Path -LiteralPath $PrivateReportLog) { throw 'El informe privado ya existe; no sobrescribir.' }
& 'C:\Users\valen\AppData\Local\Python\pythoncore-3.14-64\python.exe' -B -I -X utf8 'C:\kw\vt-bench\exam_report.py' $Out development-clips *> $PrivateReportLog
if ($LASTEXITCODE -ne 0) { throw 'Falló el resumen del informe de desarrollo; revisar su log privado.' }
```

## Paquetes M5 y barrera de transferencia

Solo después de verificar banco, recibo, informe y hashes: calcular/releer el SHA de `bank-mp-v51/manifest.json`, compararlo con `manifest.sha256` y fijar ese valor real en `EXPECTED_PARENT_MANIFEST_SHA256` de `build_mmfit_m5_mediapipe_v51_packages.py`, con revisión independiente del diff/SHA. Es una decisión ya autorizada del orquestador, sin esperar respuesta humana. La constante `None` actual bloquea intencionalmente hasta esa revisión. Tras fijarla y revisarla, usar PowerShell 7:

```powershell
$Python = 'C:\Users\valen\AppData\Local\Python\pythoncore-3.14-64\python.exe'
$PackageBuilder = 'C:\kw\vt-mmfit\tools\build_mmfit_m5_mediapipe_v51_packages.py'
foreach ($Path in @('C:\kw\vt-mmfit\m5-mediapipe-v51-tune', 'C:\kw\vt-mmfit\m5-mediapipe-v51-control')) {
    if (Test-Path -LiteralPath $Path) { throw "Destino existente: $Path; no sobrescribir ni borrar." }
}
& $Python -B -I $PackageBuilder --check-only
if ($LASTEXITCODE -ne 0) { throw "Preflight M5 falló ($LASTEXITCODE); detenerse." }
& $Python -B -I $PackageBuilder
if ($LASTEXITCODE -ne 0) { throw "Empaquetado M5 falló ($LASTEXITCODE); conservar evidencia." }
```

Debe producir tune folds 0–3 (509 filas, 405 G1) y control fold 4 (107, 89 G1); no borrar ni reintentar automáticamente si solo uno llega a publicarse.

Antes de transferir, verificar ambos destinos tras sus renames: tune debe contener solo folds0–3, sus509 filas y el vínculo padre correcto; control tiene107 filas del fold4. El ajustador recibe únicamente `m5-mediapipe-v51-tune`, su recibo y el pin padre revisado. El control y las salidas individuales del banco completo quedan para el orquestador.

La CV real de Fase3 es una corrida posterior y secuencial: runner congelado en ce16580d3, tune MM-Fit más `C:/kw/vt-tune`, salida nueva, `KPKN_M5_MMFIT_PARENT_MANIFEST_SHA256` igual al padre MP verificado y pin legacy `4a1cd07b421917c29034a5e22dc350b888f542f080cc798eb8dbe2c8a061788d`. Preservar contexto, log y XML antes de otra corrida. El orquestador revisa la selección y después mide controles; no compartir resultados individuales ciegos ni afirmar metas finales antes de esos gates.

Esta guía solo se contrastó estáticamente con las fuentes de los constructores, el wrapper y los arneses. Ningún comando de publicación o evaluación se ejecutó como parte de su preparación.
