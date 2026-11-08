package org.cloudstore.worker.data;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataId;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.upload.WorkerChunkFileStorage;
import org.cloudstore.worker.upload.constants.WorkerChunkStorageProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Database and filesystem tests for chunk metadata storage.
 */
@DataJpaTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class ChunkFileMetadataStorageTest
{
    private static final Path TEST_DIRECTORY = Path.of("target", "chunk-metadata-tests");
    private static final String FILE_ID = "metadata-file";
    private static final String CHUNK_ID = "metadata-chunk";
    private static final String CHECKSUM = "verified-sha256";

    @Autowired
    private ChunkFileMetadataRepository metadataRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Path storageDirectory;
    private WorkerChunkFileStorage chunkFileStorage;

    @BeforeEach
    void setUp() throws IOException
    {
        Files.createDirectories(TEST_DIRECTORY);
        storageDirectory = Files.createTempDirectory(TEST_DIRECTORY, "storage-");
        WorkerChunkStorageProperties storageProperties = new WorkerChunkStorageProperties();
        storageProperties.setRootDirectory(storageDirectory);
        chunkFileStorage = new WorkerChunkFileStorage(
                metadataRepository, storageProperties, transactionManager);
    }

    @AfterEach
    void cleanStorageDirectory() throws IOException
    {
        try (Stream<Path> paths = Files.walk(storageDirectory))
        {
            paths.sorted(Comparator.reverseOrder()).forEach(path ->
            {
                try
                {
                    Files.deleteIfExists(path);
                } catch (IOException cleanupFailure)
                {
                    throw new IllegalStateException(cleanupFailure);
                }
            });
        }
    }

    @Test
    void storesFileAndMetadataAndRemovesReplacedFile() throws IOException
    {
        byte[] firstContent = "first-content".getBytes(StandardCharsets.UTF_8);
        Path firstTemporaryFile = temporaryFile(firstContent);
        ChunkFileMetadata firstMetadata = chunkFileStorage.store(
                firstTemporaryFile, FILE_ID, CHUNK_ID, firstContent.length, CHECKSUM);

        assertThat(Files.exists(Path.of(firstMetadata.getFileLocation()))).isTrue();
        Optional<ChunkFileMetadata> firstDatabaseRow =
                metadataRepository.findById(new ChunkFileMetadataId(FILE_ID, CHUNK_ID));
        assertThat(firstDatabaseRow).isPresent();
        assertThat(firstDatabaseRow.orElseThrow().getFileSize()).isEqualTo(firstContent.length);
        assertThat(firstDatabaseRow.orElseThrow().getChecksum()).isEqualTo(CHECKSUM);

        byte[] replacementContent = "replacement-content".getBytes(StandardCharsets.UTF_8);
        Path replacementTemporaryFile = temporaryFile(replacementContent);
        ChunkFileMetadata replacement = chunkFileStorage.store(
                replacementTemporaryFile, FILE_ID, CHUNK_ID, replacementContent.length, CHECKSUM);

        assertThat(Files.exists(Path.of(replacement.getFileLocation()))).isTrue();
        assertThat(Files.exists(Path.of(firstMetadata.getFileLocation()))).isFalse();
        assertThat(metadataRepository.findById(new ChunkFileMetadataId(FILE_ID, CHUNK_ID)))
                .get()
                .extracting(ChunkFileMetadata::getFileLocation)
                .isEqualTo(replacement.getFileLocation());
    }

    @Test
    void removesNewFileWhenMetadataTransactionFails() throws IOException
    {
        byte[] content = "unrecorded-content".getBytes(StandardCharsets.UTF_8);
        Path temporaryFile = temporaryFile(content);

        assertThatThrownBy(() -> chunkFileStorage.store(
                temporaryFile, null, CHUNK_ID, content.length, CHECKSUM))
                .isInstanceOf(RuntimeException.class);

        try (Stream<Path> storedFiles = Files.list(storageDirectory))
        {
            assertThat(storedFiles.count()).isZero();
        }
        assertThat(metadataRepository.count()).isZero();
    }

    private Path temporaryFile(byte[] content) throws IOException
    {
        Path temporaryFile = Files.createTempFile(storageDirectory, "incoming-", ".tmp");
        Files.write(temporaryFile, content);
        return temporaryFile;
    }
}
