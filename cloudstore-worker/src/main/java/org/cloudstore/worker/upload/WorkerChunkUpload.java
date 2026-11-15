package org.cloudstore.worker.upload;

import org.cloudstore.worker.integrity.ChunkContentVerifier;
import org.cloudstore.worker.reservation.lease.ReservationLeaseService;
import org.cloudstore.worker.reservation.lease.ReservationLeaseService.LeaseRenewalHandle;
import org.cloudstore.worker.storage.WorkerChunkFileStorage;
import org.cloudstore.worker.storage.constants.WorkerChunkStorageProperties;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadConstants;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadLogMessages;
import org.cloudstore.worker.upload.exceptions.ChunkUploadNotReservedException;
import org.cloudstore.worker.upload.exceptions.InvalidChunkUploadException;
import org.cloudstore.worker.validation.identifier.IdentifierValidator;
import org.cloudstore.worker.validation.key.ReservationKeyValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HexFormat;

/**
 * Validates a worker reservation, verifies the chunk stream, and stores the file with metadata.
 */
@Service
public class WorkerChunkUpload
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerChunkUpload.class);

    private final IdentifierValidator identifierValidator;
    private final ReservationKeyValidator keyValidator;
    private final WorkerChunkStorageProperties storageProperties;
    private final ReservationLeaseService leaseService;
    private final ChunkContentVerifier contentVerifier;
    private final WorkerChunkFileStorage chunkFileStorage;

    /**
     * Creates the upload operation with reservation validation and storage dependencies.
     */
    public WorkerChunkUpload(
            IdentifierValidator identifierValidator,
            ReservationKeyValidator keyValidator,
            WorkerChunkStorageProperties storageProperties,
            ReservationLeaseService leaseService,
            ChunkContentVerifier contentVerifier,
            WorkerChunkFileStorage chunkFileStorage)
    {
        this.identifierValidator = identifierValidator;
        this.keyValidator = keyValidator;
        this.storageProperties = storageProperties;
        this.leaseService = leaseService;
        this.contentVerifier = contentVerifier;
        this.chunkFileStorage = chunkFileStorage;
    }

    /**
     * Streams and verifies the request body before installing the file and its metadata.
     *
     * @param fileId           file identifier from the master reservation
     * @param chunkId          chunk identifier from the master reservation
     * @param key              reservation key, which may be {@code null}
     * @param expectedSize     expected number of bytes
     * @param expectedChecksum expected SHA-256 digest in hexadecimal form
     * @param content          incoming chunk stream
     * @return stored chunk size in bytes
     * @throws IOException when the request cannot be written to local storage
     */
    public long upload(
            String fileId,
            String chunkId,
            String key,
            long expectedSize,
            String expectedChecksum,
            InputStream content) throws IOException
    {
        if (identifierValidator.isInvalid(fileId)
                || identifierValidator.isInvalid(chunkId)
                || keyValidator.isInvalid(key))
        {
            throw new InvalidChunkUploadException();
        }

        byte[] expectedChecksumBytes =
                contentVerifier.validateExpectedValues(expectedSize, expectedChecksum);

        LeaseRenewalHandle renewal = leaseService.startRenewal(
                fileId, chunkId, key, () -> closeUploadStream(content, fileId, chunkId));
        Path chunkDirectory = storageProperties.getRootDirectory();
        Path temporaryFile = null;
        try
        {
            Files.createDirectories(chunkDirectory);
            temporaryFile = Files.createTempFile(
                    chunkDirectory,
                    WorkerChunkUploadConstants.TEMP_FILE_PREFIX,
                    WorkerChunkUploadConstants.TEMP_FILE_SUFFIX);
            long size;
            try
            {
                size = contentVerifier.copyAndVerify(
                        content, temporaryFile, expectedSize, expectedChecksumBytes);
            } catch (IOException uploadFailure)
            {
                if (renewal.isLeaseLost())
                {
                    throw new ChunkUploadNotReservedException();
                }
                throw uploadFailure;
            }
            if (renewal.isLeaseLost())
            {
                throw new ChunkUploadNotReservedException();
            }
            renewal.close();
            leaseService.renewOrThrowIfInactive(fileId, chunkId, key);
            String verifiedChecksum = HexFormat.of().formatHex(expectedChecksumBytes);
            chunkFileStorage.store(
                    temporaryFile,
                    fileId,
                    chunkId,
                    size,
                    verifiedChecksum);
            LOGGER.info(WorkerChunkUploadLogMessages.CHUNK_STORED, fileId, chunkId, size);
            return size;
        } finally
        {
            renewal.close();
            if (temporaryFile != null)
            {
                Files.deleteIfExists(temporaryFile);
            }
        }
    }

    /**
     * Closes the incoming stream after the shared lease service detects lease loss.
     */
    private void closeUploadStream(InputStream content, String fileId, String chunkId)
    {
        try
        {
            content.close();
        } catch (IOException closeFailure)
        {
            LOGGER.warn(WorkerChunkUploadLogMessages.HEARTBEAT_FAILED, fileId, chunkId, closeFailure);
        }
    }

}


