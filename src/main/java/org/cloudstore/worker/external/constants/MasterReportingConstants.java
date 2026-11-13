package org.cloudstore.worker.external.constants;

/**
 * Constants for the worker-to-master corruption reporting contract.
 */
public final class MasterReportingConstants
{
    public static final String CORRUPTION_REPORT_SUBMITTED =
            "Submitted chunk corruption report for file {} chunk {} to master";
    public static final String CORRUPTION_REPORT_FAILED =
            "Could not submit chunk corruption report for file {} chunk {} to master";
    public static final String WORKER_AVAILABLE = "AVAILABLE";
    public static final String WORKER_REGISTERED = "Registered worker {} with master";
    public static final String WORKER_REGISTRATION_FAILED = "Could not register worker {} with master";
    public static final String WORKER_HEARTBEAT_SENT = "Sent availability heartbeat for worker {}";
    public static final String WORKER_HEARTBEAT_FAILED = "Could not send availability heartbeat for worker {}";

    private MasterReportingConstants()
    {
    }
}
