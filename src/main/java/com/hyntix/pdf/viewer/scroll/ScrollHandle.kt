package com.hyntix.pdf.viewer.scroll

import com.hyntix.pdf.viewer.PDFView

interface ScrollHandle {
    /**
     * Set the current scroll handler
     *
     * @param pdfView PDFView instance
     */
    fun setupLayout(pdfView: PDFView)

    /**
     * Destroy the scroll handler
     */
    fun destroyLayout()

    /**
     * Set the current page number
     *
     * @param pageNum The current page number (1-indexed)
     * @param totalPages The total number of pages
     */
    fun setPageNum(pageNum: Int, totalPages: Int)

    /**
     * Set the scroll handler to the given position
     *
     * @param position Position relative to the PDFView
     */
    fun setScroll(position: Float)

    /**
     * Hide the scroll handler
     */
    fun hide()

    /**
     * Show the scroll handler
     */
    fun show()

    /**
     * Hide the scroll handler after a delay
     */
    fun hideDelayed()

    /**
     * @return true if the scroll handler is currently shown
     */
    fun shown(): Boolean
}
