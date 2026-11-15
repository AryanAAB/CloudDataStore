package org.cloudstore.worker.external;

import java.time.Instant;

/**
 * Worker availability information sent to the master periodically.
 */
public record MasterWorkerHeartbeat(String workerId, String status, Instant timestamp)
{
}
