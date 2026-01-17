package com.hyntix.pdf.viewer.cache

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.os.Build
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Disk-based cache for rendered PDF tile bitmaps.
 * Caches tiles to the app's cache directory for fast reopening of PDFs.
 * Uses WebP format for 30-50% smaller file sizes.
 */
class DiskCacheManager(context: Context) {
    
    private val cacheDir: File = File(context.cacheDir, "pdf_tiles")
    private val maxCacheSize: Long = 100 * 1024 * 1024 // 100 MB
    private val saveExecutor = Executors.newSingleThreadExecutor()
    
    init {
        if (!cacheDir.exists()) {
            cacheDir.mkdirs()
        }
    }
    
    /**
     * Generate a unique cache key for a tile.
     * @param documentHash Unique identifier for the document (e.g., file path hash)
     * @param page Page index
     * @param bounds Tile bounds (relative coordinates)
     * @param zoom Zoom level
     */
    fun getCacheKey(documentHash: String, page: Int, bounds: RectF, zoom: Float): String {
        val key = "${documentHash}_${page}_${bounds.left}_${bounds.top}_${bounds.right}_${bounds.bottom}_$zoom"
        return md5Hash(key)
    }
    
    /**
     * Check if a tile exists in disk cache.
     */
    fun hasTile(cacheKey: String): Boolean {
        return getTileFile(cacheKey).exists()
    }
    
    /**
     * Load a tile from disk cache.
     * @return Bitmap or null if not found
     */
    fun loadTile(cacheKey: String): Bitmap? {
        val file = getTileFile(cacheKey)
        if (!file.exists()) return null
        
        return try {
            // Update access time for LRU
            file.setLastModified(System.currentTimeMillis())
            // Use ARGB_8888 for maximum device compatibility
            // Hardware bitmaps cause issues on some GPU drivers (MALI, etc.)
            val options = BitmapFactory.Options()
            options.inPreferredConfig = Bitmap.Config.ARGB_8888
            BitmapFactory.decodeFile(file.absolutePath, options)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tile from cache: $cacheKey", e)
            file.delete() // Remove corrupted file
            null
        }
    }
    
    /**
     * Save a tile to disk cache synchronously.
     */
    fun saveTile(cacheKey: String, bitmap: Bitmap) {
        val file = getTileFile(cacheKey)
        try {
            FileOutputStream(file).use { out ->
                // Use WebP for 30-50% smaller files
                val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    Bitmap.CompressFormat.WEBP_LOSSLESS
                } else {
                    @Suppress("DEPRECATION")
                    Bitmap.CompressFormat.WEBP
                }
                bitmap.compress(format, 100, out)
            }
            // Update access time for LRU
            file.setLastModified(System.currentTimeMillis())
            
            // Check if we need to evict old tiles
            evictIfNeeded()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save tile to cache: $cacheKey", e)
        }
    }
    
    /**
     * Save a tile asynchronously to avoid blocking render thread.
     * Creates a copy of the bitmap since original may be recycled.
     */
    fun saveTileAsync(cacheKey: String, bitmap: Bitmap) {
        // Only save if bitmap is valid
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return
        
        // Create a copy since original may be recycled before async save completes
        val copy = try {
            bitmap.copy(bitmap.config ?: Bitmap.Config.RGB_565, false)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to copy bitmap for async save", e)
            return
        }
        
        saveExecutor.execute {
            try {
                saveTile(cacheKey, copy)
            } finally {
                copy.recycle()
            }
        }
    }
    
    /**
     * Clear all cached tiles.
     */
    fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
    }
    
    /**
     * Clear cache for a specific document.
     */
    fun clearDocument(documentHash: String) {
        cacheDir.listFiles()?.filter { it.name.startsWith(documentHash) }?.forEach { it.delete() }
    }
    
    /**
     * Get current cache size in bytes.
     */
    fun getCacheSize(): Long {
        return cacheDir.listFiles()?.sumOf { it.length() } ?: 0
    }
    
    private fun getTileFile(cacheKey: String): File {
        return File(cacheDir, "$cacheKey.webp")
    }
    
    private fun evictIfNeeded() {
        val currentSize = getCacheSize()
        if (currentSize <= maxCacheSize) return
        
        // LRU eviction: delete oldest files first
        // LRU eviction: delete oldest files first
        // Fix: Capture lastModified times before sorting to satisfy sort contract
        val files = cacheDir.listFiles() ?: return
        val filesWithTime = files.map { it to it.lastModified() }
        // Sort by captured timestamp
        val sortedFiles = filesWithTime.sortedBy { it.second }
        
        var freedSpace = 0L
        val targetFree = currentSize - (maxCacheSize * 0.8).toLong() // Free 20%
        
        for ((file, _) in sortedFiles) {
            if (freedSpace >= targetFree) break
            freedSpace += file.length()
            file.delete()
        }
        
        Log.d(TAG, "Evicted ${freedSpace / 1024}KB from disk cache")
    }
    
    private fun md5Hash(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Shutdown the executor when no longer needed.
     */
    fun shutdown() {
        saveExecutor.shutdown()
    }
    
    companion object {
        private const val TAG = "DiskCacheManager"
    }
}
