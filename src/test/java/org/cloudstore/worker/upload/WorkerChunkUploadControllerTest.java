package org.cloudstore.worker.upload;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class WorkerChunkUploadControllerTest
{
    @Test
    void forwardsIdsKeySizeChecksumAndRawBodyToUploadService() throws Exception
    {
        byte[] requestBytes = "chunk-body".getBytes(StandardCharsets.UTF_8);
        CapturingChunkUpload uploadService = new CapturingChunkUpload();
        WorkerChunkUploadController controller = new WorkerChunkUploadController(uploadService);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContent(requestBytes);

        ResponseEntity<Long> response = controller.upload(
                "file-id", "chunk-id", "key", requestBytes.length, "checksum", request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isEqualTo(requestBytes.length);
        assertThat(uploadService.fileId).isEqualTo("file-id");
        assertThat(uploadService.chunkId).isEqualTo("chunk-id");
        assertThat(uploadService.key).isEqualTo("key");
        assertThat(uploadService.expectedSize).isEqualTo(requestBytes.length);
        assertThat(uploadService.expectedChecksum).isEqualTo("checksum");
        assertThat(uploadService.receivedBytes).containsExactly(requestBytes);
    }

    private static final class CapturingChunkUpload extends WorkerChunkUpload
    {
        private String fileId;
        private String chunkId;
        private String key;
        private long expectedSize;
        private String expectedChecksum;
        private byte[] receivedBytes;

        private CapturingChunkUpload()
        {
            super(null, null, null, null, null, null);
        }

        @Override
        public long upload(
                String fileId,
                String chunkId,
                String key,
                long expectedSize,
                String expectedChecksum,
                InputStream content) throws IOException
        {
            this.fileId = fileId;
            this.chunkId = chunkId;
            this.key = key;
            this.expectedSize = expectedSize;
            this.expectedChecksum = expectedChecksum;
            this.receivedBytes = content.readAllBytes();
            return this.receivedBytes.length;
        }
    }
}
