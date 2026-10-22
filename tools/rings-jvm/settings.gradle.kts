// Arnés JVM de la ola VT: compila y prueba SOLO domain/biomechanics de un worktree, sin Android ni la cola de Gradle.
// Uso: cd /c/kw/vt-jvm && ./gradlew test --offline -PappRoot=C:/kw/vt-a/android-native/app [--tests "<filtro>"]
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
rootProject.name = "r4-jvm"
