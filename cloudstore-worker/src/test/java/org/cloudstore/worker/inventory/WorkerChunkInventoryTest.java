package org.cloudstore.worker.inventory;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.external.constants.MasterReportingProperties;
import org.cloudstore.worker.inventory.constants.WorkerChunkInventoryProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerChunkInventoryTest
{
    private static final int DEFAULT_PAGE_SIZE = 25;

    private StubMetadataRepository metadataRepository;
    private MasterReportingProperties workerProperties;
    private WorkerChunkInventory inventory;

    @BeforeEach
    void setUp()
    {
        metadataRepository = new StubMetadataRepository();
        workerProperties = new MasterReportingProperties();
        workerProperties.setWorkerId("worker-test");
        workerProperties.setAdvertisedAddress("http://worker-test:8080");
        WorkerChunkInventoryProperties inventoryProperties = new WorkerChunkInventoryProperties();
        inventoryProperties.setDefaultPageSize(DEFAULT_PAGE_SIZE);
        inventoryProperties.setMaximumPageSize(100);
        inventory = new WorkerChunkInventory(metadataRepository.repository(), workerProperties, inventoryProperties);
    }

    @Test
    void returnsWorkerIdentityChunkMetadataAndPaginationDetails()
    {
        Instant lastIntegrityCheck = Instant.parse("2026-11-14T10:15:30Z");
        ChunkFileMetadata metadata = new ChunkFileMetadata(
                "file-1", "chunk-2", "local-path", 512L, "checksum-1");
        metadata.markIntegrityCheckedAt(lastIntegrityCheck);
        metadataRepository.setPage(new PageImpl<>(List.of(metadata), Pageable.ofSize(2).withPage(1), 5L));

        WorkerChunkInventoryResponse response = inventory.list(1, 2);

        assertThat(response.workerId()).isEqualTo("worker-test");
        assertThat(response.workerAddress()).isEqualTo("http://worker-test:8080");
        assertThat(response.page()).isEqualTo(1);
        assertThat(response.pageSize()).isEqualTo(2);
        assertThat(response.totalChunks()).isEqualTo(5L);
        assertThat(response.totalPages()).isEqualTo(3);
        assertThat(response.chunks()).containsExactly(
                new WorkerChunkInventoryItem("file-1", "chunk-2", 512L, "checksum-1", lastIntegrityCheck));
        assertThat(metadataRepository.pageable().getPageNumber()).isEqualTo(1);
        assertThat(metadataRepository.pageable().getPageSize()).isEqualTo(2);
        assertThat(metadataRepository.pageable().getSort().getOrderFor("fileId")).isNotNull();
        assertThat(metadataRepository.pageable().getSort().getOrderFor("chunkId")).isNotNull();
    }

    @Test
    void usesConfiguredDefaultPageSizeWhenCallerOmitsIt()
    {
        metadataRepository.setPage(new PageImpl<>(List.of(), Pageable.ofSize(DEFAULT_PAGE_SIZE), 0L));

        inventory.list(0, null);

        assertThat(metadataRepository.pageable().getPageSize()).isEqualTo(DEFAULT_PAGE_SIZE);
    }

    private static final class StubMetadataRepository
    {
        private Pageable pageable;
        private PageImpl<ChunkFileMetadata> page;

        private ChunkFileMetadataRepository repository()
        {
            return (ChunkFileMetadataRepository) Proxy.newProxyInstance(
                    ChunkFileMetadataRepository.class.getClassLoader(),
                    new Class<?>[]{ChunkFileMetadataRepository.class},
                    (proxy, method, arguments) ->
                    {
                        if (method.getName().equals("findAll")
                                && arguments != null
                                && arguments.length == 1
                                && arguments[0] instanceof Pageable requestedPage)
                        {
                            pageable = requestedPage;
                            return page;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }

        private void setPage(PageImpl<ChunkFileMetadata> page)
        {
            this.page = page;
        }

        private Pageable pageable()
        {
            return pageable;
        }
    }
}
