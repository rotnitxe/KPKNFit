@file:kotlin.OptIn(androidx.camera.core.ExperimentalSessionConfig::class)

package com.example.kpkn.screens.workout.technique

import android.util.Range
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraInfo
import androidx.camera.core.ExperimentalSessionConfig
import androidx.camera.core.Preview
import androidx.camera.core.SessionConfig
import androidx.camera.core.featuregroup.GroupableFeature
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.video.Recorder
import androidx.camera.video.VideoCapture
import com.example.kpkn.domain.biomechanics.profiles.AnalysisProfileId
import com.example.kpkn.screens.workout.WorkoutMediaCaptureController

/**
 * Nivel de cadencia (fps) concedido por la cámara.
 */
enum class RecorderFpsTier {
    /** Nivel 1: grupo de funciones de 60 fps de CameraX (SessionConfig + isFeatureGroupSupported). */
    FEATURE_GROUP_60,

    /** Nivel 2: rango fijo (60, 60) entre los rangos soportados de AE. */
    RANGE_60,

    /** Nivel 3: 30 fps estándar de respaldo. */
    FALLBACK_30,
}

/**
 * Capacidades de fps de la cámara para la función pura de elección de nivel.
 */
data class CameraFpsCapabilities(
    val isFeatureGroup60Supported: Boolean,
    val supportedRanges: Set<Range<Int>>,
)

/**
 * Sesión configurada lista para enlazarse al ciclo de vida con CameraX 1.5.3.
 */
data class RecorderSession(
    val preview: Preview,
    val videoCapture: VideoCapture<Recorder>,
    val sessionConfig: SessionConfig,
    val tier: RecorderFpsTier,
    val grantedFps: Float,
) {
    val is60FpsGranted: Boolean
        get() = tier == RecorderFpsTier.FEATURE_GROUP_60 || tier == RecorderFpsTier.RANGE_60

    /** El tier efectivo puede cambiar sin reemplazar los use cases propiedad de este efecto. */
    fun sharesUseCasesWith(other: RecorderSession): Boolean =
        preview === other.preview && videoCapture === other.videoCapture
}

internal data class RecorderSessionBindResult<T>(
    val camera: T,
    val effectiveSession: RecorderSession,
    val usedUnconfiguredFallback: Boolean,
)

/**
 * Factoría de la sesión de cámara para el grabador de técnica (VT8a).
 * Configura Preview 16:9 + VideoCapture y negocia 60 fps según el dispositivo y perfil.
 */
object RecorderSessionFactory {

    /**
     * Si CameraX rechaza el SessionConfig solicitado, el bind bare solo concede 30 fps nominales.
     * Mantiene separados el pedido original y la concesión efectiva.
     */
    internal fun <T> bindWithFallback(
        session: RecorderSession,
        bindConfigured: () -> T,
        bindUnconfigured: () -> T,
        onConfiguredBindFailure: () -> Unit = {},
    ): RecorderSessionBindResult<T> {
        if (session.tier == RecorderFpsTier.FALLBACK_30) {
            return RecorderSessionBindResult(
                camera = bindUnconfigured(),
                effectiveSession = session,
                usedUnconfiguredFallback = true,
            )
        }

        return runCatching(bindConfigured).fold(
            onSuccess = { camera ->
                RecorderSessionBindResult(
                    camera = camera,
                    effectiveSession = session,
                    usedUnconfiguredFallback = false,
                )
            },
            onFailure = {
                onConfiguredBindFailure()
                RecorderSessionBindResult(
                    camera = bindUnconfigured(),
                    effectiveSession = session.copy(
                        tier = RecorderFpsTier.FALLBACK_30,
                        grantedFps = 30f,
                    ),
                    usedUnconfiguredFallback = true,
                )
            },
        )
    }

    /**
     * Perfiles que usan disco o son de velocidad y por tanto se benefician de 60 fps
     * (SQUAT, DEADLIFT, BENCH, OHP, HIP_THRUST, ROW).
     */
    val SPEED_OR_PLATE_PROFILES = setOf(
        AnalysisProfileId.SQUAT,
        AnalysisProfileId.DEADLIFT,
        AnalysisProfileId.BENCH,
        AnalysisProfileId.OHP,
        AnalysisProfileId.HIP_THRUST,
        AnalysisProfileId.ROW,
    )

    /**
     * Función pura de elección de nivel de fps:
     * 1. grupo de 60 fps si está soportado;
     * 2. rango (60, 60) si está entre los soportados;
     * 3. 30 fps de respaldo.
     */
    fun chooseTier(caps: CameraFpsCapabilities, want60: Boolean): RecorderFpsTier {
        if (!want60) return RecorderFpsTier.FALLBACK_30
        if (caps.isFeatureGroup60Supported) return RecorderFpsTier.FEATURE_GROUP_60
        val has60Range = caps.supportedRanges.any { it.lower == 60 && it.upper == 60 }
        if (has60Range) return RecorderFpsTier.RANGE_60
        return RecorderFpsTier.FALLBACK_30
    }

    /**
     * Determina si se deben pedir 60 fps según los ajustes y el perfil.
     */
    fun wants60Fps(prefer60Setting: Boolean, profileId: AnalysisProfileId?): Boolean {
        if (!prefer60Setting) return false
        return profileId in SPEED_OR_PLATE_PROFILES
    }

    /**
     * Crea los use cases y el [SessionConfig] correspondiente.
     */
    @OptIn(ExperimentalSessionConfig::class)
    fun createSession(
        cameraInfo: CameraInfo,
        want60: Boolean,
    ): RecorderSession {
        val previewResolution = ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
            .build()
        val preview = Preview.Builder()
            .setResolutionSelector(previewResolution)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(WorkoutMediaCaptureController.hdThenSdSelector())
            .setAspectRatio(AspectRatio.RATIO_16_9)
            .build()
        val videoCapture = VideoCapture.withOutput(recorder)

        val candidateGroupConfig = SessionConfig.Builder(preview, videoCapture)
            .setRequiredFeatureGroup(GroupableFeature.FPS_60)
            .build()

        val isGroupSupported = runCatching { cameraInfo.isFeatureGroupSupported(candidateGroupConfig) }.getOrDefault(false)
        val ranges = runCatching { cameraInfo.supportedFrameRateRanges }.getOrDefault(emptySet())

        val caps = CameraFpsCapabilities(isGroupSupported, ranges)
        val tier = chooseTier(caps, want60)

        val sessionConfig = when (tier) {
            RecorderFpsTier.FEATURE_GROUP_60 -> candidateGroupConfig
            RecorderFpsTier.RANGE_60 -> SessionConfig.Builder(preview, videoCapture)
                .setFrameRateRange(Range(60, 60))
                .build()
            RecorderFpsTier.FALLBACK_30 -> SessionConfig.Builder(preview, videoCapture)
                .build()
        }

        val grantedFps = when (tier) {
            RecorderFpsTier.FEATURE_GROUP_60, RecorderFpsTier.RANGE_60 -> 60f
            RecorderFpsTier.FALLBACK_30 -> 30f
        }

        return RecorderSession(
            preview = preview,
            videoCapture = videoCapture,
            sessionConfig = sessionConfig,
            tier = tier,
            grantedFps = grantedFps,
        )
    }
}
