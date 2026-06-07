package com.example.ui

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.CustomAlbum
import com.example.data.GalleryMediaItem
import com.example.data.VaultItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViraMainApp(
    viewModel: ViraViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("gallery") }
    
    // Theme options from viewmodel
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }

    // Selected media details view overlay state
    val galleryList by viewModel.galleryItems.collectAsStateWithLifecycle()
    val selectedIndex by viewModel.activeMediaIndex.collectAsStateWithLifecycle()
    val slideshowActive by viewModel.slideshowActive.collectAsStateWithLifecycle()

    // Refresh on first mount
    LaunchedEffect(Unit) {
        viewModel.refreshGallery(context)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        bottomBar = {
            if (selectedIndex == -1) {
                val navContainerColor = if (isDark) MaterialTheme.colorScheme.surface else Color(0xFFF3EDF7)
                val navIndicatorColor = if (isDark) activeColor.copy(alpha = 0.15f) else Color(0xFFEADDFF)
                val selectedAccentColor = if (isDark) activeColor else Color(0xFF21005D)
                val unselectedAccentColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else Color(0xFF49454F)

                NavigationBar(
                    containerColor = navContainerColor,
                    tonalElevation = 8.dp,
                    windowInsets = WindowInsets.navigationBars
                ) {
                    val tabs = listOf(
                        Triple("gallery", "Gallery", Icons.Filled.PhotoLibrary),
                        Triple("studio", "Studio", Icons.Filled.Brush),
                        Triple("vault", "Vault", Icons.Filled.Security),
                        Triple("settings", "Settings", Icons.Filled.Tune)
                    )

                    tabs.forEach { (tabId, label, icon) ->
                        val isSelected = currentTab == tabId
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tabId },
                            icon = {
                                Icon(
                                    imageVector = icon,
                                    contentDescription = label,
                                    tint = if (isSelected) selectedAccentColor else unselectedAccentColor
                                )
                            },
                            label = {
                                Text(
                                    text = label,
                                    color = if (isSelected) selectedAccentColor else unselectedAccentColor,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = navIndicatorColor
                            ),
                            modifier = Modifier.testTag("nav_tab_${tabId}")
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when (currentTab) {
                "gallery" -> GalleryScreen(viewModel = viewModel)
                "studio" -> StudioScreen(viewModel = viewModel)
                "vault" -> VaultLockScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
            }

            // High Fidelity Slideshow & Detail Overlay
            if (selectedIndex != -1) {
                AnimatedVisibility(
                    visible = selectedIndex != -1,
                    enter = fadeIn(animationSpec = tween(400)) + scaleIn(initialScale = 0.9f),
                    exit = fadeOut(animationSpec = tween(400)) + scaleOut(targetScale = 0.9f)
                ) {
                    MediaDetailViewerOverlay(
                        viewModel = viewModel,
                        onClose = {
                            viewModel.stopSlideshow()
                            viewModel.setActiveMediaIndex(-1)
                        }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 1. GALLERY SCREEN (Simple Gallery & HashPhotos Inspired)
// ---------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(viewModel: ViraViewModel) {
    val context = LocalContext.current
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val mediaItems by viewModel.galleryItems.collectAsStateWithLifecycle()
    val folder by viewModel.selectedFolder.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val gridColumns by viewModel.gridColumnsCount.collectAsStateWithLifecycle()
    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }

    var isFolderCreatorOpen by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var albumsList by remember { mutableStateOf<List<String>>(emptyList()) }

    // Auto calculate albums/folders
    LaunchedEffect(mediaItems) {
        val albums = mediaItems.map { it.folderName }.distinct().toMutableList()
        if (!albums.contains("All")) {
            albums.add(0, "All")
        }
        albumsList = albums
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // App Top Title Plate with Search and Made in India badging
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Vira",
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Black,
                        color = activeColor
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(Color(0xFFFF9933), Color.White, Color(0xFF138808))
                                ),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "MADE IN INDIA",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Black,
                                color = Color.Black,
                                fontSize = 8.sp,
                                letterSpacing = 0.5.sp
                            )
                        )
                    }
                }
                Text(
                    text = "Luxe Media Vault & Creative Suite",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Grid customized indicator & columns toggle
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val nextCols = if (gridColumns >= 5) 2 else gridColumns + 1
                        viewModel.setGridColumns(nextCols)
                    },
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                ) {
                    Icon(
                        imageVector = when (gridColumns) {
                            2 -> Icons.Filled.GridView
                            3 -> Icons.Filled.Grid3x3
                            4 -> Icons.Filled.Grid4x4
                            else -> Icons.Filled.ViewComfy
                        },
                        contentDescription = "Change Grid columns",
                        tint = activeColor
                    )
                }
            }
        }

        // Custom Search Input Box
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { viewModel.setSearchQuery(context, it) },
            placeholder = { Text("Search title, location or folders...", style = MaterialTheme.typography.bodyMedium) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search", tint = activeColor) },
            trailingIcon = if (searchQuery.isNotEmpty()) {
                {
                    IconButton(onClick = { viewModel.setSearchQuery(context, "") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else null,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = activeColor,
                unfocusedBorderColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .testTag("search_field")
        )

        // Horizontal Folder/Album Management Tabs Carousel
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(albumsList) { currentFolder ->
                    val isSelected = currentFolder == folder
                    val labelText = if (currentFolder == "All") "All Photos" else currentFolder
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (isSelected) {
                                    if (isDark) activeColor.copy(alpha = 0.25f) else Color(0xFFEADDFF)
                                } else {
                                    Color.Transparent
                                }
                            )
                            .border(
                                width = if (isSelected) 1.5.dp else 1.dp,
                                color = if (isSelected) activeColor else Color(0xFF79747E),
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { viewModel.setSelectedFolder(context, currentFolder) }
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                            .testTag("album_chip_$currentFolder")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (isSelected) {
                                Icon(
                                    imageVector = Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isDark) activeColor else Color(0xFF21005D)
                                )
                            }
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    fontSize = 13.sp,
                                    letterSpacing = 0.sp
                                ),
                                color = if (isSelected) {
                                    if (isDark) activeColor else Color(0xFF21005D)
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }

            // Create custom Folder prompt button
            IconButton(
                onClick = { isFolderCreatorOpen = true },
                modifier = Modifier
                    .padding(end = 16.dp)
                    .background(activeColor.copy(alpha = 0.1f), CircleShape)
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "Create Album", tint = activeColor)
            }
        }

        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = activeColor)
            }
        } else if (mediaItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Filled.PhotoAlbum,
                        contentDescription = "Empty",
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No media files indexed",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Authorize access properties to fetch device files, or create artistic layouts in Creative Studio!",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // Main Media Cards Grid with dynamic high-density spacing
            val gridSpacing = remember(gridColumns) { if (gridColumns >= 4) 4.dp else 8.dp }
            val gridHorizontalPadding = remember(gridColumns) { if (gridColumns >= 4) 6.dp else 16.dp }
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(gridColumns),
                contentPadding = PaddingValues(horizontal = gridHorizontalPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(gridSpacing),
                verticalArrangement = Arrangement.spacedBy(gridSpacing),
                modifier = Modifier.weight(1f)
            ) {
                items(mediaItems, key = { it.id }) { item ->
                    MediaIndexCard(
                        item = item,
                        activeColor = activeColor,
                        gridColumns = gridColumns,
                        onOpen = {
                            val realIndex = mediaItems.indexOf(item)
                            viewModel.setActiveMediaIndex(realIndex)
                        },
                        onMoveVault = {
                            viewModel.moveItemToVault(context, item)
                            Toast.makeText(context, "${item.title} safe locked inside VIP Vault!", Toast.LENGTH_SHORT).show()
                        },
                        onStartEditing = {
                            viewModel.startEditing(item)
                            Toast.makeText(context, "Selected ${item.title} in CapCut Studio editor", Toast.LENGTH_SHORT).show()
                        }
                    )
                }
            }
        }
    }

    // Modal prompt for folder creator
    if (isFolderCreatorOpen) {
        AlertDialog(
            onDismissRequest = { isFolderCreatorOpen = false },
            title = { Text("New Custom Album") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    placeholder = { Text("e.g. Goa Holidays, Sunset Series") },
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newFolderName.isNotBlank()) {
                            viewModel.setSelectedFolder(context, newFolderName)
                            Toast.makeText(context, "Album created: $newFolderName", Toast.LENGTH_SHORT).show()
                            newFolderName = ""
                            isFolderCreatorOpen = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { isFolderCreatorOpen = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaIndexCard(
    item: GalleryMediaItem,
    activeColor: Color,
    gridColumns: Int,
    onOpen: () -> Unit,
    onMoveVault: () -> Unit,
    onStartEditing: () -> Unit
) {
    var showQuickMenu by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()
    val cardShape = remember(gridColumns) {
        if (gridColumns >= 4) RoundedCornerShape(4.dp) else RoundedCornerShape(12.dp)
    }

    Card(
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .combinedClickable(
                onClick = onOpen,
                onLongClick = { showQuickMenu = true }
            )
            .testTag("media_card_${item.id}")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Load live stock preview image
            AsyncImage(
                model = item.uriOrPath,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(cardShape)
            )

            // Dynamic autoplay simulation overlay (Rotating/scrolling visual cues)
            if (item.isVideo) {
                // Dim gradient panel representing playback
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                            )
                        )
                )

                // Video badge
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Video file",
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        text = item.durationString ?: "0:10",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 9.sp
                    )
                }

                // Autoplay scrolling simulation wave
                val infiniteTransition = rememberInfiniteTransition(label = "playback")
                val waveTranslation by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 20f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1500, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "playback_wave"
                )
                
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = activeColor.copy(alpha = 0.9f),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                        .graphicsLayer {
                            translationY = waveTranslation
                        }
                )
            } else {
                // If favorite icon present
                if (item.isFavorite) {
                    Icon(
                        imageVector = Icons.Filled.Favorite,
                        contentDescription = "Fav",
                        tint = Color.Red,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(14.dp)
                    )
                }
            }

            // Folder badging if in 'All' view
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Text(
                    text = item.folderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    // Long press dropdown configuration options
    if (showQuickMenu) {
        AlertDialog(
            onDismissRequest = { showQuickMenu = false },
            title = { Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Type: ${if (item.isVideo) "Video File" else "Image File"}")
                    Text("Size: ${item.sizeString}")
                    Text("Folder: ${item.folderName}")
                    Text("Vira Source: ${if (item.isLocalSample) "Stock Asset" else "Device Asset"}")
                }
            },
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(
                        onClick = {
                            onMoveVault()
                            showQuickMenu = false
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move to Vault")
                        }
                    }

                    Button(
                        onClick = {
                            onStartEditing()
                            showQuickMenu = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Icon(Icons.Filled.Brush, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                }
            }
        )
    }
}

