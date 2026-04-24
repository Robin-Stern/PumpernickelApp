package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.domain.model.MuscleGroup
import com.pumpernickel.presentation.exercises.CreateExerciseViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateExerciseScreen(navController: NavHostController) {
    val viewModel: CreateExerciseViewModel = koinViewModel()
    val name by viewModel.name.collectAsState()
    val selectedMuscleGroup by viewModel.selectedMuscleGroup.collectAsState()
    val selectedEquipment by viewModel.selectedEquipment.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val equipmentOptions by viewModel.equipmentOptions.collectAsState()
    val categoryOptions by viewModel.categoryOptions.collectAsState()
    val isFormValid by viewModel.isFormValid.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.saveResult.collect { result ->
            when (result) {
                is CreateExerciseViewModel.SaveResult.Success -> {
                    navController.popBackStack()
                }
                is CreateExerciseViewModel.SaveResult.Error -> {
                    snackbarHostState.showSnackbar(result.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Exercise") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            // Name field
            OutlinedTextField(
                value = name,
                onValueChange = { viewModel.onNameChanged(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                label = { Text("Exercise name") },
                singleLine = true
            )

            // Muscle group selector
            MuscleGroupDropdown(
                selectedMuscleGroup = selectedMuscleGroup,
                onMuscleGroupSelected = { viewModel.onMuscleGroupSelected(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Equipment picker
            OptionDropdown(
                label = "Equipment",
                selectedOption = selectedEquipment,
                options = equipmentOptions,
                onOptionSelected = { viewModel.onEquipmentSelected(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            // Category picker
            OptionDropdown(
                label = "Category",
                selectedOption = selectedCategory,
                options = categoryOptions,
                onOptionSelected = { viewModel.onCategorySelected(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp)
            )

            Spacer(modifier = Modifier.weight(1f))

            // Create button
            Button(
                onClick = { viewModel.createExercise() },
                enabled = isFormValid,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Create Exercise")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MuscleGroupDropdown(
    selectedMuscleGroup: MuscleGroup?,
    onMuscleGroupSelected: (MuscleGroup?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedMuscleGroup?.displayName ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text("Muscle group") },
            placeholder = { Text("Select muscle group") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            MuscleGroup.entries.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.displayName) },
                    onClick = {
                        onMuscleGroupSelected(group)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OptionDropdown(
    label: String,
    selectedOption: String?,
    options: List<String>,
    onOptionSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedOption?.replaceFirstChar { it.uppercase() } ?: "",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            placeholder = { Text("Select $label") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.replaceFirstChar { it.uppercase() }) },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}
