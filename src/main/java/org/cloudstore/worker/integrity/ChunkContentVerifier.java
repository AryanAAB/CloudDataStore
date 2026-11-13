package org.cloudstore.worker.integrity;

import org.cloudstore.worker.integrity.constants.ChecksumConstants;
import org.cloudstore.worker.integrity.constants.ChunkIntegrityProperties;
import org.cloudstore.worker.integrity.constants.WorkerChunkIntegrityMessages;
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
    private final ChunkIntegrityProperties integrityProperties;

    /**
     * Creates the verifier with the configured streaming buffer size.
     */
    public ChunkContentVerifier(ChunkIntegrityProperties integrityProperties)
    {
        this.integrityProperties = integrityProperties;
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
        byte[] buffer = new byte[integrityProperties.getBufferSizeBytes()];
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
     * Checks a stored file against its recorded size and hexadecimal checksum.
     */
    public String verifyStoredFile(String fileLocation, long expectedSize, String expectedChecksum)
    {
        try
        {
            Path file = Path.of(fileLocation);
            if (Files.size(file) != expectedSize)
            {
                return WorkerChunkIntegrityMessages.SIZE_MISMATCH;
            }
            MessageDigest digest = newSha256Digest();
            try (InputStream input = Files.newInputStream(file))
            {
                byte[] buffer = new byte[integrityProperties.getBufferSizeBytes()];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1)
                {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return HexFormat.of().formatHex(digest.digest()).equalsIgnoreCase(expectedChecksum)
                    ? null
                    : WorkerChunkIntegrityMessages.CHECKSUM_MISMATCH;
        } catch (IOException | RuntimeException failure)
        {
            return WorkerChunkIntegrityMessages.FILE_UNAVAILABLE;
        }
    }

    /**
     * Creates the SHA-256 implementation required by the upload protocol.
     */
    private MessageDigest newSha256Digest()
    {
        try
        {
            return MessageDigest.getInstance(ChecksumConstants.SHA_256_ALGORITHM);
        } catch (NoSuchAlgorithmException impossible)
        {
            throw new IllegalStateException(impossible);
        }
    }
}
