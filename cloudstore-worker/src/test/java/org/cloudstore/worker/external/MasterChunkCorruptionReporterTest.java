package org.cloudstore.worker.external;

import com.sun.net.httpserver.HttpServer;
import org.cloudstore.worker.external.constants.MasterReportingProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterChunkCorruptionReporterTest
{
    private HttpServer server;

    @AfterEach
    void stopServer()
    {
        if (server != null)
        {
            server.stop(0);
        }
    }

    @Test
    void postsCorruptionReportToConfiguredMasterEndpoint() throws IOException
    {
        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        startServer(200, receivedPath, receivedBody);
        MasterChunkCorruptionReporter reporter = reporter();

        reporter.report("file-1", "chunk-2", "checksum mismatch");

        assertThat(receivedPath.get()).isEqualTo(
                "/api/v1/workers/worker-test/chunks/corruption-reports");
        assertThat(receivedBody.get()).contains("\"fileId\":\"file-1\"")
                .contains("\"chunkId\":\"chunk-2\"")
                .contains("\"reason\":\"checksum mismatch\"");
    }

    @Test
    void throwsWhenMasterRejectsCorruptionReport() throws IOException
    {
        startServer(500, new AtomicReference<>(), new AtomicReference<>());
        MasterChunkCorruptionReporter reporter = reporter();

        assertThatThrownBy(() -> reporter.report("file-1", "chunk-2", "checksum mismatch"))
                .isInstanceOf(RuntimeException.class);
    }

    private void startServer(
            int status,
            AtomicReference<String> receivedPath,
            AtomicReference<String> receivedBody) throws IOException
    {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange ->
        {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(status, -1L);
            exchange.close();
        });
        server.start();
    }

    private MasterChunkCorruptionReporter reporter()
    {
        MasterReportingProperties properties = new MasterReportingProperties();
        properties.setWorkerId("worker-test");
        properties.setCorruptionReportUrl("http://127.0.0.1:" + server.getAddress().getPort()
                + "/api/v1/workers/{workerId}/chunks/corruption-reports");
        return new MasterChunkCorruptionReporter(properties);
    }
}
