package org.cloudstore.controller.proxy;

import org.cloudstore.common.constants.WorkerApiConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * HTTP client for communicating with worker storage nodes.
 */
@Component
public class WorkerChunkClient
{
    private static final Logger LOGGER = LoggerFactory.getLogger(WorkerChunkClient.class);

    private final HttpClient httpClient;

    public WorkerChunkClient()
    {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Reserves a chunk for upload on a worker node.
     */
    public boolean reserveChunk(String workerAddress, String fileId, String chunkId, String reservationKey)
    {
        String url = String.format("%s%s/%s/chunks/%s/reservation",
                workerAddress, WorkerApiConstants.API_ROOT, fileId, chunkId);
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header(WorkerApiConstants.RESERVATION_KEY_HEADER, reservationKey)
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() >= 200 && response.statusCode() < 300;
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Failed to reserve chunk {} for file {} on worker at {}",
                    chunkId, fileId, workerAddress, e);
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Uploads chunk bytes to a worker node with hash and size verification.
     */
    public boolean uploadChunk(
            String workerAddress,
            String fileId,
            String chunkId,
            String reservationKey,
            byte[] chunkBytes,
            String sha256Hex)
    {
        String url = String.format("%s%s/%s/chunks/%s/upload",
                workerAddress, WorkerApiConstants.API_ROOT, fileId, chunkId);
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/octet-stream")
                    .header(WorkerApiConstants.RESERVATION_KEY_HEADER, reservationKey)
                    .header(WorkerApiConstants.CHUNK_SIZE_HEADER, String.valueOf(chunkBytes.length))
                    .header(WorkerApiConstants.CHUNK_SHA256_HEADER, sha256Hex)
                    .PUT(HttpRequest.BodyPublishers.ofByteArray(chunkBytes))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 200 && response.statusCode() < 300)
            {
                return true;
            }
            LOGGER.warn("Worker at {} rejected chunk {} upload with status {}: {}",
                    workerAddress, chunkId, response.statusCode(), response.body());
            return false;
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Upload failed for chunk {} on worker at {}", chunkId, workerAddress, e);
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    /**
     * Downloads chunk bytes from a worker node. Returns null on failure.
     */
    public byte[] downloadChunk(String workerAddress, String fileId, String chunkId, String reservationKey)
    {
        String url = String.format("%s%s/%s/chunks/%s",
                workerAddress, WorkerApiConstants.API_ROOT, fileId, chunkId);
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header(WorkerApiConstants.RESERVATION_KEY_HEADER, reservationKey)
                    .GET()
                    .build();

            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() == 200)
            {
                return response.body();
            }
            LOGGER.warn("Failed to download chunk {} from worker at {}: status {}",
                    chunkId, workerAddress, response.statusCode());
            return null;
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Error downloading chunk {} from worker at {}", chunkId, workerAddress, e);
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    /**
     * Deletes a chunk from a worker node.
     */
    public boolean deleteChunk(String workerAddress, String fileId, String chunkId, String reservationKey)
    {
        String url = String.format("%s%s/%s/chunks/%s",
                workerAddress, WorkerApiConstants.API_ROOT, fileId, chunkId);
        try
        {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header(WorkerApiConstants.RESERVATION_KEY_HEADER, reservationKey)
                    .DELETE()
                    .build();

            HttpResponse<Void> response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200 || response.statusCode() == 204 || response.statusCode() == 404;
        }
        catch (IOException | InterruptedException e)
        {
            LOGGER.error("Error deleting chunk {} from worker at {}", chunkId, workerAddress, e);
            if (e instanceof InterruptedException)
            {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }
}

