package com.hyntix.pdf.viewer.model

/**
 * Represents a PDF bookmark/table of contents entry.
 * This is a library-facing class that wraps the underlying pdfium Bookmark.
 */
data class PdfBookmark(
    /** Title of the bookmark */
    val title: String,
    /** Target page index (0-indexed) */
    val pageIndex: Int,
    /** Child bookmarks (for hierarchical TOC) */
    val children: List<PdfBookmark>?,
    /** Actual page label from the PDF (e.g., "i", "ii", "1", "2", "A-1") */
    val pageLabel: String = ""
) {
    companion object {
        /**
         * Convert from pdfium Bookmark to PdfBookmark
         */
        fun fromPdfiumBookmark(bookmark: com.hyntix.pdfium.PdfBookmark): PdfBookmark {
            return PdfBookmark(
                title = bookmark.title,
                pageIndex = bookmark.pageIndex.toInt(),
                children = bookmark.children.map { fromPdfiumBookmark(it) },
                pageLabel = bookmark.pageLabel
            )
        }
        
        /**
         * Convert a list of pdfium Bookmarks to PdfBookmarks
         */
        fun fromPdfiumBookmarks(bookmarks: List<com.hyntix.pdfium.PdfBookmark>): List<PdfBookmark> {
            return bookmarks.map { fromPdfiumBookmark(it) }
        }
    }
}
