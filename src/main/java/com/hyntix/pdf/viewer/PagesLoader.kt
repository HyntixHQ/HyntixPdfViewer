package com.hyntix.pdf.viewer

import android.graphics.RectF
import com.hyntix.pdf.viewer.util.Constants
import com.hyntix.pdf.viewer.util.Constants.Cache.CACHE_SIZE
import com.hyntix.pdf.viewer.util.MathUtils
import com.hyntix.pdf.viewer.util.Util
import java.util.LinkedList
import kotlin.math.abs

internal class PagesLoader(private val pdfView: PDFView) {
    private var cacheOrder = 0
    private var xOffset = 0f
    private var yOffset = 0f
    private var pageRelativePartWidth = 0f
    private var pageRelativePartHeight = 0f
    private var partRenderWidth = 0f
    private var partRenderHeight = 0f
    private val thumbnailRect = RectF(0f, 0f, 1f, 1f)
    private val preloadOffset: Int = Util.getDP(pdfView.context, Constants.PRELOAD_OFFSET)

    private class Holder {
        var row = 0
        var col = 0
        override fun toString(): String {
            return "Holder{" +
                    "row=" + row +
                    ", col=" + col +
                    '}'
        }
    }

    private inner class RenderRange {
        var page = 0
        var gridSize: GridSize = GridSize()
        var leftTop: Holder = Holder()
        var rightBottom: Holder = Holder()

        override fun toString(): String {
            return "RenderRange{" +
                    "page=" + page +
                    ", gridSize=" + gridSize +
                    ", leftTop=" + leftTop +
                    ", rightBottom=" + rightBottom +
                    '}'
        }
    }

    private class GridSize {
        var rows = 0
        var cols = 0
        override fun toString(): String {
            return "GridSize{" +
                    "rows=" + rows +
                    ", cols=" + cols +
                    '}'
        }
    }

    private fun getPageColsRows(grid: GridSize, pageIndex: Int) {
        val size = pdfView.pdfFile!!.getPageSize(pageIndex)
        val ratioX = 1f / size.width
        val ratioY = 1f / size.height
        // Original reference formula from AndroidPdfViewer
        val partHeight = Constants.PART_SIZE * ratioY / pdfView.zoom
        val partWidth = Constants.PART_SIZE * ratioX / pdfView.zoom
        grid.rows = MathUtils.ceil(1f / partHeight)
        grid.cols = MathUtils.ceil(1f / partWidth)
    }

    private fun calculatePartSize(grid: GridSize) {
        pageRelativePartWidth = 1f / grid.cols.toFloat()
        pageRelativePartHeight = 1f / grid.rows.toFloat()
        // Use fixed tile size - natural grid subdivision handles zoom quality
        // More tiles are automatically created at higher zoom (see getPageColsRows)
        partRenderWidth = Constants.PART_SIZE / pageRelativePartWidth
        partRenderHeight = Constants.PART_SIZE / pageRelativePartHeight
    }

