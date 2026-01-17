package com.hyntix.pdf.viewer.listener

import android.view.MotionEvent

interface OnTapListener {
    /**
     * Called when the user has a tap gesture, before processing scroll handle.
     *
     * @param e MotionEvent that registered as a confirmed single tap
     * @return true if the event was consumed, false otherwise
     */
    fun onTap(e: MotionEvent): Boolean
}
