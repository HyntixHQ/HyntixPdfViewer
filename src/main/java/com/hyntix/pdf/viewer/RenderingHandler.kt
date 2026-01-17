/**
 * Copyright 2016 Bartosz Schiller
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hyntix.pdf.viewer

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import com.hyntix.pdf.viewer.cache.DiskCacheManager
import com.hyntix.pdf.viewer.exception.PageRenderingException
import com.hyntix.pdf.viewer.model.PagePart
import kotlin.math.roundToInt

/**
 * A [Handler] that will process incoming [RenderingTask] messages
 * and alert [PDFView.onBitmapRendered] when the portion of the
 * PDF is ready to render.
 * 
 * Now integrates with DiskCacheManager for cache-first tile loading.
 */
internal class RenderingHandler(
    looper: Looper, 
    private val pdfView: PDFView,
    private val diskCache: DiskCacheManager?
) : Handler(looper) {

    private val renderBounds = RectF()
    private val roundedRenderBounds = Rect()
    private val renderMatrix = Matrix()
    private var running = false
    private var scratchBitmap: Bitmap? = null
    private val paint = android.graphics.Paint().apply {
         isDither = true
         isFilterBitmap = true
    }

    fun addRenderingTask(
        page: Int,
        width: Float,
        height: Float,
        bounds: RectF,
        thumbnail: Boolean,
        cacheOrder: Int,
        bestQuality: Boolean,
        annotationRendering: Boolean
    ) {
        val task = RenderingTask(width, height, bounds, page, thumbnail, cacheOrder, bestQuality, annotationRendering)
        val msg = obtainMessage(MSG_RENDER_TASK, task)
        sendMessage(msg)
    }

    override fun handleMessage(message: Message) {
        val task = message.obj as RenderingTask
        try {
            val part = proceed(task)
            if (part != null) {
                if (running) {
                    pdfView.post { pdfView.onBitmapRendered(part) }
                } else {
                    pdfView.bitmapPool?.release(part.renderedBitmap)
                }
            }
        } catch (ex: PageRenderingException) {
            pdfView.post { pdfView.onPageError(ex) }
        }
    }

    private fun proceed(renderingTask: RenderingTask): PagePart? {
        val pdfFile = pdfView.pdfFile ?: return null
        
        // Generate cache key for this tile
        val documentHash = pdfView.documentHash
        val cacheKey = if (diskCache != null && documentHash.isNotEmpty() && !renderingTask.thumbnail) {
            diskCache.getCacheKey(documentHash, renderingTask.page, renderingTask.bounds, pdfView.zoom)
        } else null
        
        // Try loading from disk cache first (for non-thumbnails only)
        if (cacheKey != null && diskCache != null) {
            val cachedBitmap = diskCache.loadTile(cacheKey)
            if (cachedBitmap != null) {
                Log.d(TAG, "Disk cache hit for page ${renderingTask.page}")
                return PagePart(
                    renderingTask.page,
                    cachedBitmap,
                    renderingTask.bounds,
                    renderingTask.thumbnail,
                    renderingTask.cacheOrder
                )
            }
        }
        
        // Not in cache - render the tile
        pdfFile.openPage(renderingTask.page)

        val w = renderingTask.width.roundToInt()
        val h = renderingTask.height.roundToInt()

        if (w == 0 || h == 0 || pdfFile.pageHasError(renderingTask.page)) {
            return null
        }

        // Hybrid Strategy with GPU Acceleration:
        // 1. Render to ARGB_8888 scratch (CPU) - PDFium Requirement.
        // 2. Async save scratch to Disk Cache (handled by saveTileAsync acting on scratch).
        // 3. Convert scratch to HARDWARE Bitmap (GPU) for display (API 26+).
        // 4. Fallback to RGB_565 for older devices.
        
        ensureScratch(w, h)
        
        val scratch = scratchBitmap ?: return null
        
        // CRITICAL: Calculate the render bounds for PDFium
        calculateBounds(w, h, renderingTask.bounds)
        
        // Render to SCRATCH (CPU)
        // Note: We reuse the same scratch bitmap, so we don't need to clear it (PDFium overwrites)
        // But for safety against transparency issues, we could erase color if needed.
        // pdfFile.renderPageBitmap handles it.
        pdfFile.renderPageBitmap(scratch, renderingTask.page, roundedRenderBounds, renderingTask.annotationRendering)
        
        // Save to disk cache using scratch (copy happens in saveTileAsync)
        if (cacheKey != null && diskCache != null && !renderingTask.thumbnail) {
            // Need to set bounds of scratch to match w/h? 
            // saveTileAsync takes the whole bitmap.
            // If scratch is 512x512 but we used 256x256, we are saving garbage?
            // Yes. Scratch is reused "Max Size".
            // We must create a subset for saving if w/h != scratch size.
            // But usually w/h == PART_SIZE.
            // If adaptive size changes, scratch might be bigger.
            // Solution: Subset creation for save?
            // Or just pass the active region? saveTileAsync takes Bitmap.
            
            // For now, assume w/h matches scratch size mostly, or we save the whole thing (wasteful but safe).
            // Better: Create subset for save?
            if (scratch.width == w && scratch.height == h) {
                diskCache.saveTileAsync(cacheKey, scratch)
            } else {
                // Rare case: Adaptive size changed or partial tile?
                // Create strict copy for save
                 try {
                    val subset = Bitmap.createBitmap(scratch, 0, 0, w, h)
                    diskCache.saveTileAsync(cacheKey, subset)
                    // subset recycles in saveTileAsync? No, saveTileAsync copies it.
                    // We must recycle subset.
                    // But saveTileAsync is async.
                    // Actually saveTileAsync makes a copy. So we can recycle subset immediately?
                    // "Create a copy since original ... may be recycled".
                    // Yes.
                    subset.recycle()
                 } catch (e: Exception) {
                    // Ignore save fail
                 }
            }
        }
        
        // Create Result Bitmap (ARGB_8888 for maximum compatibility)
        // Hardware bitmaps cause GPU driver issues on some devices (MALI, etc.)
        val render: Bitmap
        try {
            render = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(render)
            canvas.drawBitmap(scratch, Rect(0, 0, w, h), Rect(0, 0, w, h), paint)
        } catch (e: Exception) {
            Log.e(TAG, "Cannot create result bitmap", e)
            return null
        }
        
        return PagePart(
            renderingTask.page,
            render,
            renderingTask.bounds,
            renderingTask.thumbnail,
            renderingTask.cacheOrder
        )
    }

    private fun calculateBounds(width: Int, height: Int, pageSliceBounds: RectF) {
        renderMatrix.reset()
        renderMatrix.postTranslate(-pageSliceBounds.left * width, -pageSliceBounds.top * height)
        renderMatrix.postScale(1 / pageSliceBounds.width(), 1 / pageSliceBounds.height())

        renderBounds.set(0f, 0f, width.toFloat(), height.toFloat())
        renderMatrix.mapRect(renderBounds)
        renderBounds.round(roundedRenderBounds)
    }

    private fun ensureScratch(w: Int, h: Int) {
        if (scratchBitmap == null || scratchBitmap!!.width < w || scratchBitmap!!.height < h) {
            scratchBitmap?.recycle()
            // Create a scratch bitmap. 
            // Use at least 512x512 to reduce re-allocations if tile size varies slightly.
            // But respect huge requests.
            val sizeW = maxOf(w, 512)
            val sizeH = maxOf(h, 512)
            scratchBitmap = Bitmap.createBitmap(sizeW, sizeH, Bitmap.Config.ARGB_8888)
        }
    }

    fun stop() {
        running = false
    }

    fun start() {
        running = true
    }

    private data class RenderingTask(
        val width: Float,
        val height: Float,
        val bounds: RectF,
        val page: Int,
        val thumbnail: Boolean,
        val cacheOrder: Int,
        val bestQuality: Boolean,
        val annotationRendering: Boolean
    )

    companion object {
        const val MSG_RENDER_TASK = 1
        private val TAG = RenderingHandler::class.java.name
    }
}

