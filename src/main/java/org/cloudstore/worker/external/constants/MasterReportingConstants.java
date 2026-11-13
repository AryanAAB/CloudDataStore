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

    private MasterReportingConstants()
    {
    }
}
