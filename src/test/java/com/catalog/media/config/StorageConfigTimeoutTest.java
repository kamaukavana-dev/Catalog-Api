package com.catalog.media.config;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static java.time.Duration.ofSeconds;

/**
 * Proves the S3 client is built with a bounded socket timeout.
 *
 * <p>A local server accepts the TCP connection but never sends a response — exactly the
 * "slow storage" failure that pins a DB connection during image processing. Without a
 * socket timeout the S3 read blocks indefinitely; with one it fails fast. The test caps
 * total wall time well below any hang, so it fails if the timeout wiring is removed.
 */
class StorageConfigTimeoutTest {

    @Test
    void s3ClientFailsFastWhenStorageAcceptsButNeverResponds() throws Exception {
        try (ServerSocket blackhole = new ServerSocket(0)) {
            // Hold accepted sockets open without ever writing a byte back.
            CopyOnWriteArrayList<Socket> accepted = new CopyOnWriteArrayList<>();
            Thread acceptor = new Thread(() -> {
                try {
                    while (!Thread.currentThread().isInterrupted()) {
                        accepted.add(blackhole.accept());
                    }
                } catch (Exception ignored) {
                    // socket closed on teardown
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            StorageConfig config = new StorageConfig();
            config.setRegion("us-east-1");
            config.setAccessKey("test");
            config.setSecretKey("test");
            config.setBucket("test-bucket");
            config.setEndpoint("http://127.0.0.1:" + blackhole.getLocalPort());
            config.setConnectionTimeoutSeconds(1);
            config.setSocketTimeoutSeconds(1);
            config.setApiCallTimeoutSeconds(3);

            S3Client client = config.s3Client();

            // If the socket timeout were not wired, this read would hang forever and the
            // preemptive timeout would trip, failing the test.
            assertTimeoutPreemptively(ofSeconds(10), () ->
                    assertThatThrownBy(() -> client.headObject(HeadObjectRequest.builder()
                            .bucket("test-bucket")
                            .key("anything")
                            .build()))
                            .isInstanceOf(Exception.class));

            accepted.forEach(s -> {
                try { s.close(); } catch (Exception ignored) { }
            });
            acceptor.interrupt();
        }
    }

    @Test
    void defaultsAreSaneAndPositive() {
        StorageConfig config = new StorageConfig();
        assertThat(config.getConnectionTimeoutSeconds()).isPositive();
        assertThat(config.getSocketTimeoutSeconds()).isPositive();
        assertThat(config.getApiCallTimeoutSeconds()).isGreaterThanOrEqualTo(config.getSocketTimeoutSeconds());
    }
}
