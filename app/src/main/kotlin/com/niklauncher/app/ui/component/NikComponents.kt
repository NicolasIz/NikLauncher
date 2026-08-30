package com.niklauncher.app.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Section heading used across the screens. */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The NikLauncher wordmark: the angular "N" from the app icon, rendered as a
 * gradient tile beside the name.
 */
@Composable
fun NikWordmark(modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.secondary,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "N",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onPrimary,
            )
        }
        Text(
            text = "NikLauncher",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** Compact status strip; [tone] carries whether the state is good or blocking. */
enum class StatusTone { NEUTRAL, POSITIVE, WARNING }

@Composable
fun StatusCard(
    icon: ImageVector,
    title: String,
    body: String,
    tone: StatusTone = StatusTone.NEUTRAL,
    modifier: Modifier = Modifier,
) {
    val container = when (tone) {
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant
        StatusTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.secondaryContainer
    }
    val content = when (tone) {
        StatusTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
        StatusTone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
        StatusTone.WARNING -> MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = container,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(22.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = content)
                Text(
                    body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = content,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
