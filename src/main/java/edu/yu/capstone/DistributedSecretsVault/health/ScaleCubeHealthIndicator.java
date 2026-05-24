package edu.yu.capstone.DistributedSecretsVault.health;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import io.scalecube.services.Microservices;

/**
 * Spring Boot Actuator health indicator for ScaleCube cluster membership.
 * <p>
 * Reports {@code UP} with the number of discovered service endpoints when
 * ScaleCube is initialized, or {@code DOWN} otherwise. Only active when
 * a {@link Microservices} bean exists in the context.
 */
@Component
@ConditionalOnBean(Microservices.class)
public class ScaleCubeHealthIndicator implements HealthIndicator {

    @Autowired
    private Microservices microservices;

    /**
     * Checks ScaleCube connectivity and returns health details.
     *
     * @return {@link Health#up()} with endpoint count, or {@link Health#down()} if
     *         ScaleCube is not initialized
     */
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
