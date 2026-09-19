package com.example.snapgps.di

import com.example.snapgps.data.camera.CameraManager
import com.example.snapgps.data.local.AppDatabase
import com.example.snapgps.data.local.SettingsDataStore
import com.example.snapgps.data.local.settingsDataStore
import com.example.snapgps.data.location.AndroidGeocodingRepository
import com.example.snapgps.data.location.HeadingSensorDataSource
import com.example.snapgps.data.location.LocationRepositoryImpl
import com.example.snapgps.data.media.ExifWriter
import com.example.snapgps.data.media.ImageProcessor
import com.example.snapgps.data.media.MediaStoreManager
import com.example.snapgps.data.media.OverlayBitmapRenderer
import com.example.snapgps.data.media.PhotoRepositoryImpl
import com.example.snapgps.data.media.TempFiles
import com.example.snapgps.domain.Clock
import com.example.snapgps.domain.repository.CameraRepository
import com.example.snapgps.domain.repository.GeocodingRepository
import com.example.snapgps.domain.repository.HeadingRepository
import com.example.snapgps.domain.repository.LocationRepository
import com.example.snapgps.domain.repository.PhotoProcessor
import com.example.snapgps.domain.repository.PhotoRepository
import com.example.snapgps.domain.repository.SettingsRepository
import com.example.snapgps.presentation.camera.CameraViewModel
import com.example.snapgps.presentation.gallery.GalleryViewModel
import com.example.snapgps.presentation.gallery.PhotoDetailViewModel
import com.example.snapgps.presentation.settings.SettingsViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.bind
import org.koin.dsl.module

val dataModule = module {
    single { Clock.System }

    // Local storage
    single { AppDatabase.create(androidContext()) }
    single { get<AppDatabase>().photoDao() }
    single<SettingsRepository> { SettingsDataStore(androidContext().settingsDataStore) }

    // Camera
    singleOf(::TempFiles)
    singleOf(::CameraManager) bind CameraRepository::class

    // Location
    singleOf(::LocationRepositoryImpl) bind LocationRepository::class
    singleOf(::AndroidGeocodingRepository) bind GeocodingRepository::class
    singleOf(::HeadingSensorDataSource) bind HeadingRepository::class

    // Photo pipeline
    singleOf(::OverlayBitmapRenderer)
    singleOf(::ExifWriter)
    singleOf(::ImageProcessor) bind PhotoProcessor::class
    singleOf(::MediaStoreManager)
    singleOf(::PhotoRepositoryImpl) bind PhotoRepository::class
}

val presentationModule = module {
    viewModelOf(::CameraViewModel)
    viewModelOf(::GalleryViewModel)
    viewModelOf(::PhotoDetailViewModel)
    viewModelOf(::SettingsViewModel)
}