    /**
     * calculate the render range of each page
     */
    private fun getRenderRangeList(
        firstXOffset: Float,
        firstYOffset: Float,
        lastXOffset: Float,
        lastYOffset: Float
    ): List<RenderRange> {
        val fixedFirstXOffset = -MathUtils.max(firstXOffset, 0f)
        val fixedFirstYOffset = -MathUtils.max(firstYOffset, 0f)
        val fixedLastXOffset = -MathUtils.max(lastXOffset, 0f)
        val fixedLastYOffset = -MathUtils.max(lastYOffset, 0f)
        val offsetFirst =
            if (pdfView.isSwipeVertical) fixedFirstYOffset else fixedFirstXOffset
        val offsetLast =
            if (pdfView.isSwipeVertical) fixedLastYOffset else fixedLastXOffset
        val firstPage =
            pdfView.pdfFile!!.getPageAtOffset(offsetFirst, pdfView.zoom)
        val lastPage =
            pdfView.pdfFile!!.getPageAtOffset(offsetLast, pdfView.zoom)
        val pageCount = lastPage - firstPage + 1
        val renderRanges: MutableList<RenderRange> = LinkedList()
        for (page in firstPage..lastPage) {
            val range = RenderRange()
            range.page = page
            var pageFirstXOffset: Float
            var pageFirstYOffset: Float
            var pageLastXOffset: Float
            var pageLastYOffset: Float
            if (page == firstPage) {
                pageFirstXOffset = fixedFirstXOffset
                pageFirstYOffset = fixedFirstYOffset
                if (pageCount == 1) {
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = fixedLastYOffset
                } else {
                    val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)
                    val pageSize = pdfView.pdfFile!!.getScaledPageSize(page, pdfView.zoom)
                    if (pdfView.isSwipeVertical) {
                        pageLastXOffset = fixedLastXOffset
                        pageLastYOffset = pageOffset + pageSize.height
                    } else {
                        pageLastYOffset = fixedLastYOffset
                        pageLastXOffset = pageOffset + pageSize.width
                    }
                }
            } else if (page == lastPage) {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)
                if (pdfView.isSwipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                } else {
                    pageFirstYOffset = fixedFirstYOffset
                    pageFirstXOffset = pageOffset
                }
                pageLastXOffset = fixedLastXOffset
                pageLastYOffset = fixedLastYOffset
            } else {
                val pageOffset = pdfView.pdfFile!!.getPageOffset(page, pdfView.zoom)
                val pageSize = pdfView.pdfFile!!.getScaledPageSize(page, pdfView.zoom)
                if (pdfView.isSwipeVertical) {
                    pageFirstXOffset = fixedFirstXOffset
                    pageFirstYOffset = pageOffset
                    pageLastXOffset = fixedLastXOffset
                    pageLastYOffset = pageOffset + pageSize.height
                } else {
                    pageFirstXOffset = pageOffset
                    pageFirstYOffset = fixedFirstYOffset
                    pageLastXOffset = pageOffset + pageSize.width
                    pageLastYOffset = fixedLastYOffset
                }
            }
            getPageColsRows(
                range.gridSize,
                range.page
            ) // get the page's grid size that rows and cols
            val scaledPageSize =
                pdfView.pdfFile!!.getScaledPageSize(range.page, pdfView.zoom)
            val rowHeight = scaledPageSize.height / range.gridSize.rows
            val colWidth = scaledPageSize.width / range.gridSize.cols

            // get the page offset int the whole file
            // ---------------------------------------
            // |            |           |            |
            // |<--offset-->|   (page)  |<--offset-->|
            // |            |           |            |
            // |            |           |            |
            // ---------------------------------------
            val secondaryOffset =
                pdfView.pdfFile!!.getSecondaryPageOffset(page, pdfView.zoom)

