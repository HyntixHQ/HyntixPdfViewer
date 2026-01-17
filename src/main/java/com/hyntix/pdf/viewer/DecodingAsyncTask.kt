package com.hyntix.pdf.viewer


import com.hyntix.pdf.viewer.source.DocumentSource
import com.hyntix.pdfium.PdfiumCore
import com.hyntix.pdfium.util.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference

class DecodingAsyncTask(
    private val docSource: DocumentSource,
    private val password: String?,
    private val userPages: IntArray?,
    pdfView: PDFView,
    private val pdfiumCore: PdfiumCore
) {
    private var cancelled = false
    private val pdfViewReference: WeakReference<PDFView> = WeakReference(pdfView)
    private var pdfFile: PdfFile? = null
    private var job: Job? = null

    fun execute() {
        job = CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                doInBackground()
            }
            onPostExecute(result)
        }
    }

    fun cancel() {
        cancelled = true
        job?.cancel()
    }

    private fun doInBackground(): Throwable? {
        try {
            val pdfView = pdfViewReference.get()
            if (pdfView != null) {
                val pdfDocument =
                    docSource.createDocument(pdfView.context, pdfiumCore, password)
                pdfFile = PdfFile(
                    pdfiumCore,
                    pdfDocument,
                    pdfView.pageFitPolicy,
                    getViewSize(pdfView),
                    userPages,
                    pdfView.isSwipeVertical,
                    pdfView.spacingPx,
                    pdfView.isAutoSpacingEnabled,
                    pdfView.isFitEachPage
                )
                return null
            } else {
                return NullPointerException("pdfView == null")
            }
        } catch (t: Throwable) {
            return t
        }
    }

    private fun getViewSize(pdfView: PDFView): Size {
        return Size(pdfView.width, pdfView.height)
    }

    private fun onPostExecute(t: Throwable?) {
        val pdfView = pdfViewReference.get()
        if (pdfView != null) {
            if (t != null) {
                pdfView.loadError(t)
                return
            }
            if (!cancelled) {
                pdfView.loadComplete(pdfFile!!)
            }
        }
    }
}
