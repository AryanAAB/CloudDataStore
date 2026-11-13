package org.cloudstore.worker.upload;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.heartbeat.HeartbeatService;
import org.cloudstore.worker.heartbeat.WorkerReservationLeaseHeartbeat;
import org.cloudstore.worker.integrity.ChunkContentVerifier;
import org.cloudstore.worker.integrity.constants.ChecksumConstants;
import org.cloudstore.worker.integrity.constants.ChunkIntegrityProperties;
import org.cloudstore.worker.upload.constants.WorkerChunkStorageProperties;
import org.cloudstore.worker.upload.exceptions.ChunkIntegrityException;
import org.cloudstore.worker.upload.exceptions.ChunkUploadNotReservedException;
import org.cloudstore.worker.validation.identifier.IdentifierValidator;
import org.cloudstore.worker.validation.key.ReservationKeyValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.PlatformTransactionManager;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkerChunkUploadTest
{
    private static final Path TEST_DIRECTORY = Path.of("target", "worker-upload-tests");
    private static final String FILE_ID = "upload-file";
    private static final String CHUNK_ID = "upload-chunk";
    private static final String KEY = "upload-key";
    private static final byte[] CONTENT = "valid-upload-content".getBytes(StandardCharsets.UTF_8);

    private Path temporaryDirectory;

    private ThreadPoolTaskScheduler scheduler;
    private ControlledLeaseHeartbeat leaseHeartbeat;
    private WorkerChunkUpload uploader;

    @BeforeEach
    void setUp() throws IOException
    {
        Files.createDirectories(TEST_DIRECTORY);
        temporaryDirectory = Files.createTempDirectory(TEST_DIRECTORY, "upload-");
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();

        leaseHeartbeat = new ControlledLeaseHeartbeat(Duration.ofSeconds(20L));
        WorkerChunkStorageProperties storageProperties = new WorkerChunkStorageProperties();
        storageProperties.setRootDirectory(temporaryDirectory);
        ChunkIntegrityProperties integrityProperties = new ChunkIntegrityProperties();
        integrityProperties.setBufferSizeBytes(8);
        ChunkContentVerifier verifier = new ChunkContentVerifier(integrityProperties);
        WorkerChunkFileStorage fileStorage = new LocalTestChunkFileStorage(temporaryDirectory);
        IdentifierValidator identifiers = identifier -> identifier == null || identifier.isBlank();
        ReservationKeyValidator keys = key -> key != null && key.isBlank();
        uploader = new WorkerChunkUpload(
                identifiers,
                keys,
                storageProperties,
                leaseHeartbeat,
                new HeartbeatService(scheduler),
                verifier,
                fileStorage);
    }

    @AfterEach
    void stopScheduler() throws IOException
    {
        scheduler.shutdown();
        try (Stream<Path> paths = Files.walk(temporaryDirectory))
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
    void writesOnlyVerifiedContentAndRenewsLeaseBeforeAndAfterTheUpload() throws Exception
    {
        long storedBytes = uploader.upload(
                FILE_ID, CHUNK_ID, KEY, CONTENT.length, checksum(CONTENT),
                new InputStreamOf(CONTENT));

        assertThat(storedBytes).isEqualTo(CONTENT.length);
        try (Stream<Path> storedFiles = Files.list(temporaryDirectory))
        {
            Path storedChunk = storedFiles.filter(path -> path.toString().endsWith(".chunk"))
                    .findFirst().orElseThrow();
            assertThat(Files.readAllBytes(storedChunk)).containsExactly(CONTENT);
        }
        assertThat(leaseHeartbeat.renewalCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void renewsReservationWhileUploadStreamIsStillInProgress() throws Exception
    {
        leaseHeartbeat.setHeartbeatInterval(Duration.ofMillis(10L));
        GatedInputStream content = new GatedInputStream(CONTENT);
        ExecutorService uploadExecutor = Executors.newSingleThreadExecutor();
        try
        {
            Future<Long> upload = uploadExecutor.submit(() -> uploader.upload(
                    FILE_ID, CHUNK_ID, KEY, CONTENT.length, checksum(CONTENT), content));

            assertThat(content.awaitFirstRead(1L, TimeUnit.SECONDS)).isTrue();
            assertThat(leaseHeartbeat.awaitAdditionalRenewal(1L, TimeUnit.SECONDS)).isTrue();
            assertThat(upload.isDone()).isFalse();

            content.allowReadToContinue();
            assertThat(upload.get(2L, TimeUnit.SECONDS)).isEqualTo(CONTENT.length);
        } finally
        {
            content.allowReadToContinue();
            uploadExecutor.shutdownNow();
        }
    }

    @Test
    void doesNotInstallChunkWhenChecksumDoesNotMatch() throws Exception
    {
        assertThatThrownBy(() -> uploader.upload(
                FILE_ID,
                CHUNK_ID,
                KEY,
                CONTENT.length,
                checksum("different".getBytes(StandardCharsets.UTF_8)),
                new InputStreamOf(CONTENT)))
                .isInstanceOf(ChunkIntegrityException.class);

        assertDirectoryIsEmpty();
        assertThat(leaseHeartbeat.renewalCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void stopsUploadAndDeletesTemporaryFileWhenHeartbeatLosesReservation() throws Exception
    {
        leaseHeartbeat.failOnRenewal(2);
        leaseHeartbeat.setHeartbeatInterval(Duration.ofMillis(10L));
        BlockingInputStream content = new BlockingInputStream();

        assertThatThrownBy(() -> uploader.upload(
                FILE_ID, CHUNK_ID, KEY, CONTENT.length, checksum(CONTENT), content))
                .isInstanceOf(ChunkUploadNotReservedException.class);

        assertThat(content.wasClosed()).isTrue();
        assertDirectoryIsEmpty();
        assertThat(leaseHeartbeat.renewalCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void deletesTemporaryFileWhenTheInputStreamFails() throws Exception
    {
        InputStream failingStream = new InputStream()
        {
            @Override
            public int read() throws IOException
            {
                throw new IOException();
            }
        };

        assertThatThrownBy(() -> uploader.upload(
                FILE_ID, CHUNK_ID, KEY, CONTENT.length, checksum(CONTENT), failingStream))
                .isInstanceOf(IOException.class);

        assertDirectoryIsEmpty();
        assertThat(leaseHeartbeat.renewalCount()).isGreaterThanOrEqualTo(1);
    }

    private void assertDirectoryIsEmpty() throws IOException
    {
        try (Stream<Path> storedFiles = Files.list(temporaryDirectory))
        {
            assertThat(storedFiles.count()).isZero();
        }
    }

    private static String checksum(byte[] content) throws Exception
    {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance(ChecksumConstants.SHA_256_ALGORITHM).digest(content));
    }

    private static final class ControlledLeaseHeartbeat extends WorkerReservationLeaseHeartbeat
    {
        private final AtomicInteger renewalCount = new AtomicInteger();
        private final CountDownLatch additionalRenewal = new CountDownLatch(1);
        private volatile int failedRenewalNumber = Integer.MAX_VALUE;
        private volatile Duration heartbeatInterval;

        private ControlledLeaseHeartbeat(Duration heartbeatInterval)
        {
            super(null, null, null, null);
            this.heartbeatInterval = heartbeatInterval;
        }

        @Override
        public void renewOrThrowIfInactive(String fileId, String chunkId, String key)
        {
            int renewalNumber = renewalCount.incrementAndGet();
            if (renewalNumber > 1)
            {
                additionalRenewal.countDown();
            }
            if (renewalNumber == failedRenewalNumber)
            {
                throw new ChunkUploadNotReservedException();
            }
        }

        @Override
        public Duration getHeartbeatInterval()
        {
            return heartbeatInterval;
        }

        private int renewalCount()
        {
            return renewalCount.get();
        }

        private boolean awaitAdditionalRenewal(long timeout, TimeUnit unit) throws InterruptedException
        {
            return additionalRenewal.await(timeout, unit);
        }

        private void failOnRenewal(int renewalNumber)
        {
            failedRenewalNumber = renewalNumber;
        }

        private void setHeartbeatInterval(Duration heartbeatInterval)
        {
            this.heartbeatInterval = heartbeatInterval;
        }
    }

    private static final class LocalTestChunkFileStorage extends WorkerChunkFileStorage
    {
        private final Path rootDirectory;

        private LocalTestChunkFileStorage(Path rootDirectory)
        {
            super(null, null, (PlatformTransactionManager) null);
            this.rootDirectory = rootDirectory;
        }

        @Override
        public ChunkFileMetadata store(
                Path temporaryFile,
                String fileId,
                String chunkId,
                long fileSize,
                String checksum) throws IOException
        {
            Path destination = rootDirectory.resolve("verified.chunk");
            Files.move(temporaryFile, destination);
            return new ChunkFileMetadata(fileId, chunkId, destination.toString(), fileSize, checksum);
        }
    }

    private static final class InputStreamOf extends InputStream
    {
        private final byte[] content;
        private int currentIndex;

        private InputStreamOf(byte[] content)
        {
            this.content = content;
        }

        @Override
        public int read()
        {
            if (currentIndex >= content.length)
            {
                return -1;
            }
            return Byte.toUnsignedInt(content[currentIndex++]);
        }
    }

    private static final class BlockingInputStream extends InputStream
    {
        private final CountDownLatch closed = new CountDownLatch(1);

        @Override
        public int read() throws IOException
        {
            try
            {
                if (!closed.await(2L, TimeUnit.SECONDS))
                {
                    throw new IOException();
                }
            } catch (InterruptedException interrupted)
            {
                Thread.currentThread().interrupt();
                throw new IOException(interrupted);
            }
            throw new IOException();
        }

        @Override
        public void close()
        {
            closed.countDown();
        }

        private boolean wasClosed()
        {
            return closed.getCount() == 0L;
        }
    }

    private static final class GatedInputStream extends InputStream
    {
        private final byte[] content;
        private final CountDownLatch firstRead = new CountDownLatch(1);
        private final CountDownLatch continueReading = new CountDownLatch(1);
        private int currentIndex;
        private boolean waitingForRelease = true;

        private GatedInputStream(byte[] content)
        {
            this.content = content;
        }

        @Override
        public int read() throws IOException
        {
            if (waitingForRelease)
            {
                waitingForRelease = false;
                firstRead.countDown();
                try
                {
                    if (!continueReading.await(2L, TimeUnit.SECONDS))
                    {
                        throw new IOException("Timed out waiting for the upload test to continue");
                    }
                } catch (InterruptedException interrupted)
                {
                    Thread.currentThread().interrupt();
                    throw new IOException(interrupted);
                }
            }

            if (currentIndex >= content.length)
            {
                return -1;
            }
            return Byte.toUnsignedInt(content[currentIndex++]);
        }

        private boolean awaitFirstRead(long timeout, TimeUnit unit) throws InterruptedException
        {
            return firstRead.await(timeout, unit);
        }

        private void allowReadToContinue()
        {
            continueReading.countDown();
        }
    }
}
