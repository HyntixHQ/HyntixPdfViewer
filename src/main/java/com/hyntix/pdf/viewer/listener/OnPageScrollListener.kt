package com.hyntix.pdf.viewer.listener

interface OnPageScrollListener {
    /**
     * Called on every move while scrolling
     *
     * @param page current page index
     * @param positionOffset see [com.github.barteksc.pdfviewer.PDFView.getPositionOffset]
     */
    fun onPageScrolled(page: Int, positionOffset: Float)
}
