package com.example.snapgps.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.snapgps.domain.format.OverlayContentBuilder
import com.example.snapgps.domain.model.AppSettings
import com.example.snapgps.domain.model.CoordinateFormat
import com.example.snapgps.domain.model.DatePattern
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.LensFacing
import com.example.snapgps.domain.model.LowAccuracyBehavior
import com.example.snapgps.domain.model.OverlayConfig
import com.example.snapgps.domain.model.OverlayPosition
import com.example.snapgps.domain.model.PhotoMetadata
import com.example.snapgps.domain.model.SpeedUnit
import com.example.snapgps.domain.model.ThemeMode
import com.example.snapgps.presentation.camera.components.LocationOverlay
import com.example.snapgps.presentation.theme.SnapGpsTheme
import org.koin.compose.viewmodel.koinViewModel
import java.time.Instant
import kotlin.math.roundToInt

@Composable
fun SettingsRoot(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    SettingsScreen(
        state = state,
        onBack = onBack,
        onUpdate = viewModel::update,
        onUpdateOverlay = viewModel::updateOverlay
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsState?,
    onBack: () -> Unit,
    onUpdate: ((AppSettings) -> AppSettings) -> Unit,
    onUpdateOverlay: ((OverlayConfig) -> OverlayConfig) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (state == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        val s = state.settings
        val o = s.overlay
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .testTag("settings_list")
        ) {
            // ---- Overlay -------------------------------------------------------------------
            item { SectionHeader("Photo overlay") }
            item { OverlayPreview(state) }
            item { SwitchRow("Coordinates", o.showCoordinates) { v -> onUpdateOverlay { it.copy(showCoordinates = v) } } }
            item { SwitchRow("Address", o.showAddress, "Needs internet; skipped when offline") { v -> onUpdateOverlay { it.copy(showAddress = v) } } }
            item { SwitchRow("Date", o.showDate) { v -> onUpdateOverlay { it.copy(showDate = v) } } }
            item { SwitchRow("Time", o.showTime) { v -> onUpdateOverlay { it.copy(showTime = v) } } }
            item { SwitchRow("Altitude", o.showAltitude) { v -> onUpdateOverlay { it.copy(showAltitude = v) } } }
            item { SwitchRow("Accuracy", o.showAccuracy) { v -> onUpdateOverlay { it.copy(showAccuracy = v) } } }
            item { SwitchRow("Speed", o.showSpeed) { v -> onUpdateOverlay { it.copy(showSpeed = v) } } }
            item { SwitchRow("Direction", o.showDirection, "Compass heading, or travel direction when moving") { v -> onUpdateOverlay { it.copy(showDirection = v) } } }
            item {
                SegmentedRow(
                    label = "Position",
                    options = OverlayPosition.entries,
                    selected = o.position,
                    optionLabel = {
                        when (it) {
                            OverlayPosition.TOP_LEFT -> "↖ Top L"
                            OverlayPosition.TOP_RIGHT -> "↗ Top R"
                            OverlayPosition.BOTTOM_LEFT -> "↙ Bot L"
                            OverlayPosition.BOTTOM_RIGHT -> "↘ Bot R"
                        }
                    }
                ) { v -> onUpdateOverlay { it.copy(position = v) } }
            }
            item {
                SliderRow(
                    label = "Background opacity",
                    value = o.opacity,
                    valueRange = 0f..1f,
                    steps = 9,
                    valueLabel = { "${(it * 100).roundToInt()}%" }
                ) { v -> onUpdateOverlay { it.copy(opacity = v) } }
            }

            // ---- Format --------------------------------------------------------------------
            item { SectionHeader("Format") }
            item {
                SegmentedRow("Coordinates", CoordinateFormat.entries, s.coordinateFormat, {
                    if (it == CoordinateFormat.DECIMAL) "Decimal" else "Deg Min Sec"
                }) { v -> onUpdate { it.copy(coordinateFormat = v) } }
            }
            item {
                RadioGroup("Date format", DatePattern.entries, s.datePattern, {
                    when (it) {
                        DatePattern.DAY_MONTH_YEAR -> "19 Sep 2026"
                        DatePattern.MONTH_DAY_YEAR -> "Sep 19, 2026"
                        DatePattern.ISO -> "2026-09-19"
                    }
                }) { v -> onUpdate { it.copy(datePattern = v) } }
            }
            item { SwitchRow("24-hour time", s.use24HourTime) { v -> onUpdate { it.copy(use24HourTime = v) } } }
            item {
                SegmentedRow("Distance units", DistanceUnit.entries, s.distanceUnit, {
                    if (it == DistanceUnit.METRIC) "Meters" else "Feet"
                }) { v -> onUpdate { it.copy(distanceUnit = v) } }
            }
            item {
                SegmentedRow("Speed units", SpeedUnit.entries, s.speedUnit, {
                    when (it) {
                        SpeedUnit.KMH -> "km/h"
                        SpeedUnit.MPH -> "mph"
                        SpeedUnit.MPS -> "m/s"
                    }
                }) { v -> onUpdate { it.copy(speedUnit = v) } }
            }

            // ---- Location & privacy --------------------------------------------------------
            item { SectionHeader("Location & privacy") }
            item {
                SwitchRow("Show location on photo", s.stampLocationOnPhoto, "Draw GPS details into the image") { v ->
                    onUpdate { it.copy(stampLocationOnPhoto = v) }
                }
            }
            item {
                SwitchRow(
                    "Embed GPS in photo metadata", s.embedGpsMetadata,
                    "Anyone you share the file with can read the exact location"
                ) { v -> onUpdate { it.copy(embedGpsMetadata = v) } }
            }
            item {
                SliderRow(
                    label = "Required accuracy",
                    value = s.accuracyThresholdM.toFloat(),
                    valueRange = 5f..100f,
                    steps = 18,
                    valueLabel = { "±${it.roundToInt()} m" }
                ) { v -> onUpdate { it.copy(accuracyThresholdM = v.roundToInt()) } }
            }
            item {
                SliderRow(
                    label = "Maximum location age",
                    value = s.maxLocationAgeSec.toFloat(),
                    valueRange = 10f..120f,
                    steps = 10,
                    valueLabel = { "${it.roundToInt()} s" }
                ) { v -> onUpdate { it.copy(maxLocationAgeSec = v.roundToInt()) } }
            }
            item {
                RadioGroup("When accuracy is low", LowAccuracyBehavior.entries, s.lowAccuracyBehavior, {
                    when (it) {
                        LowAccuracyBehavior.STAMP_WITH_WARNING -> "Stamp anyway and warn me"
                        LowAccuracyBehavior.WAIT_FOR_ACCURACY -> "Don't capture until accurate"
                        LowAccuracyBehavior.CAPTURE_WITHOUT_STAMP -> "Capture without location"
                    }
                }) { v -> onUpdate { it.copy(lowAccuracyBehavior = v) } }
            }

            // ---- Appearance & camera -------------------------------------------------------
            item { SectionHeader("Appearance & camera") }
            item {
                SegmentedRow("Theme", ThemeMode.entries, s.themeMode, {
                    when (it) {
                        ThemeMode.SYSTEM -> "System"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                    }
                }) { v -> onUpdate { it.copy(themeMode = v) } }
            }
            item {
                SegmentedRow("Default camera", LensFacing.entries, s.defaultLens, {
                    if (it == LensFacing.BACK) "Rear" else "Front"
                }) { v -> onUpdate { it.copy(defaultLens = v) } }
            }
            item { Box(Modifier.padding(bottom = 24.dp)) }
        }
    }
}

