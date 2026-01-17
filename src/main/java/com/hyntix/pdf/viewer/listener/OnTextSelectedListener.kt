package com.hyntix.pdf.viewer.listener

import android.graphics.RectF

/**
 * Listener for text selection events.
 */
interface OnTextSelectedListener {
    /**
     * Called when text is selected.
     * @param text The selected text
     * @param rects Bounding rectangles of the selection (in PDF coordinates)
     * @param page The page index
     */
    fun onTextSelected(text: String, rects: List<RectF>, page: Int)
    
    /**
     * Called when selection is cleared.
     */
    fun onSelectionCleared()
}