// ---------------------------------------------------------
// 2. CREATIVE STUDIO SCREEN (CapCut Editing Engine Sandbox)
// ---------------------------------------------------------
@Composable
fun StudioScreen(viewModel: ViraViewModel) {
    val context = LocalContext.current
    val activeMedia by viewModel.editorSelectedMedia.collectAsStateWithLifecycle()
    val filter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val textOverlay by viewModel.editorTextOverlay.collectAsStateWithLifecycle()
    val textColorHex by viewModel.editorTextColorHex.collectAsStateWithLifecycle()
    val fontFamilyName by viewModel.editorFontFamilyName.collectAsStateWithLifecycle()
    val effect by viewModel.editorVideoEffect.collectAsStateWithLifecycle()
    val transition by viewModel.editorTransition.collectAsStateWithLifecycle()

    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }

    var isExporting by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableStateOf(0f) }
    var customTitle by remember { mutableStateOf("") }
    
    val viewCoroutineScope = rememberCoroutineScope()

    if (activeMedia == null) {
        // Studio Empty selection state - prompt user to select image
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Filled.Brush,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = activeColor.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "Vira CapCut Studio",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Pick any image or video clip from the Gallery Hub list, long press on it, and click 'Edit' to unlock high fidelity overlay captions, artistic Indian filters, custom transitions, and physics glitch filters!",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        // Active Editing Suite
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            // Header bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.startEditing(activeMedia!!) }) { // simple resetting selection trigger
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                }
                Text(
                    text = "Studio: ${activeMedia!!.title}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                )
                Button(
                    onClick = {
                        isExporting = true
                        exportProgress = 0f
                        viewCoroutineScope.launch {
                            while (exportProgress < 1f) {
                                delay(60L)
                                exportProgress += 0.05f
                            }
                            viewModel.exportCurrentCreation(context, customTitle)
                            isExporting = false
                            Toast.makeText(context, "Exported successfully to 'My Creations' folder!", Toast.LENGTH_LONG).show()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = activeColor),
                    modifier = Modifier.testTag("export_button")
                ) {
                    Icon(Icons.Filled.Save, contentDescription = "Save", modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editing Sandbox Canvas Preview Card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.3f)
                    .background(Color.Black, RoundedCornerShape(16.dp))
                    .border(2.dp, activeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    .testTag("editing_sandbox_preview")
            ) {
                // Stock render block with customized canvas filters
                val filterMatrix = remember(filter) {
                    when (filter) {
                        "Indian Spice" -> ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                1.3f, 0f, 0f, 0f, 20f,
                                0f, 1.0f, 0f, 0f, 0f,
                                0f, 0f, 0.8f, 0f, -10f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        "Ganga Twilight" -> ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                1.2f, 0.1f, 0.2f, 0f, 30f,
                                0f, 0.9f, 0.1f, 0f, 10f,
                                0f, 0f, 0.7f, 0f, -20f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        "Himalaya Cool" -> ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                0.8f, 0f, 0f, 0f, -15f,
                                0f, 1.0f, 0.1f, 0f, 5f,
                                0f, 0f, 1.4f, 0f, 25f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        "Monochrome Heritage" -> ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                0.299f, 0.587f, 0.114f, 0f, 0f,
                                0f,      0f,      0f,      1f, 0f
                            ))
                        )
                        "Bollywood Crimson" -> ColorFilter.colorMatrix(
                            androidx.compose.ui.graphics.ColorMatrix(floatArrayOf(
                                1.4f, 0f, 0.4f, 0f, 35f,
                                0f, 0.8f, 0f, 0f, -15f,
                                0f, 0.1f, 0.9f, 0f, -10f,
                                0f, 0f, 0f, 1f, 0f
                            ))
                        )
                        else -> null
                    }
                }

                AsyncImage(
                    model = activeMedia!!.uriOrPath,
                    contentDescription = "Edit preview",
                    contentScale = ContentScale.Fit,
                    colorFilter = filterMatrix,
                    modifier = Modifier.fillMaxSize()
                )

                // Simulated filter physics overlay rendering using visual canvas drawings
                if (effect != "None") {
                    val infiniteTransition = rememberInfiniteTransition(label = "fx")
                    val effectPulse by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1000, easing = LinearEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "fx_pulse"
                    )

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        when (effect) {
                            "Glitch Shake" -> {
                                drawRect(
                                    color = Color.Cyan.copy(alpha = 0.15f * effectPulse),
                                    size = size
                                )
                                drawRect(
                                    color = Color.Red.copy(alpha = 0.1f * (1f - effectPulse)),
                                    size = size
                                )
                            }
                            "Film Burn Glow" -> {
                                drawCircle(
                                    color = Color(0xFFFF4500).copy(alpha = 0.25f * effectPulse),
                                    radius = size.width / 1.5f,
                                    center = center
                                )
                            }
                            "Sepia Grain" -> {
                                drawRect(
                                    color = Color(0xFF8B5A2B).copy(alpha = 0.2f),
                                    size = size
                                )
                            }
                        }
                    }
                }

                // Dynamic subtitle moving overlay
                if (textOverlay.isNotBlank()) {
                    val parsedColor = try {
                        Color(android.graphics.Color.parseColor(textColorHex))
                    } catch (e: Exception) {
                        Color.White
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 32.dp, start = 16.dp, end = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = textOverlay,
                            color = parsedColor,
                            style = TextStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontStyle = if (fontFamilyName == "Vira Script") FontStyle.Italic else FontStyle.Normal,
                                fontFamily = when (fontFamilyName) {
                                    "Bold Block" -> FontFamily.SansSerif
                                    "Vira Script" -> FontFamily.Serif
                                    "Cyber Mono" -> FontFamily.Monospace
                                    else -> FontFamily.Default
                                }
                            ),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Editing configurations panels
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1.5f)
            ) {
                var selectedToolbarTab by remember { mutableStateOf("filters") }
                
                // Studio Tools select list
                TabRow(
                    selectedTabIndex = when (selectedToolbarTab) {
                        "filters" -> 0
                        "text" -> 1
                        "transitions" -> 2
                        else -> 0
                    },
                    containerColor = Color.Transparent,
                    contentColor = activeColor
                ) {
                    Tab(
                        selected = selectedToolbarTab == "filters",
                        onClick = { selectedToolbarTab = "filters" },
                        text = { Text("Filters/FX", style = MaterialTheme.typography.bodyMedium) }
                    )
                    Tab(
                        selected = selectedToolbarTab == "text",
                        onClick = { selectedToolbarTab = "text" },
                        text = { Text("Text Tool", style = MaterialTheme.typography.bodyMedium) }
                    )
                    Tab(
                        selected = selectedToolbarTab == "transitions",
                        onClick = { selectedToolbarTab = "transitions" },
                        text = { Text("Transitions", style = MaterialTheme.typography.bodyMedium) }
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    when (selectedToolbarTab) {
                        "filters" -> {
                            Text("1. Artistic Overlays", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val filters = listOf("Original", "Indian Spice", "Ganga Twilight", "Himalaya Cool", "Monochrome Heritage", "Bollywood Crimson")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(filters) { f ->
                                    val isSel = f == filter
                                    Button(
                                        onClick = { viewModel.setEditorFilter(f) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSel) activeColor else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Text(f, color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            Text("2. CapCut Glitch & Effects Layer", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            val visualEffects = listOf("None", "Glitch Shake", "Film Burn Glow", "Sepia Grain")
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(visualEffects) { fx ->
                                    val isSel = fx == effect
                                    Button(
                                        onClick = { viewModel.setEditorVideoEffect(fx) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (isSel) activeColor else MaterialTheme.colorScheme.surface
                                        )
                                    ) {
                                        Text(fx, color = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }

                        "text" -> {
                            OutlinedTextField(
                                value = textOverlay,
                                onValueChange = { viewModel.setEditorTextOverlay(it) },
                                label = { Text("Write Subtitle / Overlay Text") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor)
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Font Style:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                val fontsList = listOf("Standard", "Bold Block", "Vira Script", "Cyber Mono")
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(fontsList) { boldName ->
                                        val isSel = boldName == fontFamilyName
                                        Text(
                                            boldName,
                                            modifier = Modifier
                                                .background(
                                                    if (isSel) activeColor.copy(alpha = 0.3f) else Color.Transparent,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .border(
                                                    1.dp,
                                                    if (isSel) activeColor else MaterialTheme.colorScheme.outline,
                                                    RoundedCornerShape(4.dp)
                                                )
                                                .clickable { viewModel.setEditorFontAndColor(boldName, textColorHex) }
                                                .padding(horizontal = 6.dp, vertical = 3.dp),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Caption Color:", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                val colorsConfig = listOf(
                                    Triple("#FFFFFF", "White", Color.White),
                                    Triple("#FF9933", "Saffron", Color(0xFFFF9933)),
                                    Triple("#138808", "Green", Color(0xFF138808)),
                                    Triple("#FFD700", "Gold", Color(0xFFFFD700)),
                                    Triple("#0F52BA", "Peacock", Color(0xFF0F52BA))
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    colorsConfig.forEach { (colorHex, _, colorVal) ->
                                        val isSel = colorHex == textColorHex
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .background(colorVal, CircleShape)
                                                .border(
                                                    if (isSel) 2.dp else 1.dp,
                                                    if (isSel) activeColor else Color.Black,
                                                    CircleShape
                                                )
                                                .clickable { viewModel.setEditorFontAndColor(fontFamilyName, colorHex) }
                                        )
                                    }
                                }
                            }
                        }

                        "transitions" -> {
                            Text("Cinematic Transition Animator", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Sets transition animations during slide view transitions. Select from clean native filters:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            val transitions = listOf("None", "Cross Fade", "Zoom Slide", "Flash White", "Spin Rotation")
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                transitions.forEach { transId ->
                                    val isSel = transId == transition
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSel) activeColor.copy(alpha = 0.15f) else Color.Transparent,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .clickable { viewModel.setEditorTransition(transId) }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(
                                            selected = isSel,
                                            onClick = { viewModel.setEditorTransition(transId) },
                                            colors = RadioButtonDefaults.colors(selectedColor = activeColor)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(transId, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Export progress feedback screen
    if (isExporting) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Rendering Cinematic Video Assets...") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Compiling custom elements, captions, and transition templates safely...")
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(
                        progress = { exportProgress },
                        color = activeColor,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("${(exportProgress * 100).toInt()}% Done")
                }
            },
            confirmButton = {}
        )
    }
}

// ---------------------------------------------------------
// 3. PRIVATE SPACE (Biometric Vault PIN Protection Screen)
// ---------------------------------------------------------
@Composable
fun VaultLockScreen(viewModel: ViraViewModel) {
    val context = LocalContext.current
    val authenticated by viewModel.isVaultAuthenticated.collectAsStateWithLifecycle()
    val vaultPIN by viewModel.vaultPIN.collectAsStateWithLifecycle()
    val vaultItems by viewModel.vaultItemsState.collectAsStateWithLifecycle()
    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }
    val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()

    var enteredPasscode by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    // Automatic biometric trigger when screen displays
    LaunchedEffect(authenticated, isBiometricEnabled) {
        if (!authenticated && isBiometricEnabled) {
            val activity = BiometricHelper.findActivity(context)
            if (activity != null && BiometricHelper.isBiometricAvailable(context)) {
                BiometricHelper.launchBiometricPrompt(
                    activity = activity,
                    onSuccess = { viewModel.setVaultAuthenticated(true) },
                    onError = { /* Fail gracefully and fallback to numeric keypad lock */ }
                )
            }
        }
    }

    if (authenticated) {
        // Vault Index list
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, contentDescription = null, tint = activeColor, modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Vira VIP Vault",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { viewModel.logOutVault() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Lock")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (vaultItems.isEmpty()) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LockOpen,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "VIP Vault is Empty",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Long-press any media on the main Gallery screen and tap 'Move to Vault' to lock them safely with PIN protection.",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                Text(
                    text = "${vaultItems.size} files hidden from the device system databases:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(vaultItems) { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, activeColor.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        ) {
                            AsyncImage(
                                model = item.pathOrUri,
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Locked badge
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.15f))
                            )

                            IconButton(
                                onClick = {
                                    viewModel.restoreItemFromVault(context, item.pathOrUri)
                                    Toast.makeText(context, "Restored successfully back to public Gallery!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(4.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                                    .size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.LockOpen,
                                    contentDescription = "Restore Item",
                                    tint = activeColor,
                                    modifier = Modifier.size(14.dp)
                                )
                            }

                            if (item.mediaType == "video") {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(24.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // High fidelity lock Keypad screen resembling modern secure vaults
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Security,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = activeColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Secure VIP Private Vault",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Enter your 4-Digit Passcode PIN",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Dedicated patriotism hint
            Text(
                text = "Pre-configured PIN: 1947",
                style = MaterialTheme.typography.labelSmall,
                color = activeColor.copy(alpha = 0.7f)
            )

            if (isBiometricEnabled && BiometricHelper.isBiometricAvailable(context)) {
                Spacer(modifier = Modifier.height(16.dp))
                IconButton(
                    onClick = {
                        val activity = BiometricHelper.findActivity(context)
                        if (activity != null) {
                            BiometricHelper.launchBiometricPrompt(
                                activity = activity,
                                onSuccess = { viewModel.setVaultAuthenticated(true) },
                                onError = { /* User clicked negative button or dismissed prompt */ }
                            )
                        }
                    },
                    modifier = Modifier
                        .size(56.dp)
                        .background(activeColor.copy(alpha = 0.1f), CircleShape)
                        .border(1.5.dp, activeColor, CircleShape)
                        .testTag("biometric_trigger_button")
                ) {
                    Icon(
                        imageVector = Icons.Filled.Fingerprint,
                        contentDescription = "Unlock with Biometrics",
                        tint = activeColor,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Tap fingerprint scanner to unlock",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // PIN Code representation dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.padding(12.dp)
            ) {
                for (i in 1..4) {
                    val occupied = enteredPasscode.length >= i
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(
                                color = if (occupied) activeColor else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                            .border(
                                width = 2.dp,
                                color = if (pinError) MaterialTheme.colorScheme.error else activeColor,
                                shape = CircleShape
                            )
                    )
                }
            }

            if (pinError) {
                Text(
                    text = "Incorrect passcode. Try again!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Dedicated Keypad grid layout
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.testTag("pin_keypad")
            ) {
                val keys = listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                    listOf("Clear", "0", "OK")
                )

                keys.forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxWidth(0.85f)
                    ) {
                        row.forEach { k ->
                            Button(
                                onClick = {
                                    pinError = false
                                    when (k) {
                                        "Clear" -> {
                                            if (enteredPasscode.isNotEmpty()) {
                                                enteredPasscode = enteredPasscode.dropLast(1)
                                            }
                                        }
                                        "OK" -> {
                                            if (viewModel.verifyVaultPIN(enteredPasscode)) {
                                                enteredPasscode = ""
                                            } else {
                                                pinError = true
                                                enteredPasscode = ""
                                            }
                                        }
                                        else -> {
                                            if (enteredPasscode.length < 4) {
                                                enteredPasscode += k
                                            }
                                        }
                                    }
                                },
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (k == "OK" || k == "Clear") MaterialTheme.colorScheme.surfaceVariant else activeColor.copy(alpha = 0.1f),
                                    contentColor = if (k == "OK" || k == "Clear") MaterialTheme.colorScheme.onSurface else activeColor
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1.5f)
                                    .testTag("key_$k")
                            ) {
                                Text(
                                    text = k,
                                    fontSize = if (k == "OK" || k == "Clear") 14.sp else 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 4. SETTINGS SCREEN (Indian accent custom colors, dynamic Theme, Slideshow time)
// ---------------------------------------------------------
@Composable
fun SettingsScreen(viewModel: ViraViewModel) {
    val context = LocalContext.current
    val isDark by viewModel.isDarkMode.collectAsStateWithLifecycle()
    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }
    val slideshowInterval by viewModel.slideshowIntervalSec.collectAsStateWithLifecycle()

    var customPinInput by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Text(
            text = "Vira System Configurations",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Black,
            fontSize = 24.sp,
            color = activeColor
        )
        Text(
            text = "Proudly Made in India: Secure Luxe Utility",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Toggle native Dark/Light Screen
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Theme customisation", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Dark Theme", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = isDark,
                        onCheckedChange = { viewModel.changeTheme(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = activeColor)
                    )
                }

                Divider(color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f))

                // Indian Premium Colors Picker
                Text("Vira Native Accent Color Theme", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                val colorPalettes = listOf(
                    Triple("#FF9933", "Saffron Orange", Color(0xFFFF9933)),
                    Triple("#FFD700", "Marigold Gold", Color(0xFFFFD700)),
                    Triple("#138808", "Tiranga Green", Color(0xFF138808)),
                    Triple("#0F52BA", "Peacock Blue", Color(0xFF0F52BA)),
                    Triple("#D2042D", "Jaipur Red", Color(0xFFD2042D))
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    colorPalettes.forEach { (colorHex, paletteLabel, colorVal) ->
                        val isSel = colorHex == accentHex
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable {
                                    viewModel.changeAccentColor(colorHex)
                                    Toast.makeText(context, "Theme set to $paletteLabel!", Toast.LENGTH_SHORT).show()
                                }
                                .padding(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .background(colorVal, CircleShape)
                                    .border(
                                        if (isSel) 3.dp else 1.dp,
                                        if (isSel) Color.White else Color.Transparent,
                                        CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Customizable Slideshow intervals settings
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Slideshow Interval Delay", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                val intervalsConfig = listOf(2, 5, 10)
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    intervalsConfig.forEach { secs ->
                        val isSel = secs == slideshowInterval
                        Button(
                            onClick = {
                                viewModel.setSlideshowInterval(secs)
                                Toast.makeText(context, "Slideshow interval updated to $secs Sec!", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) activeColor else MaterialTheme.colorScheme.surface,
                                contentColor = if (isSel) Color.Black else MaterialTheme.colorScheme.onSurface
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("${secs}s")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Biometric Settings Switch Toggle
        val isBiometricEnabled by viewModel.isBiometricEnabled.collectAsStateWithLifecycle()
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Biometric Locks", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Fingerprint & Face Unlock", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = if (BiometricHelper.isBiometricAvailable(context)) "Biometrics enrolled and active" else "Biometrics not available or not enrolled",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = activeColor),
                        modifier = Modifier.testTag("biometric_enable_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Customizable Passcode management settings
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Passcode Protection Setup", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Set a new 4-digit numeric PIN lock to secure files index behind biometrics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customPinInput,
                        onValueChange = { if (it.length <= 4) customPinInput = it },
                        placeholder = { Text("4 Digits PIN") },
                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = activeColor),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            if (customPinInput.length == 4 && customPinInput.all { it.isDigit() }) {
                                viewModel.setupNewPIN(customPinInput)
                                customPinInput = ""
                                Toast.makeText(context, "Private passcode redefined!", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "PIN must be exactly 4 digits!", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = activeColor)
                    ) {
                        Text("Update")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Patriotic dedication banner card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color(0xFFFF9933).copy(alpha = 0.15f), Color(0xFF138808).copy(alpha = 0.15f))
                    ),
                    RoundedCornerShape(12.dp)
                )
                .border(1.dp, activeColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Flag,
                    contentDescription = "flag",
                    tint = activeColor,
                    modifier = Modifier.size(36.dp)
                )
                Column {
                    Text(
                        "Vira Native Suite",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White
                    )
                    Text(
                        "Designed exclusively in India. Built with clean, offline-first Material Design principles for robust privacy compliance.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------
// 5. IMMERSIVE DETAIL SINGLE VIEWER & AUTOMATED SLIDESHOW ENGINE
// ---------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaDetailViewerOverlay(
    viewModel: ViraViewModel,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val items by viewModel.galleryItems.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeMediaIndex.collectAsStateWithLifecycle()
    val slideshowActive by viewModel.slideshowActive.collectAsStateWithLifecycle()
    val slideshowInterval by viewModel.slideshowIntervalSec.collectAsStateWithLifecycle()

    val accentHex by viewModel.appAccentColorHex.collectAsStateWithLifecycle()
    val activeColor = remember(accentHex) { Color(android.graphics.Color.parseColor(accentHex)) }

    // Guard safe index
    if (activeIndex == -1 || activeIndex >= items.size) {
        onClose()
        return
    }

    val item = items[activeIndex]

    // Capture device back buttons
    BackHandler(onBack = onClose)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("media_detail_overlay")
    ) {
        // Render content card with dynamic Zooming/Scrolling details
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp),
            contentAlignment = Alignment.Center
        ) {
            // Immersive load card
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                AsyncImage(
                    model = item.uriOrPath,
                    contentDescription = item.title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .graphicsLayer(
                            // Minimalist parallax and transitions matching slide configurations
                            translationX = 0f
                        )
                )

                // Render play/timeline properties if type is video
                if (item.isVideo) {
                    val progressAnim = remember { Animatable(0f) }
                    
                    // Live simulation of video duration timelines autoplay as user scrolls/views
                    LaunchedEffect(activeIndex) {
                        progressAnim.snapTo(0f)
                        progressAnim.animateTo(
                            targetValue = 1f,
                            animationSpec = tween(
                                durationMillis = 10000,
                                easing = LinearEasing
                            )
                        )
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("0:${String.format("%02d", (progressAnim.value * 10).toInt())}", color = Color.White, fontSize = 11.sp)
                            Text(item.durationString ?: "0:12", color = Color.White, fontSize = 11.sp)
                        }
                        
                        LinearProgressIndicator(
                            progress = { progressAnim.value },
                            color = activeColor,
                            trackColor = Color.White.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                        )
                    }
                }
            }
        }

        // Slideshow running progress bar overlay
        if (slideshowActive) {
            var slideRemainingProgress by remember { mutableStateOf(1f) }
            LaunchedEffect(activeIndex) {
                slideRemainingProgress = 1f
                val stepCount = 50
                for (s in 1..stepCount) {
                    delay((slideshowInterval * 1000) / stepCount.toLong())
                    slideRemainingProgress = 1f - (s.toFloat() / stepCount)
                }
            }

            LinearProgressIndicator(
                progress = { slideRemainingProgress },
                color = activeColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .height(6.dp)
            )
        }

        // Header Action overlays (Close, Edit, Move to secure space)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                    )
                )
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close detailed view", tint = Color.White)
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${item.folderName} • ${item.sizeString}",
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
            }

            Row {
                IconButton(
                    onClick = {
                        viewModel.startEditing(item)
                        Toast.makeText(context, "Opening in Studio Editor!", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Brush, contentDescription = "Edit file", tint = activeColor)
                }
                
                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = {
                        viewModel.moveItemToVault(context, item)
                        Toast.makeText(context, "Moved to VIP Vault successfully!", Toast.LENGTH_SHORT).show()
                        onClose()
                    },
                    modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = "Hide in Vault", tint = activeColor)
                }
            }
        }

        // Footer navigation / Slide show engine actions
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                    )
                )
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Previous Button
            IconButton(
                onClick = {
                    val prevIdx = if (activeIndex <= 0) items.size - 1 else activeIndex - 1
                    viewModel.setActiveMediaIndex(prevIdx)
                },
                modifier = Modifier.background(activeColor.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous File", tint = Color.White)
            }

            // Central slideshow automate switch
            Button(
                onClick = {
                    if (slideshowActive) {
                        viewModel.stopSlideshow()
                        Toast.makeText(context, "Slideshow paused", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.startSlideshow()
                        Toast.makeText(context, "Vira Slide Engine Running (${slideshowInterval}s Interval)", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (slideshowActive) MaterialTheme.colorScheme.error else activeColor
                )
            ) {
                Icon(
                    imageVector = if (slideshowActive) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (slideshowActive) Color.White else Color.Black
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (slideshowActive) "Pause" else "Slide Link (${slideshowInterval}s)",
                    color = if (slideshowActive) Color.White else Color.Black,
                    fontWeight = FontWeight.Bold
                )
            }

            // Next Button
            IconButton(
                onClick = {
                    val nextIdx = if (activeIndex >= items.size - 1) 0 else activeIndex + 1
                    viewModel.setActiveMediaIndex(nextIdx)
                },
                modifier = Modifier.background(activeColor.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Next File", tint = Color.White)
            }
        }
    }
}
