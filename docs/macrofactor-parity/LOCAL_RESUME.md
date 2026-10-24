# Continuación MFP · vigente 24-oct-2026, 18:18 Chile

Orden: terminar directamente en local, sin push ni master, sin preguntas rutinarias, subagentes ni contacto con otros chats. D19 autoriza T6/Ta/Tb. No tocar S11/H-i, importador ni .gitignore. Preservar datos/WIP. Teléfono solo phone_run/phone.lock; única KPKN Dev com.example.kpkn.dbg, firma y Room compatibles antes de-r. ZFlip desconectado: p95 físico pendiente, sin cifras de emulador.

## Fuente

C:/kw/mfpi, rama mfp/i, código en003766a85. Integrados todos los paquetes. Últimos: a882fdf9f formato de progreso; daedb169b contratos; 286ad8a45 esquema31 KSP; f9307e648 selector y diagnóstico sintético OCR; abdacd270 baseDebug con paquete/firma debug y nombre Dev en ambos idiomas SIN init; 003766a85 imágenes OCR legibles y regresiones de lecturas109/39. Falta compilar/verificar estos dos últimos commits. Todos los scripts antiguos de correcciones YA están aplicados: no repetir mfp-final-validation-fixes.py, Myo, calentamiento ni EER/GTIN.

feat/macrofactor-parity en C:/kw/mfp sigue026a8c1e3. Tras validar, incorporar mfp/i allí sin master/push. Worktrees componentes limpios y sus recibos anteriores permanecen. No limpiar antes del cierre.

## Validación

Ver cambios/VALIDACION_INTEGRADA_LOCAL.md. Suite completa con renders:13.025 pruebas/1.520 clases,12.936 aprobadas,79fallos,10omitidas,1.307PNG.42heredados nominales+13renders reproducidos en base intacta8ac57c9d4+19contaminación (33/33 editores pasan aislados)+5contratos corregidos/verificados.21/21 sospechosos checkin verdes. Focal4:190/176aprobadas/14fallos; control:117/104aprobadas/13mismosrenders; focal5:7/7selector. JSON nominal final C:/kw/logs/mfp-final-failure-classification.json, XML de cada corrida preservados. No llamar verde a la suite completa.

JVM integrada anterior:3.067/275clases,0fallos; recibo en cambios/. Entrenamiento Android129/129 y40PNG; CSV/OCR previo460ejecutadas/67clases,0fallos,2omisiones condicionales,28PNG. Paquetes anteriores están documentados en sus recibos.

Control C:/kw/mfp-render-control detached8ac57c9d4 TERMINÓ. Solo local.properties ignorado, fuentes intactas. Ningún Gradle propio activo en este punto. Próximo: JVM focal del parser y Android final SIN init (baseDebug ordinario), identidad en/es + parser + selector; ensamblar APK. Código debe congelarse durante pruebas.

## QA nativa

AVD propia KPKN_MFP_Local_QA, API36, serial emulator-5584, C:/kw/mfpi-qa-avd. Todos los accesos por helper que importa emu_run y mantiene emu.lock. Nombre de AVD verificado antes de operar. Originales5554(Room34)/5580(Room30) preservadas. Nunca usar5590. Arranque propio exige12GB y lock libre; revisión automática rechazó bajar esa guarda: se mantuvo y se esperó. No eludir. Qemu cayó dos veces con SwiftShader (c0000005 en Windows); Vulkan-off no bastó. Ahora -gpu host -feature -Vulkan: estable, activó persona QA y abrió Main real. Mantener datos; no clear/uninstall.

APK inicial c480 SHA4b4ae951b61f1acbb27a930d6978f78ecdb55103c4209a4aa57bef55cf005ccd. Smoke nativo60/60 estados100/130, BarcodeSelfTest8/8. Esos arneses son en memoria. Perfil QA activado por WizardHarness(scopefull,personaqa,autonext250,untilREVIEW_ACTIVATE,activate1500); DemoSeed sembró1programa/8entrenos/2comidas/nombreAlex y MainActivity abrió. Permiso notificaciones concedido, ubicación denegado solo en esta AVD.

Ajustes reales: descanso180s, autoStartRestAfterRecording=false, augeAutoDeload=true; confirmado en SQLite persistido, no solo UI. Exportación real SAF guardó ZIP5CSV: comidas6filas/cuerpo5/entrenos170/biblioteca2archivosvacíos; BOM y columnas coherentes. Evidencia runtime-local-20261024/native-export.zip y .json.

APK5 f9307e648 SHA076b187e02930880e99fd04915ca2ae74bbc9931cab7630fdf87dab57a3bf1d7 instalado-r tras verificar hash anterior, certificadof14259a4a5dd75ffca3732b5930cd7e30fa7f57d740e624e07cda6db87fe6fe4 y Room31/integridad. Recibo mfp-native-update5-receipt.json. Snapshot conWAL/SHM en mfp-native-db-before-update5/raw; validación SQLite en copia separada/validated. Nunca abrir raw para validar porque SQLite puede checkpointar sus propios WAL/SHM.

OCR nativo4/5 explicado: la imagen30px hizo leer «10g» como109; parser lo rechaza correctamente por>100g/100g. RAW en MfpLabelSelfTest.log (solo imágenes sintéticas), evidencia inicial preservada comoMfpLabelSelfTest-initial-4of5.log. Nuevo003766a85 usa48–56px en cinco positivos y tests que mantienen rechazo109 y no reinterpretan39como3. NO cambiar parser para adivinar. Falta APK final y verificar5/5; luego una selección real de galería y guardado manual.

En el APK5 solo nombre inglés eraKPKN por recurso values-en. abdacd270 lo corrige definitivamente en resources debug+build.gradle (baseDebug .dbg, firma debug; release igual). Hay testDebugAppIdentityTest en/es. No usar init en validación final, para probar build ordinario. Los helpers propios están fuera de Git en C:/kw/mfpi-qa-avd; qa.py start usa flags0x10008000; tap deriva boundsdelXML; scroll usa área desplazable delXML.

## Resto

JVM/parser+Androidfinal y metadatos/certificado; update-r protegida en AVD conservandoRoom31/ajustes; OCR5/5+galería; acciones nativas de checkin,recetas,N3/N4,progreso/entreno según corresponda. Después README/CAMBIOS/recibos definitivos, integrar en feat/macrofactor-parity sin master, APK+LEEME+informe/manifiesto a G:/Mi unidad (disponible), limpieza solo propia conservando evidencia. No declararQA física ni100%conp95pendiente.
