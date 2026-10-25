buildscript {
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.errorprone" && requested.name == "error_prone_annotations") useVersion("2.30.0")
        }
    }
}

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
}

// Compila TODO el código puro (domain/** + modelos y protocolos) menos las exclusiones de excluded-main.txt, y solo los tests que pidas.
// No sustituye a :app:testBaseDebugUnitTest (la verificación oficial): sirve para validar lógica pura en minutos, sin la cola de Android.
val appRoot: String = (findProperty("appRoot") as String?) ?: error("Falta -PappRoot=<ruta a android-native/app del worktree>")
val testInclude: List<String> = ((findProperty("testInclude") as String?) ?: "com/example/kpkn/domain/**").split(",").map { it.trim() }.filter { it.isNotEmpty() }
val excludedMain: List<String> = file("excluded-main.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()
val extraMain: List<String> = file("included-extra.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()
val excludedTest: List<String> = file("excluded-test.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()
// Métodos de prueba excluidos por entorno (filtro de Gradle: "paquete.Clase.método"). Ver excluded-test-methods.txt y ../README.md.
val excludedTestMethods: List<String> = file("excluded-test-methods.txt").takeIf { it.exists() }?.readLines()?.map { it.trim() }?.filter { it.isNotEmpty() && !it.startsWith("#") } ?: emptyList()

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("$appRoot/src/main/java")
        // Stubs (Kotlin) de clases Android/UI/Room que el código puro usa. Solo entran las rutas listadas en included-extra.txt (ver ../README.md).
        kotlin.srcDir("stubs")
        kotlin.include(
            "com/example/kpkn/domain/**",
            "com/example/kpkn/data/models/**",
            "com/example/kpkn/data/protocols/**",
            "com/example/kpkn/data/splits/**",
            "com/example/kpkn/data/sessions/**",
            "com/example/kpkn/data/programs/**",
            "com/example/kpkn/data/food/**",
            "com/example/kpkn/data/exercises/**",
            "com/example/kpkn/data/competitions/**",
            "com/example/kpkn/data/wikilab/**",
        )
        extraMain.forEach { kotlin.include(it) }
        excludedMain.forEach { kotlin.exclude(it) }
    }
    test {
        kotlin.srcDir("$appRoot/src/test/java")
        // Recursos de prueba (import/, golden/, food_data/...): AGP también los pone en el classpath de pruebas.
        resources.srcDir("$appRoot/src/test/resources")
        testInclude.forEach { kotlin.include(it) }
        excludedTest.forEach { kotlin.exclude(it) }
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

tasks.test {
    useJUnit()
    filter { excludedTestMethods.forEach { excludeTestsMatching(it) } }
    // Mismo directorio de trabajo que las pruebas del módulo app (rutas relativas como "src/main/assets/...").
    workingDir = file(appRoot)
    maxHeapSize = "1g"
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}
