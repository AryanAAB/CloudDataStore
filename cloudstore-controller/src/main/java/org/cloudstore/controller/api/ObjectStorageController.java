package org.cloudstore.controller.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.cloudstore.controller.dto.*;
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
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(committed));
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
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(committed));
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
            return ResponseEntity.notFound().build();
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
    // List Objects
    // ==========================================
    @GetMapping("/objects")
    public ResponseEntity<List<StoredObjectSummary>> listObjects()
    {
        List<StoredObjectSummary> summaries = uProxyService.listCommittedObjects().stream()
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

    private StoredObjectResponse toResponse(StoredObject obj)
    {
        return new StoredObjectResponse(
                obj.getObjectId(),
                obj.getName(),
                obj.getSize(),
                obj.getContentType(),
                obj.getChecksum(),
                obj.getTotalChunks(),
                obj.getStatus(),
                obj.getCreatedAt(),
                obj.getUpdatedAt(),
                obj.getCustomMetadata());
    }
}

