package org.cloudstore.worker.reservation;

import org.cloudstore.worker.reservation.constants.WorkerReservationMessages;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerReservationControllerTest
{
    @Test
    void returnsSuccessForCreatedReservation()
    {
        ReservationResult result = ReservationResult.success(WorkerReservationMessages.CREATED);
        WorkerReservationController controller = new WorkerReservationController(fakeReservation(result));

        ResponseEntity<ReservationResult> response = controller.reserve("file", "chunk", "key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(result);
    }

    @Test
    void returnsBadRequestForInvalidIdentifiers()
    {
        ReservationResult result = ReservationResult.failure(
                WorkerReservationMessages.INVALID_IDENTIFIERS,
                ReservationResult.FailureCategory.INVALID_REQUEST);
        WorkerReservationController controller = new WorkerReservationController(fakeReservation(result));

        ResponseEntity<ReservationResult> response = controller.reserve("", "chunk", "key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isEqualTo(result);
    }

    @Test
    void returnsConflictForAnActiveDifferentKey()
    {
        ReservationResult result = ReservationResult.failure(
                WorkerReservationMessages.KEY_CONFLICT,
                ReservationResult.FailureCategory.CONFLICT);
        WorkerReservationController controller = new WorkerReservationController(fakeReservation(result));

        ResponseEntity<ReservationResult> response = controller.reserve("file", "chunk", "other-key");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isEqualTo(result);
    }

    private static WorkerReservation fakeReservation(ReservationResult result)
    {
        return new WorkerReservation(null, null, null, null, null, null, null)
        {
            @Override
            public ReservationResult reserve(String chunkId, String fileId, String key)
            {
                return result;
            }
        };
    }
}
