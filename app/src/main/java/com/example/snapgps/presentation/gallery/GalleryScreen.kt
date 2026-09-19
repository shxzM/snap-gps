package com.example.snapgps.presentation.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.example.snapgps.domain.format.CaptureTimeFormatter
import com.example.snapgps.domain.model.DatePattern
import com.example.snapgps.domain.model.Photo
import com.example.snapgps.presentation.theme.SnapGpsTheme
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant

@Composable
fun GalleryRoot(
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit,
    viewModel: GalleryViewModel = koinViewModel()
) {
    val photos by viewModel.photos.collectAsStateWithLifecycle()
    GalleryScreen(photos = photos, onBack = onBack, onOpenPhoto = onOpenPhoto)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    photos: List<Photo>?,
    onBack: () -> Unit,
    onOpenPhoto: (Long) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Gallery") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            photos == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            photos.isEmpty() -> EmptyGallery(Modifier.padding(padding))
            else -> LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 110.dp),
                contentPadding = PaddingValues(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .testTag("gallery_grid")
            ) {
                items(photos, key = { it.id }) { photo ->
                    AsyncImage(
                        model = photo.uri,
                        contentDescription = "Photo taken " + CaptureTimeFormatter.formatDate(
                            Instant.ofEpochMilli(photo.capturedAt), DatePattern.DAY_MONTH_YEAR
                        ),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable(role = Role.Button) { onOpenPhoto(photo.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier = Modifier) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("gallery_empty")
    ) {
        Icon(
            Icons.Outlined.PhotoLibrary,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text("No photos yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "Photos you take with SnapGPS will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Preview
@Composable
private fun EmptyGalleryPreview() {
    SnapGpsTheme { GalleryScreen(photos = emptyList(), onBack = {}, onOpenPhoto = {}) }
}
