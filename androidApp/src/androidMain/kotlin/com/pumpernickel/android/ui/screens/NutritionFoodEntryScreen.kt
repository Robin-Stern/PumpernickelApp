package com.pumpernickel.android.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.components.PrimaryActionButton
import com.pumpernickel.android.ui.components.SectionCard
import com.pumpernickel.android.ui.components.TonalActionButton
import com.pumpernickel.domain.model.Food
import com.pumpernickel.domain.model.FoodUnit
import com.pumpernickel.domain.nutrition.SearchFoodsRemoteUseCase
import com.pumpernickel.presentation.nutrition.FoodEntryEvent
import com.pumpernickel.presentation.nutrition.FoodEntryViewModel
import org.koin.compose.viewmodel.koinViewModel
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NutritionFoodEntryScreen(
    navController: NavController,
    viewModel: FoodEntryViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val filteredFoods by viewModel.filteredFoods.collectAsStateWithLifecycle()
    val isEditing = uiState.editingFoodId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (isEditing) R.string.title_food_edit else R.string.title_food_entry)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(Modifier.height(0.dp)) }

            // ── Lebensmittel ──
            item {
                SectionCard(title = stringResource(R.string.section_food)) {
                    OutlinedTextField(
                        value = uiState.name,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnNameChanged(it)) },
                        label = { Text(stringResource(R.string.label_name)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            // ── Nährwerte pro 100 g/ml ──
            item {
                SectionCard(title = stringResource(R.string.section_nutrition_per_100)) {
                    OutlinedTextField(
                        value = uiState.calories,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnCaloriesChanged(it)) },
                        label = { Text(stringResource(R.string.label_calories)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.protein,
                            onValueChange = { viewModel.onEvent(FoodEntryEvent.OnProteinChanged(it)) },
                            label = { Text(stringResource(R.string.label_protein)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = uiState.fat,
                            onValueChange = { viewModel.onEvent(FoodEntryEvent.OnFatChanged(it)) },
                            label = { Text(stringResource(R.string.label_fat)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = uiState.carbs,
                            onValueChange = { viewModel.onEvent(FoodEntryEvent.OnCarbsChanged(it)) },
                            label = { Text(stringResource(R.string.label_carbs)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                        OutlinedTextField(
                            value = uiState.sugar,
                            onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSugarChanged(it)) },
                            label = { Text(stringResource(R.string.label_sugar)) },
                            modifier = Modifier.weight(1f), singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                        )
                    }
                }
            }

            // ── Einheit ──
            item {
                SectionCard(title = stringResource(R.string.section_unit)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        FoodUnit.entries.forEachIndexed { index, unit ->
                            SegmentedButton(
                                selected = uiState.unit == unit,
                                onClick = { viewModel.onEvent(FoodEntryEvent.OnUnitChanged(unit)) },
                                shape = SegmentedButtonDefaults.itemShape(index, FoodUnit.entries.size),
                                label = { Text(stringResource(if (unit == FoodUnit.GRAM) R.string.unit_gram else R.string.unit_ml)) }
                            )
                        }
                    }
                }
            }

            // ── Barcode + Actions ──
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        BarcodeScannerButton(
                            onBarcodeScanned = { viewModel.onEvent(FoodEntryEvent.OnBarcodeScanned(it)) },
                            modifier = Modifier.weight(1f)
                        )
                        if (uiState.isLookingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                    if (uiState.barcode.isNotEmpty()) {
                        Text(
                            text = "Barcode: ${uiState.barcode}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    uiState.errorMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    uiState.successMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                    }
                    if (isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TonalActionButton(
                                text = stringResource(R.string.action_cancel),
                                onClick = { viewModel.onEvent(FoodEntryEvent.OnCancelEdit) },
                                modifier = Modifier.weight(1f)
                            )
                            PrimaryActionButton(
                                text = stringResource(R.string.action_update),
                                onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        PrimaryActionButton(
                            text = stringResource(R.string.action_save),
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnSaveClicked) }
                        )
                    }
                }
            }

            // ── Gespeicherte Lebensmittel ──
            item {
                SectionCard(title = stringResource(R.string.section_saved_foods)) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.onEvent(FoodEntryEvent.OnSearchQueryChanged(it)) },
                        label = { Text(stringResource(R.string.hint_search)) },
                        modifier = Modifier.fillMaxWidth(), singleLine = true
                    )
                }
            }

            if (filteredFoods.isEmpty()) {
                item {
                    Text(
                        stringResource(if (uiState.searchQuery.isBlank()) R.string.msg_no_foods else R.string.msg_no_foods_found),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                items(filteredFoods.reversed(), key = { it.id }) { food ->
                    FoodSwipeCard(
                        food = food,
                        onDelete = { viewModel.onEvent(FoodEntryEvent.OnFoodDeleted(food)) },
                        onEdit = { viewModel.onEvent(FoodEntryEvent.OnFoodSelected(food)) }
                    )
                }
            }

            if (uiState.searchQuery.length >= 3) {
                item {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "OpenFoodFacts",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (uiState.isSearchingRemote) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        }
                    }
                }

                uiState.remoteSearchError?.let { error ->
                    item {
                        Text(error, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (uiState.remoteSearchError == null && !uiState.isSearchingRemote && uiState.remoteSearchResults.isEmpty()) {
                    item {
                        Text("Keine Ergebnisse", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    items(uiState.remoteSearchResults, key = { "remote_${it.name}" }) { result ->
                        RemoteFoodCard(
                            result = result,
                            onClick = { viewModel.onEvent(FoodEntryEvent.OnRemoteFoodSelected(result)) }
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    uiState.pendingLogFood?.let { food ->
        LogAmountDialog(
            food = food,
            onConfirm = { amount -> viewModel.onEvent(FoodEntryEvent.OnConfirmLogAmount(food, amount)) },
            onDismiss = { viewModel.onEvent(FoodEntryEvent.OnDismissLogDialog) }
        )
    }
}

@Composable
private fun LogAmountDialog(food: Food, onConfirm: (Double) -> Unit, onDismiss: () -> Unit) {
    var amount by remember { mutableStateOf("100") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                amount.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }?.let(onConfirm)
            }) { Text(stringResource(R.string.action_log_entry)) }
        },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    label = { Text(stringResource(R.string.label_amount) + " (${food.unit.label})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                Text(
                    "pro 100 ${food.unit.label}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                MacroRow(
                    protein = food.protein,
                    fat = food.fat,
                    carbs = food.carbohydrates,
                    sugar = food.sugar
                )
                Text(
                    "${food.calories.roundToInt()} kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FoodSwipeCard(food: Food, onDelete: () -> Unit, onEdit: () -> Unit) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) showConfirmDialog = true
            false
        }
    )
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = { Text(stringResource(R.string.action_delete)) },
            text = { Text(stringResource(R.string.confirm_delete_food)) },
            confirmButton = {
                Button(
                    onClick = { showConfirmDialog = false; onDelete() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red, contentColor = Color.Black)
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
    SwipeToDismissBox(
        state = dismissState, enableDismissFromStartToEnd = false,
        backgroundContent = {
            Card(modifier = Modifier.fillMaxSize(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp), contentAlignment = Alignment.CenterEnd) {
                    Text(stringResource(R.string.action_delete), fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        Card(onClick = onEdit, modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    Text(food.name, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("${food.calories.roundToInt()} kcal/100${food.unit.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, softWrap = false, maxLines = 1)
                }
                MacroRow(protein = food.protein, fat = food.fat, carbs = food.carbohydrates, sugar = food.sugar)
            }
        }
    }
}

// ── Barcode Scanner ──

@Composable
fun BarcodeScannerButton(onBarcodeScanned: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var showScanner by remember { mutableStateOf(false) }
    var permissionDenied by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { showScanner = true; permissionDenied = false } else { permissionDenied = true }
    }

    Button(
        onClick = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                showScanner = true
            } else { permissionLauncher.launch(Manifest.permission.CAMERA) }
        },
        modifier = modifier
    ) { Text(stringResource(R.string.action_scan_barcode)) }

    if (permissionDenied) {
        Text(stringResource(R.string.msg_camera_permission_denied), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }

    if (showScanner) {
        BarcodeScannerDialog(
            onBarcodeDetected = { showScanner = false; onBarcodeScanned(it) },
            onDismiss = { showScanner = false }
        )
    }
}

@Composable
private fun BarcodeScannerDialog(onBarcodeDetected: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(onBarcodeDetected = onBarcodeDetected)
            ScannerOverlay()
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.scanner_hint),
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium
                )
                FilledTonalButton(onClick = onDismiss) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        }
    }
}

@Composable
private fun ScannerOverlay(modifier: Modifier = Modifier) {
    val viewfinderWidth = 280.dp
    val viewfinderHeight = 180.dp
    val cornerRadiusDp = 12.dp
    val cornerLength = 24.dp
    val cornerStrokeWidth = 4.dp
    val dimColor = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.55f)
    val borderColor = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.5f)
    val markerColor = androidx.compose.ui.graphics.Color.White

    Canvas(modifier = modifier.fillMaxSize()) {
        val vfW = viewfinderWidth.toPx()
        val vfH = viewfinderHeight.toPx()
        val l = (size.width - vfW) / 2f
        val t = (size.height - vfH) / 2f
        val cr = cornerRadiusDp.toPx()

        // Dim overlay with cutout
        val cutoutPath = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    left = l, top = t,
                    right = l + vfW, bottom = t + vfH,
                    radiusX = cr, radiusY = cr
                )
            )
        }
        clipPath(cutoutPath, clipOp = androidx.compose.ui.graphics.ClipOp.Difference) {
            drawRect(dimColor)
        }

        // White border around viewfinder
        drawRoundRect(
            color = borderColor,
            topLeft = androidx.compose.ui.geometry.Offset(l, t),
            size = androidx.compose.ui.geometry.Size(vfW, vfH),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(cr, cr),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx())
        )

        // L-shaped corner markers
        val cLen = cornerLength.toPx()
        val cStroke = cornerStrokeWidth.toPx()
        val cap = androidx.compose.ui.graphics.StrokeCap.Round
        fun o(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x, y)

        // Top-left
        drawLine(markerColor, o(l, t + cLen), o(l, t), strokeWidth = cStroke, cap = cap)
        drawLine(markerColor, o(l, t), o(l + cLen, t), strokeWidth = cStroke, cap = cap)
        // Top-right
        drawLine(markerColor, o(l + vfW - cLen, t), o(l + vfW, t), strokeWidth = cStroke, cap = cap)
        drawLine(markerColor, o(l + vfW, t), o(l + vfW, t + cLen), strokeWidth = cStroke, cap = cap)
        // Bottom-left
        drawLine(markerColor, o(l, t + vfH - cLen), o(l, t + vfH), strokeWidth = cStroke, cap = cap)
        drawLine(markerColor, o(l, t + vfH), o(l + cLen, t + vfH), strokeWidth = cStroke, cap = cap)
        // Bottom-right
        drawLine(markerColor, o(l + vfW - cLen, t + vfH), o(l + vfW, t + vfH), strokeWidth = cStroke, cap = cap)
        drawLine(markerColor, o(l + vfW, t + vfH - cLen), o(l + vfW, t + vfH), strokeWidth = cStroke, cap = cap)
    }
}

@Composable
private fun CameraPreview(onBarcodeDetected: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    var detected by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val scannerOptions = remember {
        BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8).build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    // Hoisted out of the AndroidView factory so the tap-to-focus pointerInput
    // and the haptic side-effect can reference them after composition.
    val previewView = remember { PreviewView(context) }
    var camera by remember { mutableStateOf<Camera?>(null) }

    DisposableEffect(Unit) {
        onDispose { scanner.close(); analysisExecutor.shutdown() }
    }

    // Mirror iOS BarcodeScannerView.swift:232 — vibrate once when the first
    // barcode is recognised. Driven off `detected` so the call lands on the
    // composition (main) thread rather than the analyzer executor.
    LaunchedEffect(detected) {
        if (detected) {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            // Mirror iOS BarcodeScannerView.swift:156-180 — tap anywhere on the
            // preview to refocus + re-meter exposure. PreviewView's metering
            // point factory handles the view→sensor coordinate transform.
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val cam = camera ?: return@detectTapGestures
                    val point = previewView.meteringPointFactory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(
                        point,
                        FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE
                    ).build()
                    runCatching { cam.cameraControl.startFocusAndMetering(action) }
                }
            }
    ) {
        AndroidView(
            factory = {
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build()
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        processImage(imageProxy, scanner) { barcode ->
                            if (!detected) { detected = true; onBarcodeDetected(barcode) }
                        }
                    }
                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                    } catch (_: Exception) {}
                }, ContextCompat.getMainExecutor(context))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
private fun RemoteFoodCard(result: SearchFoodsRemoteUseCase.RemoteFoodResult, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(result.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "${result.calories.roundToInt()} kcal · E ${result.protein.roundToInt()}g · F ${result.fat.roundToInt()}g · KH ${result.carbs.roundToInt()}g",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
private fun processImage(imageProxy: ImageProxy, scanner: com.google.mlkit.vision.barcode.BarcodeScanner, onBarcode: (String) -> Unit) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) { imageProxy.close(); return }
    val inputImage = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
    scanner.process(inputImage)
        .addOnSuccessListener { barcodes -> barcodes.firstOrNull()?.rawValue?.let(onBarcode) }
        .addOnCompleteListener { imageProxy.close() }
}
