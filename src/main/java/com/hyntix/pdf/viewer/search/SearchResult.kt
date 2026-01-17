package com.hyntix.pdf.viewer.search

import android.graphics.RectF

/**
 * Represents a single search result within a PDF document.
 *
 * @param page Page index (0-indexed) where the match was found
 * @param matchIndex Index of this match within the page
 * @param textRect Rectangle bounds of the matched text on the page (in page coordinates)
 * @param matchedText The actual text that was matched
 */
data class SearchResult(
    val page: Int,
    val matchIndex: Int,
    val textRect: RectF?,
    val matchedText: String
)

/**
 * Callback interface for search operations.
 */
interface SearchCallback {
    /**
     * Called when a match is found.
     * @param result The search result
     */
    fun onSearchResult(result: SearchResult)
    
    /**
     * Called when search is complete.
     * @param totalMatches Total number of matches found
     */
    fun onSearchComplete(totalMatches: Int)
    
    /**
     * Called when search fails.
     * @param error The exception that occurred
     */
    fun onSearchError(error: Throwable)
}
