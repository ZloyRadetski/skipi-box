// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package features.routing.ui

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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import ui.AppTheme
import ui.components.AppWindowBottomSheet
import ui.text.themedFontWeight

private data class RuleCategoryHelpItem(
    val name: String,
    val descriptionRes: Int,
    val example: String,
    val isAndroidLimitation: Boolean = false,
)

@Composable
fun RoutingRulesInfoBottomSheet(
    show: Boolean,
    onDismissRequest: () -> Unit,
) {
    val items = listOf(
        RuleCategoryHelpItem(
            name = "DOMAIN",
            descriptionRes = R.string.routing_info_desc_domain,
            example = "DOMAIN,example.com,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "DOMAIN-SUFFIX",
            descriptionRes = R.string.routing_info_desc_domain_suffix,
            example = "DOMAIN-SUFFIX,google.com,DIRECT",
        ),
        RuleCategoryHelpItem(
            name = "DOMAIN-KEYWORD",
            descriptionRes = R.string.routing_info_desc_domain_keyword,
            example = "DOMAIN-KEYWORD,twitter,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "DOMAIN-WILDCARD",
            descriptionRes = R.string.routing_info_desc_domain_wildcard,
            example = "DOMAIN-WILDCARD,*.youtube.com,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "GEOSITE / DOMAIN-SET",
            descriptionRes = R.string.routing_info_desc_geosite,
            example = "GEOSITE,youtube,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "IP-CIDR / IP-CIDR6",
            descriptionRes = R.string.routing_info_desc_ip_cidr,
            example = "IP-CIDR,192.168.1.0/24,DIRECT",
        ),
        RuleCategoryHelpItem(
            name = "GEOIP",
            descriptionRes = R.string.routing_info_desc_geoip,
            example = "GEOIP,ru,DIRECT",
        ),
        RuleCategoryHelpItem(
            name = "DST-PORT",
            descriptionRes = R.string.routing_info_desc_dst_port,
            example = "DST-PORT,443,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "NETWORK",
            descriptionRes = R.string.routing_info_desc_network,
            example = "NETWORK,udp,REJECT",
        ),
        RuleCategoryHelpItem(
            name = "PROTOCOL",
            descriptionRes = R.string.routing_info_desc_protocol,
            example = "PROTOCOL,bittorrent,REJECT",
        ),
        RuleCategoryHelpItem(
            name = "RULE-SET",
            descriptionRes = R.string.routing_info_desc_ruleset,
            example = "RULE-SET,geosite:openai,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "FINAL",
            descriptionRes = R.string.routing_info_desc_final,
            example = "FINAL,PROXY",
        ),
        RuleCategoryHelpItem(
            name = "PROCESS-NAME / USER-AGENT",
            descriptionRes = R.string.routing_info_desc_android_limitations,
            example = "",
            isAndroidLimitation = true,
        ),
    )

    AppWindowBottomSheet(
        show = show,
        title = stringResource(R.string.routing_info_title),
        endAction = {
            TextButton(
                text = stringResource(R.string.common_close),
                onClick = onDismissRequest,
            )
        },
        onDismissRequest = onDismissRequest,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(items, key = { it.name }) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.defaultColors(
                        color = if (item.isAndroidLimitation) {
                            AppTheme.colors.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            AppTheme.colors.surface
                        },
                    ),
                    cornerRadius = 12.dp,
                    insideMargin = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 14.sp,
                                fontWeight = themedFontWeight(FontWeight.Bold),
                                color = if (item.isAndroidLimitation) {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                                } else {
                                    MiuixTheme.colorScheme.primary
                                },
                            )
                            if (item.isAndroidLimitation) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        text = "Android",
                                        fontSize = 11.sp,
                                        fontWeight = themedFontWeight(FontWeight.Medium),
                                        color = MiuixTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = stringResource(item.descriptionRes),
                            fontSize = 13.sp,
                            lineHeight = 17.sp,
                            color = MiuixTheme.colorScheme.onSurface,
                        )
                        if (item.example.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(AppTheme.colors.surfaceVariant.copy(alpha = 0.6f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                            ) {
                                Text(
                                    text = item.example,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
