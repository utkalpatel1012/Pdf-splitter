package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ChapterEditorScreen
import com.example.ui.screens.ExportScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.PreviewScreen
import com.example.ui.theme.ChapterSplitTheme
import com.example.viewmodel.PdfSplitViewModel

enum class AppScreen {
    HOME,
    CHAPTER_EDITOR,
    PREVIEW,
    EXPORT
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChapterSplitTheme {
                val viewModel: PdfSplitViewModel = viewModel()
                var currentScreen by remember { mutableStateOf(AppScreen.HOME) }
                val doc by viewModel.currentDoc.collectAsState()
                val splitResult by viewModel.splitResult.collectAsState()

                // BackHandler routing
                BackHandler(enabled = currentScreen != AppScreen.HOME) {
                    when (currentScreen) {
                        AppScreen.EXPORT -> currentScreen = AppScreen.CHAPTER_EDITOR
                        AppScreen.PREVIEW -> currentScreen = AppScreen.CHAPTER_EDITOR
                        AppScreen.CHAPTER_EDITOR -> currentScreen = AppScreen.HOME
                        AppScreen.HOME -> Unit
                    }
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        if (doc != null && currentScreen != AppScreen.PREVIEW) {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.HOME,
                                    onClick = { currentScreen = AppScreen.HOME },
                                    icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                                    label = { Text("Import") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.CHAPTER_EDITOR,
                                    onClick = { currentScreen = AppScreen.CHAPTER_EDITOR },
                                    icon = { Icon(Icons.AutoMirrored.Filled.ListAlt, contentDescription = "Chapters") },
                                    label = { Text("Chapters") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.PREVIEW,
                                    onClick = { currentScreen = AppScreen.PREVIEW },
                                    icon = { Icon(Icons.Default.Visibility, contentDescription = "Preview") },
                                    label = { Text("Preview") }
                                )
                                NavigationBarItem(
                                    selected = currentScreen == AppScreen.EXPORT,
                                    onClick = {
                                        if (splitResult != null) {
                                            currentScreen = AppScreen.EXPORT
                                        } else {
                                            viewModel.splitAndExportZip()
                                            currentScreen = AppScreen.EXPORT
                                        }
                                    },
                                    icon = { Icon(Icons.Default.FolderZip, contentDescription = "Export") },
                                    label = { Text("Export") }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        label = "ScreenTransition"
                    ) { targetScreen ->
                        when (targetScreen) {
                            AppScreen.HOME -> {
                                HomeScreen(
                                    viewModel = viewModel,
                                    onNavigateToWorkbench = { currentScreen = AppScreen.CHAPTER_EDITOR }
                                )
                            }
                            AppScreen.CHAPTER_EDITOR -> {
                                ChapterEditorScreen(
                                    viewModel = viewModel,
                                    onNavigateToPreview = { currentScreen = AppScreen.PREVIEW },
                                    onNavigateToExport = { currentScreen = AppScreen.EXPORT },
                                    onBackToHome = {
                                        viewModel.resetState()
                                        currentScreen = AppScreen.HOME
                                    }
                                )
                            }
                            AppScreen.PREVIEW -> {
                                PreviewScreen(
                                    viewModel = viewModel,
                                    onBackToEditor = { currentScreen = AppScreen.CHAPTER_EDITOR },
                                    onNavigateToExport = { currentScreen = AppScreen.EXPORT }
                                )
                            }
                            AppScreen.EXPORT -> {
                                ExportScreen(
                                    viewModel = viewModel,
                                    onBackToEditor = { currentScreen = AppScreen.CHAPTER_EDITOR },
                                    onStartNew = {
                                        viewModel.resetState()
                                        currentScreen = AppScreen.HOME
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
