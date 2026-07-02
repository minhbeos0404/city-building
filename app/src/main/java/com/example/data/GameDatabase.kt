package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GameDao {
    @Query("SELECT * FROM city_cells ORDER BY id ASC")
    fun getCityCells(): Flow<List<CityCellEntity>>

    @Query("SELECT * FROM game_state WHERE id = 1 LIMIT 1")
    fun getGameState(): Flow<GameStateEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCells(cells: List<CityCellEntity>)

    @Update
    suspend fun updateCell(cell: CityCellEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGameState(state: GameStateEntity)

    @Query("DELETE FROM city_cells")
    suspend fun clearCityCells()

    @Query("DELETE FROM game_state")
    suspend fun clearGameState()
}

@Database(entities = [CityCellEntity::class, GameStateEntity::class], version = 1, exportSchema = false)
abstract class GameDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao

    companion object {
        @Volatile
        private var INSTANCE: GameDatabase? = null

        fun getDatabase(context: Context): GameDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    GameDatabase::class.java,
                    "city_builder_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
