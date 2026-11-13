package org.cloudstore.worker.upload;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.upload.constants.WorkerChunkStorageProperties;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadConstants;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadLogMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Installs verified chunk files and records their metadata in the database.
 */
@Service
public class WorkerChunkFileStorage
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerChunkFileStorage.class);

    private final ChunkFileMetadataRepository metadataRepository;
    private final WorkerChunkStorageProperties storageProperties;
    private final TransactionTemplate metadataTransaction;

    /**
     * Creates storage with its metadata repository and database transaction manager.
     */
    public WorkerChunkFileStorage(
            ChunkFileMetadataRepository metadataRepository,
            WorkerChunkStorageProperties storageProperties,
            PlatformTransactionManager transactionManager)
    {
        this.metadataRepository = metadataRepository;
        this.storageProperties = storageProperties;
        this.metadataTransaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Moves a verified temporary file into storage and commits its metadata row.
     * If the database transaction fails, this operation removes the newly installed file.
     *
     * @return persisted metadata for the installed chunk
     * @throws IOException when filesystem operations fail
     */
    public ChunkFileMetadata store(
            Path temporaryFile,
            String fileId,
            String chunkId,
            long fileSize,
            String checksum) throws IOException
    {
        Path rootDirectory = storageProperties.getRootDirectory().toAbsolutePath().normalize();
        Files.createDirectories(rootDirectory);
        Path targetFile = rootDirectory.resolve(UUID.randomUUID()
                        + WorkerChunkUploadConstants.CHUNK_FILE_SUFFIX)
                .normalize();
        try
        {
            Files.move(temporaryFile, targetFile, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupportedAtomicMove)
        {
            Files.move(temporaryFile, targetFile);
        }

        String previousFileLocation;
        ChunkFileMetadata metadata = new ChunkFileMetadata(
                fileId,
                chunkId,
                targetFile.toString(),
                fileSize,
                checksum);
        metadata.markIntegrityCheckedAt(Instant.now());
        try
        {
            previousFileLocation = metadataTransaction.execute(transactionStatus ->
            {
                Optional<ChunkFileMetadata> previous =
                        metadataRepository.findByFileIdAndChunkId(fileId, chunkId);
                String previousLocation =
                        previous.map(ChunkFileMetadata::getFileLocation).orElse(null);
                metadataRepository.saveAndFlush(metadata);
                return previousLocation;
            });
        } catch (RuntimeException databaseFailure)
        {
            try
            {
                Files.deleteIfExists(targetFile);
            } catch (IOException cleanupFailure)
            {
                databaseFailure.addSuppressed(cleanupFailure);
                LOGGER.error(
                        WorkerChunkUploadLogMessages.NEW_CHUNK_FILE_CLEANUP_FAILED,
                        targetFile,
                        cleanupFailure);
            }
            LOGGER.error(
                    WorkerChunkUploadLogMessages.FILE_METADATA_STORE_FAILED,
                    fileId,
                    chunkId,
                    databaseFailure);
            throw databaseFailure;
        }

        removeReplacedFile(previousFileLocation, targetFile, rootDirectory);
        return metadata;
    }

    /**
     * Removes a superseded file after commit if no row still references it.
     */
    private void removeReplacedFile(String previousLocation, Path currentFile, Path rootDirectory)
    {
        if (previousLocation == null || metadataRepository.existsByFileLocation(previousLocation))
        {
            return;
        }

        Path previousFile = Path.of(previousLocation).toAbsolutePath().normalize();
        if (!previousFile.startsWith(rootDirectory) || previousFile.equals(currentFile))
        {
            return;
        }

        try
        {
            Files.deleteIfExists(previousFile);
        } catch (IOException cleanupFailure)
        {
            LOGGER.warn(WorkerChunkUploadLogMessages.OLD_CHUNK_FILE_CLEANUP_FAILED,
                    previousLocation,
                    cleanupFailure);
        }
    }
}
