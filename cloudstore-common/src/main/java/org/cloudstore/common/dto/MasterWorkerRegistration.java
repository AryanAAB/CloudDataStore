package org.cloudstore.common.dto;

/**
 * Worker identity and reachable address sent to the master during registration.
 */
public record MasterWorkerRegistration(String workerId, String address)
{
}

