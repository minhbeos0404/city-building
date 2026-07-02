package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BuildingType
import com.example.data.CityCellEntity
import com.example.data.GameStateEntity
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel) {
    val cells by viewModel.cells.collectAsStateWithLifecycle()
    val gameState by viewModel.gameState.collectAsStateWithLifecycle()
    val selectedTool by viewModel.selectedTool.collectAsStateWithLifecycle()
    val isDemolishSelected by viewModel.isDemolishSelected.collectAsStateWithLifecycle()
    val inspectedCell by viewModel.inspectedCell.collectAsStateWithLifecycle()
    val newsTicker by viewModel.newsTicker.collectAsStateWithLifecycle()
    val simulationSpeed by viewModel.simulationSpeed.collectAsStateWithLifecycle()
    val activeEvent by viewModel.activeEvent.collectAsStateWithLifecycle()
    val burningCellId by viewModel.burningCellId.collectAsStateWithLifecycle()

    val populationHistory by viewModel.populationHistory.collectAsStateWithLifecycle()
    val budgetHistory by viewModel.budgetHistory.collectAsStateWithLifecycle()

    var showResetDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var newCityName by remember { mutableStateOf("") }
    var showStatsSheet by remember { mutableStateOf(false) }

    // Navigation and Edge-to-Edge window inset handling
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("game_screen"),
        containerColor = Color(0xFF111827), // Sleek deep slate dark mode background
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1F2937))
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(8.dp)
            ) {
                // News Ticker
                NewsTickerBar(newsTicker)

                Spacer(modifier = Modifier.height(4.dp))

                // Build Actions Toolbar
                BuildingPaletteBar(
                    selectedTool = selectedTool,
                    isDemolishSelected = isDemolishSelected,
                    onToolSelect = { viewModel.setTool(it) },
                    onDemolishSelect = { viewModel.setDemolishMode(it) }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Isometric Main Canvas Game View
            IsometricCanvasView(
                cells = cells,
                burningCellId = burningCellId,
                onCellTapped = { x, y -> viewModel.handleCellInteraction(x, y) }
            )

            // Header Overlay Panel
            HeaderOverlayPanel(
                gameState = gameState,
                speed = simulationSpeed,
                onSpeedChanged = { viewModel.setSimulationSpeed(it) },
                onRenameClick = {
                    newCityName = gameState?.cityName ?: ""
                    showRenameDialog = true
                },
                onResetClick = { showResetDialog = true },
                onStatsClick = { showStatsSheet = true },
                onTriggerEventClick = { viewModel.triggerRandomEvent() }
            )

            // Dynamic Hover Inspector Card
            AnimatedVisibility(
                visible = inspectedCell != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp, start = 16.dp, end = 16.dp)
            ) {
                inspectedCell?.let { cell ->
                    InspectorCard(
                        cell = cell,
                        onClose = { viewModel.setTool(BuildingType.RESIDENTIAL) } // Reset to build tool
                    )
                }
            }

            // Emergency / Disasters Banner
            AnimatedVisibility(
                visible = activeEvent != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 110.dp, start = 16.dp, end = 16.dp)
            ) {
                activeEvent?.let { event ->
                    EmergencyBanner(
                        event = event,
                        isFireActive = burningCellId != null,
                        onExtinguish = { viewModel.extinguishFire() },
                        onDismiss = { viewModel.dismissEvent() }
                    )
                }
            }
        }
    }

    // Modal Sheet showing population/budget analytics
    if (showStatsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showStatsSheet = false },
            containerColor = Color(0xFF1F2937),
            contentColor = Color.White
        ) {
            StatsAnalyticsView(
                cityName = gameState?.cityName ?: "City",
                populationHistory = populationHistory,
                budgetHistory = budgetHistory,
                cells = cells
            )
        }
    }

    // Rename City Dialog
    if (showRenameDialog) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("Rename Your City", color = Color.White, fontFamily = FontFamily.Monospace) },
            text = {
                OutlinedTextField(
                    value = newCityName,
                    onValueChange = { newCityName = it },
                    label = { Text("City Name", color = Color.LightGray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF60A5FA),
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.testTag("city_name_input")
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetWholeCity(newCityName)
                        showRenameDialog = false
                    },
                    modifier = Modifier.testTag("rename_confirm_button")
                ) {
                    Text("Rename & Reset", color = Color(0xFF34D399), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) {
                    Text("Cancel", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1F2937)
        )
    }

    // Reset Confirmation Dialog
    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Confirm New City", color = Color.White, fontFamily = FontFamily.Monospace) },
            text = { Text("Are you sure you want to demolish the entire city and start fresh with \$10,000?", color = Color.LightGray) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.resetWholeCity(gameState?.cityName ?: "Pixelopolis")
                        showResetDialog = false
                    },
                    modifier = Modifier.testTag("reset_confirm_button")
                ) {
                    Text("Start Fresh", color = Color.Red, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Keep Building", color = Color.LightGray)
                }
            },
            containerColor = Color(0xFF1F2937)
        )
    }
}

