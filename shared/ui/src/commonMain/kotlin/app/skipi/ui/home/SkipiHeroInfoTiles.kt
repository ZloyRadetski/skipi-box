// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.home_tile_ip
import app.skipi.ui.resources.home_tile_memory
import app.skipi.ui.resources.home_tile_ping
import app.skipi.ui.resources.home_tile_time
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Text

enum class SkipiHeroTileKind { Time, Ping, Memory, Ip }

/** Public address of the active tunnel with its country flag emoji, when known. */
data class SkipiHeroIp(val value: String, val flag: String?)

data class SkipiHeroTile(
    val kind: SkipiHeroTileKind,
    val value: String,
    val color: Color? = null,
    val flag: String? = null,
)

private const val UnknownValue = "—"

/**
 * Facts shown as tiles under the connection button. Only a live tunnel has them, so nothing is
 * shown otherwise; a missing value drops its tile, except the IP tile which shows a placeholder
 * until the address is known (when [showIp] is on).
 */
fun skipiHeroInfoTiles(
    phase: SkipiConnectionHeroPhase,
    sessionDurationText: String?,
    latency: SkipiProxyHeroLatency?,
    memoryText: String?,
    ip: SkipiHeroIp?,
    showIp: Boolean,
): List<SkipiHeroTile> {
    if (phase != SkipiConnectionHeroPhase.Connected) return emptyList()
    return buildList {
        sessionDurationText?.let { add(SkipiHeroTile(SkipiHeroTileKind.Time, it)) }
        latency?.let { add(SkipiHeroTile(SkipiHeroTileKind.Ping, it.text, color = it.color)) }
        memoryText?.let { add(SkipiHeroTile(SkipiHeroTileKind.Memory, it)) }
        if (showIp) add(SkipiHeroTile(SkipiHeroTileKind.Ip, ip?.value ?: UnknownValue, flag = ip?.flag))
    }
}

/** Two tiles per row; a lone last tile keeps half width so columns stay aligned. */
@Composable
fun SkipiHeroInfoTileGrid(
    tiles: List<SkipiHeroTile>,
    surface: Color,
    labelColor: Color,
    valueColor: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    if (tiles.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tiles.chunked(2).forEach { rowTiles ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowTiles.forEach { tile ->
                    SkipiHeroInfoTile(tile, surface, labelColor, valueColor, fontWeight, Modifier.weight(1f))
                }
                if (rowTiles.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SkipiHeroInfoTile(
    tile: SkipiHeroTile,
    surface: Color,
    labelColor: Color,
    valueColor: Color,
    fontWeight: FontWeight,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = when (tile.kind) {
                SkipiHeroTileKind.Time -> stringResource(Res.string.home_tile_time)
                SkipiHeroTileKind.Ping -> stringResource(Res.string.home_tile_ping)
                SkipiHeroTileKind.Memory -> stringResource(Res.string.home_tile_memory)
                SkipiHeroTileKind.Ip -> stringResource(Res.string.home_tile_ip)
            },
            fontSize = 11.sp,
            color = labelColor,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (tile.flag != null) {
                SkipiProxyServerFlagBadge(
                    flag = tile.flag,
                    containerColor = Color.Transparent,
                    size = 18.dp,
                    shapeRadius = 4.dp,
                )
                Spacer(Modifier.width(6.dp))
            }
            Text(
                text = tile.value,
                fontSize = 14.sp,
                fontWeight = fontWeight,
                color = tile.color ?: valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
