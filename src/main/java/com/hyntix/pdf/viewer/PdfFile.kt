package com.hyntix.pdf.viewer

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.SparseArray
import android.util.SparseBooleanArray
import com.hyntix.pdf.viewer.exception.PageRenderingException
import com.hyntix.pdf.viewer.util.FitPolicy
import com.hyntix.pdf.viewer.util.PageSizeCalculator
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import com.hyntix.pdfium.util.Size
import com.hyntix.pdfium.util.SizeF
import java.util.ArrayList

class PdfFile(
    private val pdfiumCore: PdfiumCore,
    private var _pdfDocument: PdfDocument?,
    private val pageFitPolicy: FitPolicy,
    viewSize: Size,
    private var originalUserPages: IntArray?,
    private val isVertical: Boolean,
    private val spacingPx: Int,
    private val autoSpacing: Boolean,
    private val fitEachPage: Boolean
) {
    private val lock = Any()
    var pagesCount = 0
        private set
    
    /** Access to the underlying PdfDocument for metadata/info queries */
    val pdfDocument: PdfDocument? get() = _pdfDocument


    /** Original page sizes  */
    private val originalPageSizes: MutableList<Size> = ArrayList()

    /** Scaled page sizes  */
    private val pageSizes: MutableList<SizeF> = ArrayList()

    /** Opened pages with indicator whether opening was successful (for error tracking) */
    private val openedPages = SparseBooleanArray()

    /** Active PdfPage objects */
    private val activePages = SparseArray<com.hyntix.pdfium.PdfPage>()

    /** Page with maximum width  */
    private var originalMaxWidthPageSize = Size(0, 0)

    /** Page with maximum height  */
    private var originalMaxHeightPageSize = Size(0, 0)

    /** Scaled page with maximum height  */
    private var maxHeightPageSize = SizeF(0f, 0f)

    /** Scaled page with maximum width  */
    private var maxWidthPageSize = SizeF(0f, 0f)

    /** Calculated offsets for pages  */
    private val pageOffsets: MutableList<Float> = ArrayList()

    /** Calculated auto spacing for pages  */
    private val pageSpacing: MutableList<Float> = ArrayList()

    /** Calculated document length (width or height, depending on swipe mode)  */
    private var documentLength = 0f

    val maxPageWidth: Float
        get() = maxPageSize.width

    val maxPageSize: SizeF
        get() = if (isVertical) maxWidthPageSize else maxHeightPageSize

    val maxPageHeight: Float
        get() = maxPageSize.height

    init {
        setup(viewSize)
    }

    private fun setup(viewSize: Size) {
        val document = pdfDocument ?: return
        pagesCount = originalUserPages?.size ?: document.pageCount
        
        // FAST: Use FPDF_GetPageSizeByIndex via getAllPageSizes() - no page loading needed
        val allSizes = document.getAllPageSizes()
        
        for (i in 0 until pagesCount) {
            val docPage = documentPage(i)
            val (width, height) = if (docPage >= 0 && docPage < allSizes.size) {
                allSizes[docPage]
            } else {
                Pair(595.0, 842.0) // Default A4
            }
            
            val pageSize = Size(width.toInt(), height.toInt())
            
            if (pageSize.width > originalMaxWidthPageSize.width) {
                originalMaxWidthPageSize = pageSize
            }
            if (pageSize.height > originalMaxHeightPageSize.height) {
                originalMaxHeightPageSize = pageSize
            }
            originalPageSizes.add(pageSize)
        }
        
        recalculatePageSizes(viewSize)
    }

    fun recalculatePageSizes(viewSize: Size) {
        pageSizes.clear()
        val calculator = PageSizeCalculator(
            pageFitPolicy, originalMaxWidthPageSize,
            originalMaxHeightPageSize, viewSize, fitEachPage
        )
        maxWidthPageSize = calculator.optimalMaxWidthPageSize!!
        maxHeightPageSize = calculator.optimalMaxHeightPageSize!!
        for (size in originalPageSizes) {
            pageSizes.add(calculator.calculate(size))
        }
        if (autoSpacing) {
            prepareAutoSpacing(viewSize)
        }
        prepareDocLen()
        preparePagesOffset()
    }

    fun getPageSize(pageIndex: Int): SizeF {
        val docPage = documentPage(pageIndex)
        return if (docPage < 0) {
            SizeF(0f, 0f)
        } else pageSizes[pageIndex]
    }

    fun getScaledPageSize(pageIndex: Int, zoom: Float): SizeF {
        val size = getPageSize(pageIndex)
        return SizeF(size.width * zoom, size.height * zoom)
    }

    private fun prepareAutoSpacing(viewSize: Size) {
        pageSpacing.clear()
        for (i in 0 until pagesCount) {
            val pageSize = pageSizes[i]
            var spacing = Math.max(
                0f,
                if (isVertical) viewSize.height - pageSize.height else viewSize.width - pageSize.width
            )
            if (i < pagesCount - 1) {
                spacing += spacingPx.toFloat()
            }
            pageSpacing.add(spacing)
        }
    }

    private fun prepareDocLen() {
        var length = 0f
        for (i in 0 until pagesCount) {
            val pageSize = pageSizes[i]
            length += if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                length += pageSpacing[i]
            } else if (i < pagesCount - 1) {
                length += spacingPx.toFloat()
            }
        }
        documentLength = length
    }

    private fun preparePagesOffset() {
        pageOffsets.clear()
        var offset = 0f
        for (i in 0 until pagesCount) {
            val pageSize = pageSizes[i]
            val size = if (isVertical) pageSize.height else pageSize.width
            if (autoSpacing) {
                offset += pageSpacing[i] / 2f
                if (i == 0) {
                    offset -= spacingPx / 2f
                } else if (i == pagesCount - 1) {
                    offset += spacingPx / 2f
                }
                pageOffsets.add(offset)
                offset += size + pageSpacing[i] / 2f
            } else {
                pageOffsets.add(offset)
                offset += size + spacingPx
            }
        }
    }

    fun getDocLen(zoom: Float): Float {
        return documentLength * zoom
    }

    fun getPageLength(pageIndex: Int, zoom: Float): Float {
        val size = getPageSize(pageIndex)
        return (if (isVertical) size.height else size.width) * zoom
    }

    fun getPageSpacing(pageIndex: Int, zoom: Float): Float {
        val spacing = if (autoSpacing) pageSpacing[pageIndex] else spacingPx.toFloat()
        return spacing * zoom
    }

    fun getPageOffset(pageIndex: Int, zoom: Float): Float {
        val docPage = documentPage(pageIndex)
        return if (docPage < 0) {
            0f
        } else pageOffsets[pageIndex] * zoom
    }

    fun getSecondaryPageOffset(pageIndex: Int, zoom: Float): Float {
        val pageSize = getPageSize(pageIndex)
        return if (isVertical) {
            val maxWidth = maxPageWidth
            zoom * (maxWidth - pageSize.width) / 2 // x
        } else {
            val maxHeight = maxPageHeight
            zoom * (maxHeight - pageSize.height) / 2 // y
        }
    }

    fun getPageAtOffset(offset: Float, zoom: Float): Int {
        var currentPage = 0
        for (i in 0 until pagesCount) {
            val off = pageOffsets[i] * zoom - getPageSpacing(i, zoom) / 2f
            if (off >= offset) {
                break
            }
            currentPage++
        }
        return if (--currentPage >= 0) currentPage else 0
    }

    @Throws(PageRenderingException::class)
    fun openPage(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) {
            return false
        }
        synchronized(lock) {
            if (activePages.indexOfKey(docPage) < 0) {
                try {
                    val document = pdfDocument ?: return false
                    val page = document.openPage(docPage)
                    activePages.put(docPage, page)
                    openedPages.put(docPage, true)
                    return true
                } catch (e: Exception) {
                    openedPages.put(docPage, false)
                    throw PageRenderingException(pageIndex, e)
                }
            }
            return false
        }
    }
    
    fun pageHasError(pageIndex: Int): Boolean {
        val docPage = documentPage(pageIndex)
        return !openedPages.get(docPage, false)
    }

    fun renderPageBitmap(
        bitmap: Bitmap?,
        pageIndex: Int,
        bounds: Rect,
        annotationRendering: Boolean
    ) {
        synchronized(lock) {
            val docPage = documentPage(pageIndex)
            val page = activePages.get(docPage)
            if (page != null && bitmap != null) {
                 page.render(
                    bitmap,
                    bounds.left, bounds.top, bounds.width(), bounds.height(),
                    annotationRendering
                 )
            }
        }
    }

    val metaData: com.hyntix.pdfium.PdfDocument?
        get() = pdfDocument

    val bookmarks: List<com.hyntix.pdfium.PdfBookmark>
        get() {
            val document = pdfDocument
            return document?.getTableOfContents() ?: ArrayList()
        }

    fun getPageLinks(pageIndex: Int): List<com.hyntix.pdfium.PdfLink> {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return java.util.Collections.emptyList()
        
        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return java.util.Collections.emptyList()
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return java.util.Collections.emptyList()
            }
        }
        
        try {
            return page.getLinks()
        } finally {
            if (shouldClose) page.close()
        }
    }

    fun getLinkAt(pageIndex: Int, x: Double, y: Double): com.hyntix.pdfium.PdfLink? {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return null
        
        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return null
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return null
            }
        }
        
        try {
            return page.getLinkAt(x, y)
        } finally {
            if (shouldClose) page.close()
        }
    }




    /**
     * Get all text-based web links on the page (plain text URLs).
     * This detects URLs that are not explicit link annotations.
     */
    fun getWebLinksForPage(pageIndex: Int): List<com.hyntix.pdfium.PdfLink> {
        // Synchronize with lock to prevent concurrent PDFium access
        synchronized(lock) {
            val docPage = documentPage(pageIndex)
            if (docPage < 0) return java.util.Collections.emptyList()

            var page = activePages.get(docPage)
            var shouldClose = false
            if (page == null) {
                try {
                    val document = pdfDocument ?: return java.util.Collections.emptyList()
                    page = document.openPage(docPage)
                    shouldClose = true
                } catch (e: Exception) {
                    return java.util.Collections.emptyList()
                }
            }

            try {
                val textPage = page.openTextPage()
                try {
                    val webLinks = textPage.loadWebLinks()
                    try {
                        val count = webLinks.count
                        val result = java.util.ArrayList<com.hyntix.pdfium.PdfLink>(count)
                        
                        for (i in 0 until count) {
                            val url = webLinks.getURL(i)
                            val rects: List<RectF> = webLinks.getRects(i, textPage)
                            
                            if (rects.isNotEmpty()) {
                                val fullRect = RectF(rects[0])
                                for (j in 1 until rects.size) {
                                    fullRect.union(rects[j])
                                }
                                result.add(com.hyntix.pdfium.PdfLink(fullRect, -1, url))
                            }
                        }
                        return result
                    } finally {
                        webLinks.close()
                    }
                } finally {
                    textPage.close()
                }
            } catch (e: Exception) {
                return java.util.Collections.emptyList()
            } finally {
                if (shouldClose) page.close()
            }
        }
    }





    fun getCharIndexAt(pageIndex: Int, x: Float, y: Float): Int {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return -1

        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return -1
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return -1
            }
        }

        try {
            val textPage = page.openTextPage()
            try {
                // Tolerance 5.0 points
                return textPage.getIndexAtPos(x.toDouble(), y.toDouble(), 5.0, 5.0)
            } finally {
                textPage.close()
            }
        } catch (e: Exception) {
            return -1
        } finally {
            if (shouldClose) page.close()
        }
    }

    fun getTextRects(pageIndex: Int, startIndex: Int, count: Int): List<RectF> {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return java.util.Collections.emptyList()

        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return java.util.Collections.emptyList()
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return java.util.Collections.emptyList()
            }
        }

        try {
            val textPage = page.openTextPage()
            try {
                return textPage.getTextRects(startIndex, count)
            } finally {
                textPage.close()
            }
        } catch (e: Exception) {
            return java.util.Collections.emptyList()
        } finally {
            if (shouldClose) page.close()
        }
    }
    
    fun getPageText(pageIndex: Int): String {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return ""

        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return ""
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return ""
            }
        }

        try {
            val textPage = page.openTextPage()
            try {
                return textPage.text
            } finally {
                textPage.close()
            }
        } catch (e: Exception) {
            return ""
        } finally {
            if (shouldClose) page.close()
        }
    }

    fun getPageText(pageIndex: Int, startIndex: Int, count: Int): String {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return ""

        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return ""
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return ""
            }
        }

        try {
            val textPage = page.openTextPage()
            try {
                return textPage.extractText(startIndex, count)
            } finally {
                textPage.close()
            }
        } catch (e: Exception) {
            return ""
        } finally {
            if (shouldClose) page.close()
        }
    }

    fun mapRectToDevice(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        rect: RectF?
    ): RectF {
        if (rect == null) return RectF(0f, 0f, 0f, 0f)
        
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return RectF(0f, 0f, 0f, 0f)
        
        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return RectF(0f, 0f, 0f, 0f)
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return RectF(0f, 0f, 0f, 0f)
            }
        }
        
        try {
            return page.mapRectToDevice(startX, startY, sizeX, sizeY, rect)
        } finally {
            if (shouldClose) page.close()
        }
    }

    /**
     * Map device (screen) coordinates to page (PDF) coordinates.
     * Returns DoubleArray [pageX, pageY].
     */
    fun mapDeviceToPage(
        pageIndex: Int, startX: Int, startY: Int, sizeX: Int, sizeY: Int,
        deviceX: Int, deviceY: Int
    ): DoubleArray {
        val docPage = documentPage(pageIndex)
        val page = activePages.get(docPage)
        if (page != null) {
            return page.mapDeviceToPage(startX, startY, sizeX, sizeY, deviceX, deviceY)
        }
        return doubleArrayOf(0.0, 0.0)
    }

    fun dispose() {
        synchronized(lock) {
            // Close all active pages
            for (i in 0 until activePages.size()) {
                try {
                    activePages.valueAt(i).close()
                } catch (e: Exception) {
                    // Ignore close errors
                }
            }
            activePages.clear()

            if (this._pdfDocument != null) {
                try {
                    this._pdfDocument!!.close()
                } catch (e: Exception) { }
            }
            this._pdfDocument = null
            this.originalUserPages = null
        }
    }
    
    /**
     * Search for text in a specific page.
     * 
     * @param pageIndex The page index to search in
     * @param query The text to search for
     * @return List of RectF representing the bounding boxes of matches
     */
    fun searchPage(pageIndex: Int, query: String): List<RectF> {
        val docPage = documentPage(pageIndex)
        if (docPage < 0) return emptyList()

        var page = activePages.get(docPage)
        var shouldClose = false
        if (page == null) {
            try {
                val document = pdfDocument ?: return emptyList()
                page = document.openPage(docPage)
                shouldClose = true
            } catch (e: Exception) {
                return emptyList()
            }
        }

        try {
            val textPage = page.openTextPage()
            try {
                val matches = textPage.search(query, matchCase = false, matchWholeWord = false)
                val rects = ArrayList<RectF>()
                for (match in matches) {
                    rects.addAll(textPage.getTextRects(match.startIndex, match.count))
                }
                return rects
            } finally {
                textPage.close()
            }
        } catch (e: Exception) {
            return emptyList()
        } finally {
            if (shouldClose) page.close()
        }
    }

    /**
     * Given the UserPage number, this method restrict it
     * to be sure it's an existing page. It takes care of
     * using the user defined pages if any.
     *
     * @param userPage A page number.
     * @return A restricted valid page number (example : -2 => 0)
     */
    fun determineValidPageNumberFrom(userPage: Int): Int {
        if (userPage <= 0) {
            return 0
        }
        if (originalUserPages != null) {
            if (userPage >= originalUserPages!!.size) {
                return originalUserPages!!.size - 1
            }
        } else {
            if (userPage >= pagesCount) {
                return pagesCount - 1
            }
        }
        return userPage
    }

    fun documentPage(userPage: Int): Int {
        var documentPage = userPage
        if (originalUserPages != null) {
            if (userPage < 0 || userPage >= originalUserPages!!.size) {
                return -1
            } else {
                documentPage = originalUserPages!![userPage]
            }
        }
        if (documentPage < 0 || userPage >= pagesCount) {
            return -1
        }
        return documentPage
    }
}