// ==========================================
// ISOMETRIC CORE CANVAS GAME COMPONENT
// ==========================================
@Composable
fun IsometricCanvasView(
    cells: List<CityCellEntity>,
    burningCellId: Int?,
    onCellTapped: (Int, Int) -> Unit
) {
    var scale by remember { mutableStateOf(1.0f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val configuration = LocalConfiguration.current
    val screenWidthPx = with(LocalDensity.current) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(LocalDensity.current) { configuration.screenHeightDp.dp.toPx() }

    // Constant Isometric Tile metrics
    val tileWidth = 72f
    val tileHeight = 36f

    // Center offset based on 10x10 grid dimensions
    // x centers at: (x - y) * tileWidth/2 + screenWidth/2
    // y centers at: (x + y) * tileHeight/2 + tileHeight (near top)
    val gridCenterOffset = Offset(screenWidthPx / 2f, screenHeightPx / 5f)

    // Animated game ticking for flames/smoke
    val infiniteTransition = rememberInfiniteTransition(label = "rendering_ticks")
    val animProgress by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anim_progress"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale = (scale * zoom).coerceIn(0.4f, 2.5f)
                    offset = offset + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGesturesWithDensity(
                    onTap = { tapOffset ->
                        // Reverse isometric coordinates back to tile x,y
                        val tx = tapOffset.x - gridCenterOffset.x - offset.x
                        val ty = tapOffset.y - gridCenterOffset.y - offset.y

                        // Scale adjustments
                        val scaledX = tx / scale
                        val scaledY = ty / scale

                        val u = scaledX / (tileWidth / 2f)
                        val v = scaledY / (tileHeight / 2f)

                        val gridX = (u + v) / 2f
                        val gridY = (v - u) / 2f

                        val finalX = gridX.roundToInt()
                        val finalY = gridY.roundToInt()

                        if (finalX in 0 until 10 && finalY in 0 until 10) {
                            onCellTapped(finalX, finalY)
                        }
                    }
                )
            }
    ) {
        Canvas(modifier = Modifier.fillMaxSize().testTag("isometric_map")) {
            // Draw background ambient sky with floating clouds
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                )
            )

            // Draw a couple of procedural cute clouds in background
            drawCloud(Offset(100f, 150f), animProgress)
            drawCloud(Offset(size.width - 250f, 280f), -animProgress)

            // Center camera transformation
            withTransform({
                translate(offset.x, offset.y)
                scale(scale, scale, pivot = gridCenterOffset)
            }) {
                // Painter's algorithm: Draw tiles back to front based on sum of coordinates (x + y)
                // Grid size is 10x10, so max x+y is 18
                for (sum in 0..18) {
                    for (x in 0 until 10) {
                        val y = sum - x
                        if (y in 0 until 10) {
                            val cell = cells.find { it.x == x && it.y == y }
                            if (cell != null) {
                                val cx = (x - y) * (tileWidth / 2f) + gridCenterOffset.x
                                val cy = (x + y) * (tileHeight / 2f) + gridCenterOffset.y
                                drawIsometricTile(
                                    cx = cx,
                                    cy = cy,
                                    tileWidth = tileWidth,
                                    tileHeight = tileHeight,
                                    cell = cell,
                                    isBurning = cell.id == burningCellId,
                                    animProgress = animProgress
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Helper pointer handler preserving local densities
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.detectTapGesturesWithDensity(
    onTap: (Offset) -> Unit
) {
    detectTapGestures(onTap = onTap)
}

// Drawing procedural retro pixelated cloud
fun DrawScope.drawCloud(pos: Offset, animOffset: Float) {
    val cloudColor = Color(0x18FFFFFF)
    val floatX = pos.x + animOffset * 30f
    drawCircle(cloudColor, 35f, Offset(floatX, pos.y))
    drawCircle(cloudColor, 45f, Offset(floatX + 40f, pos.y - 15f))
    drawCircle(cloudColor, 35f, Offset(floatX + 80f, pos.y))
    drawRect(cloudColor, Offset(floatX, pos.y - 15f), Size(80f, 30f))
}

// Procedural 3D Isometric Tile Drawer
fun DrawScope.drawIsometricTile(
    cx: Float,
    cy: Float,
    tileWidth: Float,
    tileHeight: Float,
    cell: CityCellEntity,
    isBurning: Boolean,
    animProgress: Float
) {
    val bType = BuildingType.fromString(cell.buildingType)

    // Calculate height of building based on type and growth level
    val blockHeight = when (bType) {
        BuildingType.EMPTY -> 6f
        BuildingType.ROAD -> 3f
        BuildingType.PARK -> 8f
        BuildingType.SOLAR_POWER -> 24f
        BuildingType.RESIDENTIAL -> when (cell.level) {
            1 -> 18f
            2 -> 36f
            3 -> 62f
            else -> 6f // level 0 is empty lot
        }
        BuildingType.COMMERCIAL -> when (cell.level) {
            1 -> 24f
            2 -> 45f
            3 -> 80f
            else -> 6f
        }
        BuildingType.INDUSTRIAL -> when (cell.level) {
            1 -> 28f
            2 -> 48f
            3 -> 72f
            else -> 6f
        }
    }

    // Color definitions for retro isometric palette
    val topFaceColor: Color
    val leftFaceColor: Color
    val rightFaceColor: Color

    when (bType) {
        BuildingType.EMPTY -> {
            // Grass
            topFaceColor = Color(0xFF34D399) // Vivid emerald
            leftFaceColor = Color(0xFF047857) // Dark soil grass
            rightFaceColor = Color(0xFF059669) // Medium dirt grass
        }
        BuildingType.ROAD -> {
            topFaceColor = Color(0xFF4B5563) // Cool slate road
            leftFaceColor = Color(0xFF1F2937)
            rightFaceColor = Color(0xFF374151)
        }
        BuildingType.PARK -> {
            topFaceColor = Color(0xFF10B981) // Greenery park
            leftFaceColor = Color(0xFF047857)
            rightFaceColor = Color(0xFF059669)
        }
        BuildingType.SOLAR_POWER -> {
            topFaceColor = Color(0xFF2563EB) // Solar sapphire panels
            leftFaceColor = Color(0xFF1D4ED8)
            rightFaceColor = Color(0xFF1E40AF)
        }
        BuildingType.RESIDENTIAL -> {
            if (cell.level == 0) {
                topFaceColor = Color(0xFF854D0E) // Muddy brown zone lot
                leftFaceColor = Color(0xFF451A03)
                rightFaceColor = Color(0xFF78350F)
            } else {
                topFaceColor = Color(0xFFFCA5A5) // Warm red roof
                leftFaceColor = Color(0xFFDC2626) // Deep brick red
                rightFaceColor = Color(0xFFEF4444)
            }
        }
        BuildingType.COMMERCIAL -> {
            if (cell.level == 0) {
                topFaceColor = Color(0xFF1E3A8A) // Dark blue zone lot
                leftFaceColor = Color(0xFF172554)
                rightFaceColor = Color(0xFF1E40AF)
            } else {
                topFaceColor = Color(0xFF93C5FD) // Sky blue commercial glass
                leftFaceColor = Color(0xFF2563EB) // Royal cobalt blue
                rightFaceColor = Color(0xFF3B82F6)
            }
        }
        BuildingType.INDUSTRIAL -> {
            if (cell.level == 0) {
                topFaceColor = Color(0xFF71717A) // Raw iron zone lot
                leftFaceColor = Color(0xFF27272A)
                rightFaceColor = Color(0xFF52525B)
            } else {
                topFaceColor = Color(0xFFFCD34D) // Amber industrial sheet metal
                leftFaceColor = Color(0xFFD97706) // Heavy copper rust
                rightFaceColor = Color(0xFFF59E0B)
            }
        }
    }

    val halfW = tileWidth / 2f
    val halfH = tileHeight / 2f

    // 1. Draw Left Face (extruding downwards to ground)
    val leftPath = Path().apply {
        moveTo(cx - halfW, cy - blockHeight)
        lineTo(cx, cy + halfH - blockHeight)
        lineTo(cx, cy + halfH)
        lineTo(cx - halfW, cy)
        close()
    }
    drawPath(path = leftPath, color = leftFaceColor)

    // 2. Draw Right Face
    val rightPath = Path().apply {
        moveTo(cx, cy + halfH - blockHeight)
        lineTo(cx + halfW, cy - blockHeight)
        lineTo(cx + halfW, cy)
        lineTo(cx, cy + halfH)
        close()
    }
    drawPath(path = rightPath, color = rightFaceColor)

    // 3. Draw Top Diamond Face
    val topPath = Path().apply {
        moveTo(cx, cy - halfH - blockHeight)
        lineTo(cx + halfW, cy - blockHeight)
        lineTo(cx, cy + halfH - blockHeight)
        lineTo(cx - halfW, cy - blockHeight)
        close()
    }
    drawPath(path = topPath, color = topFaceColor)

    // 4. Draw Black Outlines to preserve crispy Retro feel
    val outlinePath = Path().apply {
        moveTo(cx, cy - halfH - blockHeight)
        lineTo(cx + halfW, cy - blockHeight)
        lineTo(cx, cy + halfH - blockHeight)
        lineTo(cx - halfW, cy - blockHeight)
        close()
        moveTo(cx - halfW, cy - blockHeight)
        lineTo(cx - halfW, cy)
        lineTo(cx, cy + halfH)
        lineTo(cx + halfW, cy)
        lineTo(cx + halfW, cy - blockHeight)
        moveTo(cx, cy + halfH - blockHeight)
        lineTo(cx, cy + halfH)
    }
    drawPath(
        path = outlinePath,
        color = Color(0xFF0F172A),
        style = Stroke(width = 1.5f)
    )

    // 5. Drawing detailed pixel decorations
    if (bType == BuildingType.ROAD) {
        // Road dashed divider lines
        val dividerPath = Path().apply {
            moveTo(cx - 10f, cy)
            lineTo(cx + 10f, cy)
        }
        drawPath(dividerPath, color = Color.White, style = Stroke(width = 1.5f))
    } else if (bType == BuildingType.PARK) {
        // Draw little fountain ring
        drawCircle(
            color = Color(0xFF38BDF8),
            radius = 6f,
            center = Offset(cx, cy - blockHeight),
            style = Stroke(width = 1.5f)
        )
        // Little fountain center dot
        drawCircle(
            color = Color.White,
            radius = 2f + animProgress * 1.5f,
            center = Offset(cx, cy - blockHeight)
        )
    } else if (bType == BuildingType.SOLAR_POWER) {
        // Solar panels grids
        drawLine(
            color = Color.White,
            start = Offset(cx - 15f, cy - blockHeight),
            end = Offset(cx + 15f, cy - blockHeight),
            strokeWidth = 1f
        )
        drawLine(
            color = Color.White,
            start = Offset(cx, cy - halfH / 2f - blockHeight),
            end = Offset(cx, cy + halfH / 2f - blockHeight),
            strokeWidth = 1f
        )
    } else if (bType == BuildingType.RESIDENTIAL && cell.level > 0) {
        // Draw lit pixel-art windows
        val windowColor = if (isBurning) Color(0xFFEF4444) else Color(0xFFFDE047)
        // Left wall window
        drawRect(
            color = windowColor,
            topLeft = Offset(cx - 18f, cy - blockHeight + 6f),
            size = Size(4f, 6f)
        )
        // Right wall window
        drawRect(
            color = windowColor,
            topLeft = Offset(cx + 12f, cy - blockHeight + 6f),
            size = Size(4f, 6f)
        )
        // Level indicators
        if (cell.level >= 2) {
            drawRect(
                color = windowColor,
                topLeft = Offset(cx - 18f, cy - blockHeight + 16f),
                size = Size(4f, 6f)
            )
            drawRect(
                color = windowColor,
                topLeft = Offset(cx + 12f, cy - blockHeight + 16f),
                size = Size(4f, 6f)
            )
        }
    } else if (bType == BuildingType.COMMERCIAL && cell.level > 0) {
        val glassColor = Color(0xFFE0F2FE)
        // Window facade grids
        for (i in 0 until cell.level * 2) {
            drawRect(
                color = glassColor,
                topLeft = Offset(cx - 16f + (i * 6f), cy - blockHeight + 10f),
                size = Size(3f, 8f)
            )
        }
    } else if (bType == BuildingType.INDUSTRIAL && cell.level > 0) {
        // Factory chimneys
        val chimneyLeft = cx + 8f
        val chimneyTop = cy - blockHeight - 8f
        drawRect(color = Color(0xFF52525B), topLeft = Offset(chimneyLeft, chimneyTop), size = Size(6f, 10f))
        // Chimney rim
        drawRect(color = Color.Black, topLeft = Offset(chimneyLeft - 1f, chimneyTop), size = Size(8f, 2f))

        // Industrial smoke rising
        if (cell.isPowered && !isBurning) {
            drawCircle(
                color = Color(0x6694A3B8),
                radius = 3f + animProgress * 3f,
                center = Offset(chimneyLeft + 3f, chimneyTop - 6f - animProgress * 10f)
            )
        }
    }

    // 6. Alert icon overlays (Warning: Missing road or electricity)
    if (bType != BuildingType.EMPTY) {
        if (!cell.isPowered && bType != BuildingType.SOLAR_POWER && bType != BuildingType.ROAD) {
            // Flash electrical warning
            if (animProgress > 0.4f) {
                drawLightningBolt(cx, cy - blockHeight - 12f)
            }
        }
        if (!cell.hasRoadAccess && bType != BuildingType.ROAD) {
            // Flash road warning
            if (animProgress <= 0.6f) {
                drawRoadWarning(cx, cy - blockHeight - 25f)
            }
        }
    }

    // 7. Active Flame overlay if burning
    if (isBurning) {
        drawFireFlame(cx, cy - blockHeight, animProgress)
    }
}

// Draw warning lightning bolts
fun DrawScope.drawLightningBolt(x: Float, y: Float) {
    val boltPath = Path().apply {
        moveTo(x + 2f, y - 8f)
        lineTo(x - 4f, y)
        lineTo(x - 1f, y)
        lineTo(x - 3f, y + 6f)
        lineTo(x + 3f, y - 2f)
        lineTo(x, y - 2f)
        close()
    }
    drawPath(boltPath, color = Color(0xFFFBBF24))
    drawPath(boltPath, color = Color(0xFF78350F), style = Stroke(width = 1f))
}

// Draw road connection alert
fun DrawScope.drawRoadWarning(x: Float, y: Float) {
    drawCircle(color = Color(0xFFEF4444), radius = 6f, center = Offset(x, y))
    drawRect(color = Color.White, topLeft = Offset(x - 1.5f, y - 3f), size = Size(3f, 6f))
}

// Draw fire flames procedurally
fun DrawScope.drawFireFlame(cx: Float, cy: Float, anim: Float) {
    val fireColor = if (anim > 0.5f) Color(0xFFF97316) else Color(0xFFEF4444)
    val yellowSpark = Color(0xFFFDE047)

    val mainFlame = Path().apply {
        moveTo(cx - 10f, cy)
        quadraticTo(cx - 8f, cy - 12f - anim * 8f, cx - 2f, cy - 16f - anim * 12f)
        quadraticTo(cx + 4f, cy - 8f, cx + 10f, cy)
        close()
    }
    drawPath(mainFlame, color = fireColor)

    val innerFlame = Path().apply {
        moveTo(cx - 5f, cy)
        quadraticTo(cx - 3f, cy - 8f - anim * 4f, cx - 1f, cy - 12f - anim * 6f)
        quadraticTo(cx + 2f, cy - 6f, cx + 5f, cy)
        close()
    }
    drawPath(innerFlame, color = yellowSpark)
}

// ==========================================
// DYNAMIC DOCK SYSTEM & STAT PANEL HUDS
// ==========================================
@Composable
fun NewsTickerBar(newsText: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .border(1.dp, Color(0xFF374151), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Campaign,
            contentDescription = "News Flash",
            tint = Color(0xFFF59E0B),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = newsText,
            color = Color(0xFFF3F4F6),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("news_ticker_text")
        )
    }
}

@Composable
fun BuildingPaletteBar(
    selectedTool: BuildingType,
    isDemolishSelected: Boolean,
    onToolSelect: (BuildingType) -> Unit,
    onDemolishSelect: (Boolean) -> Unit
) {
    val toolsList = listOf(
        Pair(BuildingType.EMPTY, Icons.Default.Visibility), // Inspect eye icon
        Pair(BuildingType.ROAD, Icons.Default.EditRoad),
        Pair(BuildingType.RESIDENTIAL, Icons.Default.Home),
        Pair(BuildingType.COMMERCIAL, Icons.Default.Storefront),
        Pair(BuildingType.INDUSTRIAL, Icons.Default.PrecisionManufacturing),
        Pair(BuildingType.SOLAR_POWER, Icons.Default.Bolt),
        Pair(BuildingType.PARK, Icons.Default.LocalFlorist)
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = if (isDemolishSelected) "DEMOLISH MODE (Tap cells to demolish for \$5)" else "BUILD ACTION PALETTE",
            color = if (isDemolishSelected) Color(0xFFF87171) else Color.LightGray,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(toolsList) { (type, icon) ->
                    val isActive = selectedTool == type && !isDemolishSelected
                    BuildingPaletteItem(
                        toolType = type,
                        icon = icon,
                        isActive = isActive,
                        onSelect = {
                            onToolSelect(type)
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Demolish Bulldozer Tool Card
            IconButton(
                onClick = { onDemolishSelect(!isDemolishSelected) },
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isDemolishSelected) Color(0xFFDC2626) else Color(0xFF374151))
                    .border(1.5.dp, if (isDemolishSelected) Color.White else Color.Transparent, RoundedCornerShape(12.dp))
                    .testTag("demolish_tool_button")
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = "Demolish",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }
}

@Composable
fun BuildingPaletteItem(
    toolType: BuildingType,
    icon: ImageVector,
    isActive: Boolean,
    onSelect: () -> Unit
) {
    val highlightColor = when (toolType) {
        BuildingType.EMPTY -> Color(0xFF60A5FA)
        BuildingType.ROAD -> Color(0xFF9CA3AF)
        BuildingType.RESIDENTIAL -> Color(0xFFFCA5A5)
        BuildingType.COMMERCIAL -> Color(0xFF93C5FD)
        BuildingType.INDUSTRIAL -> Color(0xFFFCD34D)
        BuildingType.SOLAR_POWER -> Color(0xFF60A5FA)
        BuildingType.PARK -> Color(0xFF34D399)
    }

    Box(
        modifier = Modifier
            .size(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) highlightColor.copy(alpha = 0.3f) else Color(0xFF1F2937))
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) highlightColor else Color(0xFF374151),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable { onSelect() }
            .testTag("tool_${toolType.name.lowercase()}"),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = toolType.label,
                tint = if (isActive) highlightColor else Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (toolType == BuildingType.EMPTY) "Inspect" else "\$${toolType.cost}",
                color = Color.LightGray,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

// ==========================================
// TOP HEADER CONTROLS VIEW
// ==========================================
@Composable
fun HeaderOverlayPanel(
    gameState: GameStateEntity?,
    speed: Int,
    onSpeedChanged: (Int) -> Unit,
    onRenameClick: () -> Unit,
    onResetClick: () -> Unit,
    onStatsClick: () -> Unit,
    onTriggerEventClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .testTag("header_panel"),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937).copy(alpha = 0.92f)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: City Name and Reset/Stats Menu Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { onRenameClick() }.testTag("city_name_click_area")
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationCity,
                        contentDescription = "Rename",
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = gameState?.cityName ?: "Pixelopolis",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("city_name_text")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit Name",
                        tint = Color.LightGray,
                        modifier = Modifier.size(14.dp)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(
                        onClick = onTriggerEventClick,
                        modifier = Modifier.size(34.dp).testTag("trigger_event_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CrisisAlert,
                            contentDescription = "Trigger Event",
                            tint = Color(0xFFF87171)
                        )
                    }

                    IconButton(
                        onClick = onStatsClick,
                        modifier = Modifier.size(34.dp).testTag("stats_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.BarChart,
                            contentDescription = "City Stats",
                            tint = Color(0xFF34D399)
                        )
                    }

                    IconButton(
                        onClick = onResetClick,
                        modifier = Modifier.size(34.dp).testTag("reset_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset Game",
                            tint = Color.LightGray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 2: Metrics Counters and Speed Toggles
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Budget and Population Info
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AttachMoney,
                                contentDescription = "Budget",
                                tint = Color(0xFF34D399),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "%,d".format((gameState?.budget ?: 10000.0).toInt()),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("budget_text")
                            )
                        }
                        Text(
                            text = "BUDGET",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.People,
                                contentDescription = "Population",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "%,d".format(gameState?.population ?: 0),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("population_text")
                            )
                        }
                        Text(
                            text = "POPULATION",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Column {
                        Text(
                            text = "Month ${gameState?.currentMonth ?: 1}",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.testTag("month_text")
                        )
                        Text(
                            text = "TIMELINE",
                            color = Color.LightGray,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                // Speed HUD
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF111827))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    listOf(
                        Pair(0, Icons.Default.Pause),
                        Pair(1, Icons.Default.PlayArrow),
                        Pair(2, Icons.Default.FastForward)
                    ).forEach { (sValue, icon) ->
                        val isSelected = speed == sValue
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (isSelected) Color(0xFF374151) else Color.Transparent)
                                .clickable { onSpeedChanged(sValue) }
                                .testTag("speed_$sValue"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = "Speed $sValue",
                                tint = if (isSelected) Color(0xFF60A5FA) else Color.LightGray,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // RCI Developer Demand Bars
            RciDemandBars(
                r = gameState?.demandResidential ?: 0.5f,
                c = gameState?.demandCommercial ?: 0.3f,
                i = gameState?.demandIndustrial ?: 0.2f
            )
        }
    }
}

@Composable
fun RciDemandBars(r: Float, c: Float, i: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xFF111827))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text("RCI DEMAND", color = Color.LightGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1.5f))

        Row(
            modifier = Modifier.weight(3.5f),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RciSingleBar(label = "R", value = r, barColor = Color(0xFFF87171), modifier = Modifier.weight(1f))
            RciSingleBar(label = "C", value = c, barColor = Color(0xFF60A5FA), modifier = Modifier.weight(1f))
            RciSingleBar(label = "I", value = i, barColor = Color(0xFFFBBF24), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun RciSingleBar(label: String, value: Float, barColor: Color, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.width(4.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFF374151))
        ) {
            // Horizontal meter centered around zero
            // Map -1..1 to horizontal weights
            val normalizedWidth = (value + 1f) / 2f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(normalizedWidth.coerceIn(0.1f, 1.0f))
                    .background(barColor)
            )
        }
    }
}

// ==========================================
// CELL DETAIL INSPECTOR COMPONENT
// ==========================================
@Composable
fun InspectorCard(
    cell: CityCellEntity,
    onClose: () -> Unit
) {
    val bType = BuildingType.fromString(cell.buildingType)
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2937)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF4B5563), RoundedCornerShape(12.dp))
            .testTag("inspector_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (bType) {
                            BuildingType.EMPTY -> Icons.Default.Terrain
                            BuildingType.ROAD -> Icons.Default.EditRoad
                            BuildingType.RESIDENTIAL -> Icons.Default.Home
                            BuildingType.COMMERCIAL -> Icons.Default.Storefront
                            BuildingType.INDUSTRIAL -> Icons.Default.PrecisionManufacturing
                            BuildingType.SOLAR_POWER -> Icons.Default.Bolt
                            BuildingType.PARK -> Icons.Default.LocalFlorist
                        },
                        contentDescription = bType.label,
                        tint = Color(0xFF60A5FA),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = bType.label + if (cell.level > 0) " (Lvl ${cell.level})" else "",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("inspector_title")
                        )
                        Text(
                            text = "Grid Location: (${cell.x}, ${cell.y})",
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp)) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close Inspector", tint = Color.LightGray)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Divider(color = Color(0xFF374151))
            Spacer(modifier = Modifier.height(10.dp))

            // Stats info grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Happiness", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "${cell.happiness}%",
                        color = when {
                            cell.happiness > 70 -> Color(0xFF34D399)
                            cell.happiness > 40 -> Color(0xFFFBBF24)
                            else -> Color(0xFFF87171)
                        },
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("inspector_happiness")
                    )
                }

                Column {
                    Text("Occupancy", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Text(
                        text = "${cell.occupancy} ${if (bType == BuildingType.RESIDENTIAL) "Residents" else "Workers"}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.testTag("inspector_occupancy")
                    )
                }

                Column {
                    Text("Services", color = Color.LightGray, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Power",
                            tint = if (cell.isPowered) Color(0xFFFBBF24) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.DirectionsCar,
                            contentDescription = "Road",
                            tint = if (cell.hasRoadAccess) Color(0xFF34D399) else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// DISASTER / EMERGENCY OVERLAY BANNER
// ==========================================
@Composable
fun EmergencyBanner(
    event: GameEvent,
    isFireActive: Boolean,
    onExtinguish: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (event.isPositive) Color(0xFF064E3B) else Color(0xFF7F1D1D)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(2.dp, if (event.isPositive) Color(0xFF10B981) else Color(0xFFEF4444), RoundedCornerShape(12.dp))
            .testTag("emergency_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (event.isPositive) Icons.Default.MilitaryTech else Icons.Default.Warning,
                contentDescription = "Alert",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = event.title,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = event.description,
                    color = Color(0xFFE5E7EB),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (event.type == GameEventType.FIRE && isFireActive) {
                    Button(
                        onClick = onExtinguish,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("extinguish_button")
                    ) {
                        Text("EXTINGUISH", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp).testTag("dismiss_event_button")
                ) {
                    Text("DISMISS", color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

// ==========================================
// STATISTICS AND VISUAL ANALYTICS PANEL
// ==========================================
@Composable
fun StatsAnalyticsView(
    cityName: String,
    populationHistory: List<Int>,
    budgetHistory: List<Double>,
    cells: List<CityCellEntity>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1F2937))
            .padding(20.dp)
            .testTag("stats_sheet_content")
    ) {
        Text(
            text = "📊 $cityName CITY STATS",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // General stats recap grid
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            RecapStatCell("Total Roads", cells.count { it.buildingType == BuildingType.ROAD.name }.toString())
            RecapStatCell("Parks Built", cells.count { it.buildingType == BuildingType.PARK.name }.toString())
            RecapStatCell("Residential Lvl 3", cells.count { it.buildingType == BuildingType.RESIDENTIAL.name && it.level == 3 }.toString())
        }

        Divider(color = Color(0xFF374151), modifier = Modifier.padding(bottom = 16.dp))

        // History Charts
        Text(
            text = "BUDGET REVENUE GRAPH (\$)",
            color = Color(0xFF34D399),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111827))
                .padding(8.dp)
        ) {
            BudgetHistoryGraph(budgetHistory)
        }

        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "POPULATION GROWTH GRAPH",
            color = Color(0xFFF87171),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF111827))
                .padding(8.dp)
        ) {
            PopulationHistoryGraph(populationHistory)
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun RecapStatCell(title: String, value: String) {
    Column(
        modifier = Modifier
            .width(100.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF111827))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(title, color = Color.LightGray, fontSize = 9.sp, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

// Custom Draw Canvas retro budget chart
@Composable
fun BudgetHistoryGraph(history: List<Double>) {
    Canvas(modifier = Modifier.fillMaxSize().testTag("budget_chart")) {
        if (history.size < 2) return@Canvas
        val maxVal = max(10000.0, history.maxOrNull() ?: 10000.0)
        val minVal = min(0.0, history.minOrNull() ?: 0.0)
        val delta = maxVal - minVal

        val points = history.mapIndexed { index, value ->
            val rx = (index.toFloat() / (history.size - 1)) * size.width
            val ry = size.height - (((value - minVal) / delta) * size.height).toFloat()
            Offset(rx, ry)
        }

        // Area gradient fill
        val areaPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x5534D399), Color.Transparent)
            )
        )

        // Line Path
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = Color(0xFF34D399), style = Stroke(width = 4f))

        // Grid Lines
        for (i in 1..3) {
            val yPos = (size.height / 4f) * i
            drawLine(
                color = Color(0xFF1F2937),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1.5f
            )
        }
    }
}

