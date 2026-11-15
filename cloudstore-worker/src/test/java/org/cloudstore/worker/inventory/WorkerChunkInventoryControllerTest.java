package org.cloudstore.worker.inventory;

import org.cloudstore.worker.inventory.constants.WorkerChunkInventoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerChunkInventoryControllerTest
{
    private static final int DEFAULT_PAGE_SIZE = 25;
    private static final int MAXIMUM_PAGE_SIZE = 100;

    private StubInventory inventory;
    private WorkerChunkInventoryController controller;

    @BeforeEach
    void setUp()
    {
        inventory = new StubInventory();
        WorkerChunkInventoryProperties properties = new WorkerChunkInventoryProperties();
        properties.setDefaultPageSize(DEFAULT_PAGE_SIZE);
        properties.setMaximumPageSize(MAXIMUM_PAGE_SIZE);
        controller = new WorkerChunkInventoryController(inventory, properties);
    }

    @Test
    void usesConfiguredPageSizeWhenSizeIsOmitted()
    {
        WorkerChunkInventoryResponse expectedResponse =
                new WorkerChunkInventoryResponse("worker-1", "http://worker-1:8080", 0, 25, 0L, 0, List.of());
        inventory.setResponse(expectedResponse);

        ResponseEntity<WorkerChunkInventoryResponse> response = controller.list(0, null);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).isEqualTo(expectedResponse);
        assertThat(inventory.requestedPage()).isZero();
        assertThat(inventory.requestedPageSize()).isNull();
    }

    @Test
    void rejectsNegativePageNumbers()
    {
        ResponseEntity<WorkerChunkInventoryResponse> response = controller.list(-1, 10);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(inventory.wasCalled()).isFalse();
    }

    @Test
    void rejectsNonpositivePageSizes()
    {
        ResponseEntity<WorkerChunkInventoryResponse> response = controller.list(0, 0);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(inventory.wasCalled()).isFalse();
    }

    @Test
    void rejectsPageSizesAboveConfiguredMaximum()
    {
        ResponseEntity<WorkerChunkInventoryResponse> response = controller.list(0, MAXIMUM_PAGE_SIZE + 1);

        assertThat(response.getStatusCode().is4xxClientError()).isTrue();
        assertThat(inventory.wasCalled()).isFalse();
    }

    private static final class StubInventory extends WorkerChunkInventory
    {
        private WorkerChunkInventoryResponse response;
        private int requestedPage;
        private Integer requestedPageSize;
        private boolean called;

        private StubInventory()
        {
            super(null, null, null);
        }

        @Override
        public WorkerChunkInventoryResponse list(int pageNumber, Integer requestedPageSize)
        {
            called = true;
            this.requestedPage = pageNumber;
            this.requestedPageSize = requestedPageSize;
            return response;
        }

        private void setResponse(WorkerChunkInventoryResponse response)
        {
            this.response = response;
        }

        private int requestedPage()
        {
            return requestedPage;
        }

        private Integer requestedPageSize()
        {
            return requestedPageSize;
        }

        private boolean wasCalled()
        {
            return called;
        }
    }
}
