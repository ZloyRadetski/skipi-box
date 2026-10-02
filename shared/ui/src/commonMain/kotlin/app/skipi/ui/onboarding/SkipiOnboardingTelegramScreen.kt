// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.verticalScroll
import app.skipi.ui.resources.Res
import app.skipi.ui.resources.onboarding_telegram_copied_link
import app.skipi.ui.resources.onboarding_telegram_feature_chat_desc
import app.skipi.ui.resources.onboarding_telegram_feature_chat_title
import app.skipi.ui.resources.onboarding_telegram_feature_news_desc
import app.skipi.ui.resources.onboarding_telegram_feature_news_title
import app.skipi.ui.resources.onboarding_telegram_feature_releases_desc
import app.skipi.ui.resources.onboarding_telegram_feature_releases_title
import app.skipi.ui.resources.onboarding_telegram_open_button
import app.skipi.ui.resources.onboarding_telegram_subtitle
import app.skipi.ui.resources.onboarding_telegram_title
import app.skipi.ui.text.themedFontWeight
import app.skipi.ui.theme.SkipiTheme
import org.jetbrains.compose.resources.stringResource

private const val TelegramChannelUrl = "https://t.me/skipi_public"

/** Shared presentation for the onboarding Telegram step. Platform actions stay callbacks. */
@Composable
fun SkipiOnboardingTelegramScreen(
    onOpenChannel: (String) -> Unit,
    onCopyChannelLink: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkipiTheme.colors
    val telegram = Color(0xFF2AABEE)
    val copiedMessage = stringResource(Res.string.onboarding_telegram_copied_link)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        listOf(
                            telegram.copy(alpha = if (colors.isDark) 0.35f else 0.20f),
                            telegram.copy(alpha = if (colors.isDark) 0.10f else 0.05f),
                            Color.Transparent,
                        ),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                telegram.copy(alpha = if (colors.isDark) 0.30f else 0.18f),
                                telegram.copy(alpha = if (colors.isDark) 0.15f else 0.08f),
                            ),
                        ),
                    )
                    .border(
                        2.dp,
                        Brush.linearGradient(listOf(telegram, telegram.copy(alpha = 0.5f))),
                        CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("✈️", fontSize = 32.sp)
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = stringResource(Res.string.onboarding_telegram_title),
            fontSize = 24.sp,
            fontWeight = themedFontWeight(FontWeight.Bold),
            color = colors.onSurface,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboarding_telegram_subtitle),
            fontSize = 14.sp,
            color = colors.onSurfaceVariant,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(0.9f),
        )
        Spacer(Modifier.height(24.dp))

        val cardShape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(if (colors.isDark) Color(0xFF18191E) else Color.White)
                .border(1.dp, if (colors.isDark) Color(0xFF282932) else Color(0xFFE5E7EB), cardShape)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            TelegramFeature(
                emoji = "🚀",
                title = stringResource(Res.string.onboarding_telegram_feature_releases_title),
                description = stringResource(Res.string.onboarding_telegram_feature_releases_desc),
            )
            TelegramFeature(
                emoji = "💬",
                title = stringResource(Res.string.onboarding_telegram_feature_chat_title),
                description = stringResource(Res.string.onboarding_telegram_feature_chat_desc),
            )
            TelegramFeature(
                emoji = "📢",
                title = stringResource(Res.string.onboarding_telegram_feature_news_title),
                description = stringResource(Res.string.onboarding_telegram_feature_news_desc),
            )
        }

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(telegram)
                .clickable { onOpenChannel(TelegramChannelUrl) }
                .padding(vertical = 15.dp, horizontal = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✈️  ${stringResource(Res.string.onboarding_telegram_open_button)}",
                color = Color.White,
                fontWeight = themedFontWeight(FontWeight.Bold),
                fontSize = 15.sp,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = "@skipi_public",
            color = telegram,
            fontWeight = themedFontWeight(FontWeight.SemiBold),
            fontSize = 13.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable { onCopyChannelLink(TelegramChannelUrl, copiedMessage) }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun TelegramFeature(
    emoji: String,
    title: String,
    description: String,
) {
    val telegram = Color(0xFF2AABEE)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(telegram.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = emoji, fontSize = 18.sp)
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = themedFontWeight(FontWeight.Bold),
                color = SkipiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = SkipiTheme.colors.onSurfaceVariant,
                lineHeight = 16.sp,
            )
        }
    }
}
