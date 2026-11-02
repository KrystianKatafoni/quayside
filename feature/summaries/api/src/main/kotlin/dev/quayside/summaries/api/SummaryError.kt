package dev.quayside.summaries.api

/** Why there is no summary. Holds no part of the message (ENG-C8). Every error keeps the previous summary. */
public sealed interface SummaryError {
    /** Blank, or only code blocks, tables or markup. */
    public data object Empty : SummaryError
}
