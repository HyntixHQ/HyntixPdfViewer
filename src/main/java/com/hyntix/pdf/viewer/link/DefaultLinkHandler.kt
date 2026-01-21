package com.hyntix.pdf.viewer.link

import android.content.Intent
import android.net.Uri
import android.util.Log
import com.hyntix.pdf.viewer.PDFView
import com.hyntix.pdf.viewer.model.LinkTapEvent

class DefaultLinkHandler(private val pdfView: PDFView) : LinkHandler {

    override fun handleLinkEvent(event: LinkTapEvent) {
        val uri = event.link.uri
        val page = event.link.destPageIndex
        Log.d(TAG, "handleLinkEvent: uri=$uri, page=$page")
        if (!uri.isNullOrEmpty()) {
            handleUri(uri)
        } else if (page >= 0) {
            handlePage(page)
        }
    }

    private fun handleUri(uri: String) {
        var finalUri = uri.trim()
        Log.d(TAG, "handleUri: raw=$uri, trimmed=$finalUri")
        
        // Add schema if missing - PDFium URIs sometimes lack them
        if (!finalUri.contains("://") && !finalUri.startsWith("mailto:") && !finalUri.startsWith("tel:")) {
            finalUri = "https://" + finalUri
        }
        
        try {
            val parsedUri = Uri.parse(finalUri)
            val intent = Intent(Intent.ACTION_VIEW, parsedUri)
            pdfView.context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start activity for URI: $finalUri (original: $uri)", e)
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }

    companion object {
        private val TAG = DefaultLinkHandler::class.java.simpleName
    }
}
