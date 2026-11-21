package org.cloudstore.worker.integrity;

import org.cloudstore.worker.data.metadata.ChunkFileMetadata;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataId;
import org.cloudstore.worker.data.metadata.ChunkFileMetadataRepository;
import org.cloudstore.worker.integrity.constants.IntegrityScanProperties;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Checks stored chunk files against their committed size and SHA-256 metadata.
 */
@Service
public class WorkerChunkIntegrityScanner
{
    private final ChunkFileMetadataRepository metadataRepository;
    private final IntegrityScanProperties scanProperties;
    private final ChunkCorruptionReporter corruptionReporter;
    private final ChunkContentVerifier contentVerifier;

    public WorkerChunkIntegrityScanner(
            ChunkFileMetadataRepository metadataRepository,
            IntegrityScanProperties scanProperties,
            ChunkCorruptionReporter corruptionReporter,
            ChunkContentVerifier contentVerifier)
    {
        this.metadataRepository = metadataRepository;
        this.scanProperties = scanProperties;
        this.corruptionReporter = corruptionReporter;
        this.contentVerifier = contentVerifier;
    }

    /**
     * Checks one oldest-first batch and persists check timestamps for unchanged rows.
     */
    public int scanBatch()
    {
        List<ChunkFileMetadata> batch = metadataRepository.findIntegrityCheckBatch(
                PageRequest.of(0, scanProperties.getBatchSize()));
        int checked = 0;
        for (ChunkFileMetadata snapshot : batch)
        {
            String reason = contentVerifier.verifyStoredFile(
                    snapshot.getFileLocation(), snapshot.getFileSize(), snapshot.getChecksum());
            Optional<ChunkFileMetadata> current = metadataRepository.findById(
                    new ChunkFileMetadataId(snapshot.getFileId(), snapshot.getChunkId()));
            if (current.isEmpty() || !current.get().getFileLocation().equals(snapshot.getFileLocation()))
            {
                continue;
            }
            current.get().markIntegrityCheckedAt(Instant.now());
            metadataRepository.save(current.get());
            checked++;
            if (reason != null)
            {
                corruptionReporter.report(snapshot.getFileId(), snapshot.getChunkId(), reason);
            }
        }
        return checked;
    }

}
