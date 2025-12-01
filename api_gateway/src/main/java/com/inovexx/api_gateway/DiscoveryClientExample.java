package com.inovexx.api_gateway;

import org.springframework.cloud.client.discovery.DiscoveryClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryClientExample implements CommandLineRunner {

    @Autowired
    private DiscoveryClient discoveryClient;

    @Override
    public void run(String... args) throws Exception {
        discoveryClient.getServices().forEach(serviceId -> {
            System.out.println("ServiceId: " + serviceId);
            discoveryClient.getInstances(serviceId).forEach(instance -> {
                System.out.println("  Instance: " + instance.getUri());
            });
        });
    }
}