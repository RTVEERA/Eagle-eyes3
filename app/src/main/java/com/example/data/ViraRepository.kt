package com.example.data

import android.content.ContentResolver
import android.content.Context
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ViraRepository(private val db: ViraDatabase) {

    val vaultItems: Flow<List<VaultItem>> = db.vaultItemDao().getAllVaultItems()
    val customAlbums: Flow<List<CustomAlbum>> = db.customAlbumDao().getAllCustomAlbums()
    val mediaCreations: Flow<List<MediaCreation>> = db.mediaCreationDao().getAllCreations()

    // Database actions (Marked as suspend according to Room guidelines)
    suspend fun insertVaultItem(item: VaultItem) = withContext(Dispatchers.IO) {
        db.vaultItemDao().insertVaultItem(item)
    }

    suspend fun removeVaultItem(item: VaultItem) = withContext(Dispatchers.IO) {
        db.vaultItemDao().deleteVaultItem(item)
    }

    suspend fun deleteVaultItemByPath(pathOrUri: String) = withContext(Dispatchers.IO) {
        db.vaultItemDao().deleteByPath(pathOrUri)
    }

    suspend fun getVaultCount(): Int = withContext(Dispatchers.IO) {
        db.vaultItemDao().getCount()
    }

    suspend fun insertCustomAlbum(album: CustomAlbum) = withContext(Dispatchers.IO) {
        db.customAlbumDao().insertAlbum(album)
    }

    suspend fun deleteCustomAlbum(album: CustomAlbum) = withContext(Dispatchers.IO) {
        db.customAlbumDao().deleteAlbum(album)
    }

    suspend fun insertMediaCreation(creation: MediaCreation) = withContext(Dispatchers.IO) {
        db.mediaCreationDao().insertCreation(creation)
    }

    suspend fun deleteMediaCreation(creation: MediaCreation) = withContext(Dispatchers.IO) {
        db.mediaCreationDao().deleteCreation(creation)
    }

    /**
     * Scans the actual Android device database for images and videos using ContentResolver.
     * Combines them with our rich Indian template samples so the app ALWAYS has beautiful, professional content.
     */
    suspend fun fetchAllGalleryItems(context: Context, showVaulted: Boolean = false): List<GalleryMediaItem> = withContext(Dispatchers.IO) {
        val list = mutableListOf<GalleryMediaItem>()

        // 1. First, always add Vira-custom premium sample files as the artistic base of the app
        val samples = getIndianPreloadedSamples()
        
        // Filter out items that are currently in the vault
        val vaultedPaths = db.vaultItemDao().getAllVaultItems().first().map { it.pathOrUri }.toSet()
        
        list.addAll(samples.filter { showVaulted || it.uriOrPath !in vaultedPaths })

        // 2. Scan the actual device hardware if permission is granted
        try {
            val contentResolver: ContentResolver = context.contentResolver
            
            // Scan Images
            val imageUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val imageProjection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED
            )
            
            contentResolver.query(
                imageUri, imageProjection, null, null, 
                "${MediaStore.Images.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 100) { // Limit to 100 for speed
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Image_$id"
                    val bucketName = cursor.getString(bucketCol) ?: "Camera"
                    val size = cursor.getLong(sizeCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000 // Convert to MS
                    
                    val pathUri = "${imageUri}/${id}"
                    
                    if (showVaulted || pathUri !in vaultedPaths) {
                        list.add(
                            GalleryMediaItem(
                                id = "device_img_$id",
                                title = name,
                                folderName = bucketName,
                                uriOrPath = pathUri,
                                isVideo = false,
                                sizeString = formatSize(size),
                                dateAdded = dateAdded,
                                isLocalSample = false
                            )
                        )
                    }
                    count++
                }
            }

            // Scan Videos
            val videoUri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            val videoProjection = arrayOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.DISPLAY_NAME,
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.DATE_ADDED
            )
            
            contentResolver.query(
                videoUri, videoProjection, null, null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)

                var count = 0
                while (cursor.moveToNext() && count < 50) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: "Video_$id"
                    val bucketName = cursor.getString(bucketCol) ?: "Videos"
                    val size = cursor.getLong(sizeCol)
                    val duration = cursor.getLong(durCol)
                    val dateAdded = cursor.getLong(dateCol) * 1000
                    
                    val pathUri = "${videoUri}/${id}"
                    
                    if (showVaulted || pathUri !in vaultedPaths) {
                        list.add(
                            GalleryMediaItem(
                                id = "device_vid_$id",
                                title = name,
                                folderName = bucketName,
                                uriOrPath = pathUri,
                                isVideo = true,
                                durationString = formatDuration(duration),
                                sizeString = formatSize(size),
                                dateAdded = dateAdded,
                                isLocalSample = false
                            )
                        )
                    }
                    count++
                }
            }
        } catch (e: SecurityException) {
            Log.e("ViraRepository", "Storage permissions not yet granted. Custom stock files shown.", e)
        } catch (e: Exception) {
            Log.e("ViraRepository", "Error scanning device media", e)
        }

        // Add user configurations from creations to main list
        val creations = db.mediaCreationDao().getAllCreations().first()
        for (c in creations) {
            val creationPath = "creations_${c.id}"
            if (showVaulted || creationPath !in vaultedPaths) {
                list.add(
                    0, // Prepend creations showing newest first
                    GalleryMediaItem(
                        id = "creation_${c.id}",
                        title = c.title,
                        folderName = "My Creations",
                        uriOrPath = c.originalSourceUri, // Keep representation of back source
                        isVideo = c.mediaType == "video",
                        durationString = if (c.mediaType == "video") "0:05" else null,
                        sizeString = "2.4 MB",
                        dateAdded = c.timestamp,
                        isLocalSample = true, // is editable asset
                        isFavorite = true
                    )
                )
            }
        }

        return@withContext list
    }

    private fun formatSize(size: Long): String {
        if (size <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB")
        val digitGroups = (Math.log10(size.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", size / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun formatDuration(durationMs: Long): String {
        val seconds = (durationMs / 1000) % 60
        val minutes = (durationMs / (1000 * 60)) % 60
        val hours = (durationMs / (1000 * 60 * 60))
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }

    /**
     * Exquisite stock Indian content representing tourist milestones, digital hub aesthetic,
     * and majestic cultural locations to showcase the CapCut style filters, text overlays and animations instantly.
     */
    fun getIndianPreloadedSamples(): List<GalleryMediaItem> {
        return listOf(
            GalleryMediaItem(
                id = "sample_taj",
                title = "Taj Mahal Sunset Glow",
                folderName = "Wonders of India",
                uriOrPath = "https://images.unsplash.com/photo-1564507592333-c60657eea523?w=800&auto=format&fit=crop&q=80",
                isVideo = false,
                isFavorite = true
            ),
            GalleryMediaItem(
                id = "sample_goa",
                title = "Goa Shore Golden Waves",
                folderName = "Coastlines",
                uriOrPath = "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800&auto=format&fit=crop&q=80",
                isVideo = true,
                durationString = "0:12"
            ),
            GalleryMediaItem(
                id = "sample_ganga",
                title = "Varanasi Ganga Devotional Aarti",
                folderName = "Spirituality",
                uriOrPath = "https://images.unsplash.com/photo-1561361531-99522c3aee60?w=800&auto=format&fit=crop&q=80",
                isVideo = false,
                isFavorite = true
            ),
            GalleryMediaItem(
                id = "sample_ladakh",
                title = "Ladakh Pangong Lake Breeze",
                folderName = "Highlands",
                uriOrPath = "https://images.unsplash.com/photo-1581791534721-e5993473fd9c?w=800&auto=format&fit=crop&q=80",
                isVideo = true,
                durationString = "0:15"
            ),
            GalleryMediaItem(
                id = "sample_kerala",
                title = "Kerala Backwaters Houseboat",
                folderName = "Wonders of India",
                uriOrPath = "https://images.unsplash.com/photo-1593693397690-362cb9666fc2?w=800&auto=format&fit=crop&q=80",
                isVideo = false
            ),
            GalleryMediaItem(
                id = "sample_jaipur",
                title = "Jaipur Hawa Mahal Crimson Gates",
                folderName = "Heritage",
                uriOrPath = "https://images.unsplash.com/photo-1477584322904-487a554d3e47?w=800&auto=format&fit=crop&q=80",
                isVideo = false
            ),
            GalleryMediaItem(
                id = "sample_darjeeling",
                title = "Darjeeling Sunrise Tea Slopes",
                folderName = "Highlands",
                uriOrPath = "https://images.unsplash.com/photo-1555899434-94d1368aa7af?w=800&auto=format&fit=crop&q=80",
                isVideo = true,
                durationString = "0:08"
            ),
            GalleryMediaItem(
                id = "sample_bangalore",
                title = "Namma Bengaluru Silicon Lights",
                folderName = "Metro Vibe",
                uriOrPath = "https://images.unsplash.com/photo-1596176530529-78163a4f7af2?w=800&auto=format&fit=crop&q=80",
                isVideo = false
            )
        )
    }
}
