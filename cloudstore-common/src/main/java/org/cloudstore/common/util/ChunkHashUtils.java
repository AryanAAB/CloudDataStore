package org.cloudstore.common.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Utility functions for SHA-256 hashing.
 */
public final class ChunkHashUtils
{
    private static final String SHA_256 = "SHA-256";
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private ChunkHashUtils()
    {
    }

    public static MessageDigest newDigest()
    {
        try
        {
            return MessageDigest.getInstance(SHA_256);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("SHA-256 algorithm not available in JVM", e);
        }
    }

    public static String sha256Hex(byte[] data)
    {
        MessageDigest digest = newDigest();
        byte[] hash = digest.digest(data);
        return HEX_FORMAT.formatHex(hash);
    }

    public static String sha256Hex(InputStream inputStream) throws IOException
    {
        MessageDigest digest = newDigest();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1)
        {
            digest.update(buffer, 0, read);
        }
        return HEX_FORMAT.formatHex(digest.digest());
    }
}

