package com.example.snapgps.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.snapgps.presentation.camera.CameraRoot
import com.example.snapgps.presentation.gallery.GalleryRoot
import com.example.snapgps.presentation.gallery.PhotoDetailRoot
import com.example.snapgps.presentation.settings.SettingsRoot

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = CameraRoute) {
        composable<CameraRoute> {
            CameraRoot(
                onOpenGallery = dropUnlessResumed { navController.navigate(GalleryRoute) },
                onOpenSettings = dropUnlessResumed { navController.navigate(SettingsRoute) },
                onOpenPhoto = { id -> navController.navigate(PhotoDetailRoute(id)) }
            )
        }
        composable<GalleryRoute> {
            GalleryRoot(
                // dropUnlessResumed makes repeated back requests (e.g. during a transition) harmless.
                onBack = dropUnlessResumed { navController.popBackStack() },
                onOpenPhoto = { id -> navController.navigate(PhotoDetailRoute(id)) }
            )
        }
        composable<PhotoDetailRoute> {
            PhotoDetailRoot(onBack = dropUnlessResumed { navController.popBackStack() })
        }
        composable<SettingsRoute> {
            SettingsRoot(onBack = dropUnlessResumed { navController.popBackStack() })
        }
    }
}
