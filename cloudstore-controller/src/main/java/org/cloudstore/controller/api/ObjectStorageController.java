package org.cloudstore.controller.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.cloudstore.controller.dto.*;
import org.cloudstore.controller.exception.ObjectNotFoundException;
import org.cloudstore.controller.master.StorageAccountingService;
import org.cloudstore.controller.master.WorkerRegistryService;
import org.cloudstore.controller.model.StoredObject;
import org.cloudstore.controller.model.WorkerNode;
import org.cloudstore.controller.proxy.UProxyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Public REST Controller exposing 2-step resumable uploads, 1-step multipart facade,
 * streaming downloads, object listing, and cluster diagnostics.
 */
@RestController
@RequestMapping("/api/v1")
public class ObjectStorageController
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectStorageController.class);

    private final UProxyService uProxyService;
    private final WorkerRegistryService workerRegistry;
    private final StorageAccountingService storageAccounting;
    private final ObjectMapper objectMapper;

    public ObjectStorageController(
            UProxyService uProxyService,
            WorkerRegistryService workerRegistry,
            StorageAccountingService storageAccounting,
            ObjectMapper objectMapper)
    {
        this.uProxyService = uProxyService;
        this.workerRegistry = workerRegistry;
        this.storageAccounting = storageAccounting;
        this.objectMapper = objectMapper;
    }

    // ==========================================
    // Step 1: Resumable Upload Initiation
    // ==========================================
    @PostMapping("/objects/sessions")
    public ResponseEntity<SessionInitiateResponse> initiateSession(@RequestBody SessionInitiateRequest request)
    {
        SessionInitiateResponse response = uProxyService.initiateSession(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ==========================================
    // Step 2: Resumable Binary Streaming
    // ==========================================
    @PutMapping("/objects/sessions/{sessionId}/data")
    public ResponseEntity<StoredObjectResponse> streamUpload(
            @PathVariable String sessionId,
            @RequestHeader(name = "X-Session-Key", required = true) String sessionKey,
            HttpServletRequest request) throws IOException
    {
        try (InputStream in = request.getInputStream())
        {
            StoredObject committed = uProxyService.processStreamUpload(sessionId, sessionKey, in);
            return ResponseEntity.status(HttpStatus.CREATED).body(uProxyService.buildObjectResponse(committed));
        }
    }

    // ==========================================
    // Upload Session Status (Resumability check)
    // ==========================================
    @GetMapping("/objects/sessions/{sessionId}")
    public ResponseEntity<SessionStatusResponse> getSessionStatus(@PathVariable String sessionId)
    {
        return ResponseEntity.ok(uProxyService.getSessionStatus(sessionId));
    }

    // ==========================================
    // 1-Step Backward-Compatible Facade
    // ==========================================
    @PostMapping(value = "/objects", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<StoredObjectResponse> uploadObjectMultipart(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "metadata", required = false) String metadataJson) throws IOException
    {
        Map<String, String> customMetadata = new HashMap<>();
        if (metadataJson != null && !metadataJson.isBlank())
        {
            try
            {
                customMetadata = objectMapper.readValue(metadataJson, new TypeReference<>() {});
            }
            catch (Exception e)
            {
                LOGGER.warn("Failed to parse custom metadata JSON: {}", metadataJson);
            }
        }

        SessionInitiateRequest initRequest = new SessionInitiateRequest(
                file.getOriginalFilename(),
                file.getSize(),
                file.getContentType(),
                customMetadata);

        SessionInitiateResponse session = uProxyService.initiateSession(initRequest);
        try (InputStream in = file.getInputStream())
        {
            StoredObject committed = uProxyService.processStreamUpload(
                    session.sessionId(), session.sessionKey(), in);
            return ResponseEntity.status(HttpStatus.CREATED).body(uProxyService.buildObjectResponse(committed));
        }
    }

    // ==========================================
    // Object Download (Streaming with fallback)
    // ==========================================
    @GetMapping("/objects/{objectId}")
    public ResponseEntity<StreamingResponseBody> downloadObject(@PathVariable String objectId)
    {
        Optional<StoredObject> opt = uProxyService.getObject(objectId);
        if (opt.isEmpty() || !StoredObject.STATUS_COMMITTED.equalsIgnoreCase(opt.get().getStatus()))
        {
            throw new ObjectNotFoundException("Object not found: " + objectId);
        }

        StoredObject obj = opt.get();
        StreamingResponseBody responseBody = outputStream -> uProxyService.streamDownload(objectId, outputStream);

        HttpHeaders headers = new HttpHeaders();
        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        if (obj.getContentType() != null && !obj.getContentType().isBlank())
        {
            try
            {
                mediaType = MediaType.parseMediaType(obj.getContentType());
            }
            catch (Exception ignored)
            {
            }
        }
        headers.setContentType(mediaType);
        headers.setContentLength(obj.getSize());
        headers.setContentDispositionFormData("attachment", obj.getName());

        return ResponseEntity.ok().headers(headers).body(responseBody);
    }

    // ==========================================
    // Get Object Metadata
    // ==========================================
    @GetMapping("/objects/{objectId}/metadata")
    public ResponseEntity<StoredObjectResponse> getObjectMetadata(@PathVariable String objectId)
    {
        StoredObject obj = uProxyService.getObject(objectId)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found: " + objectId));
        return ResponseEntity.ok(uProxyService.buildObjectResponse(obj));
    }

    // ==========================================
    // Update Object Custom Metadata (PATCH)
    // ==========================================
    @PatchMapping("/objects/{objectId}/metadata")
    public ResponseEntity<StoredObjectResponse> updateObjectMetadata(
            @PathVariable String objectId,
            @RequestBody Map<String, String> patchMetadata)
    {
        StoredObject updated = uProxyService.updateCustomMetadata(objectId, patchMetadata)
                .orElseThrow(() -> new ObjectNotFoundException("Object not found: " + objectId));
        return ResponseEntity.ok(uProxyService.buildObjectResponse(updated));
    }

    // ==========================================
    // Delete Object
    // ==========================================
    @DeleteMapping("/objects/{objectId}")
    public ResponseEntity<Void> deleteObject(@PathVariable String objectId)
    {
        boolean deleted = uProxyService.deleteObject(objectId);
        if (!deleted)
        {
            throw new ObjectNotFoundException("Object not found: " + objectId);
        }
        return ResponseEntity.noContent().build();
    }

    // ==========================================
    // List Objects (paginated)
    // ==========================================
    @GetMapping("/objects")
    public ResponseEntity<List<StoredObjectSummary>> listObjects(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "50") int size)
    {
        if (page < 0 || size < 1 || size > 500)
        {
            throw new IllegalArgumentException("page must be >= 0 and size must be between 1 and 500");
        }

        List<StoredObject> all = uProxyService.listCommittedObjects();
        int fromIndex = page * size;
        if (fromIndex >= all.size())
        {
            return ResponseEntity.ok(List.of());
        }
        int toIndex = Math.min(fromIndex + size, all.size());

        List<StoredObjectSummary> summaries = all.subList(fromIndex, toIndex).stream()
                .map(obj -> new StoredObjectSummary(
                        obj.getObjectId(),
                        obj.getName(),
                        obj.getSize(),
                        obj.getContentType(),
                        obj.getCreatedAt()))
                .collect(Collectors.toList());

        return ResponseEntity.ok(summaries);
    }

    // ==========================================
    // Admin Cluster Diagnostics
    // ==========================================
    @GetMapping("/admin/cluster")
    public ResponseEntity<Map<String, Object>> getClusterStatus()
    {
        Map<String, Object> status = new LinkedHashMap<>();
        List<WorkerNode> allWorkers = workerRegistry.getAllWorkers();
        status.put("totalWorkers", allWorkers.size());
        status.put("upWorkers", allWorkers.stream().filter(WorkerNode::isUp).count());
        status.put("workers", allWorkers);
        status.put("totalAvailableCapacityBytes", storageAccounting.getTotalAvailableCapacity());
        status.put("totalCommittedObjects", uProxyService.listCommittedObjects().size());
        return ResponseEntity.ok(status);
    }

    // ==========================================
    // Admin Object Chunk Replication Topology
    // ==========================================
    @GetMapping("/admin/objects/{objectId}/topology")
    public ResponseEntity<ChunkTopologyResponse> getObjectTopology(@PathVariable String objectId)
    {
        return uProxyService.getObjectTopology(objectId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

}