// Custom Draw Canvas retro population chart
@Composable
fun PopulationHistoryGraph(history: List<Int>) {
    Canvas(modifier = Modifier.fillMaxSize().testTag("population_chart")) {
        if (history.size < 2) return@Canvas
        val maxVal = max(10, history.maxOrNull() ?: 10)
        val minVal = 0f
        val delta = maxVal - minVal

        val points = history.mapIndexed { index, value ->
            val rx = (index.toFloat() / (history.size - 1)) * size.width
            val ry = size.height - (((value - minVal) / delta) * size.height).toFloat()
            Offset(rx, ry)
        }

        // Area gradient fill
        val areaPath = Path().apply {
            moveTo(points.first().x, size.height)
            points.forEach { lineTo(it.x, it.y) }
            lineTo(points.last().x, size.height)
            close()
        }
        drawPath(
            path = areaPath,
            brush = Brush.verticalGradient(
                colors = listOf(Color(0x55F87171), Color.Transparent)
            )
        )

        // Line Path
        val linePath = Path().apply {
            moveTo(points.first().x, points.first().y)
            points.forEach { lineTo(it.x, it.y) }
        }
        drawPath(linePath, color = Color(0xFFF87171), style = Stroke(width = 4f))

        // Grid Lines
        for (i in 1..3) {
            val yPos = (size.height / 4f) * i
            drawLine(
                color = Color(0xFF1F2937),
                start = Offset(0f, yPos),
                end = Offset(size.width, yPos),
                strokeWidth = 1.5f
            )
        }
    }
}
