package com.example.snapgps.presentation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.presentation.camera.CameraPermissionScreen
import com.example.snapgps.presentation.gallery.GalleryScreen
import com.example.snapgps.presentation.settings.SettingsScreen
import com.example.snapgps.presentation.settings.SettingsState
import com.example.snapgps.presentation.theme.SnapGpsTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun galleryShowsEmptyState() {
        compose.setContent { SnapGpsTheme { GalleryScreen(photos = emptyList(), onBack = {}, onOpenPhoto = {}) } }
        compose.onNodeWithTag("gallery_empty").assertIsDisplayed()
        compose.onNodeWithText("No photos yet").assertIsDisplayed()
    }

    @Test
    fun galleryShowsGridWhenPhotosExist() {
        val photo = Photo(1, "content://nothing", 25.3, 82.9, null, 5f, null, null, null, 0L)
        compose.setContent { SnapGpsTheme { GalleryScreen(photos = listOf(photo), onBack = {}, onOpenPhoto = {}) } }
        compose.onNodeWithTag("gallery_grid").assertIsDisplayed()
    }

    @Test
    fun settingsToggleUpdatesState() {
        val sample = PhotoMetadata(25.3176, 82.9739, 81.0, 8f, null, null, "Varanasi", Instant.now())
        compose.setContent {
            var settings by remember { mutableStateOf(AppSettings()) }
            SnapGpsTheme {
                SettingsScreen(
                    state = SettingsState(settings, OverlayContentBuilder.build(sample, settings)),
                    onBack = {},
                    onUpdate = { settings = it(settings) },
                    onUpdateOverlay = { t -> settings = settings.copy(overlay = t(settings.overlay)) }
                )
            }
        }
        val altitude = compose.onNodeWithTag("switch_Altitude")
        altitude.assertIsOn()
        altitude.performClick()
        altitude.assertIsOff()
        // The live preview drops the altitude line.
        compose.onNodeWithText("Altitude: 81 m").assertDoesNotExist()
    }

    @Test
    fun cameraPermissionScreenOffersGrant() {
        var granted = false
        compose.setContent {
            SnapGpsTheme { CameraPermissionScreen(permanentlyDenied = false, onGrant = { granted = true }, onOpenSettings = {}) }
        }
        compose.onNodeWithText("Camera access needed").assertIsDisplayed()
        compose.onNodeWithText("Grant access").performClick()
        assertTrue(granted)
    }

    @Test
    fun cameraPermissionScreenOffersSettingsWhenPermanentlyDenied() {
        compose.setContent {
            SnapGpsTheme { CameraPermissionScreen(permanentlyDenied = true, onGrant = {}, onOpenSettings = {}) }
        }
        compose.onNodeWithText("Open app settings").assertIsDisplayed()
    }
}
