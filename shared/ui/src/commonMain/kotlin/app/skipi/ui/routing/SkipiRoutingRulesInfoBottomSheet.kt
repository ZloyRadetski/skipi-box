// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.routing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.skipi.ui.components.AppWindowBottomSheet
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.common_close
import app.skipi.ui.resources.routing_info_desc_android_limitations
import app.skipi.ui.resources.routing_info_desc_domain
import app.skipi.ui.resources.routing_info_desc_domain_keyword
import app.skipi.ui.resources.routing_info_desc_domain_suffix
import app.skipi.ui.resources.routing_info_desc_domain_wildcard
import app.skipi.ui.resources.routing_info_desc_dst_port
import app.skipi.ui.resources.routing_info_desc_final
import app.skipi.ui.resources.routing_info_desc_geoip
import app.skipi.ui.resources.routing_info_desc_geosite
import app.skipi.ui.resources.routing_info_desc_ip_cidr
import app.skipi.ui.resources.routing_info_desc_network
import app.skipi.ui.resources.routing_info_desc_protocol
import app.skipi.ui.resources.routing_info_desc_ruleset
import app.skipi.ui.resources.routing_info_title
import app.skipi.ui.theme.SkipiTheme
import app.skipi.ui.text.themedFontWeight
import org.jetbrains.compose.resources.stringResource
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class RuleCategoryHelpItem(
    val name: String,
    val description: org.jetbrains.compose.resources.StringResource,
    val example: String,
    val isAndroidLimitation: Boolean = false,
)

@Composable
fun SkipiRoutingRulesInfoBottomSheet(show: Boolean, onDismissRequest: () -> Unit) {
    val items = listOf(
        RuleCategoryHelpItem("DOMAIN", Res.string.routing_info_desc_domain, "DOMAIN,example.com,PROXY"),
        RuleCategoryHelpItem("DOMAIN-SUFFIX", Res.string.routing_info_desc_domain_suffix, "DOMAIN-SUFFIX,google.com,DIRECT"),
        RuleCategoryHelpItem("DOMAIN-KEYWORD", Res.string.routing_info_desc_domain_keyword, "DOMAIN-KEYWORD,twitter,PROXY"),
        RuleCategoryHelpItem("DOMAIN-WILDCARD", Res.string.routing_info_desc_domain_wildcard, "DOMAIN-WILDCARD,*.youtube.com,PROXY"),
        RuleCategoryHelpItem("GEOSITE / DOMAIN-SET", Res.string.routing_info_desc_geosite, "GEOSITE,youtube,PROXY"),
        RuleCategoryHelpItem("IP-CIDR / IP-CIDR6", Res.string.routing_info_desc_ip_cidr, "IP-CIDR,192.168.1.0/24,DIRECT"),
        RuleCategoryHelpItem("GEOIP", Res.string.routing_info_desc_geoip, "GEOIP,ru,DIRECT"),
        RuleCategoryHelpItem("DST-PORT", Res.string.routing_info_desc_dst_port, "DST-PORT,443,PROXY"),
        RuleCategoryHelpItem("NETWORK", Res.string.routing_info_desc_network, "NETWORK,udp,REJECT"),
        RuleCategoryHelpItem("PROTOCOL", Res.string.routing_info_desc_protocol, "PROTOCOL,bittorrent,REJECT"),
        RuleCategoryHelpItem("RULE-SET", Res.string.routing_info_desc_ruleset, "RULE-SET,geosite:openai,PROXY"),
        RuleCategoryHelpItem("FINAL", Res.string.routing_info_desc_final, "FINAL,PROXY"),
        RuleCategoryHelpItem("PROCESS-NAME / USER-AGENT", Res.string.routing_info_desc_android_limitations, "", true),
    )

    AppWindowBottomSheet(
        show = show,
        title = stringResource(Res.string.routing_info_title),
        endAction = { TextButton(text = stringResource(Res.string.common_close), onClick = onDismissRequest) },
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.name }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = if (item.isAndroidLimitation) SkipiTheme.colors.surfaceVariant.copy(alpha = 0.5f)
                        else SkipiTheme.colors.surface,
                    ),
                    cornerRadius = 12.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = item.name,
                                fontSize = 14.sp,
                                fontWeight = themedFontWeight(FontWeight.Bold),
                                color = if (item.isAndroidLimitation) MiuixTheme.colorScheme.onSurfaceVariantSummary
                                else MiuixTheme.colorScheme.primary,
                            )
                            if (item.isAndroidLimitation) {
                                Box(
                                    modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text("Android", fontSize = 11.sp, fontWeight = themedFontWeight(FontWeight.Medium), color = MiuixTheme.colorScheme.primary)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(item.description), fontSize = 13.sp, lineHeight = 17.sp, color = MiuixTheme.colorScheme.onSurface)
                        if (item.example.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(6.dp))
                                    .background(SkipiTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(item.example, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                            }
                        }
                    }
                }
            }
        }
    }
}
