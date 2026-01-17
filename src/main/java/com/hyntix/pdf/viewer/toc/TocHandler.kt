package com.hyntix.pdf.viewer.toc

import com.hyntix.pdfium.PdfDocument

/**
 * Interface for Table of Contents handlers.
 * Implement this to create custom TOC UI (sidesheet, bottom sheet, dialog, etc.)
 */
interface TocHandler {
    
    /**
     * Update the TOC with the given bookmarks.
     * Called when the PDF document is loaded.
     *
     * @param bookmarks List of bookmarks from the PDF
     */
    fun updateToc(bookmarks: List<com.hyntix.pdfium.PdfBookmark>)
    
    /**
     * Show the TOC UI
     */
    fun show()
    
    /**
     * Hide the TOC UI
     */
    fun hide()
    
    /**
     * Toggle TOC visibility
     */
    fun toggle() {
        if (isVisible()) hide() else show()
    }
    
    /**
     * @return true if the TOC is currently visible
     */
    fun isVisible(): Boolean
    
    /**
     * Set the callback for when a bookmark is selected.
     * The callback receives the page index to navigate to.
     *
     * @param callback Function that receives the target page index (0-indexed)
     */
    fun onBookmarkSelected(callback: (pageIndex: Int) -> Unit)
}
