// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.tools_dns_exit_info
import app.skipi.ui.resources.tools_dns_isp_unknown
import app.skipi.ui.resources.tools_dns_observed_egress
import app.skipi.ui.resources.tools_dns_observed_subnet
import app.skipi.ui.resources.tools_dns_server_public
import app.skipi.ui.resources.tools_dns_server_system
import app.skipi.ui.resources.tools_dns_start
import app.skipi.ui.resources.tools_dns_stop
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

data class DnsLeakResolverUi(
    val server: String,
    val isSystemServer: Boolean,
    val observedIp: String?,
    val clientSubnetIp: String?,
    val observedCountryCode: String,
    val isp: String,
)

@Composable
fun SkipiDnsLeakSummary(
    description: String,
    statusText: String,
    detailText: String?,
    isError: Boolean,
    isRunning: Boolean,
    exitIp: String?,
    exitCountry: String?,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(description, fontSize = 13.sp, lineHeight = 18.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.size(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val color = when {
                    isError -> MiuixTheme.colorScheme.error
                    isRunning -> MiuixTheme.colorScheme.primary
                    else -> MiuixTheme.colorScheme.primary
                }
                Box(Modifier.size(10.dp).clip(CircleShape).background(color))
                Spacer(Modifier.size(8.dp))
                Text(statusText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            if (!detailText.isNullOrBlank()) {
                Text(detailText, Modifier.padding(top = 4.dp), fontSize = 12.sp, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (!exitIp.isNullOrBlank()) {
                Text(
                    stringResource(Res.string.tools_dns_exit_info, exitIp, exitCountry.orEmpty()),
                    Modifier.padding(top = 6.dp),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            TextButton(
                text = stringResource(if (isRunning) Res.string.tools_dns_stop else Res.string.tools_dns_start),
                onClick = { if (isRunning) onStop() else onStart() },
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
fun SkipiDnsLeakResolverCard(resolver: DnsLeakResolverUi, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(countryFlagEmoji(resolver.observedCountryCode), fontSize = 22.sp)
            Column {
                Text(
                    resolver.isp.ifBlank { stringResource(Res.string.tools_dns_isp_unknown) },
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                )
                val serverLabel = stringResource(
                    if (resolver.isSystemServer) Res.string.tools_dns_server_system else Res.string.tools_dns_server_public,
                ) + " ${resolver.server}"
                val observedLabel = resolver.observedIp?.let { ip ->
                    stringResource(
                        if (resolver.clientSubnetIp != null) Res.string.tools_dns_observed_subnet else Res.string.tools_dns_observed_egress,
                        ip,
                    )
                }
                Text(
                    listOfNotNull(serverLabel, observedLabel).joinToString(" · "),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

private fun countryFlagEmoji(countryCode: String): String {
    val code = countryCode.trim().uppercase()
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return "🌐"
    val first = 0x1F1E6 + (code[0] - 'A')
    val second = 0x1F1E6 + (code[1] - 'A')
    fun regionalIndicator(codePoint: Int): String {
        val offset = codePoint - 0x10000
        return charArrayOf(
            ((offset / 0x400) + 0xD800).toChar(),
            ((offset % 0x400) + 0xDC00).toChar(),
        ).concatToString()
    }
    return regionalIndicator(first) + regionalIndicator(second)
}
