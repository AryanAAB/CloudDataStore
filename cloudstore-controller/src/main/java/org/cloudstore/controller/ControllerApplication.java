package org.cloudstore.controller;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the CloudDataStore Controller (Master Control Plane + u-Proxy Data Plane).
 */
@SpringBootApplication
@EnableScheduling
public class ControllerApplication
{
    public static void main(String[] args)
    {
        SpringApplication.run(ControllerApplication.class, args);
    }
}

