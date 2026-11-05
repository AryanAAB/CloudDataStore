package org.cloudstore.worker;

/** Lifecycle states a worker can report for a stored chunk. */
public enum ChunkState
{
    /** The master assigned the chunk to a worker, but no data has arrived. */
    RESERVED,
    /** The worker is currently receiving the chunk's data. */
    RECEIVING,
    /** The worker received the complete chunk and is verifying or staging it. */
    STAGED,
    /** The chunk has been accepted into permanent worker storage. */
    COMMITTED,
    /** The reservation expired before the upload completed. */
    EXPIRED,
    /** The upload was cancelled and its temporary data can be removed. */
    ABORTED
}
