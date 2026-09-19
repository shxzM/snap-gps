package com.example.snapgps.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.snapgps.data.local.SettingsDataStore
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.CoordinateFormat
import com.example.snapgps.domain.model.LowAccuracyBehavior
import com.example.snapgps.domain.model.OverlayPosition
import com.example.snapgps.domain.model.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SettingsDataStoreTest {

    private fun newStore(): SettingsDataStore {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "test_settings_${UUID.randomUUID()}.preferences_pb")
        return SettingsDataStore(PreferenceDataStoreFactory.create { file })
    }

    @Test
    fun emptyStoreYieldsDefaults() = runTest {
        assertEquals(AppSettings(), newStore().settings.first())
    }

    @Test
    fun updatesArePersisted() = runTest {
        val store = newStore()
        store.update {
            it.copy(
                overlay = it.overlay.copy(showSpeed = true, position = OverlayPosition.TOP_RIGHT, opacity = 0.3f),
                coordinateFormat = CoordinateFormat.DMS,
                themeMode = ThemeMode.DARK,
                embedGpsMetadata = false,
                accuracyThresholdM = 35,
                lowAccuracyBehavior = LowAccuracyBehavior.WAIT_FOR_ACCURACY
            )
        }

        val s = store.settings.first()
        assertEquals(true, s.overlay.showSpeed)
        assertEquals(OverlayPosition.TOP_RIGHT, s.overlay.position)
        assertEquals(0.3f, s.overlay.opacity, 0.0001f)
        assertEquals(CoordinateFormat.DMS, s.coordinateFormat)
        assertEquals(ThemeMode.DARK, s.themeMode)
        assertFalse(s.embedGpsMetadata)
        assertEquals(35, s.accuracyThresholdM)
        assertEquals(LowAccuracyBehavior.WAIT_FOR_ACCURACY, s.lowAccuracyBehavior)
    }
}
