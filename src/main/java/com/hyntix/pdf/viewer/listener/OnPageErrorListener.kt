package com.hyntix.pdf.viewer.listener

interface OnPageErrorListener {
    /**
     * Called when an error occurs for the page
     * @param page The page where the error occurred
     * @param t The throwable corresponding to the error
     */
    fun onPageError(page: Int, t: Throwable?)
}
