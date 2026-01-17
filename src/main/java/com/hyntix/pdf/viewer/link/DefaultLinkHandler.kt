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
        if (!uri.isNullOrEmpty()) {
            handleUri(uri)
        } else if (page >= 0) {
            handlePage(page)
        }
    }

    private fun handleUri(uri: String) {
        val parsedUri = Uri.parse(uri)
        val intent = Intent(Intent.ACTION_VIEW, parsedUri)
        val context = pdfView.context
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
        } else {
            Log.w(TAG, "No activity found for URI: $uri")
        }
    }

    private fun handlePage(page: Int) {
        pdfView.jumpTo(page)
    }

    companion object {
        private val TAG = DefaultLinkHandler::class.java.simpleName
    }
}
