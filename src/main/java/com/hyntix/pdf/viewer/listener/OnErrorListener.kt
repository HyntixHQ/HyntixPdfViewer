package com.hyntix.pdf.viewer.listener

interface OnErrorListener {
    /**
     * Called when an error occurs for the document
     * @param t The throwable corresponding to the error
     */
    fun onError(t: Throwable)
}
