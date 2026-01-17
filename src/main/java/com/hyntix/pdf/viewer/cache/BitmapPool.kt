package com.hyntix.pdf.viewer.cache

import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Object pool for Bitmap instances to reduce GC pressure during scrolling.
 * Reuses bitmaps of the same size instead of creating new ones.
 * Thread-safe implementation using ConcurrentLinkedQueue and AtomicInteger.
 */
class BitmapPool(private val maxPoolSize: Int = 10) {
    
    companion object {
        private const val TAG = "BitmapPool"
    }
    
    // Separate pools for different bitmap configurations
    private val rgb565Pool = ConcurrentLinkedQueue<Bitmap>()
    private val argb8888Pool = ConcurrentLinkedQueue<Bitmap>()
    
    // Thread-safe counters using AtomicInteger
    private val rgb565PoolSize = AtomicInteger(0)
    private val argb8888PoolSize = AtomicInteger(0)
    
    /**
     * Get a bitmap from the pool or create a new one.
     * @param width Desired width
     * @param height Desired height  
     * @param config Bitmap configuration (RGB_565 or ARGB_8888)
     * @return Bitmap ready for drawing
     */
    fun acquire(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val pool = if (config == Bitmap.Config.RGB_565) rgb565Pool else argb8888Pool
        
        // Try to find a bitmap of the exact size using poll (thread-safe)
        // Poll up to maxPoolSize times to find a matching bitmap
        val tempList = mutableListOf<Bitmap>()
        var found: Bitmap? = null
        
        repeat(maxPoolSize) {
            val bitmap = pool.poll() ?: return@repeat
            
            if (found == null && !bitmap.isRecycled && 
                bitmap.width == width && bitmap.height == height) {
                // Found matching bitmap
                decrementPoolSize(config)
                try {
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    found = bitmap
                } catch (e: Exception) {
                    bitmap.recycle()
                }
            } else if (!bitmap.isRecycled) {
                // Not matching, save for re-adding
                tempList.add(bitmap)
            }
        }
        
        // Re-add non-matching bitmaps back to pool
        tempList.forEach { pool.offer(it) }
        
        // Return found bitmap or create new one
        found?.let { return it }
        
        // No suitable bitmap found, create a new one
        return try {
            Bitmap.createBitmap(width, height, config)
        } catch (e: OutOfMemoryError) {
            // OOM - try to clear pool and retry
            clearPool()
            System.gc()
            Bitmap.createBitmap(width, height, config)
        }
    }
    
    /**
     * Return a bitmap to the pool for reuse.
     * @param bitmap The bitmap to recycle (will be added to pool or recycled)
     */
    fun release(bitmap: Bitmap?) {
        if (bitmap == null || bitmap.isRecycled) return
        
        val config = bitmap.config ?: Bitmap.Config.RGB_565
        
        // Hardware bitmaps cannot be pooled (immutable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && config == Bitmap.Config.HARDWARE) {
            bitmap.recycle()
            return
        }

        val pool = if (config == Bitmap.Config.RGB_565) rgb565Pool else argb8888Pool
        val poolSize = if (config == Bitmap.Config.RGB_565) rgb565PoolSize.get() else argb8888PoolSize.get()
        
        // Check if pool is full
        if (poolSize >= maxPoolSize) {
            // Pool is full, recycle the bitmap
            bitmap.recycle()
            return
        }
        
        // Check if bitmap is too large (> 2MB per bitmap)
        if (bitmap.byteCount > 2 * 1024 * 1024) {
            bitmap.recycle()
            return
        }
        
        // Add to pool
        try {
            pool.offer(bitmap)
            incrementPoolSize(config)
        } catch (e: Exception) {
            bitmap.recycle()
        }
    }
    
    /**
     * Clear all pooled bitmaps.
     */
    fun clearPool() {
        clearPoolInternal(rgb565Pool)
        clearPoolInternal(argb8888Pool)
        rgb565PoolSize.set(0)
        argb8888PoolSize.set(0)
        Log.d(TAG, "Bitmap pool cleared")
    }
    
    private fun clearPoolInternal(pool: ConcurrentLinkedQueue<Bitmap>) {
        while (pool.isNotEmpty()) {
            val bitmap = pool.poll()
            bitmap?.recycle()
        }
    }
    
    private fun incrementPoolSize(config: Bitmap.Config) {
        if (config == Bitmap.Config.RGB_565) {
            rgb565PoolSize.incrementAndGet()
        } else {
            argb8888PoolSize.incrementAndGet()
        }
    }
    
    private fun decrementPoolSize(config: Bitmap.Config) {
        if (config == Bitmap.Config.RGB_565) {
            rgb565PoolSize.updateAndGet { maxOf(0, it - 1) }
        } else {
            argb8888PoolSize.updateAndGet { maxOf(0, it - 1) }
        }
    }
    
    /**
     * Get current pool statistics.
     */
    fun getStats(): String {
        return "BitmapPool[RGB565: ${rgb565PoolSize.get()}/${maxPoolSize}, ARGB8888: ${argb8888PoolSize.get()}/${maxPoolSize}]"
    }
}
