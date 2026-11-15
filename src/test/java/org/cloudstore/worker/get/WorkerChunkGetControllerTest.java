package org.cloudstore.worker.get;

import org.cloudstore.worker.heartbeat.HeartbeatService;
import org.cloudstore.worker.reservation.ReservationResult;
import org.cloudstore.worker.reservation.WorkerReservation;
import org.cloudstore.worker.reservation.lease.ReservationLeaseService;
import org.cloudstore.worker.storage.WorkerChunkFileStorage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerChunkGetControllerTest
{
    private ThreadPoolTaskScheduler scheduler;
    private ReservationLeaseService leaseService;

    @BeforeEach
    void setUp()
    {
        scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.initialize();
        leaseService = new TestLeaseService(scheduler);
    }

    @AfterEach
    void tearDown()
    {
        scheduler.shutdown();
    }

    @Test
    void streamsChunkAfterSuccessfulReservation() throws Exception
    {
        byte[] content = "chunk-content".getBytes();
        StubChunkStorage storage = new StubChunkStorage(content);
        WorkerChunkGetController controller =
                new WorkerChunkGetController(storage, new StubReservation(true), leaseService);

        ResponseEntity<StreamingResponseBody> response = controller.get(
                "file-id", "chunk-id", "reservation-key");
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        response.getBody().writeTo(output);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
        assertThat(response.getHeaders().getContentLength()).isEqualTo(content.length);
        assertThat(output.toByteArray()).containsExactly(content);
        assertThat(storage.wasOpened()).isTrue();
    }

    @Test
    void returnsConflictWithoutOpeningChunkWhenReservationIsRejected() throws IOException
    {
        StubChunkStorage storage = new StubChunkStorage(null);
        WorkerChunkGetController controller =
                new WorkerChunkGetController(storage, new StubReservation(false), leaseService);

        ResponseEntity<StreamingResponseBody> response = controller.get(
                "file-id", "chunk-id", "wrong-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(storage.wasOpened()).isFalse();
    }

    @Test
    void returnsNotFoundWhenChunkIsNotStored() throws IOException
    {
        WorkerChunkGetController controller = new WorkerChunkGetController(
                new StubChunkStorage(null), new StubReservation(true), leaseService);

        ResponseEntity<StreamingResponseBody> response = controller.get(
                "file-id", "missing-chunk", "reservation-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private static final class TestLeaseService extends ReservationLeaseService
    {
        private TestLeaseService(ThreadPoolTaskScheduler scheduler)
        {
            super(null, null, null, new HeartbeatService(scheduler));
        }

        @Override
        public void renewOrThrowIfInactive(String fileId, String chunkId, String key)
        {
            // Keep this controller test independent from the reservation database.
        }

        @Override
        public Duration getHeartbeatInterval()
        {
            return Duration.ofHours(1L);
        }
    }

    private static final class StubChunkStorage extends WorkerChunkFileStorage
    {
        private final byte[] content;
        private boolean opened;

        private StubChunkStorage(byte[] content)
        {
            super(null, null, null);
            this.content = content;
        }

        @Override
        public Optional<ChunkReadHandle> openForRead(String fileId, String chunkId)
        {
            opened = true;
            if (content == null)
            {
                return Optional.empty();
            }
            return Optional.of(new ChunkReadHandle(new ByteArrayInputStream(content), content.length));
        }

        private boolean wasOpened()
        {
            return opened;
        }
    }

    private static final class StubReservation extends WorkerReservation
    {
        private final boolean accepted;

        private StubReservation(boolean accepted)
        {
            super(null, null, null, null, null, null, null);
            this.accepted = accepted;
        }

        @Override
        public ReservationResult reserve(String chunkId, String fileId, String key)
        {
            return accepted
                    ? ReservationResult.success("reserved")
                    : ReservationResult.failure("conflict", ReservationResult.FailureCategory.CONFLICT);
        }
    }
}
