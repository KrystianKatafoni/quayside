package dev.quayside.sessions.api

import dev.quayside.core.domain.SessionId

/**
 * A [PastSessionsHarness] that can also change the disk and the supervisor the way the resume of a disk session
 * (FR-P9, AM-313) has to survive: a deleted session file and a conversation open in another Claude Code process.
 */
public interface ResumeHarness : PastSessionsHarness {
    /**
     * The session file of conversation [id] is deleted outside the app. The disk session stays on the list until the
     * next scan, so a test that calls this and not [settle] resumes a row that is no longer backed by a file.
     */
    public fun removeSessionFile(id: SessionId)

    /** The conversation [id] is open in another Claude Code process: the next resume of it starts a copy (R4). */
    public fun openElsewhere(id: SessionId)
}
