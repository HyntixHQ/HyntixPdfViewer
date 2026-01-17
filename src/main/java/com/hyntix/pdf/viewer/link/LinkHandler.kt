package com.hyntix.pdf.viewer.link

import com.hyntix.pdf.viewer.model.LinkTapEvent

interface LinkHandler {
    /**
     * Called when link was tapped by user
     *
     * @param event current event
     */
    fun handleLinkEvent(event: LinkTapEvent)
}
