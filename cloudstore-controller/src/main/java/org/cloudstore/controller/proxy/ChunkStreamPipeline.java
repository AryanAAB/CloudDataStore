package org.cloudstore.controller.proxy;

import org.cloudstore.common.util.ChunkHashUtils;
import org.cloudstore.controller.exception.QuotaExceededException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Pipelined chunk partitioner and strict quota enforcement stream reader.
 */
public class ChunkStreamPipeline
{
    private static final HexFormat HEX_FORMAT = HexFormat.of();

    private final InputStream inputStream;
    private final long maxAllowedBytes;
    private final int maxAllowedChunks;
    private final int chunkSize;
    private final MessageDigest totalDigest;

    private long totalBytesRead = 0;
    private int currentChunkIndex = 0;
    private boolean streamExhausted = false;

    public ChunkStreamPipeline(InputStream inputStream, long maxAllowedBytes, int maxAllowedChunks, int chunkSize)
    {
        this.inputStream = inputStream;
        this.maxAllowedBytes = maxAllowedBytes;
        this.maxAllowedChunks = maxAllowedChunks;
        this.chunkSize = chunkSize;
        this.totalDigest = ChunkHashUtils.newDigest();
    }

    /**
     * Reads the next chunk from the stream. Returns null when the stream is fully read.
     * Throws QuotaExceededException if the client attempts to stream past allocated limits.
     */
    public ChunkPayload readNextChunk() throws IOException
    {
        if (streamExhausted)
        {
            return null;
        }

        if (currentChunkIndex >= maxAllowedChunks)
        {
            int nextByte = inputStream.read();
            if (nextByte == -1)
            {
                streamExhausted = true;
                return null;
            }

            if (totalBytesRead + 1 > maxAllowedBytes)
            {
                throw new QuotaExceededException(
                        "Upload exceeded declared byte budget of " + maxAllowedBytes + " bytes");
            }

            throw new QuotaExceededException(
                    "Upload exceeded maximum allocated chunk count: " + maxAllowedChunks);
        }

        ByteArrayOutputStream buffer = new ByteArrayOutputStream(chunkSize);
        byte[] readBuffer = new byte[8192];
        int remainingInChunk = chunkSize;

        while (remainingInChunk > 0)
        {
            int toRead = Math.min(readBuffer.length, remainingInChunk);
            int bytesRead = inputStream.read(readBuffer, 0, toRead);
            if (bytesRead == -1)
            {
                streamExhausted = true;
                break;
            }

            if (totalBytesRead + bytesRead > maxAllowedBytes)
            {
                throw new QuotaExceededException(
                        "Upload exceeded declared byte budget of " + maxAllowedBytes + " bytes");
            }

            buffer.write(readBuffer, 0, bytesRead);
            totalDigest.update(readBuffer, 0, bytesRead);
            totalBytesRead += bytesRead;
            remainingInChunk -= bytesRead;
        }

        byte[] chunkBytes = buffer.toByteArray();

        // Edge case: if initial stream was 0 bytes, emit a single empty chunk if maxAllowedBytes == 0
        if (chunkBytes.length == 0 && totalBytesRead == 0 && !streamExhausted)
        {
            streamExhausted = true;
        }
        else if (chunkBytes.length == 0 && streamExhausted)
        {
            return null;
        }

        String chunkHash = ChunkHashUtils.sha256Hex(chunkBytes);
        ChunkPayload payload = new ChunkPayload(
                currentChunkIndex,
                chunkBytes,
                chunkBytes.length,
                chunkHash,
                streamExhausted);

        currentChunkIndex++;
        return payload;
    }

    public long getTotalBytesRead()
    {
        return totalBytesRead;
    }

    public String getFinalFileChecksum()
    {
        return HEX_FORMAT.formatHex(totalDigest.digest());
    }

    public record ChunkPayload(
            int chunkIndex,
            byte[] data,
            int size,
            String sha256Hex,
            boolean isLast)
    {
    }
}

