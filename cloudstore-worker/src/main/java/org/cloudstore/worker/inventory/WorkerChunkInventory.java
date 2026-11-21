package org.cloudstore.worker.inventory;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.external.constants.MasterReportingProperties;
import org.cloudstore.worker.inventory.constants.WorkerChunkInventoryConstants;
import org.cloudstore.worker.inventory.constants.WorkerChunkInventoryProperties;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Provides paged chunk metadata for master placement and replication reconciliation.
 */
@Service
public class WorkerChunkInventory
{
    private final ChunkFileMetadataRepository metadataRepository;
    private final MasterReportingProperties workerProperties;
    private final WorkerChunkInventoryProperties inventoryProperties;

    /**
     * Creates the inventory operation with database, identity, and paging settings.
     */
    public WorkerChunkInventory(
            ChunkFileMetadataRepository metadataRepository,
            MasterReportingProperties workerProperties,
            WorkerChunkInventoryProperties inventoryProperties)
    {
        this.metadataRepository = metadataRepository;
        this.workerProperties = workerProperties;
        this.inventoryProperties = inventoryProperties;
    }

    /**
     * Returns one deterministic page of stored chunks and this worker's placement identity.
     *
     * @param pageNumber        zero-based page number
     * @param requestedPageSize requested page size, or {@code null} to use the configured default
     */
    public WorkerChunkInventoryResponse list(int pageNumber, Integer requestedPageSize)
    {
        int pageSize = requestedPageSize == null
                ? inventoryProperties.getDefaultPageSize()
                : requestedPageSize;
        Page<ChunkFileMetadata> metadataPage = metadataRepository.findAll(PageRequest.of(
                pageNumber,
                pageSize,
                Sort.by(
                        WorkerChunkInventoryConstants.FILE_ID_PROPERTY,
                        WorkerChunkInventoryConstants.CHUNK_ID_PROPERTY)));
        List<WorkerChunkInventoryItem> chunks = metadataPage.getContent().stream()
                .map(this::toInventoryItem)
                .toList();
        return new WorkerChunkInventoryResponse(
                workerProperties.getWorkerId(),
                workerProperties.getAdvertisedAddress(),
                metadataPage.getNumber(),
                metadataPage.getSize(),
                metadataPage.getTotalElements(),
                metadataPage.getTotalPages(),
                chunks);
    }

    /**
     * Converts persisted storage metadata to the fields safe and useful for master coordination.
     */
    private WorkerChunkInventoryItem toInventoryItem(ChunkFileMetadata metadata)
    {
        return new WorkerChunkInventoryItem(
                metadata.getFileId(),
                metadata.getChunkId(),
                metadata.getFileSize(),
                metadata.getChecksum(),
                metadata.getLastIntegrityCheckedAt());
    }
}