            // calculate the row,col of the point in the leftTop and rightBottom
            if (pdfView.isSwipeVertical) {
                range.leftTop.row = MathUtils.floor(
                    abs(
                        (pageFirstYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )).toDouble()
                    ).toFloat() / rowHeight
                )
                range.leftTop.col = MathUtils.floor(
                     MathUtils.min(
                        pageFirstXOffset - secondaryOffset,
                        0f
                    ) / colWidth
                )
                range.rightBottom.row = MathUtils.ceil(
                    abs(
                        (pageLastYOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )).toDouble()
                    ).toFloat() / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                     MathUtils.min(
                        pageLastXOffset - secondaryOffset,
                        0f
                    ) / colWidth
                )
            } else {
                range.leftTop.col = MathUtils.floor(
                    abs(
                        (pageFirstXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )).toDouble()
                    ).toFloat() / colWidth
                )
                range.leftTop.row = MathUtils.floor(
                     MathUtils.min(
                        pageFirstYOffset - secondaryOffset,
                        0f
                    ) / rowHeight
                )
                range.rightBottom.col = MathUtils.floor(
                    abs(
                        (pageLastXOffset - pdfView.pdfFile!!.getPageOffset(
                            range.page,
                            pdfView.zoom
                        )).toDouble()
                    ).toFloat() / colWidth
                )
                range.rightBottom.row = MathUtils.floor(
                    MathUtils.min(
                        pageLastYOffset - secondaryOffset,
                        0f
                    ) / rowHeight
                )
            }
            renderRanges.add(range)
        }
        return renderRanges
    }

    private fun loadVisible() {
        var parts = 0
        val scaledPreloadOffset = preloadOffset.toFloat()
        val firstXOffset = -xOffset + scaledPreloadOffset
        val lastXOffset = -xOffset - pdfView.width - scaledPreloadOffset
        val firstYOffset = -yOffset + scaledPreloadOffset
        val lastYOffset = -yOffset - pdfView.height - scaledPreloadOffset
        val rangeList =
            getRenderRangeList(firstXOffset, firstYOffset, lastXOffset, lastYOffset)
        
        // Load thumbnails for visible pages first
        for (range in rangeList) {
            loadThumbnail(range.page)
        }
        
        // Load visible page tiles (high priority)
        for (range in rangeList) {
            calculatePartSize(range.gridSize)
            parts += loadPage(
                range.page,
                range.leftTop.row,
                range.rightBottom.row,
                range.leftTop.col,
                range.rightBottom.col,
                CACHE_SIZE - parts
            )
            if (parts >= CACHE_SIZE) {
                break
            }
        }
        
        // Preload adjacent pages (lower priority, thumbnails only to save memory)
        if (parts < CACHE_SIZE && rangeList.isNotEmpty()) {
            val preloadPages = Constants.PRELOAD_PAGES
            val pageCount = pdfView.pdfFile?.pagesCount ?: 0
            val currentPage = pdfView.currentPage
            
            // Preload pages ahead and behind
            for (offset in 1..preloadPages) {
                // Page ahead
                val aheadPage = currentPage + offset
                if (aheadPage < pageCount && rangeList.none { it.page == aheadPage }) {
                    loadThumbnail(aheadPage)
                }
                // Page behind
                val behindPage = currentPage - offset
                if (behindPage >= 0 && rangeList.none { it.page == behindPage }) {
                    loadThumbnail(behindPage)
                }
            }
        }
    }

    private fun loadPage(
        page: Int,
        firstRow: Int,
        lastRow: Int,
        firstCol: Int,
        lastCol: Int,
        nbOfPartsLoadable: Int
    ): Int {
        var loaded = 0
        for (row in firstRow..lastRow) {
            for (col in firstCol..lastCol) {
                if (loadCell(page, row, col, pageRelativePartWidth, pageRelativePartHeight)) {
                    loaded++
                }
                if (loaded >= nbOfPartsLoadable) {
                    return loaded
                }
            }
        }
        return loaded
    }

    private fun loadCell(
        page: Int,
        row: Int,
        col: Int,
        pageRelativePartWidth: Float,
        pageRelativePartHeight: Float
    ): Boolean {
        val relX = pageRelativePartWidth * col
        val relY = pageRelativePartHeight * row
        var relWidth = pageRelativePartWidth
        var relHeight = pageRelativePartHeight
        var renderWidth = partRenderWidth
        var renderHeight = partRenderHeight
        if (relX + relWidth > 1) {
            relWidth = 1 - relX
        }
        if (relY + relHeight > 1) {
            relHeight = 1 - relY
        }
        renderWidth *= relWidth
        renderHeight *= relHeight
        val pageRelativeBounds =
            RectF(relX, relY, relX + relWidth, relY + relHeight)
        if (renderWidth > 0 && renderHeight > 0) {
            if (!pdfView.cacheManager!!.upPartIfContained(
                    page,
                    pageRelativeBounds,
                    cacheOrder
                )
            ) {
                pdfView.renderingHandler!!.addRenderingTask(
                    page, renderWidth, renderHeight,
                    pageRelativeBounds, false, cacheOrder, pdfView.isBestQuality,
                    pdfView.isAnnotationRendering
                )
            }
            cacheOrder++
            return true
        }
        return false
    }

    private fun loadThumbnail(page: Int) {
        val pageSize = pdfView.pdfFile!!.getPageSize(page)
        val thumbnailWidth = pageSize.width * Constants.THUMBNAIL_RATIO
        val thumbnailHeight = pageSize.height * Constants.THUMBNAIL_RATIO
        if (!pdfView.cacheManager!!.containsThumbnail(page, thumbnailRect)) {
            pdfView.renderingHandler!!.addRenderingTask(
                page,
                thumbnailWidth, thumbnailHeight, thumbnailRect,
                true, 0, false, pdfView.isAnnotationRendering  // Note: bestQuality=false, but RenderingHandler uses ARGB_8888 anyway (PDFium requirement)
            )
        }
    }

    fun loadPages() {
        cacheOrder = 1
        xOffset = -MathUtils.max(pdfView.currentXOffset, 0f)
        yOffset = -MathUtils.max(pdfView.currentYOffset, 0f)
        loadVisible()
    }
}
