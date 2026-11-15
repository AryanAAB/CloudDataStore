package org.cloudstore.worker.storage;

import org.cloudstore.worker.data.constants.ChunkFileMetadataConstants;
import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.storage.constants.WorkerChunkStorageProperties;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadConstants;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadLogMessages;
import org.cloudstore.worker.upload.constants.WorkerChunkUploadMessages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
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
     * Deletes a chunk's metadata and stored bytes; returns false when it is absent.
     */
    public boolean delete(String fileId, String chunkId) throws IOException
    {
        Path rootDirectory = storageProperties.getRootDirectory().toAbsolutePath().normalize();
        Optional<ChunkFileMetadata> existing = metadataTransaction.execute(status ->
                metadataRepository.findByFileIdAndChunkId(fileId, chunkId));
        if (existing == null || existing.isEmpty())
        {
            return false;
        }

        Path storedFile = Path.of(existing.get().getFileLocation()).toAbsolutePath().normalize();
        if (!storedFile.startsWith(rootDirectory))
        {
            throw new IOException(WorkerChunkUploadMessages.STORAGE_PATH_OUTSIDE_ROOT);
        }

        Path stagedFile = rootDirectory.resolve(
                ChunkFileMetadataConstants.DELETE_STAGING_PREFIX + UUID.randomUUID()
                        + ChunkFileMetadataConstants.DELETE_STAGING_SUFFIX);
        boolean fileWasStaged = Files.exists(storedFile);
        if (fileWasStaged)
        {
            Files.move(storedFile, stagedFile, StandardCopyOption.ATOMIC_MOVE);
        }
        try
        {
            metadataTransaction.executeWithoutResult(status ->
            {
                Optional<ChunkFileMetadata> locked =
                        metadataRepository.findByFileIdAndChunkId(fileId, chunkId);
                if (locked.isEmpty()
                        || !locked.get().getFileLocation().equals(existing.get().getFileLocation()))
                {
                    throw new IllegalStateException(WorkerChunkUploadMessages.CHUNK_METADATA_CHANGED);
                }
                metadataRepository.delete(locked.get());
                metadataRepository.flush();
            });
        } catch (RuntimeException deletionFailure)
        {
            if (fileWasStaged && Files.exists(stagedFile))
            {
                Files.move(stagedFile, storedFile, StandardCopyOption.REPLACE_EXISTING);
            }
            LOGGER.error(WorkerChunkUploadLogMessages.CHUNK_DELETE_FAILED,
                    fileId, chunkId, deletionFailure);
            throw deletionFailure;
        }

        if (fileWasStaged)
        {
            try
            {
                Files.deleteIfExists(stagedFile);
            } catch (IOException cleanupFailure)
            {
                LOGGER.error(WorkerChunkUploadLogMessages.CHUNK_DELETE_CLEANUP_FAILED,
                        stagedFile, cleanupFailure);
                throw cleanupFailure;
            }
        }
        return true;
    }

    /**
     * Opens a stored chunk for streaming, returning empty when no metadata exists.
     */
    public Optional<ChunkReadHandle> openForRead(String fileId, String chunkId) throws IOException
    {
        Optional<ChunkFileMetadata> metadata = metadataRepository.findByFileIdAndChunkId(fileId, chunkId);
        if (metadata.isEmpty())
        {
            return Optional.empty();
        }

        Path rootDirectory = storageProperties.getRootDirectory().toAbsolutePath().normalize();
        Path storedFile = Path.of(metadata.get().getFileLocation()).toAbsolutePath().normalize();
        if (!storedFile.startsWith(rootDirectory))
        {
            throw new IOException(WorkerChunkUploadMessages.STORAGE_PATH_OUTSIDE_ROOT);
        }
        if (!Files.isRegularFile(storedFile))
        {
            return Optional.empty();
        }
        return Optional.of(new ChunkReadHandle(Files.newInputStream(storedFile), metadata.get().getFileSize()));
    }

    /**
     * Open input stream and declared size for one stored chunk.
     */
    public record ChunkReadHandle(InputStream inputStream, long fileSize) implements AutoCloseable
    {
        @Override
        public void close() throws IOException
        {
            inputStream.close();
        }
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

