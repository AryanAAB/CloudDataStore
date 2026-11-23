package org.cloudstore.controller.proxy;

import org.cloudstore.common.util.ChunkHashUtils;
import org.cloudstore.controller.exception.QuotaExceededException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class ChunkStreamPipelineTest
{
    @Test
    void readNextChunk_partitionsStreamAccurately() throws IOException
    {
        // 2.5 MB of data
        int size = (int) (2.5 * 1024 * 1024);
        byte[] inputData = new byte[size];
        Arrays.fill(inputData, (byte) 'A');

        ByteArrayInputStream in = new ByteArrayInputStream(inputData);
        int chunkSize = 1024 * 1024;
        int maxChunks = 3;

        ChunkStreamPipeline pipeline = new ChunkStreamPipeline(in, size, maxChunks, chunkSize);

        // Chunk 0: 1 MB
        ChunkStreamPipeline.ChunkPayload c0 = pipeline.readNextChunk();
        assertNotNull(c0);
        assertEquals(0, c0.chunkIndex());
        assertEquals(chunkSize, c0.size());
        assertFalse(c0.isLast());

        // Chunk 1: 1 MB
        ChunkStreamPipeline.ChunkPayload c1 = pipeline.readNextChunk();
        assertNotNull(c1);
        assertEquals(1, c1.chunkIndex());
        assertEquals(chunkSize, c1.size());
        assertFalse(c1.isLast());

        // Chunk 2: 0.5 MB
        ChunkStreamPipeline.ChunkPayload c2 = pipeline.readNextChunk();
        assertNotNull(c2);
        assertEquals(2, c2.chunkIndex());
        assertEquals(size - (2 * chunkSize), c2.size());
        assertTrue(c2.isLast());

        // End of stream
        assertNull(pipeline.readNextChunk());

        assertEquals(size, pipeline.getTotalBytesRead());
        assertEquals(ChunkHashUtils.sha256Hex(inputData), pipeline.getFinalFileChecksum());
    }

    @Test
    void readNextChunk_exceedingByteBudget_throwsQuotaExceededException()
    {
        byte[] inputData = new byte[100];
        ByteArrayInputStream in = new ByteArrayInputStream(inputData);

        // Declared max is only 50 bytes
        ChunkStreamPipeline pipeline = new ChunkStreamPipeline(in, 50, 1, 1024);

        assertThrows(QuotaExceededException.class, pipeline::readNextChunk);
    }

    @Test
    void readNextChunk_exceedingChunkLimit_throwsQuotaExceededException() throws IOException
    {
        // 200 bytes, chunkSize 100 -> 2 chunks needed, but maxChunks allowed is 1
        byte[] inputData = new byte[200];
        ByteArrayInputStream in = new ByteArrayInputStream(inputData);

        ChunkStreamPipeline pipeline = new ChunkStreamPipeline(in, 200, 1, 100);

        // Chunk 0 succeeds
        assertNotNull(pipeline.readNextChunk());

        // Attempting to read chunk 1 exceeds the chunk budget
        assertThrows(QuotaExceededException.class, pipeline::readNextChunk);
    }

    @Test
    void readNextChunk_exactlyFillingAllocatedChunksEndsNormally() throws IOException
    {
        int chunkSize = 1024;
        int maxChunks = 2;
        byte[] inputData = new byte[chunkSize * maxChunks];
        Arrays.fill(inputData, (byte) 'B');
        ChunkStreamPipeline pipeline = new ChunkStreamPipeline(
                new ByteArrayInputStream(inputData), inputData.length, maxChunks, chunkSize);

        ChunkStreamPipeline.ChunkPayload firstChunk = pipeline.readNextChunk();
        ChunkStreamPipeline.ChunkPayload secondChunk = pipeline.readNextChunk();

        assertNotNull(firstChunk);
        assertNotNull(secondChunk);
        assertEquals(chunkSize, firstChunk.size());
        assertEquals(chunkSize, secondChunk.size());
        assertNull(pipeline.readNextChunk());
        assertEquals(inputData.length, pipeline.getTotalBytesRead());
        assertEquals(ChunkHashUtils.sha256Hex(inputData), pipeline.getFinalFileChecksum());
    }
}

