package com.example.snapgps.presentation.camera.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsNotFixed
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.snapgps.domain.format.UnitConverter
import com.example.snapgps.domain.model.DistanceUnit
import com.example.snapgps.domain.model.LocationStatus
import com.example.snapgps.presentation.theme.GpsColors

/** Continuous feedback on fix quality (TDD §10); tapping retries or fixes the cause. */
@Composable
fun GpsStatusChip(
    status: LocationStatus,
    isPrecise: Boolean,
    distanceUnit: DistanceUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val presentation = presentation(status, isPrecise, distanceUnit)
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Black.copy(alpha = 0.55f),
        modifier = modifier
            .heightIn(min = 40.dp)
            .semantics {
                role = Role.Button
                contentDescription = presentation.accessibility
            }
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            if (status == LocationStatus.Searching || status == LocationStatus.Unknown) {
                CircularProgressIndicator(
                    color = presentation.color,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
            } else {
                Icon(presentation.icon, contentDescription = null, tint = presentation.color, modifier = Modifier.size(18.dp))
            }
            Text(presentation.label, color = Color.White, style = MaterialTheme.typography.labelLarge)
        }
    }
}

private data class ChipPresentation(
    val label: String,
    val accessibility: String,
    val icon: ImageVector,
    val color: Color
)

private fun presentation(status: LocationStatus, isPrecise: Boolean, unit: DistanceUnit): ChipPresentation {
    fun accuracy(value: Float?) = value?.let { " ±" + UnitConverter.formatDistance(it.toDouble(), unit) }.orEmpty()
    val approx = if (isPrecise) "" else " · approx."
    return when (status) {
        LocationStatus.Unknown -> ChipPresentation("GPS…", "Checking GPS", Icons.Filled.GpsNotFixed, GpsColors.Neutral)
        LocationStatus.PermissionRequired -> ChipPresentation(
            "GPS not attached", "Location permission off. GPS data will not be attached. Tap to grant.",
            Icons.Filled.LocationOff, GpsColors.Off
        )
        LocationStatus.Disabled -> ChipPresentation(
            "Location off", "Location is turned off. Tap to open location settings.",
            Icons.Filled.LocationDisabled, GpsColors.Off
        )
        LocationStatus.Searching -> ChipPresentation(
            "Searching…", "Searching for GPS. Tap to retry.", Icons.Filled.GpsNotFixed, GpsColors.Warning
        )
        is LocationStatus.LowAccuracy -> ChipPresentation(
            "Low accuracy" + accuracy(status.accuracyM) + approx,
            "Low GPS accuracy" + accuracy(status.accuracyM) + ". Tap to retry.",
            Icons.Filled.GpsNotFixed, GpsColors.Warning
        )
        is LocationStatus.Ready -> ChipPresentation(
            "GPS Ready" + accuracy(status.accuracyM) + approx,
            "GPS ready" + accuracy(status.accuracyM),
            Icons.Filled.GpsFixed, GpsColors.Ready
        )
    }
}

@Preview
@Composable
private fun GpsStatusChipPreview() {
    GpsStatusChip(LocationStatus.Ready(7f), isPrecise = true, distanceUnit = DistanceUnit.METRIC, onClick = {})
}
