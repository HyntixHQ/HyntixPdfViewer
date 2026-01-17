package com.hyntix.pdf.viewer.search

import android.graphics.RectF
import com.hyntix.pdf.viewer.PdfFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Search manager for finding text within PDF documents.
 * Uses pdfium's text extraction APIs to search and locate text.
 */
class SearchManager(private val pdfFile: PdfFile) {
    
    // Use SupervisorJob for proper scope management
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var currentJob: Job? = null
    private var results: MutableList<SearchResult> = mutableListOf()
    private var currentQuery: String = ""
    private var currentResultIndex: Int = -1
    
    /**
     * Search for text in the document.
     * Results are delivered via the callback.
     * 
     * @param query The text to search for (case-insensitive)
     * @param startPage Page to start searching from (0-indexed)
     * @param callback Callback for receiving search results
     */
    fun search(query: String, startPage: Int = 0, callback: SearchCallback) {
        // Cancel any existing search
        cancel()
        
        if (query.isBlank()) {
            callback.onSearchComplete(0)
            return
        }
        
        currentQuery = query.lowercase()
        results.clear()
        currentResultIndex = -1
        
        currentJob = scope.launch {
            try {
                val totalMatches = withContext(Dispatchers.IO) {
                    searchInDocument(query.lowercase(), startPage, callback)
                }
                callback.onSearchComplete(totalMatches)
            } catch (e: Exception) {
                callback.onSearchError(e)
            }
        }
    }
    
    private suspend fun searchInDocument(
        query: String, 
        startPage: Int,
        callback: SearchCallback
    ): Int {
        var totalMatches = 0
        val pageCount = pdfFile.pagesCount
        
        // Search from startPage to end, then from 0 to startPage
        val searchOrder = (startPage until pageCount) + (0 until startPage)
        
        for (page in searchOrder) {
            val pageText = getPageText(page)
            if (pageText.isNullOrBlank()) continue
            
            val lowerText = pageText.lowercase()
            var startIndex = 0
            var matchIndex = 0
            
            while (true) {
                val foundIndex = lowerText.indexOf(query, startIndex)
                if (foundIndex == -1) break
                
                val result = SearchResult(
                    page = page,
                    matchIndex = matchIndex,
                    textRect = null, // Would require character bounds API
                    matchedText = pageText.substring(foundIndex, foundIndex + query.length)
                )
                
                results.add(result)
                totalMatches++
                matchIndex++
                
                // Deliver result on main thread
                withContext(Dispatchers.Main) {
                    callback.onSearchResult(result)
                }
                
                startIndex = foundIndex + 1
            }
        }
        
        return totalMatches
    }
    
    /**
     * Get text content of a page.
     * Note: This is a placeholder - actual implementation depends on pdfium's text API.
     */
    private fun getPageText(page: Int): String? {
        // TODO: Implement using pdfiumCore.countCharactersOnPage() and extractCharacters()
        // The pdfium-android library may need to expose these methods
        return null
    }
    
    /**
     * Get the current search results.
     */
    fun getResults(): List<SearchResult> = results.toList()
    
    /**
     * Get the total number of search results.
     */
    fun getResultCount(): Int = results.size
    
    /**
     * Navigate to the next search result.
     * @return The next result, or null if no more results
     */
    fun nextResult(): SearchResult? {
        if (results.isEmpty()) return null
        currentResultIndex = (currentResultIndex + 1) % results.size
        return results[currentResultIndex]
    }
    
    /**
     * Navigate to the previous search result.
     * @return The previous result, or null if no results
     */
    fun previousResult(): SearchResult? {
        if (results.isEmpty()) return null
        currentResultIndex = if (currentResultIndex <= 0) results.size - 1 else currentResultIndex - 1
        return results[currentResultIndex]
    }
    
    /**
     * Get the current result index.
     */
    fun getCurrentIndex(): Int = currentResultIndex
    
    /**
     * Cancel the current search operation.
     */
    fun cancel() {
        currentJob?.cancel()
        currentJob = null
    }
    
    /**
     * Clear search results.
     */
    fun clear() {
        cancel()
        results.clear()
        currentQuery = ""
        currentResultIndex = -1
    }
    
    /**
     * Dispose the SearchManager and cancel all coroutines.
     * Call when no longer needed.
     */
    fun dispose() {
        scope.cancel()
        clear()
    }
}
