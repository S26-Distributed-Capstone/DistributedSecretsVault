package edu.yu.capstone.DistributedSecretsVault.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import io.scalecube.services.Microservices;

@Component
public class ScaleCubeHealthIndicator implements HealthIndicator {

    @Autowired
    private Microservices microservices;

    @Override
    public Health health() {
        if (microservices == null || microservices.serviceEndpoints() == null) {
            return Health.down().withDetail("ScaleCube", "Not initialized or no endpoints available").build();
        }
        
        return Health.up()
                .withDetail("ScaleCube", "Connected")
                .withDetail("endpointsCount", microservices.serviceEndpoints().size())
                .build();
    }
}
