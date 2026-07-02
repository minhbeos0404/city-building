package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val database = GameDatabase.getDatabase(application)
    private val repository = GameRepository(database.gameDao())

    val GRID_SIZE = 10

    // UI state flows
    private val _cells = MutableStateFlow<List<CityCellEntity>>(emptyList())
    val cells: StateFlow<List<CityCellEntity>> = _cells.asStateFlow()

    private val _gameState = MutableStateFlow<GameStateEntity?>(null)
    val gameState: StateFlow<GameStateEntity?> = _gameState.asStateFlow()

    // Interactive gameplay states
    private val _selectedTool = MutableStateFlow<BuildingType>(BuildingType.RESIDENTIAL)
    val selectedTool: StateFlow<BuildingType> = _selectedTool.asStateFlow()

    private val _isDemolishSelected = MutableStateFlow(false)
    val isDemolishSelected: StateFlow<Boolean> = _isDemolishSelected.asStateFlow()

    private val _inspectedCell = MutableStateFlow<CityCellEntity?>(null)
    val inspectedCell: StateFlow<CityCellEntity?> = _inspectedCell.asStateFlow()

    private val _newsTicker = MutableStateFlow("Welcome to your new city! Build roads and zones to start.")
    val newsTicker: StateFlow<String> = _newsTicker.asStateFlow()

    private val _simulationSpeed = MutableStateFlow(1) // 0 = Paused, 1 = Normal, 2 = Fast
    val simulationSpeed: StateFlow<Int> = _simulationSpeed.asStateFlow()

    // Lists to draw history graphs
    private val _populationHistory = MutableStateFlow<List<Int>>(listOf(0))
    val populationHistory: StateFlow<List<Int>> = _populationHistory.asStateFlow()

    private val _budgetHistory = MutableStateFlow<List<Double>>(listOf(10000.0))
    val budgetHistory: StateFlow<List<Double>> = _budgetHistory.asStateFlow()

    // Random Event States
    private val _activeEvent = MutableStateFlow<GameEvent?>(null)
    val activeEvent: StateFlow<GameEvent?> = _activeEvent.asStateFlow()

    private val _burningCellId = MutableStateFlow<Int?>(null)
    val burningCellId: StateFlow<Int?> = _burningCellId.asStateFlow()

    private var simulationJob: Job? = null

    init {
        viewModelScope.launch {
            repository.initializeIfEmpty(GRID_SIZE)

            // Collect database updates
            launch {
                repository.cityCells.collect { dbCells ->
                    _cells.value = dbCells
                    // Update inspected cell if it changes
                    _inspectedCell.value?.let { current ->
                        _inspectedCell.value = dbCells.find { it.id == current.id }
                    }
                }
            }

            launch {
                repository.gameState.collect { state ->
                    _gameState.value = state
                }
            }

            // Start simulation loop
            startSimulation()
        }
    }

    fun setTool(tool: BuildingType) {
        _selectedTool.value = tool
        _isDemolishSelected.value = false
    }

    fun setDemolishMode(enabled: Boolean) {
        _isDemolishSelected.value = enabled
    }

    fun setSimulationSpeed(speed: Int) {
        _simulationSpeed.value = speed
        startSimulation()
    }

    private fun startSimulation() {
        simulationJob?.cancel()
        if (_simulationSpeed.value == 0) return

        simulationJob = viewModelScope.launch {
            while (true) {
                val delayTime = if (_simulationSpeed.value == 2) 1500L else 3000L
                delay(delayTime)
                runSimulationTick()
            }
        }
    }

    // Grid interaction: Build or Demolish or Inspect
    fun handleCellInteraction(x: Int, y: Int) {
        val cellId = x * 100 + y
        val currentCell = _cells.value.find { it.id == cellId } ?: return

        if (_isDemolishSelected.value) {
            demolishAt(currentCell)
        } else if (_selectedTool.value == BuildingType.EMPTY) {
            // Inspect tool
            _inspectedCell.value = currentCell
        } else {
            // Build tool
            buildAt(currentCell, _selectedTool.value)
        }
    }

    private fun buildAt(cell: CityCellEntity, tool: BuildingType) {
        if (cell.buildingType != BuildingType.EMPTY.name) {
            SoundSynthesizer.playErrorSound()
            _newsTicker.value = "Cell is already occupied! Demolish it first."
            return
        }

        val currentState = _gameState.value ?: return
        if (currentState.budget < tool.cost) {
            SoundSynthesizer.playErrorSound()
            _newsTicker.value = "Insufficient funds to build ${tool.label}! Costs \$${tool.cost}."
            return
        }

        // Deduct money & update cell
        val updatedBudget = currentState.budget - tool.cost
        val updatedCell = cell.copy(
            buildingType = tool.name,
            level = if (tool == BuildingType.SOLAR_POWER || tool == BuildingType.PARK || tool == BuildingType.ROAD) 1 else 0,
            happiness = 50,
            occupancy = 0
        )

        viewModelScope.launch {
            repository.updateCell(updatedCell)
            val updatedState = currentState.copy(budget = updatedBudget)
            repository.saveGame(_cells.value.map { if (it.id == updatedCell.id) updatedCell else it }, updatedState)
            SoundSynthesizer.playBuildSound()
            _newsTicker.value = "Successfully built ${tool.label} for \$${tool.cost}."
        }
    }

    private fun demolishAt(cell: CityCellEntity) {
        if (cell.buildingType == BuildingType.EMPTY.name) {
            return
        }

        val cost = 5.0 // Demolish cost
        val currentState = _gameState.value ?: return
        if (currentState.budget < cost) {
            SoundSynthesizer.playErrorSound()
            _newsTicker.value = "Insufficient funds to demolish! Costs \$5."
            return
        }

        // Check if demolishing a burning building
        if (cell.id == _burningCellId.value) {
            _burningCellId.value = null
            _activeEvent.value = null
        }

        val updatedBudget = currentState.budget - cost
        val updatedCell = cell.copy(
            buildingType = BuildingType.EMPTY.name,
            level = 0,
            isPowered = false,
            hasRoadAccess = false,
            happiness = 50,
            occupancy = 0
        )

        viewModelScope.launch {
            repository.updateCell(updatedCell)
            val updatedState = currentState.copy(budget = updatedBudget)
            repository.saveGame(_cells.value.map { if (it.id == updatedCell.id) updatedCell else it }, updatedState)
            SoundSynthesizer.playDemolishSound()
            _newsTicker.value = "Demolished building for \$5."
        }
    }

    fun extinguishFire() {
        val burningId = _burningCellId.value ?: return
        val currentCell = _cells.value.find { it.id == burningId } ?: return

        _burningCellId.value = null
        _activeEvent.value = null

        val updatedCell = currentCell.copy(happiness = 30) // Low happiness, but saved!
        viewModelScope.launch {
            repository.updateCell(updatedCell)
            _newsTicker.value = "Fire extinguished! The citizens thank you, Mayor!"
            SoundSynthesizer.playLevelUpSound()
        }
    }

    fun triggerRandomEvent() {
        if (_activeEvent.value != null) return // Only one active event at a time

        val r = Random.nextInt(4)
        when (r) {
            0 -> { // Fire
                // Find a populated / built cell
                val builtCells = _cells.value.filter { 
                    it.buildingType != BuildingType.EMPTY.name && it.buildingType != BuildingType.ROAD.name
                }
                if (builtCells.isNotEmpty()) {
                    val target = builtCells.random()
                    _burningCellId.value = target.id
                    _activeEvent.value = GameEvent(
                        title = "FIRE OUTBREAK!",
                        description = "A massive fire started in a ${BuildingType.fromString(target.buildingType).label} at (${target.x}, ${target.y})! Tap Extinguish to save it!",
                        isPositive = false,
                        type = GameEventType.FIRE
                    )
                    SoundSynthesizer.playErrorSound()
                    _newsTicker.value = "🚨 EMERGENCY: Fire outbreak at (${target.x}, ${target.y})!"
                }
            }
            1 -> { // Economic Boom
                _activeEvent.value = GameEvent(
                    title = "ECONOMIC BOOM!",
                    description = "Commercial and Industrial demands have surged, and tax revenues are doubled for this month!",
                    isPositive = true,
                    type = GameEventType.BOOM
                )
                _newsTicker.value = "📈 NEWS: Stock market hit all-time highs! Local businesses are thriving."
                SoundSynthesizer.playLevelUpSound()
            }
            2 -> { // Meteor Strike
                val targetX = Random.nextInt(GRID_SIZE)
                val targetY = Random.nextInt(GRID_SIZE)
                val cellId = targetX * 100 + targetY
                val target = _cells.value.find { it.id == cellId }

                _activeEvent.value = GameEvent(
                    title = "METEOR STRIKE!",
                    description = "A rogue space rock slammed into (${targetX}, ${targetY})!",
                    isPositive = false,
                    type = GameEventType.METEOR
                )

                if (target != null && target.buildingType != BuildingType.EMPTY.name) {
                    val craterCell = target.copy(
                        buildingType = BuildingType.EMPTY.name,
                        level = 0,
                        occupancy = 0,
                        happiness = 0
                    )
                    viewModelScope.launch {
                        repository.updateCell(craterCell)
                    }
                    _newsTicker.value = "💥 DISASTER: Meteor struck (${targetX}, ${targetY})!"
                } else {
                    _newsTicker.value = "☄️ CLOSE CALL: Meteor struck empty fields at (${targetX}, ${targetY})!"
                }
                SoundSynthesizer.playDemolishSound()
            }
            3 -> { // Festival
                _activeEvent.value = GameEvent(
                    title = "CITY FESTIVAL!",
                    description = "Citizens are celebrating the Mayor's amazing management! Happiness is boosted city-wide.",
                    isPositive = true,
                    type = GameEventType.FESTIVAL
                )
                _newsTicker.value = "🎉 FESTIVAL: Local parade is filling the streets with joy!"
                SoundSynthesizer.playLevelUpSound()
            }
        }
    }

    fun dismissEvent() {
        _activeEvent.value = null
    }

    fun resetWholeCity(cityName: String) {
        viewModelScope.launch {
            repository.resetGame(GRID_SIZE, cityName)
            _populationHistory.value = listOf(0)
            _budgetHistory.value = listOf(10000.0)
            _burningCellId.value = null
            _activeEvent.value = null
            _newsTicker.value = "City of $cityName founded! Make your citizens proud."
            SoundSynthesizer.playLevelUpSound()
        }
    }

    // Core Game Simulation Tick
    private suspend fun runSimulationTick() {
        val currentState = _gameState.value ?: return
        val currentCellsList = _cells.value.toMutableList()

        // 1. Resolve Power & Road Access connectivity
        // Power sources
        val powerPlants = currentCellsList.filter { it.buildingType == BuildingType.SOLAR_POWER.name }
        val poweredCellIds = mutableSetOf<Int>()

        // Add solar plants as seed powered tiles
        powerPlants.forEach { poweredCellIds.add(it.id) }

        // BFS or simple propagation through connected non-empty cells
        // Let's loop a few times to propagate power outward through adjacent roads and structures
        var powerChanged = true
        var passes = 0
        while (powerChanged && passes < 12) {
            powerChanged = false
            passes++
            for (cell in currentCellsList) {
                if (cell.id in poweredCellIds) continue
                if (cell.buildingType == BuildingType.EMPTY.name) continue

                // Check neighbors
                val neighbors = getNeighbors(cell.x, cell.y)
                val anyPoweredNeighbor = neighbors.any { n -> (n.x * 100 + n.y) in poweredCellIds }
                if (anyPoweredNeighbor) {
                    poweredCellIds.add(cell.id)
                    powerChanged = true
                }
            }
        }

        // 2. Perform updates for each cell
        var totalPopulation = 0
        var totalTaxes = 0.0
        var totalMaintenance = 0.0
        var totalPowerRequired = 0
        var isAnyLevelUp = false

        val event = _activeEvent.value

        val updatedCells = currentCellsList.map { cell ->
            if (cell.buildingType == BuildingType.EMPTY.name) return@map cell

            val bType = BuildingType.fromString(cell.buildingType)
            totalMaintenance += bType.maintenance

            // Check road access (adjacent orthogonal cells containing road)
            val neighbors = getNeighbors(cell.x, cell.y)
            val hasRoad = neighbors.any { it.buildingType == BuildingType.ROAD.name }
            val isPowered = cell.id in poweredCellIds

            totalPowerRequired += bType.powerUsage

            // Calculate local happiness
            var localHappiness = 50
            if (!hasRoad) localHappiness -= 20
            if (!isPowered) localHappiness -= 30

            // Neighbor impacts
            // Parks nearby (distance 1 or 2)
            val nearbyParks = currentCellsList.filter {
                it.buildingType == BuildingType.PARK.name && (abs(it.x - cell.x) + abs(it.y - cell.y)) <= 2
            }
            localHappiness += nearbyParks.size * 15

            // Industrial nearby (pollution reduces happiness for residential)
            if (cell.buildingType == BuildingType.RESIDENTIAL.name) {
                val nearbyIndustrial = currentCellsList.filter {
                    it.buildingType == BuildingType.INDUSTRIAL.name && (abs(it.x - cell.x) + abs(it.y - cell.y)) <= 2
                }
                localHappiness -= nearbyIndustrial.size * 12
            }

            // Fire impact
            if (cell.id == _burningCellId.value) {
                localHappiness = 0
            }

            // Festival bonus
            if (event?.type == GameEventType.FESTIVAL) {
                localHappiness += 25
            }

            localHappiness = max(0, min(100, localHappiness))

            // Growth logic
            var updatedLevel = cell.level
            var updatedOccupancy = cell.occupancy

            if (hasRoad && isPowered && cell.id != _burningCellId.value) {
                // Growth triggers based on demand
                when (cell.buildingType) {
                    BuildingType.RESIDENTIAL.name -> {
                        if (currentState.demandResidential > 0.1f && updatedLevel < 3 && localHappiness > 45 && Random.nextFloat() < 0.25f) {
                            updatedLevel++
                            isAnyLevelUp = true
                        }
                        // Update occupancy based on level
                        updatedOccupancy = when (updatedLevel) {
                            0 -> 0
                            1 -> 5
                            2 -> 15
                            3 -> 40
                            else -> 40
                        }
                        totalPopulation += updatedOccupancy
                        // Taxes: $1 per resident
                        totalTaxes += updatedOccupancy * 1.0
                    }
                    BuildingType.COMMERCIAL.name -> {
                        if (currentState.demandCommercial > 0.1f && updatedLevel < 3 && localHappiness > 45 && Random.nextFloat() < 0.25f) {
                            updatedLevel++
                            isAnyLevelUp = true
                        }
                        updatedOccupancy = when (updatedLevel) {
                            0 -> 0
                            1 -> 3
                            2 -> 10
                            3 -> 25
                            else -> 25
                        }
                        totalTaxes += updatedOccupancy * 3.0
                    }
                    BuildingType.INDUSTRIAL.name -> {
                        if (currentState.demandIndustrial > 0.1f && updatedLevel < 3 && localHappiness > 40 && Random.nextFloat() < 0.25f) {
                            updatedLevel++
                            isAnyLevelUp = true
                        }
                        updatedOccupancy = when (updatedLevel) {
                            0 -> 0
                            1 -> 4
                            2 -> 12
                            3 -> 30
                            else -> 30
                        }
                        totalTaxes += updatedOccupancy * 4.0
                    }
                }
            } else {
                // Decay / Abandonment if services are missing
                if (updatedLevel > 0 && Random.nextFloat() < 0.3f) {
                    updatedLevel--
                }
                updatedOccupancy = max(0, updatedOccupancy - 5)
                if (cell.buildingType == BuildingType.RESIDENTIAL.name) {
                    totalPopulation += updatedOccupancy
                    totalTaxes += updatedOccupancy * 0.5
                } else if (cell.buildingType == BuildingType.COMMERCIAL.name) {
                    totalTaxes += updatedOccupancy * 1.0
                } else if (cell.buildingType == BuildingType.INDUSTRIAL.name) {
                    totalTaxes += updatedOccupancy * 1.5
                }
            }

            cell.copy(
                isPowered = isPowered,
                hasRoadAccess = hasRoad,
                happiness = localHappiness,
                level = updatedLevel,
                occupancy = updatedOccupancy
            )
        }

        // Apply Economic Boom event multiplier
        if (event?.type == GameEventType.BOOM) {
            totalTaxes *= 1.8
        }

        // 3. Balance RCI demands based on current state
        val residentialDemandChange = if (totalPopulation < 10) 0.1f else {
            // High demand if jobs exist, low if no jobs
            val totalJobs = updatedCells.filter {
                it.buildingType == BuildingType.COMMERCIAL.name || it.buildingType == BuildingType.INDUSTRIAL.name
            }.sumOf { it.occupancy }
            if (totalJobs > totalPopulation) 0.15f else -0.1f
        }

        val commercialDemandChange = if (totalPopulation > 20) {
            val totalComJobs = updatedCells.filter { it.buildingType == BuildingType.COMMERCIAL.name }.sumOf { it.occupancy }
            if (totalPopulation > totalComJobs * 2) 0.1f else -0.05f
        } else 0.05f

        val industrialDemandChange = if (totalPopulation > 10) {
            val totalIndJobs = updatedCells.filter { it.buildingType == BuildingType.INDUSTRIAL.name }.sumOf { it.occupancy }
            if (totalPopulation > totalIndJobs * 2.5) 0.08f else -0.04f
        } else 0.03f

        val updatedDemandR = max(-1.0f, min(1.0f, currentState.demandResidential + residentialDemandChange + (Random.nextFloat() - 0.5f) * 0.1f))
        val updatedDemandC = max(-1.0f, min(1.0f, currentState.demandCommercial + commercialDemandChange + (Random.nextFloat() - 0.5f) * 0.08f))
        val updatedDemandI = max(-1.0f, min(1.0f, currentState.demandIndustrial + industrialDemandChange + (Random.nextFloat() - 0.5f) * 0.08f))

        // Update financial budget
        val netIncome = totalTaxes - totalMaintenance
        val updatedBudget = max(0.0, currentState.budget + netIncome)

        // Increment Month
        val updatedMonth = currentState.currentMonth + 1

        // History lists (limit to 30 points)
        val newPopHist = (_populationHistory.value + totalPopulation).takeLast(30)
        val newBudHist = (_budgetHistory.value + updatedBudget).takeLast(30)

        _populationHistory.value = newPopHist
        _budgetHistory.value = newBudHist

        // Sound cues for milestones
        if (isAnyLevelUp) {
            SoundSynthesizer.playLevelUpSound()
        }

        // Mayor Advisory & News Ticker Updates based on city balance
        val totalCapacityPower = powerPlants.size * BuildingType.SOLAR_POWER.powerProduction
        val powerDeficit = totalPowerRequired > totalCapacityPower

        val tickerText = when {
            _burningCellId.value != null -> "🚨 EMERGENCY: Active fire reporting in the city!"
            powerDeficit -> "⚡ BLACKOUT THREAT: Power demand exceeds supply! Build more solar plants."
            totalPopulation == 0 -> "🏢 RCI UPDATE: Design residential zones and connect them with roads to attract citizens!"
            netIncome < 0 -> "⚠️ BUDGET DEFICIT: Maintenance cost (\$${"%.1f".format(totalMaintenance)}) exceeds tax revenue (\$${"%.1f".format(totalTaxes)}). Check zones!"
            updatedMonth % 6 == 0 -> "🌳 MAYOR REPORT: City of ${currentState.cityName} is now ${updatedMonth / 12} years old with ${totalPopulation} residents!"
            else -> "✨ STATUS: City running smoothly. Monthly net income: \$${"%.1f".format(netIncome)}."
        }
        _newsTicker.value = tickerText

        // Random chance of triggering events (approx. 5% per month)
        if (Random.nextFloat() < 0.05f && _activeEvent.value == null) {
            triggerRandomEvent()
        }

        // Save back to DB
        val updatedState = currentState.copy(
            budget = updatedBudget,
            population = totalPopulation,
            demandResidential = updatedDemandR,
            demandCommercial = updatedDemandC,
            demandIndustrial = updatedDemandI,
            currentMonth = updatedMonth
        )

        repository.saveGame(updatedCells, updatedState)
    }

    private fun getNeighbors(x: Int, y: Int): List<CityCellEntity> {
        val list = mutableListOf<CityCellEntity>()
        val directions = listOf(
            Pair(-1, 0), Pair(1, 0), Pair(0, -1), Pair(0, 1)
        )
        for (dir in directions) {
            val nx = x + dir.first
            val ny = y + dir.second
            if (nx in 0 until GRID_SIZE && ny in 0 until GRID_SIZE) {
                _cells.value.find { it.x == nx && it.y == ny }?.let {
                    list.add(it)
                }
            }
        }
        return list
    }
}

enum class GameEventType {
    FIRE, METEOR, BOOM, FESTIVAL
}

data class GameEvent(
    val title: String,
    val description: String,
    val isPositive: Boolean,
    val type: GameEventType
)
