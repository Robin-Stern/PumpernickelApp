package com.example.pumpernickelapp.food.ui.entry

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
expect fun BarcodeScannerButton(onBarcodeScanned: (String) -> Unit, modifier: Modifier = Modifier)
