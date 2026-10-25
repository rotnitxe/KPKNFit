// Arnés JVM GENERAL de KPKN (desde el 21-oct-2026, a partir del arnés de la ola MFP): compila el código puro de CUALQUIER worktree y ejecuta sus tests sin Android.
// La verificación oficial de cada paquete sigue siendo :app:testBaseDebugUnitTest por gradle_slot.sh.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
    resolutionStrategy {
        eachPlugin {
            // Sin marcadores de plugin en la caché: se resuelve el artefacto que ya usa el build de Android.
            if (requested.id.id == "org.jetbrains.kotlin.jvm") {
                useModule("org.jetbrains.kotlin:kotlin-gradle-plugin:${requested.version}")
            }
            if (requested.id.id == "org.jetbrains.kotlin.plugin.serialization") {
                useModule("org.jetbrains.kotlin:kotlin-serialization:${requested.version}")
            }
        }
    }
}
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
    }
}
rootProject.name = "kpkn-mfp-cloud-jvm"
