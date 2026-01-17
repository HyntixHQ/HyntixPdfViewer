package com.hyntix.pdf.viewer.listener

interface OnRenderListener {
    /**
     * Called when the PDF is partially or entirely rendered
     * @param nbPages the number of pages in this PDF file
     */
    fun onInitiallyRendered(nbPages: Int)
}
