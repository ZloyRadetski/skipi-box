// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import app.skipi.ui.resources.Res
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme

@Composable
internal fun SubscriptionSummaryDetails(
    state: SkipiSubscriptionSummaryState,
    actions: SkipiSubscriptionSummaryActions,
    colors: SkipiSubscriptionSummaryColors,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.statusText.takeIf(String::isNotBlank)?.let { statusText ->
            Text(
                text = statusText,
                color = if (state.enabled) colors.enabled else colors.mutedText,
                style = SkipiTheme.typography.bodySmall,
                fontWeight = themedFontWeight(FontWeight.SemiBold),
            )
        }

        val trafficText = state.trafficText?.takeIf(String::isNotBlank)
        val expiryText = state.expiryText?.takeIf(String::isNotBlank)
        if (trafficText != null || expiryText != null) {
            // One quiet line instead of two stacked paragraphs.
            Text(
                text = listOfNotNull(trafficText, expiryText).joinToString("  ·  "),
                color = colors.mutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        state.trafficProgress?.let { progress ->
            SubscriptionTrafficProgress(progress = progress, colors = colors)
        }

        state.description?.takeIf(String::isNotBlank)?.let { description ->
            Text(
                description,
                color = colors.mutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }

        state.announcementText?.takeIf(String::isNotBlank)?.let { announcementText ->
            val announcementAction = actions.onAnnouncement
            Text(
                text = announcementText,
                color = if (announcementAction != null) colors.enabled else colors.mutedText,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = if (announcementAction == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = announcementAction)
                        .padding(vertical = 2.dp)
                },
            )
        }

        val supportLabel = state.supportLabel?.takeIf(String::isNotBlank)
        val siteLabel = state.siteLabel?.takeIf(String::isNotBlank)
        if ((supportLabel != null && actions.onSupport != null) || (siteLabel != null && actions.onSite != null)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (supportLabel != null && actions.onSupport != null) {
                    SubscriptionMetadataLink(
                        label = supportLabel,
                        color = colors.text,
                        onClick = actions.onSupport,
                    )
                }
                if (siteLabel != null && actions.onSite != null) {
                    SubscriptionMetadataLink(
                        label = siteLabel,
                        color = colors.text,
                        onClick = actions.onSite,
                    )
                }
            }
        }

        state.updatedText?.takeIf(String::isNotBlank)?.let { updatedText ->
            Text(
                text = updatedText,
                color = colors.mutedText,
                style = SkipiTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SubscriptionTrafficProgress(
    progress: Float,
    colors: SkipiSubscriptionSummaryColors,
) {
    val clampedProgress = progress.coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(CircleShape)
            .background(colors.text.copy(alpha = 0.12f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(clampedProgress)
                .height(4.dp)
                .background(colors.text.copy(alpha = 0.70f)),
        )
    }
}

@Composable
private fun SubscriptionMetadataLink(
    label: String,
    color: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.08f))
            .clickable(role = Role.Button, onClick = onClick)
            .defaultMinSize(minHeight = 32.dp)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 12.sp,
            fontWeight = themedFontWeight(FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
