package com.hyntix.pdf.viewer.source

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import com.hyntix.pdfium.PdfDocument
import com.hyntix.pdfium.PdfiumCore
import java.io.IOException

import android.util.Log

class UriSource(internal val uri: Uri) : DocumentSource {
    // Store the PFD reference so it can be closed when the document is closed
    private var pfd: ParcelFileDescriptor? = null
    
    @Throws(IOException::class)
    override fun createDocument(context: Context, core: PdfiumCore, password: String?): PdfDocument {
        Log.d("UriSource", "Attempting to open URI: $uri")
        pfd = context.contentResolver.openFileDescriptor(uri, "r")
        if (pfd == null) {
            Log.e("UriSource", "openFileDescriptor returned null for $uri")
            throw IOException("File descriptor is null")
        }
        Log.d("UriSource", "FD opened successfully: ${pfd!!.fd}")
        val doc = core.openDocument(pfd!!.fd, password)
        if (doc != null) return doc
        
        val error = core.getLastError()
        if (error.code == PdfiumCore.FPDF_ERR_PASSWORD) {
            throw com.hyntix.pdf.viewer.exception.PdfPasswordException()
        }
        
        throw IOException("Failed to create document. Error code: $error")
    }
    
    /**
     * Close the ParcelFileDescriptor when the document source is no longer needed.
     * This should be called when the PDFView is recycled or the document is closed.
     */
    fun close() {
        try {
            pfd?.close()
            pfd = null
            Log.d("UriSource", "ParcelFileDescriptor closed successfully")
        } catch (e: Exception) {
            Log.e("UriSource", "Error closing ParcelFileDescriptor", e)
        }
    }
}
