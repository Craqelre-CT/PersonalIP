package com.personalip.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.personalip.app.data.local.entity.PersonaEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonaDao {
    @Query("SELECT * FROM persona WHERE id = 1 LIMIT 1")
    fun observe(): Flow<PersonaEntity?>

    @Query("SELECT * FROM persona WHERE id = 1 LIMIT 1")
    suspend fun get(): PersonaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(persona: PersonaEntity)
}
