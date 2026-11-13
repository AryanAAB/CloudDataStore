package org.cloudstore.worker.external;

/**
 * Worker identity and reachable address sent to the master during registration.
 */
public record MasterWorkerRegistration(String workerId, String address)
{
}
