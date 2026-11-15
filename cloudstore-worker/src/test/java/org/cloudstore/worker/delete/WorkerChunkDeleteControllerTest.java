package org.cloudstore.worker.delete;

import org.cloudstore.worker.reservation.ReservationResult;
import org.cloudstore.worker.reservation.WorkerReservation;
import org.cloudstore.worker.storage.WorkerChunkFileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerChunkDeleteControllerTest
{
    @Test
    void returnsNoContentWhenChunkWasDeleted() throws IOException
    {
        WorkerChunkDeleteController controller =
                new WorkerChunkDeleteController(new StubChunkStorage(true), new StubReservation(true));

        ResponseEntity<Void> response = controller.delete("file-id", "chunk-id", "reservation-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void returnsNotFoundWhenChunkDoesNotExist() throws IOException
    {
        WorkerChunkDeleteController controller =
                new WorkerChunkDeleteController(new StubChunkStorage(false), new StubReservation(true));

        ResponseEntity<Void> response = controller.delete("file-id", "missing-chunk", "reservation-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void returnsConflictAndDoesNotDeleteWhenReservationIsRejected() throws IOException
    {
        StubChunkStorage storage = new StubChunkStorage(true);
        WorkerChunkDeleteController controller =
                new WorkerChunkDeleteController(storage, new StubReservation(false));

        ResponseEntity<Void> response = controller.delete("file-id", "chunk-id", "other-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(storage.wasDeleteCalled()).isFalse();
    }

    private static final class StubChunkStorage extends WorkerChunkFileStorage
    {
        private final boolean found;
        private boolean deleteCalled;

        private StubChunkStorage(boolean found)
        {
            super(null, null, null);
            this.found = found;
        }

        @Override
        public boolean delete(String fileId, String chunkId)
        {
            deleteCalled = true;
            return found;
        }

        private boolean wasDeleteCalled()
        {
            return deleteCalled;
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

