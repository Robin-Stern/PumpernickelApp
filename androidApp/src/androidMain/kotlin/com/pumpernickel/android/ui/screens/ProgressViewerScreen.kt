package com.pumpernickel.android.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.domain.progresspic.PhotoVault
import com.pumpernickel.presentation.progresspic.ProgressViewerViewModel
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Full-screen photo viewer for one workout's photos.
 *
 * The Viewer VM holds the explicit `unlockedWorkoutId: Long?` gate
 * (T-BIOMETRIC-BYPASS). On first composition the screen calls
 * `viewModel.requestUnlock()` which fires the OS auth prompt. While
 * `unlockedWorkoutId == null` the screen shows a "locked" placeholder.
 * On Success the photos render un-blurred. On Cancelled/Failed the
 * placeholder stays and the user can tap Back to leave.
 *
 * On dispose (popBackStack), the screen calls `viewModel.relock()` so the
 * tile re-blurs in the gallery grid (D-17-13 / D-17-14).
 *
 * Inside the viewer, paging across photos does NOT re-prompt — D-17-14.
 * The pager swiping is local UI state; auth state is per-tile, not per-photo.
 */
@Composable
fun ProgressViewerScreen(
    workoutId: Long,
    navController: NavHostController,
    viewModel: ProgressViewerViewModel = koinViewModel { parametersOf(workoutId) }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        // First-composition auth — D-17-14 per-tile every-tap.
        viewModel.requestUnlock()
    }

    DisposableEffect(Unit) {
        onDispose {
            // Re-lock when the screen leaves composition (popBackStack, system
            // back, etc.) — D-17-13 / D-17-14.
            viewModel.relock()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            uiState.busy && uiState.unlockedWorkoutId == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
            uiState.unlockedWorkoutId == null -> {
                LockedPlaceholder(
                    onCloseClick = { navController.popBackStack() },
                    onRetryClick = { viewModel.requestUnlock() }
                )
            }
            uiState.photos.isEmpty() -> {
                EmptyState(onCloseClick = { navController.popBackStack() })
            }
            else -> {
                val pagerState = rememberPagerState(pageCount = { uiState.photos.size })
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxSize()
                ) { page ->
                    val photo = uiState.photos[page]
                    val photoVault: PhotoVault = koinInject()
                    val bytes by produceState<ByteArray?>(
                        initialValue = null,
                        key1 = photo.relativePath
                    ) { value = photoVault.read(photo.relativePath) }
                    // REVIEW M-08: decode at full edge (1600px is the storage
                    // target) but recycle the bitmap when this page leaves
                    // composition. Without recycling, paging through N photos
                    // leaves N decoded bitmaps in the heap waiting for GC.
                    val decodedBitmap = remember(bytes) {
                        bytes?.let { decodeDownsampledViewer(it, targetEdgePx = 1600) }
                    }
                    val imageBitmap = remember(decodedBitmap) { decodedBitmap?.asImageBitmap() }
                    DisposableEffect(decodedBitmap) {
                        onDispose { decodedBitmap?.recycle() }
                    }
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (imageBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = imageBitmap,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                }

                // Top bar overlay — close button + page indicator + delete.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                ) {
                    IconButton(
                        onClick = { navController.popBackStack() },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                    if (uiState.photos.size > 1) {
                        Text(
                            text = "${pagerState.currentPage + 1} / ${uiState.photos.size}",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                    val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage)
                    if (currentPhoto != null) {
                        IconButton(
                            onClick = { viewModel.deletePhoto(currentPhoto) },
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Delete photo",
                                tint = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LockedPlaceholder(
    onCloseClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Tippe, um zu entsperren",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Text(
                text = "Authentifizierung erforderlich.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f)
            )
            TextButton(onClick = onRetryClick) {
                Text(text = "Erneut versuchen", color = Color.White)
            }
        }
        IconButton(
            onClick = onCloseClick,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 12.dp, start = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun EmptyState(onCloseClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Keine Fotos für dieses Workout.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            TextButton(onClick = onCloseClick) {
                Text(text = "Schließen", color = Color.White)
            }
        }
    }
}

/**
 * Two-pass decode that uses [BitmapFactory.Options.inSampleSize] so the
 * resulting Bitmap's long edge is no smaller than [targetEdgePx]. Power-of-two
 * sample size halves per step — REVIEW M-08. Stored photos are written at
 * 1600px long edge (D-17-07), so for the viewer we mostly fall through with
 * sample=1; the helper still protects the heap if the source happens to be
 * larger (e.g. legacy / pre-resize captures).
 */
private fun decodeDownsampledViewer(bytes: ByteArray, targetEdgePx: Int): Bitmap? {
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
