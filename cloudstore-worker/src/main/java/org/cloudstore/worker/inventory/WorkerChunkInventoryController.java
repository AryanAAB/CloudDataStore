package org.cloudstore.worker.inventory;

import org.cloudstore.worker.api.constants.WorkerApiConstants;
import org.cloudstore.worker.inventory.constants.WorkerChunkInventoryProperties;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoint for listing chunk placement metadata held by this worker.
 */
@RestController
@RequestMapping(WorkerApiConstants.API_ROOT)
public class WorkerChunkInventoryController
{
    private final WorkerChunkInventory inventory;
    private final WorkerChunkInventoryProperties inventoryProperties;

    /**
     * Creates the endpoint with its inventory service and paging limits.
     */
    public WorkerChunkInventoryController(
            WorkerChunkInventory inventory,
            WorkerChunkInventoryProperties inventoryProperties)
    {
        this.inventory = inventory;
        this.inventoryProperties = inventoryProperties;
    }

    /**
     * Returns a page of chunk metadata and worker identity.
     */
    @GetMapping(WorkerApiConstants.CHUNK_INVENTORY_PATH)
    public ResponseEntity<WorkerChunkInventoryResponse> list(
            @RequestParam(
                    name = WorkerApiConstants.PAGE_QUERY_PARAMETER,
                    defaultValue = WorkerApiConstants.DEFAULT_PAGE_NUMBER) int page,
            @RequestParam(name = WorkerApiConstants.PAGE_SIZE_QUERY_PARAMETER, required = false) Integer size)
    {
        int effectiveSize = size == null ? inventoryProperties.getDefaultPageSize() : size;
        if (page < 0 || effectiveSize <= 0 || effectiveSize > inventoryProperties.getMaximumPageSize())
        {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(inventory.list(page, size));
    }
}
