package com.example.kpkn.screens.progress

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.kpkn.navigation.KpknRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w360dp-h800dp-mdpi", application = android.app.Application::class)
class MuscleVolumePanelNavigationTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun `la ruta sin programa cuenta todos los registros`() {
        assertEquals("progress/muscle-volume?programId=", KpknRoute.MuscleVolumePanel.create())
        assertEquals("progress/muscle-volume?programId=", KpknRoute.MuscleVolumePanel.create(null))
    }

    @Test
    fun `la ruta con programa lo codifica como argumento`() {
        assertEquals("progress/muscle-volume?programId=plan-1", KpknRoute.MuscleVolumePanel.create("plan-1"))
        assertEquals("progress/muscle-volume?programId=a%20b", KpknRoute.MuscleVolumePanel.create("a b"))
        assertEquals("progress/muscle-volume?programId={programId}", KpknRoute.MuscleVolumePanel.route)
    }

    @Test
    fun `sin proveedor el enlace de la pestana Volumen no existe y con proveedor abre el panel del programa`() {
        var seenDefault: ((String?) -> Unit)? = { }
        var opened: String? = "nada"
        rule.setContent {
            seenDefault = LocalMuscleVolumePanelOpener.current
            CompositionLocalProvider(LocalMuscleVolumePanelOpener provides { id -> opened = id }) {
                LocalMuscleVolumePanelOpener.current?.invoke("plan-7")
            }
        }
        rule.waitForIdle()
        assertNull(seenDefault)
        assertEquals("plan-7", opened)
    }
}
