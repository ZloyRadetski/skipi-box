// Copyright 2026, Radetski
// SPDX-License-Identifier: GPL-3.0

package app.skipi.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import top.yukonga.miuix.kmp.basic.Text

private const val TelegramChannelUrl = "https://t.me/skipi_public"
private val TelegramBlue = Color(0xFF2AABEE)

/** Shared presentation for the onboarding Telegram step. Platform actions stay callbacks. */
@Composable
fun SkipiOnboardingTelegramScreen(
    onOpenChannel: (String) -> Unit,
    onCopyChannelLink: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SkipiTheme.colors
    val copiedMessage = stringResource(Res.string.onboarding_telegram_copied_link)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        OnboardingHeroCard(
            title = stringResource(Res.string.onboarding_telegram_title),
            subtitle = stringResource(Res.string.onboarding_telegram_subtitle),
            tint = TelegramBlue,
        ) {
            OnboardingIconTile(TelegramBlue) { PaperPlane(Color.White, Modifier.size(30.dp)) }
        }

        Spacer(Modifier.height(24.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(colors.surface)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            TelegramFeature(
                title = stringResource(Res.string.onboarding_telegram_feature_releases_title),
                description = stringResource(Res.string.onboarding_telegram_feature_releases_desc),
            )
            TelegramFeature(
                title = stringResource(Res.string.onboarding_telegram_feature_chat_title),
                description = stringResource(Res.string.onboarding_telegram_feature_chat_desc),
            )
            TelegramFeature(
                title = stringResource(Res.string.onboarding_telegram_feature_news_title),
                description = stringResource(Res.string.onboarding_telegram_feature_news_desc),
            )
        }

        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(CircleShape)
                .background(TelegramBlue)
                .clickable { onOpenChannel(TelegramChannelUrl) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(Res.string.onboarding_telegram_open_button),
                color = Color.White,
                fontWeight = themedFontWeight(FontWeight.Bold),
                fontSize = 17.sp,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "@skipi_public",
            color = colors.onSurfaceVariant,
            fontWeight = themedFontWeight(FontWeight.Medium),
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .clip(RoundedCornerShape(14.dp))
                .clickable { onCopyChannelLink(TelegramChannelUrl, copiedMessage) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Spacer(Modifier.height(8.dp))
    }
}

/** A feature line marked by a Telegram-blue bar the height of its text. */
@Composable
private fun TelegramFeature(title: String, description: String) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(CircleShape).background(TelegramBlue))
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 17.sp,
                fontWeight = themedFontWeight(FontWeight.Bold),
                color = SkipiTheme.colors.onSurface,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                description,
                fontSize = 14.sp,
                color = SkipiTheme.colors.onSurfaceVariant,
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun PaperPlane(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawPlane(color) }
}

private fun DrawScope.drawPlane(color: Color) {
    val w = size.width
    val h = size.height
    val body = Path().apply {
        moveTo(w * 0.96f, h * 0.08f)
        lineTo(w * 0.04f, h * 0.46f)
        lineTo(w * 0.30f, h * 0.58f)
        lineTo(w * 0.40f, h * 0.90f)
        lineTo(w * 0.56f, h * 0.68f)
        lineTo(w * 0.78f, h * 0.88f)
        close()
    }
    drawPath(body, color)
}
