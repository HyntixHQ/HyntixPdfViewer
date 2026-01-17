package com.hyntix.pdf.viewer.util

import android.app.ActivityManager
import android.content.Context

object Constants {
    const val DEBUG_MODE = false // By default

    /** Between 0 and 1, the thumbnails quality (default 0.3). 
     *  Increased to 0.5 to provide better fallback during zoom transitions. */
    const val THUMBNAIL_RATIO = 0.5f

    /**
     * The size of the rendered parts (default 256)
     * TINY = 128
     * LITTLE = 192
     * REGULAR = 256
     * BIG = 384
     * HUGE = 512
     */
    const val PART_SIZE = 512
    
    /**
     * Adaptive tile size based on device memory class.
     * Higher memory devices can handle larger tiles for better quality.
     * Lower memory devices use smaller tiles to prevent OOM.
     */
    object AdaptiveTileSize {
        private var cachedSize: Int? = null
        
        /**
         * Get the optimal tile size for this device.
         * @param context Application context
         * @return Tile size in pixels (256, 384, or 512)
         */
        fun getOptimalSize(context: Context): Int {
            cachedSize?.let { return it }
            
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClass = am.memoryClass // in MB
            
            cachedSize = when {
                memoryClass >= 512 -> 512  // High-end: 512px tiles
                memoryClass >= 256 -> 384  // Mid-range: 384px tiles
                memoryClass >= 128 -> 256  // Low-end: 256px tiles
                else -> 192                 // Very low: 192px tiles
            }
            
            return cachedSize!!
        }
        
        /**
         * Get optimal cache size based on device memory.
         * @param context Application context
         * @return Number of tiles to cache
         */
        fun getOptimalCacheSize(context: Context): Int {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryClass = am.memoryClass
            
            return when {
                memoryClass >= 512 -> 200  // High-end: more tiles
                memoryClass >= 256 -> 150  // Mid-range
                memoryClass >= 128 -> 100  // Low-end
                else -> 50                  // Very low: minimal cache
            }
        }
    }

    /** Part never gets transferred to memory on a separate thread (default 0).  */
    const val PRELOAD_OFFSET = 20
    
    /** Number of pages to preload ahead/behind the current visible page.
     *  Higher values = smoother scrolling but more memory usage.
     *  0 = only visible pages, 1 = +1 page each direction, etc. */
    const val PRELOAD_PAGES = 1

    object Cache {
        /** The size of the cache (number of bitmaps kept)
         *  Increased to support more tiles at high zoom levels.
         *  512px tiles = ~0.5MB each (RGB_565), ~1MB each (ARGB_8888)
         *  150 tiles = ~75-150MB cache
         */
        const val CACHE_SIZE = 150
        /** Thumbnail cache - increased to prevent white flashes during zoom */
        const val THUMBNAILS_CACHE_SIZE = 20
    }

    object Pinch {
        /** Maximum zoom allowed. Set high for "infinite" zoom with quality retention. */
        const val MAXIMUM_ZOOM = 10f
        const val MINIMUM_ZOOM = 1f
    }
}