@Composable
private fun OverlayPreview(state: SettingsState) {
    Box(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .background(
                Brush.verticalGradient(listOf(Color(0xFF6A8CAF), Color(0xFF8FA66B), Color(0xFF4F6B3A))),
                RoundedCornerShape(12.dp)
            )
            .testTag("overlay_preview")
    ) {
        LocationOverlay(state.previewLines, state.settings.overlay)
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 8.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
        )
    }
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, subtitle: String? = null, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("switch_$title")
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> SegmentedRow(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, option ->
                SegmentedButton(
                    selected = option == selected,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    icon = {}
                ) {
                    Text(optionLabel(option), maxLines = 1, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun <T> RadioGroup(
    label: String,
    options: List<T>,
    selected: T,
    optionLabel: (T) -> String,
    onSelect: (T) -> Unit
) {
    Column(Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
        Column(Modifier.selectableGroup()) {
            options.forEach { option ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    RadioButton(selected = option == selected, onClick = null)
                    Text(optionLabel(option), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

/** Slider that only persists on release, so dragging doesn't hammer DataStore. */
@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    steps: Int,
    valueLabel: (Float) -> String,
    onChange: (Float) -> Unit
) {
    var draft by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row {
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueLabel(draft), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = draft,
            onValueChange = { draft = it },
            onValueChangeFinished = { onChange(draft) },
            valueRange = valueRange,
            steps = steps
        )
    }
}

@Preview(heightDp = 1400)
@Composable
private fun SettingsScreenPreview() {
    val settings = AppSettings()
    val sample = PhotoMetadata(25.3176, 82.9739, 81.0, 8f, null, null, "Varanasi, Uttar Pradesh", Instant.now())
    SnapGpsTheme {
        SettingsScreen(
            state = SettingsState(settings, OverlayContentBuilder.build(sample, settings)),
            onBack = {}, onUpdate = {}, onUpdateOverlay = {}
        )
    }
}
