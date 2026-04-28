package com.pumpernickel.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.pumpernickel.android.R
import com.pumpernickel.android.ui.navigation.AchievementGalleryRoute
import com.pumpernickel.android.ui.navigation.TemplateEditorRoute
import com.pumpernickel.android.ui.navigation.WorkoutHistoryListRoute
import com.pumpernickel.android.ui.navigation.WorkoutSessionRoute
import com.pumpernickel.domain.model.WorkoutTemplate
import com.pumpernickel.presentation.templates.TemplateListViewModel
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TemplateListScreen(navController: NavHostController) {
    val viewModel: TemplateListViewModel = koinViewModel()
    val templates by viewModel.templates.collectAsState()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var templateToDelete by remember { mutableStateOf<WorkoutTemplate?>(null) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tab_workout)) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigate(WorkoutHistoryListRoute) }) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = "Workout History"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettingsSheet = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            if (templates.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { navController.navigate(TemplateEditorRoute(templateId = null)) },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Create Template",
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { innerPadding ->
        if (templates.isEmpty()) {
            WorkoutEmptyStateScreen(
                onCreateTemplate = {
                    navController.navigate(TemplateEditorRoute(templateId = null))
                }
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                items(templates, key = { it.id }) { template ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { dismissValue ->
                            if (dismissValue == SwipeToDismissBoxValue.EndToStart) {
                                templateToDelete = template
                                showDeleteDialog = true
                                false // Don't auto-dismiss — wait for confirmation
                            } else {
                                false
                            }
                        }
                    )

                    // Reset dismiss state after dialog dismissed without deleting
                    LaunchedEffect(showDeleteDialog) {
                        if (!showDeleteDialog && templateToDelete == null) {
                            dismissState.reset()
                        }
                    }

                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        enableDismissFromEndToStart = true,
                        backgroundContent = {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.error)
                                    .padding(end = 16.dp),
                                contentAlignment = Alignment.CenterEnd
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Template",
                                    tint = MaterialTheme.colorScheme.onError
                                )
                            }
                        }
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = template.name,
                                    fontWeight = FontWeight.SemiBold
                                )
                            },
                            supportingContent = {
                                val count = template.exercises.size
                                Text("$count exercise${if (count == 1) "" else "s"}")
                            },
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        navController.navigate(WorkoutSessionRoute(templateId = template.id))
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PlayArrow,
                                        contentDescription = "Start Workout",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    navController.navigate(TemplateEditorRoute(templateId = template.id))
                                }
                        )
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = {
                showDeleteDialog = false
                templateToDelete = null
            },
            title = { Text(stringResource(R.string.dialog_delete_template_title)) },
            text = { Text(stringResource(R.string.dialog_delete_template_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        templateToDelete?.let { viewModel.deleteTemplate(it.id) }
                        showDeleteDialog = false
                        templateToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        templateToDelete = null
                    }
                ) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    if (showSettingsSheet) {
        SettingsSheet(
            onDismiss = { showSettingsSheet = false },
            onNavigateToAchievements = {
                navController.navigate(AchievementGalleryRoute)
            }
        )
    }
}
