package com.hyntix.pdf.viewer

import android.graphics.RectF
import com.hyntix.pdf.viewer.model.PagePart
import com.hyntix.pdf.viewer.util.Constants.Cache.CACHE_SIZE
import com.hyntix.pdf.viewer.util.Constants.Cache.THUMBNAILS_CACHE_SIZE
import java.util.PriorityQueue

internal class CacheManager(private val bitmapPool: com.hyntix.pdf.viewer.cache.BitmapPool? = null) {
    private val passiveCache: PriorityQueue<PagePart>
    private val activeCache: PriorityQueue<PagePart>
    internal val thumbnails: MutableList<PagePart>
    private val passiveActiveLock = Any()
    private val orderComparator = PagePartComparator()

    init {
        activeCache = PriorityQueue(CACHE_SIZE, orderComparator)
        passiveCache = PriorityQueue(CACHE_SIZE, orderComparator)
        thumbnails = ArrayList()
    }

    fun cachePart(part: PagePart) {
        synchronized(passiveActiveLock) {
            // If cache too big, remove and recycle
            makeAFreeSpace()

            // Then add part
            activeCache.offer(part)
        }
    }

    fun makeANewSet() {
        synchronized(passiveActiveLock) {
            passiveCache.addAll(activeCache)
            activeCache.clear()
        }
    }

    private fun makeAFreeSpace() {
        synchronized(passiveActiveLock) {
            while (activeCache.size + passiveCache.size >= CACHE_SIZE &&
                !passiveCache.isEmpty()
            ) {
                val part = passiveCache.poll()
                bitmapPool?.release(part?.renderedBitmap)
            }

            while (activeCache.size + passiveCache.size >= CACHE_SIZE &&
                !activeCache.isEmpty()
            ) {
                val part = activeCache.poll()
                bitmapPool?.release(part?.renderedBitmap)
            }
        }
    }

    fun cacheThumbnail(part: PagePart) {
        synchronized(thumbnails) {
            // If cache too big, remove and recycle
            while (thumbnails.size >= THUMBNAILS_CACHE_SIZE) {
                val part = thumbnails.removeAt(0)
                bitmapPool?.release(part.renderedBitmap)
            }

            // Then add thumbnail
            addWithoutDuplicates(thumbnails, part)
        }
    }

    fun upPartIfContained(page: Int, pageRelativeBounds: RectF, toOrder: Int): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, false, 0)
        var found: PagePart?
        synchronized(passiveActiveLock) {
            found = find(passiveCache, fakePart)
            if (found != null) {
                passiveCache.remove(found)
                found.cacheOrder = toOrder
                activeCache.offer(found)
                return true
            }
            return find(activeCache, fakePart) != null
        }
    }

    /**
     * Return true if already contains the described PagePart
     */
    fun containsThumbnail(page: Int, pageRelativeBounds: RectF): Boolean {
        val fakePart = PagePart(page, null, pageRelativeBounds, true, 0)
        synchronized(thumbnails) {
            for (part in thumbnails) {
                if (part == fakePart) {
                    return true
                }
            }
            return false
        }
    }

    /**
     * Add part if it doesn't exist, recycle bitmap otherwise
     */
    private fun addWithoutDuplicates(collection: MutableCollection<PagePart>, newPart: PagePart) {
        for (part in collection) {
            if (part == newPart) {
                bitmapPool?.release(newPart.renderedBitmap)
                return
            }
        }
        collection.add(newPart)
    }

    private fun find(vector: PriorityQueue<PagePart>, fakePart: PagePart): PagePart? {
        for (part in vector) {
            if (part == fakePart) {
                return part
            }
        }
        return null
    }

    val pageParts: List<PagePart>
        get() {
            synchronized(passiveActiveLock) {
                val parts: MutableList<PagePart> = ArrayList(passiveCache)
                parts.addAll(activeCache)
                return parts
            }
        }

    fun getThumbnails(): List<PagePart> {
        synchronized(thumbnails) {
            return thumbnails.toList()  // Return defensive copy
        }
    }

    fun recycle() {
        synchronized(passiveActiveLock) {
            for (part in passiveCache) {
                bitmapPool?.release(part.renderedBitmap)
            }
            passiveCache.clear()
            for (part in activeCache) {
                bitmapPool?.release(part.renderedBitmap)
            }
            activeCache.clear()
        }
        synchronized(thumbnails) {
            for (part in thumbnails) {
                bitmapPool?.release(part.renderedBitmap)
            }
            thumbnails.clear()
        }
    }

    internal class PagePartComparator : Comparator<PagePart> {
        override fun compare(part1: PagePart, part2: PagePart): Int {
            if (part1.cacheOrder == part2.cacheOrder) {
                return 0
            }
            return if (part1.cacheOrder > part2.cacheOrder) 1 else -1
        }
    }
}
