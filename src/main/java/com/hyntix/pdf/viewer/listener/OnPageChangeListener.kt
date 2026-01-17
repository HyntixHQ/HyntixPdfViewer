package com.hyntix.pdf.viewer.listener

interface OnPageChangeListener {
    /**
     * Called when the user changes the page
     *
     * @param page      The new page
     * @param pageCount The total number of pages
     */
    fun onPageChanged(page: Int, pageCount: Int)
}
