package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

private data class TutorialPage(val title: String, val body: String, val emoji: String)

private val pages = listOf(
    TutorialPage(
        title = "Willkommen bei Pumpernickel",
        body = "Dein persönlicher Fitness-Begleiter für strukturiertes Krafttraining.",
        emoji = "💪"
    ),
    TutorialPage(
        title = "Training starten",
        body = "Wähle eine Vorlage im Training-Tab und logge Satz für Satz dein Gewicht und deine Wiederholungen.",
        emoji = "🏋️"
    ),
    TutorialPage(
        title = "Wischen zum Löschen",
        body = "Wische Einträge nach links zum Löschen — in Templates, Rezepten und Tagebuch.",
        emoji = "👈"
    ),
    TutorialPage(
        title = "Wischen zum Favorisieren",
        body = "Wische ein Rezept nach rechts, um es als Favorit zu markieren.",
        emoji = "⭐"
    ),
    TutorialPage(
        title = "Lebensmittel verwalten",
        body = "Im Ernährung-Tab kannst du Lebensmittel mit Nährwerten anlegen und deinem Tagesprotokoll hinzufügen.",
        emoji = "🥦"
    ),
    TutorialPage(
        title = "Rezepte erstellen",
        body = "Kombiniere mehrere Lebensmittel zu einem Rezept — die Makros werden automatisch berechnet.",
        emoji = "📖"
    ),
    TutorialPage(
        title = "Barcode scannen",
        body = "Beim Erfassen eines Lebensmittels kannst du den Barcode scannen, um Nährwerte automatisch zu laden.",
        emoji = "📷"
    ),
    TutorialPage(
        title = "Los geht's!",
        body = "Du kannst dieses Tutorial jederzeit in den Einstellungen erneut aufrufen.",
        emoji = "🚀"
    )
)

@Composable
fun TutorialOverlay(onFinished: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { index ->
            val page = pages[index]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = page.emoji, style = MaterialTheme.typography.displayLarge)
                Spacer(Modifier.height(32.dp))
                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp, start = 32.dp, end = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { index ->
                    Box(
                        modifier = Modifier
                            .size(if (pagerState.currentPage == index) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (pagerState.currentPage == index)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            if (isLastPage) {
                Button(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("Jetzt starten")
                }
            } else {
                Button(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Weiter")
                }
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onFinished, modifier = Modifier.fillMaxWidth()) {
                    Text("Überspringen")
                }
            }
        }
    }
}
