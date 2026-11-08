package org.cloudstore.worker.upload;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadConstants;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadProperties;
import org.cloudstore.worker.upload.exceptions.ChunkIntegrityException;
import org.cloudstore.worker.upload.exceptions.InvalidChunkUploadException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Streams chunk bytes to disk while checking their declared size and SHA-256 checksum.
 */
@Component
public class ChunkContentVerifier
{
    private final WorkerChunkUploadProperties uploadProperties;

    /**
     * Creates the verifier with the configured streaming buffer size.
     */
    public ChunkContentVerifier(WorkerChunkUploadProperties uploadProperties)
    {
        this.uploadProperties = uploadProperties;
    }

    /**
     * Validates the request metadata before starting an upload.
     *
     * @return the expected checksum as bytes
     */
    public byte[] validateExpectedValues(long expectedSize, String expectedChecksum)
    {
        if (expectedSize < 0L || expectedChecksum == null)
        {
            throw new InvalidChunkUploadException();
        }
        try
        {
            byte[] checksum = HexFormat.of().parseHex(expectedChecksum);
            if (checksum.length != newSha256Digest().getDigestLength())
            {
                throw new InvalidChunkUploadException();
            }
            return checksum;
        } catch (IllegalArgumentException invalidHex)
        {
            throw new InvalidChunkUploadException();
        }
    }

    /**
     * Copies the stream to a temporary file, stopping if it exceeds the expected size.
     */
    public long copyAndVerify(
            InputStream content,
            Path destination,
            long expectedSize,
            byte[] expectedChecksum) throws IOException
    {
        MessageDigest digest = newSha256Digest();
        byte[] buffer = new byte[uploadProperties.getBufferSizeBytes()];
        long totalBytes = 0L;
        try (OutputStream output = Files.newOutputStream(destination))
        {
            int bytesRead;
            while ((bytesRead = content.read(buffer)) != -1)
            {
                if (bytesRead > expectedSize - totalBytes)
                {
                    throw new ChunkIntegrityException();
                }
                totalBytes += bytesRead;
                digest.update(buffer, 0, bytesRead);
                output.write(buffer, 0, bytesRead);
            }
        }

        if (totalBytes != expectedSize || !MessageDigest.isEqual(digest.digest(), expectedChecksum))
        {
            throw new ChunkIntegrityException();
        }
        return totalBytes;
    }

    /**
     * Creates the SHA-256 implementation required by the upload protocol.
     */
    private MessageDigest newSha256Digest()
    {
        try
        {
            return MessageDigest.getInstance(WorkerChunkUploadConstants.SHA_256_ALGORITHM);
        } catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException(impossible);
        }
    }
}
