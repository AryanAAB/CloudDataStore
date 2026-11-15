package org.cloudstore.controller.dto;

import java.util.List;

public record ChunkTopologyResponse(
        String objectId,
        String name,
        int totalChunks,
        int requiredReplicationFactor,
        List<ChunkTopologyItem> chunks)
{
    public record ChunkTopologyItem(
            String chunkId,
            int chunkIndex,
            long size,
            String sha256,
            long liveHealthyReplicas,
            List<ChunkReplicaInfo> replicas)
    {
    }

    public record ChunkReplicaInfo(
            String workerId,
            String address,
            String replicaState,
            String workerStatus)
    {
    }
}
