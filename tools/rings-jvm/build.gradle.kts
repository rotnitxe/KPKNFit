import org.gradle.api.execution.TaskExecutionListener
import org.gradle.api.tasks.TaskState
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar
import java.util.Locale

buildscript {
    // Igual que VT: usar el artefacto que ya está en la caché de Android, sin red.
    configurations.classpath {
        resolutionStrategy.eachDependency {
            if (requested.group == "com.google.errorprone" && requested.name == "error_prone_annotations") {
                useVersion("2.30.0")
            }
        }
    }
}

plugins {
    kotlin("jvm") version "2.2.10"
    kotlin("plugin.serialization") version "2.2.10"
}

val appRoot = file(providers.gradleProperty("appRoot").getOrElse("../../android-native/app"))
require(appRoot.resolve("src/main/java/com/example/kpkn/domain/auge").isDirectory) {
    "appRoot no es un módulo app de KPKN: $appRoot"
}
fun sourceList(name: String) = file("config/$name.txt").readLines()
    .map(String::trim).filter { it.isNotEmpty() && !it.startsWith("#") }

kotlin {
    jvmToolchain(17)
    // app/build.gradle.kts no declara opt-ins/freeCompilerArgs adicionales para domain.
}

sourceSets {
    main {
        kotlin.srcDir(appRoot.resolve("src/main/java"))
        kotlin.srcDir("shim")
        kotlin.include("com/example/kpkn/domain/**")
        kotlin.include("com/example/kpkn/data/models/**")
        kotlin.include("com/example/kpkn/data/exercises/**")
        // Fuera de esas tres raíces solo entran ARCHIVOS concretos, nunca carpetas Android.
        sourceList("main-extra-includes").forEach { kotlin.include(it) }
        fileTree("shim") { include("**/*.kt") }.files.forEach {
            kotlin.include(it.relativeTo(file("shim")).invariantSeparatorsPath)
        }
        sourceList("main-excludes").forEach { kotlin.exclude(it) }
    }
    test {
        kotlin.srcDir(appRoot.resolve("src/test/java"))
        // JVM1 continuación: AUGE es la cobertura validada; no ampliar incidentalmente UI/nutrición.
        kotlin.include("com/example/kpkn/domain/auge/**")
        sourceList("test-excludes").forEach { kotlin.exclude(it) }
        resources.srcDir(appRoot.resolve("src/test/resources"))
    }
}

dependencies {
    // app/gradle/libs.versions.toml: las mismas versiones del módulo Android.
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // El dominio usa StateFlow/Mutex; esta versión está disponible offline.
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    testImplementation("junit:junit:4.13.2")
    // Resuelto en la caché local y alineado con core para runTest del catálogo v2.
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
}

val compileWriteGuard by tasks.registering(JavaCompile::class) {
    source(fileTree("guard") { include("**/*.java") })
    classpath = files()
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(17)) })
    options.release.set(17)
    options.encoding = "UTF-8"
    destinationDirectory.set(layout.buildDirectory.dir("guard/classes"))
}
val writeGuardJar by tasks.registering(Jar::class) {
    dependsOn(compileWriteGuard)
    from(compileWriteGuard.flatMap { it.destinationDirectory })
    destinationDirectory.set(layout.buildDirectory.dir("guard"))
    archiveFileName.set("write-guard.jar")
    manifest { attributes["Premain-Class"] = "r4.jvm.FileWriteGuard" }
}

tasks.test {
    dependsOn(writeGuardJar)
    useJUnit()
    workingDir = appRoot
    maxHeapSize = "2g"
    maxParallelForks = 1
    // Cada invocación ejecuta las pruebas; la compilación sí conserva sus outputs.
    outputs.upToDateWhen { false }
    val tmp = file("build/test-tmp")
    val shimLog = file("build/shim-access.log")
    val blockedLog = file("build/blocked-writes.log")
    systemProperty("java.io.tmpdir", tmp.absolutePath)
    systemProperty("r4.shimLog", shimLog.absolutePath)
    environment("TEMP", tmp.absolutePath)
    environment("TMP", tmp.absolutePath)
    // El máster dorado solo se regenera (y la simulación completa solo corre) si quien llama lo pide expresamente:
    // -Pr4GoldenUpdate=true / -Pr4SimFull=true. Por defecto la comprobación nunca se convierte en regeneración.
    val goldenUpdate = providers.gradleProperty("r4GoldenUpdate").getOrElse("false") == "true"
    val simFull = providers.gradleProperty("r4SimFull").getOrElse("false") == "true"
    environment("AUGE_GOLDEN_UPDATE", if (goldenUpdate) "1" else "0")
    environment("AUGE_SIM_FULL", if (simFull) "1" else "0")
    // Única carpeta del worktree donde el guardián deja escribir: <app>/build/auge-golden (expected.txt / actual.txt); nunca src.
    val goldenDir = appRoot.resolve("build/auge-golden").absolutePath
    jvmArgs("-javaagent:${writeGuardJar.get().archiveFile.get().asFile.absolutePath}=${projectDir.absolutePath}|$goldenDir")
    doFirst {
        tmp.mkdirs()
        shimLog.delete()
        blockedLog.delete()
        // Reject old XML even if worker startup fails after this action.
        fileTree("build/test-results/test") { include("TEST-*.xml") }.files.forEach { it.delete() }
        file("build/test-run-id.txt").writeText(providers.gradleProperty("r4RunId").getOrElse("direct"))
    }
    doLast {
        check(!shimLog.exists()) { "Se ejecutó un shim de compilación: ${shimLog.readText()}" }
        check(!blockedLog.exists()) { "Una prueba intentó escribir fuera del arnés: ${blockedLog.readText()}" }
    }
    testLogging {
        events("failed", "skipped")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
        showStandardStreams = false
    }
}

// Evidencia local de tiempos, también para tareas UP-TO-DATE (compilación caliente).
val taskTimings = file("build/task-timings.tsv")
taskTimings.parentFile.mkdirs()
taskTimings.writeText("task\tstatus\tseconds\n")
file("build/task-timings-run-id.txt").writeText(providers.gradleProperty("r4RunId").getOrElse("direct"))
@Suppress("DEPRECATION")
gradle.taskGraph.addTaskExecutionListener(object : TaskExecutionListener {
    private val starts = mutableMapOf<String, Long>()
    override fun beforeExecute(task: Task) { starts[task.path] = System.nanoTime() }
    override fun afterExecute(task: Task, state: TaskState) {
        val seconds = (System.nanoTime() - starts.getValue(task.path)) / 1e9
        val status = if (state.failure != null) "FAILED" else state.skipMessage ?: "EXECUTED"
        val time = "%.3f".format(Locale.ROOT, seconds)
        taskTimings.appendText("${task.path}\t$status\t$time\n")
        if (task.name in setOf("compileKotlin", "compileTestKotlin", "test")) {
            logger.lifecycle("[r4-jvm] ${task.path}: $status ${time}s")
        }
    }
})
