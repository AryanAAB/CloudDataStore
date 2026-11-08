package org.cloudstore.worker.upload;

import org.cloudstore.worker.upload.constants.WorkerChunkUploadProperties;
import org.cloudstore.worker.upload.exceptions.ChunkIntegrityException;
import org.cloudstore.worker.upload.exceptions.InvalidChunkUploadException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChunkContentVerifierTest
{
    private static final String SHA_256 = "SHA-256";
    private static final String CONTENT = "chunk-payload";

    @TempDir
    private Path temporaryDirectory;

    private ChunkContentVerifier verifier;

    @BeforeEach
    void setUp()
    {
        WorkerChunkUploadProperties properties = new WorkerChunkUploadProperties();
        properties.setBufferSizeBytes(4);
        verifier = new ChunkContentVerifier(properties);
    }

    @Test
    void streamsValidChunkAndVerifiesItsSizeAndChecksum() throws Exception
    {
        byte[] content = CONTENT.getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("verified.tmp");
        byte[] expectedDigest = verifier.validateExpectedValues(content.length, checksum(content));

        long copiedBytes = verifier.copyAndVerify(
                new ByteArrayInputStream(content), target, content.length, expectedDigest);

        assertThat(copiedBytes).isEqualTo(content.length);
        assertThat(Files.readAllBytes(target)).containsExactly(content);
    }

    @Test
    void rejectsStreamThatEndsBeforeExpectedSize() throws Exception
    {
        byte[] content = CONTENT.getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("short.tmp");
        byte[] expectedDigest = verifier.validateExpectedValues(content.length + 1L, checksum(content));

        assertThatThrownBy(() -> verifier.copyAndVerify(
                new ByteArrayInputStream(content), target, content.length + 1L, expectedDigest))
                .isInstanceOf(ChunkIntegrityException.class);
    }

    @Test
    void rejectsStreamThatExceedsExpectedSizeImmediately() throws Exception
    {
        byte[] content = CONTENT.getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("large.tmp");
        byte[] expectedDigest = verifier.validateExpectedValues(content.length - 1L, checksum(content));

        assertThatThrownBy(() -> verifier.copyAndVerify(
                new ByteArrayInputStream(content), target, content.length - 1L, expectedDigest))
                .isInstanceOf(ChunkIntegrityException.class);
    }

    @Test
    void rejectsChecksumMismatchEvenWhenSizeMatches() throws Exception
    {
        byte[] content = CONTENT.getBytes(StandardCharsets.UTF_8);
        Path target = temporaryDirectory.resolve("wrong-checksum.tmp");
        byte[] expectedDigest = verifier.validateExpectedValues(
                content.length, checksum("other".getBytes(StandardCharsets.UTF_8)));

        assertThatThrownBy(() -> verifier.copyAndVerify(
                new ByteArrayInputStream(content), target, content.length, expectedDigest))
                .isInstanceOf(ChunkIntegrityException.class);
    }

    @Test
    void rejectsNegativeSizeAndMalformedDigest()
    {
        assertThatThrownBy(() -> verifier.validateExpectedValues(-1L, checksum(new byte[0])))
                .isInstanceOf(InvalidChunkUploadException.class);
        assertThatThrownBy(() -> verifier.validateExpectedValues(0L, "bad-digest"))
                .isInstanceOf(InvalidChunkUploadException.class);
    }

    private static String checksum(byte[] content) throws Exception
    {
        return HexFormat.of().formatHex(MessageDigest.getInstance(SHA_256).digest(content));
    }
}
