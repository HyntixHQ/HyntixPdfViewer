package com.hyntix.pdf.viewer.listener

import android.graphics.Canvas

interface OnDrawListener {
    /**
     * This method is called when the PDFView is
     * drawing its view.
     *
     *
     * The page is starting at (0,0)
     *
     * @param canvas        The canvas on which to draw things.
     * @param pageWidth     The width of the current page.
     * @param pageHeight    The height of the current page.
     * @param displayedPage The current page index
     */
    fun onLayerDrawn(canvas: Canvas, pageWidth: Float, pageHeight: Float, displayedPage: Int)
}
