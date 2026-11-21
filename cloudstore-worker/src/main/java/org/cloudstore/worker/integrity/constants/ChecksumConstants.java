package org.cloudstore.worker.integrity.constants;

/**
 * Shared checksum algorithm identifiers used by upload verification and integrity scans.
 */
public final class ChecksumConstants
{
    /**
     * Algorithm mandated by the current worker chunk checksum protocol.
     */
    public static final String SHA_256_ALGORITHM = "SHA-256";
    public static final int SHA_256_HEX_LENGTH = 64;

    private ChecksumConstants()
    {
    }
}
