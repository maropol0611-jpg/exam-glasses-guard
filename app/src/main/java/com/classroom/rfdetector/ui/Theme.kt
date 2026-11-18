package com.classroom.rfdetector.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.LineBreak
import com.classroom.rfdetector.core.DistanceClass

/**
 * 통일된 다크 팔레트. 교실 스윕 도구답게 은은하고 전문적인 톤을 유지하고,
 * 시스템 테마와 무관하게 항상 다크로 강제한다.
 */
object RfColors {
    val bg = Color(0xFF0A1118)
    val surface = Color(0xFF121E29)
    val surfaceAlt = Color(0xFF203140)
    val primary = Color(0xFF62E0CB)
    val danger = Color(0xFFFF5252)
    val dangerDim = Color(0xFF2E161A)
    val warning = Color(0xFFFFA726)
    val ok = Color(0xFF66BB6A)
    val textPrimary = Color(0xFFE8EEF5)
    val textDim = Color(0xFFA2B3C3)
}

/** 거리 등급 -> 강조 색 */
fun classAccent(cls: DistanceClass): Color = when (cls) {
    DistanceClass.VERY_CLOSE -> RfColors.danger
    DistanceClass.CLOSE -> RfColors.warning
    DistanceClass.NEAR -> Color(0xFFFFD54F)
    DistanceClass.FAR -> RfColors.primary
    DistanceClass.UNKNOWN -> Color(0xFF6B7686)
}

@Composable
fun AppRfTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = RfColors.primary,
            onPrimary = Color(0xFF04121B),
            secondary = RfColors.primary,
            secondaryContainer = Color(0xFF1B403D),
            onSecondaryContainer = Color(0xFF9AF3E2),
            outline = Color(0xFF526779),
            background = RfColors.bg,
            onBackground = RfColors.textPrimary,
            surface = RfColors.surface,
            onSurface = RfColors.textPrimary,
            surfaceVariant = RfColors.surfaceAlt,
            surfaceContainerHigh = RfColors.surface,
            surfaceContainer = RfColors.surface,
            onSurfaceVariant = RfColors.textDim,
            error = RfColors.danger,
        ),
        typography = koreanTypography,
        content = content,
    )
}

/** 한글이 낱말 중간("기록합니/다")에서 끊기지 않게 어절 단위로 줄을 바꾼다. Android 14 이상에서 적용되고, 그 아래에서는 기존처럼 글자 단위로 끊긴다. */
private fun TextStyle.byPhrase() = copy(lineBreak = LineBreak(LineBreak.Strategy.Simple, LineBreak.Strictness.Normal, LineBreak.WordBreak.Phrase))

private val koreanTypography = Typography().run {
    copy(
        displayLarge = displayLarge.byPhrase(), displayMedium = displayMedium.byPhrase(), displaySmall = displaySmall.byPhrase(),
        headlineLarge = headlineLarge.byPhrase(), headlineMedium = headlineMedium.byPhrase(), headlineSmall = headlineSmall.byPhrase(),
        titleLarge = titleLarge.byPhrase(), titleMedium = titleMedium.byPhrase(), titleSmall = titleSmall.byPhrase(),
        bodyLarge = bodyLarge.byPhrase(), bodyMedium = bodyMedium.byPhrase(), bodySmall = bodySmall.byPhrase(),
        labelLarge = labelLarge.byPhrase(), labelMedium = labelMedium.byPhrase(), labelSmall = labelSmall.byPhrase(),
    )
}

