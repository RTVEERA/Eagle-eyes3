package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing items locked in the secure Vault.
 */
@Entity(tableName = "vault_items")
data class VaultItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val pathOrUri: String,
    val isLocalSample: Boolean,
    val mediaType: String, // "image" or "video"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entity representing custom folders/albums created by the user within the app.
 */
@Entity(tableName = "custom_albums")
data class CustomAlbum(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val folderName: String,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Entity representing custom edits exported from the built-in Editor suite.
 */
@Entity(tableName = "media_creations")
data class MediaCreation(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val originalSourceUri: String,
    val filterApplied: String,
    val textOverlay: String,
    val mediaType: String, // "image" or "video"
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Representation of a general Media Item (used for gallery UI).
 * It represents either a real system file or a built-in preloaded standard asset.
 */
data class GalleryMediaItem(
    val id: String,
    val title: String,
    val folderName: String,
    val uriOrPath: String,
    val isVideo: Boolean,
    val durationString: String? = null,
    val sizeString: String = "1.2 MB",
    val dateAdded: Long = System.currentTimeMillis() - (86400000 * (0..10).random()),
    val isLocalSample: Boolean = true,
    val isFavorite: Boolean = false
)
