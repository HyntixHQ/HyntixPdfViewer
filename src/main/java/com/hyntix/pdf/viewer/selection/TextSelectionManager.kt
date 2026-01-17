package com.hyntix.pdf.viewer.selection

import android.graphics.PointF
import android.graphics.RectF
import com.hyntix.pdf.viewer.PdfFile

/**
 * Manages text selection state and logic for PDF pages.
 * This class handles character-level selection, word/paragraph expansion,
 * and provides the selected text and bounding rectangles.
 */
class TextSelectionManager(
    private val pdfFile: PdfFile
) {
    // Selection state
    var isSelecting: Boolean = false
        private set
    
    var selectionPage: Int = -1
        private set
    
    var startCharIndex: Int = -1
        private set
    
    var endCharIndex: Int = -1
        private set
    
    // Cached selection rectangles (in PDF coordinates)
    private var _selectionRects: List<RectF> = emptyList()
    val selectionRects: List<RectF> get() = _selectionRects
    
    // Cached selected text
    private var _selectedText: String = ""
    val selectedText: String get() = _selectedText
    
    /**
     * Start selection at a specific point.
     * Finds the character at the point and selects the word containing it.
     */
    fun startSelectionAt(page: Int, pdfX: Float, pdfY: Float): Boolean {
        val charIndex = pdfFile.getCharIndexAt(page, pdfX, pdfY)
        if (charIndex < 0) {
            return false
        }
        
        selectionPage = page
        isSelecting = true
        
        // Expand to word
        selectWordAt(page, charIndex)
        return true
    }
    
    /**
     * Select the word containing the given character index.
     */
    fun selectWordAt(page: Int, charIndex: Int) {
        val pageText = pdfFile.getPageText(page, 0, 10000) // Get up to 10000 chars
        if (pageText.isEmpty() || charIndex >= pageText.length) {
            return
        }
        
        // Find word boundaries
        var start = charIndex
        while (start > 0 && !pageText[start - 1].isWhitespace()) {
            start--
        }
        
        var end = charIndex
        while (end < pageText.length && !pageText[end].isWhitespace()) {
            end++
        }
        
        selectionPage = page
        startCharIndex = start
        endCharIndex = end
        isSelecting = true
        
        updateSelectionData()
    }
    
    /**
     * Expand or contract selection to a new end point.
     */
    fun updateSelectionTo(pdfX: Float, pdfY: Float) {
        if (!isSelecting || selectionPage < 0) return
        
        val charIndex = pdfFile.getCharIndexAt(selectionPage, pdfX, pdfY)
        if (charIndex >= 0) {
            endCharIndex = charIndex
            updateSelectionData()
        }
    }
    
    /**
     * Update selection by moving the start handle.
     */
    fun updateStartHandle(pdfX: Float, pdfY: Float) {
        if (!isSelecting || selectionPage < 0) return
        
        val charIndex = pdfFile.getCharIndexAt(selectionPage, pdfX, pdfY)
        if (charIndex >= 0 && charIndex <= endCharIndex) {
            startCharIndex = charIndex
            updateSelectionData()
        }
    }
    
    /**
     * Update selection by moving the end handle.
     */
    fun updateEndHandle(pdfX: Float, pdfY: Float) {
        if (!isSelecting || selectionPage < 0) return
        
        val charIndex = pdfFile.getCharIndexAt(selectionPage, pdfX, pdfY)
        if (charIndex >= 0 && charIndex >= startCharIndex) {
            endCharIndex = charIndex
            updateSelectionData()
        }
    }
    
    /**
     * Select all text on the current page.
     */
    fun selectAll(page: Int) {
        val pageText = pdfFile.getPageText(page, 0, 100000)
        if (pageText.isEmpty()) return
        
        selectionPage = page
        startCharIndex = 0
        endCharIndex = pageText.length
        isSelecting = true
        
        updateSelectionData()
    }
    
    /**
     * Clear the current selection.
     */
    fun clearSelection() {
        isSelecting = false
        selectionPage = -1
        startCharIndex = -1
        endCharIndex = -1
        _selectionRects = emptyList()
        _selectedText = ""
    }
    
    /**
     * Check if there is an active selection.
     */
    fun hasSelection(): Boolean {
        return isSelecting && selectionPage >= 0 && startCharIndex >= 0 && endCharIndex > startCharIndex
    }
    
    /**
     * Get the positions for start and end handles (in PDF coordinates).
     * Returns Pair(startPoint, endPoint) or null if no selection.
     */
    fun getHandlePositions(): Pair<PointF, PointF>? {
        if (!hasSelection() || _selectionRects.isEmpty()) return null
        
        val firstRect = _selectionRects.first()
        val lastRect = _selectionRects.last()
        
        // Start handle at left-bottom of first rect
        val startPoint = PointF(firstRect.left, firstRect.bottom)
        // End handle at right-bottom of last rect
        val endPoint = PointF(lastRect.right, lastRect.bottom)
        
        return Pair(startPoint, endPoint)
    }
    
    /**
     * Update cached selection data (rects and text).
     */
    private fun updateSelectionData() {
        if (selectionPage < 0 || startCharIndex < 0 || endCharIndex < startCharIndex) {
            _selectionRects = emptyList()
            _selectedText = ""
            return
        }
        
        // Ensure correct order
        val actualStart = minOf(startCharIndex, endCharIndex)
        val actualEnd = maxOf(startCharIndex, endCharIndex)
        startCharIndex = actualStart
        endCharIndex = actualEnd
        
        val count = actualEnd - actualStart
        if (count <= 0) {
            _selectionRects = emptyList()
            _selectedText = ""
            return
        }
        
        // Get text
        _selectedText = pdfFile.getPageText(selectionPage, actualStart, count)
        
        // Get rectangles
        val rawRects = pdfFile.getTextRects(selectionPage, actualStart, count)
        _selectionRects = mergeRects(rawRects)
    }
    
    /**
     * Merge adjacent rectangles on the same line to avoid fragmentation artifacts.
     */
    private fun mergeRects(rects: List<RectF>): List<RectF> {
        if (rects.isEmpty()) return emptyList()
        
        // Sort by Y (top) then X (left)
        // Note: PDF coordinates usually have Origin at Bottom-Left, so Y increases Upwards.
        // Higher Y = Visual Top. So we sort Descending by Top to get Visual Top-First order.
        // However, we must be careful: Pdfium might normalize this? 
        // Based on observed behavior (handles swapped lines), it seems we need to reverse the Y sort.
        val sorted = rects.sortedWith(Comparator { r1, r2 ->
            val topDiff = r2.top.compareTo(r1.top) // Descending Y
            if (kotlin.math.abs(r1.top - r2.top) > 5.0f) topDiff else r1.left.compareTo(r2.left) // Ascending X
        })
        
        val merged = ArrayList<RectF>()
        var current = sorted[0]
        
        for (i in 1 until sorted.size) {
            val next = sorted[i]
            
            // Checks if on same line (vertical overlap / alignment)
            // Using a tolerance of 2.0 units (arbitrary PDF units)
            val sameLine = kotlin.math.abs(next.top - current.top) < 5.0f && 
                           kotlin.math.abs(next.bottom - current.bottom) < 5.0f
            
            // Check adjacency (horizontal overlap or touching)
            // Allow small gap (e.g. space width or rounding error)
             val adjacent = next.left <= current.right + 2.0f
            
            if (sameLine && adjacent) {
                // Merge
                current = RectF(
                    kotlin.math.min(current.left, next.left),
                    kotlin.math.min(current.top, next.top),
                    kotlin.math.max(current.right, next.right),
                    kotlin.math.max(current.bottom, next.bottom)
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)
        return merged
    }
}
