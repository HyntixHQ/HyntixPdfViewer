package com.hyntix.pdf.viewer.toc

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.hyntix.pdfium.PdfDocument

/**
 * A minimal TOC handler implementation.
 * Provides basic functionality without styling - app should customize appearance.
 * 
 * This is a simple vertical list of bookmarks. For more advanced UIs
 * (sidesheets, bottom sheets, etc.), implement TocHandler directly.
 */
class DefaultTocHandler(private val context: Context) : TocHandler {
    
    private var container: ScrollView? = null
    private var listLayout: LinearLayout? = null
    private var bookmarkCallback: ((Int) -> Unit)? = null
    private var bookmarks: List<com.hyntix.pdfium.PdfBookmark> = emptyList()
    
    init {
        container = ScrollView(context).apply {
            visibility = View.GONE
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        listLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        container?.addView(listLayout)
    }
    
    /**
     * Get the view to add to your layout.
     * Add this view to your activity/fragment layout.
     */
    fun getView(): View = container!!
    
    override fun updateToc(bookmarks: List<com.hyntix.pdfium.PdfBookmark>) {
        this.bookmarks = bookmarks
        listLayout?.removeAllViews()
        addBookmarks(bookmarks, 0)
    }
    
    private fun addBookmarks(bookmarks: List<com.hyntix.pdfium.PdfBookmark>, depth: Int) {
        for (bookmark in bookmarks) {
            val textView = TextView(context).apply {
                // Show page label at the end if available, otherwise show page number
                val pageInfo = when {
                    bookmark.pageLabel.isNotEmpty() -> bookmark.pageLabel
                    bookmark.pageIndex >= 0 -> (bookmark.pageIndex + 1).toString()
                    else -> ""
                }
                text = if (pageInfo.isNotEmpty()) "${bookmark.title}  $pageInfo" else bookmark.title
                setPadding(16 + (depth * 24), 12, 16, 12)
                setOnClickListener {
                    bookmarkCallback?.invoke(bookmark.pageIndex.toInt())
                }
            }
            listLayout?.addView(textView)
            
            // Add children recursively
            if (bookmark.children.isNotEmpty()) {
                addBookmarks(bookmark.children, depth + 1)
            }
        }
    }
    
    override fun show() {
        container?.visibility = View.VISIBLE
    }
    
    override fun hide() {
        container?.visibility = View.GONE
    }
    
    override fun isVisible(): Boolean = container?.visibility == View.VISIBLE
    
    override fun onBookmarkSelected(callback: (pageIndex: Int) -> Unit) {
        bookmarkCallback = callback
    }
}
