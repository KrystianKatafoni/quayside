package dev.quayside.summaries.api

/**
 * Why there is no summary. Holds no part of the message (ENG-C8); only fixed categories. Every error keeps the
 * previous summary.
 */
public sealed interface SummaryError {
    /** Blank, or only code blocks, tables or markup. */
    public data object Empty : SummaryError

    /** The model cannot be reached: no `claude` program or no answer in time. */
    public data object Unavailable : SummaryError

    /** The model or its output failed: an error exit, unreadable output. */
    public data object Failed : SummaryError
}
