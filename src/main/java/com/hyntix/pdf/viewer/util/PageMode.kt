package com.hyntix.pdf.viewer.util

/**
 * Display mode for PDF pages.
 */
enum class PageMode {
    /** Single page view (default) */
    SINGLE,
    
    /** Two pages side-by-side (for landscape/tablet) */
    DUAL,
    
    /** Two pages with cover page on first (book style) */
    DUAL_WITH_COVER
}
