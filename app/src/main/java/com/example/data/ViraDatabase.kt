package com.example.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultItemDao {
    @Query("SELECT * FROM vault_items ORDER BY timestamp DESC")
    fun getAllVaultItems(): Flow<List<VaultItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItem)

    @Delete
    suspend fun deleteVaultItem(item: VaultItem)

    @Query("DELETE FROM vault_items WHERE pathOrUri = :pathOrUri")
    suspend fun deleteByPath(pathOrUri: String)

    @Query("SELECT COUNT(*) FROM vault_items")
    suspend fun getCount(): Int
}

@Dao
interface CustomAlbumDao {
    @Query("SELECT * FROM custom_albums ORDER BY timestamp DESC")
    fun getAllCustomAlbums(): Flow<List<CustomAlbum>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbum(album: CustomAlbum)

    @Delete
    suspend fun deleteAlbum(album: CustomAlbum)
}

@Dao
interface MediaCreationDao {
    @Query("SELECT * FROM media_creations ORDER BY timestamp DESC")
    fun getAllCreations(): Flow<List<MediaCreation>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCreation(creation: MediaCreation)

    @Delete
    suspend fun deleteCreation(creation: MediaCreation)
}

@Database(
    entities = [VaultItem::class, CustomAlbum::class, MediaCreation::class],
    version = 1,
    exportSchema = false
)
abstract class ViraDatabase : RoomDatabase() {
    abstract fun vaultItemDao(): VaultItemDao
    abstract fun customAlbumDao(): CustomAlbumDao
    abstract fun mediaCreationDao(): MediaCreationDao

    companion object {
        @Volatile
        private var INSTANCE: ViraDatabase? = null

        fun getDatabase(context: Context): ViraDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ViraDatabase::class.java,
                    "vira_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
