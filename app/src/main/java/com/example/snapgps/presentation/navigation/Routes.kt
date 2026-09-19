package com.example.snapgps.presentation.navigation

import kotlinx.serialization.Serializable

@Serializable
data object CameraRoute

@Serializable
data object GalleryRoute

@Serializable
data class PhotoDetailRoute(val photoId: Long)

@Serializable
data object SettingsRoute
