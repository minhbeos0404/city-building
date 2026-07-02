package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class GameRepository(private val gameDao: GameDao) {
    val cityCells: Flow<List<CityCellEntity>> = gameDao.getCityCells()
    val gameState: Flow<GameStateEntity?> = gameDao.getGameState()

    suspend fun updateCell(cell: CityCellEntity) {
        gameDao.updateCell(cell)
    }

    suspend fun saveGame(cells: List<CityCellEntity>, state: GameStateEntity) {
        gameDao.insertCells(cells)
        gameDao.insertGameState(state)
    }

    suspend fun resetGame(gridSize: Int, cityName: String = "Pixelopolis") {
        gameDao.clearCityCells()
        gameDao.clearGameState()

        val defaultCells = mutableListOf<CityCellEntity>()
        for (x in 0 until gridSize) {
            for (y in 0 until gridSize) {
                defaultCells.add(
                    CityCellEntity(
                        id = x * 100 + y,
                        x = x,
                        y = y,
                        buildingType = BuildingType.EMPTY.name,
                        level = 0,
                        isPowered = false,
                        hasRoadAccess = false,
                        happiness = 50,
                        occupancy = 0
                    )
                )
            }
        }
        gameDao.insertCells(defaultCells)

        val defaultState = GameStateEntity(
            id = 1,
            budget = 10000.0,
            population = 0,
            demandResidential = 0.5f,
            demandCommercial = 0.3f,
            demandIndustrial = 0.2f,
            currentMonth = 1,
            cityName = cityName
        )
        gameDao.insertGameState(defaultState)
    }

    suspend fun initializeIfEmpty(gridSize: Int) {
        val existingCells = cityCells.firstOrNull()
        if (existingCells.isNullOrEmpty()) {
            resetGame(gridSize)
        }
    }
}
