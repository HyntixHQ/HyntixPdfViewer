package com.hyntix.pdf.viewer.selection

import android.graphics.PointF
import android.graphics.RectF

/**
 * Represents a text selection within a PDF page.
 *
 * @param page Page index (0-indexed)
 * @param startChar Start character index
 * @param endChar End character index
 * @param selectedText The selected text content
 * @param bounds List of rectangles bounding the selected text
 */
data class TextSelection(
    val page: Int,
    val startChar: Int,
    val endChar: Int,
    val selectedText: String,
    val bounds: List<RectF>
)

/**
 * Callback interface for text selection events.
 */
interface TextSelectionCallback {
    /**
     * Called when text selection starts.
     */
    fun onSelectionStarted()
    
    /**
     * Called when selection changes (user is dragging).
     * @param selection Current selection state
     */
    fun onSelectionChanged(selection: TextSelection?)
    
    /**
     * Called when selection is complete (user lifted finger).
     * @param selection Final selection
     */
    fun onSelectionComplete(selection: TextSelection?)
    
    /**
     * Called when selection is cleared.
     */
    fun onSelectionCleared()
}

/**
 * Handler interface for text selection functionality.
 * Implement this to provide custom text selection UI.
 */
interface TextSelectionHandler {
    
    /**
     * Called when long press is detected (potential selection start).
     * @param point Touch point in page coordinates
     * @param page Page index
     * @return true if selection should be handled
     */
    fun onLongPress(point: PointF, page: Int): Boolean
    
    /**
     * Called when drag gesture is detected during selection.
     * @param point Current touch point
     */
    fun onDrag(point: PointF)
    
    /**
     * Called when touch is released.
     */
    fun onRelease()
    
    /**
     * Get the current selection, if any.
     */
    fun getSelection(): TextSelection?
    
    /**
     * Clear the current selection.
     */
    fun clearSelection()
    
    /**
     * Copy the selected text to clipboard.
     * @return true if copied successfully
     */
    fun copyToClipboard(): Boolean
    
    /**
     * Set callback for selection events.
     */
    fun setCallback(callback: TextSelectionCallback?)
}
