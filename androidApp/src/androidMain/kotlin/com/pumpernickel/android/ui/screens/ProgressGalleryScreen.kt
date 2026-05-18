package com.pumpernickel.android.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.ui.navigation.ProgressViewerRoute
import com.pumpernickel.domain.progresspic.ProgressGalleryTile
import com.pumpernickel.infrastructure.progresspic.PhotoVault
import com.pumpernickel.presentation.progresspic.NavEvent
import com.pumpernickel.presentation.progresspic.ProgressGalleryViewModel
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

/**
 * Gallery surface for progress photos (D-17-10 / D-17-11 / D-17-12 / D-17-13).
 *
 * One tile per workout that has at least one photo. Each tile renders the
 * cover photo blurred (24dp) so faces are unrecognizable but tone/colour
 * stays. Caption strip overlays the bottom of the tile with date + workout
 * name + volume (kg), optional PR count, optional Goal-day chip — empty
 * stats are dropped (D-17-12 final paragraph).
 *
 * Tap → emits NavEvent.OpenViewer through the VM → LaunchedEffect collector
 * navigates to ProgressViewerRoute(workoutId). Auth is fired by the viewer
 * VM on first composition (T-BIOMETRIC-BYPASS — single explicit gate).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressGalleryScreen(
    navController: NavHostController,
    viewModel: ProgressGalleryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.navEvents.collect { event ->
            when (event) {
                is NavEvent.OpenViewer ->
                    navController.navigate(ProgressViewerRoute(workoutId = event.workoutId))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fortschritt", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.tiles.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Noch keine Fortschrittsfotos. Schließe ein Workout ab und füge ein Foto hinzu.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp)
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize().padding(padding)
            ) {
                items(uiState.tiles, key = { it.workoutId }) { tile ->
                    GalleryTile(
                        tile = tile,
                        onClick = { viewModel.onTileTapped(tile.workoutId) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GalleryTile(
    tile: ProgressGalleryTile,
    onClick: () -> Unit
) {
    val photoVault: PhotoVault = koinInject()
    val bytes by produceState<ByteArray?>(initialValue = null, key1 = tile.coverRelativePath) {
        value = photoVault.read(tile.coverRelativePath)
    }
    // REVIEW M-08 + M-09: decode at a tile-appropriate resolution and recycle.
    //
    // M-09: `Modifier.blur` is a no-op on Android < API 31. minSdk on this
    // project is 26, so on Android 8/9/10/11 the gallery would silently render
    // *unblurred* photos in the grid — breaking D-17-13's "faces must be
    // unrecognisable" privacy contract. Fallback strategy: on API < 31, decode
    // at a tiny target resolution (32px long edge) and let
    // `ContentScale.Crop` upscale the pixels back to tile size — the resulting
    // chunky pixelation makes faces just as unrecognisable as a smooth blur,
    // and works on every API level. On API >= 31, decode at 400px and rely on
    // the genuine `Modifier.blur` for a smooth effect.
    val supportsRealBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val targetEdgePx = if (supportsRealBlur) 400 else 32
    val decodedBitmap = remember(bytes, targetEdgePx) {
        bytes?.let { decodeDownsampled(it, targetEdgePx) }
    }
    val imageBitmap = remember(decodedBitmap) { decodedBitmap?.asImageBitmap() }
    DisposableEffect(decodedBitmap) {
        onDispose { decodedBitmap?.recycle() }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp)),
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (imageBitmap != null) {
                // D-17-13 — faces must be unrecognisable. On API >= 31 the real
                // blur runs over a 400px source. On API < 31 the bitmap is
                // already 32px-sourced and gets pixelated by ContentScale.Crop
                // upscaling it to the tile — no real blur is needed because
                // the pixelation already obscures the photo (M-09).
                val blurModifier = if (supportsRealBlur) {
                    Modifier.fillMaxSize().blur(radius = 24.dp)
                } else {
                    Modifier.fillMaxSize()
                }
                androidx.compose.foundation.Image(
                    bitmap = imageBitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = blurModifier
                )
            }

            // Caption strip — D-17-12. Translucent gradient at bottom.
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.7f)
                            )
                        )
                    )
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "${formatGermanShortDate(tile.startTimeMillis)} • ${tile.workoutName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
                // D-17-12 — drop empty stats; never show "🏆 0".
                val stats = buildList {
                    add("${formatGermanThousand(tile.volumeKg)} kg")
                    if (tile.prCount > 0) add("🏆 ${tile.prCount}")
                }
                Text(
                    text = stats.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White
                )
                if (tile.isGoalDay) {
                    Text(
                        text = "🍎 Goal day",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun formatGermanShortDate(epochMillis: Long): String {
    val ldt = Instant.fromEpochMilliseconds(epochMillis)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    val months = listOf(
        "Jan.", "Feb.", "März", "Apr.", "Mai", "Juni",
        "Juli", "Aug.", "Sept.", "Okt.", "Nov.", "Dez."
    )
    return "${ldt.day}. ${months[ldt.month.ordinal]}"
}

/**
 * Two-pass decode that uses [BitmapFactory.Options.inSampleSize] so the
 * resulting Bitmap's long edge is no smaller than [targetEdgePx]. Power-of-two
 * sample size halves per step so we trade a tiny bit of precision for a
 * massive memory win — REVIEW M-08.
 */
private fun decodeDownsampled(bytes: ByteArray, targetEdgePx: Int): Bitmap? {
    val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
    val srcW = boundsOpts.outWidth
    val srcH = boundsOpts.outHeight
    if (srcW <= 0 || srcH <= 0) return null
    var sample = 1
    var longEdge = maxOf(srcW, srcH)
    while (longEdge / 2 >= targetEdgePx) {
        sample *= 2
        longEdge /= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
}

private fun formatGermanThousand(value: Long): String {
    // German thin-space thousands separator. Manual formatter so commonMain
    // doesn't need the JVM-only NumberFormat.
    val s = value.toString()
    val sb = StringBuilder()
    var count = 0
    for (i in s.length - 1 downTo 0) {
        sb.append(s[i])
        count++
        if (count % 3 == 0 && i != 0) sb.append(' ') // narrow no-break space
    }
    return sb.reverse().toString()
}
