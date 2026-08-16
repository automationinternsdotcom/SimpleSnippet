package com.simplesnippet.app.service

/**
 * Prevents the service's own ACTION_SET_TEXT writes from being re-processed
 * as user input. Without this, a snippet whose content contains a trigger
 * (self-referential or cyclic across snippets) re-matches on the text-changed
 * event emitted by the write and expands forever.
 *
 * Pure Kotlin so the suppression contract is unit-testable on the JVM.
 */
class ExpansionGuard {

    private var pendingServiceText: String? = null

    /** Call just before the service writes [text] into a field. */
    fun expectServiceWrite(text: String) {
        pendingServiceText = text
    }

    /**
     * Call with the text of every observed text-changed event. Returns true
     * exactly when the event is the echo of the last service write, and always
     * clears the pending state — a single write suppresses at most one event,
     * so a stale expectation can never swallow later user input.
     */
    fun consumeIfServiceWrite(observedText: String): Boolean {
        val pending = pendingServiceText
        pendingServiceText = null
        return pending != null && observedText == pending
    }
}
