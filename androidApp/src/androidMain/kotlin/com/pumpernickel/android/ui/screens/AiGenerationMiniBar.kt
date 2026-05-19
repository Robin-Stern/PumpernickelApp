package com.pumpernickel.android.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.ai.AiGenerationManager
import com.pumpernickel.domain.ai.AiGenerationState
import com.pumpernickel.domain.ai.AiType
import org.koin.compose.koinInject

@Composable
fun AiGenerationMiniBar(
    onTap: (AiType) -> Unit,
    generationManager: AiGenerationManager = koinInject()
) {
    val genState by generationManager.state.collectAsState()

    val visible = genState is AiGenerationState.Generating || genState is AiGenerationState.Success
    val activeType: AiType? = when (val s = genState) {
        is AiGenerationState.Generating -> s.type
        is AiGenerationState.Success -> s.type
        else -> null
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically { it } + fadeOut(animationSpec = tween(300))
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(enabled = activeType != null) { activeType?.let(onTap) },
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 0.dp
        ) {
            when (val s = genState) {
                is AiGenerationState.Generating -> MiniBarGenerating(s.type)
                is AiGenerationState.Success -> MiniBarSuccess(s.type)
                else -> {}
            }
        }
    }
}

@Composable
private fun MiniBarGenerating(type: AiType) {
    val label = if (type == AiType.WORKOUT) "KI generiert Workout…" else "KI generiert Mahlzeit…"
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = "KI-Generierung läuft" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        TypingIndicatorDots()
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MiniBarSuccess(type: AiType) {
    val label = if (type == AiType.WORKOUT) "Workout bereit — tippen zum Ansehen"
                else "Mahlzeit bereit — tippen zum Ansehen"
    val a11yDesc = if (type == AiType.WORKOUT) "Workout bereit, tippen zum Ansehen"
                   else "Mahlzeit bereit, tippen zum Ansehen"
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .semantics { contentDescription = a11yDesc },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun TypingIndicatorDots(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val alphas = (0..2).map { index ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 1200
                    0.3f at 0
                    1.0f at (200 + index * 200)
                    0.3f at (600 + index * 200)
                    0.3f at 1200
                },
                repeatMode = RepeatMode.Restart
            ),
            label = "dot_$index"
        )
    }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        alphas.forEach { alphaState ->
            val alphaValue by alphaState
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alphaValue),
                        shape = CircleShape
                    )
            )
        }
    }
}
