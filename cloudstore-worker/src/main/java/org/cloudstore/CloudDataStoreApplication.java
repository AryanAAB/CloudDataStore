package org.cloudstore;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Starts the CloudDataStore worker application.
 */
@SpringBootApplication
@EnableScheduling
public class CloudDataStoreApplication
{
    /**
     * Starts the Spring application.
     *
     * @param args application command-line arguments
     */
    public static void main(String[] args)
    {
        SpringApplication.run(CloudDataStoreApplication.class, args);
    }
}
