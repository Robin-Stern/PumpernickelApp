package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pumpernickel.domain.model.WeightUnit
import com.pumpernickel.presentation.settings.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(onDismiss: () -> Unit) {
    val viewModel: SettingsViewModel = koinViewModel()
    val weightUnit by viewModel.weightUnit.collectAsState()

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Weight Unit",
                style = MaterialTheme.typography.labelLarge
            )
            Spacer(modifier = Modifier.height(8.dp))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = weightUnit == WeightUnit.KG,
                    onClick = { viewModel.setWeightUnit(WeightUnit.KG) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    label = { Text("kg") }
                )
                SegmentedButton(
                    selected = weightUnit == WeightUnit.LBS,
                    onClick = { viewModel.setWeightUnit(WeightUnit.LBS) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    label = { Text("lbs") }
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
