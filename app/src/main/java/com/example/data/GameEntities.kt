package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class BuildingType(val label: String, val cost: Int, val maintenance: Int, val powerUsage: Int, val powerProduction: Int) {
    EMPTY("Empty", 0, 0, 0, 0),
    ROAD("Road", 10, 1, 0, 0),
    RESIDENTIAL("Residential", 50, 2, 5, 0),
    COMMERCIAL("Commercial", 100, 5, 10, 0),
    INDUSTRIAL("Industrial", 150, 7, 15, 0),
    SOLAR_POWER("Solar Power", 300, 15, 0, 50),
    PARK("Park", 80, 4, 2, 0);

    companion object {
        fun fromString(value: String): BuildingType {
            return try {
                valueOf(value)
            } catch (e: Exception) {
                EMPTY
            }
        }
    }
}

@Entity(tableName = "city_cells")
data class CityCellEntity(
    @PrimaryKey val id: Int, // id = x * 100 + y
    val x: Int,
    val y: Int,
    val buildingType: String,
    val level: Int,
    val isPowered: Boolean,
    val hasRoadAccess: Boolean,
    val happiness: Int,
    val occupancy: Int
)

@Entity(tableName = "game_state")
data class GameStateEntity(
    @PrimaryKey val id: Int = 1,
    val budget: Double,
    val population: Int,
    val demandResidential: Float,
    val demandCommercial: Float,
    val demandIndustrial: Float,
    val currentMonth: Int,
    val cityName: String
)
