package org.cloudstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Boots the CloudDataStore worker application. */
@SpringBootApplication
@EnableScheduling
public class CloudDataStoreApplication {
    /**
     * Starts Spring and keeps the worker process running.
     *
     * @param args command-line arguments passed to the application
     */
    public static void main(String[] args) {
        SpringApplication.run(CloudDataStoreApplication.class, args);
    }
}
