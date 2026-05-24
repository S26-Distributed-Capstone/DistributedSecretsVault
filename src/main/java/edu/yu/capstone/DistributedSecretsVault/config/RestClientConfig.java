package edu.yu.capstone.DistributedSecretsVault.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Provides a configured {@link RestClient} bean for inter-node HTTP
 * communication. Timeouts are derived from {@link ClusterConfig#getWriteTimeoutMillis()}.
 */
@Configuration
public class RestClientConfig {

    /**
     * Creates a {@link RestClient} with connect and read timeouts derived from
     * {@link ClusterConfig#getWriteTimeoutMillis()}, falling back to 5000ms.
     *
     * @param clusterConfig cluster configuration providing timeout values
     * @return a configured {@link RestClient} instance
     */
    @Bean
    public RestClient restClient(ClusterConfig clusterConfig) {
        long timeoutMs = clusterConfig.getWriteTimeoutMillis();
        if (timeoutMs <= 0) {
            timeoutMs = 5000L; // sensible default
        }

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(timeoutMs));
        factory.setReadTimeout(Duration.ofMillis(timeoutMs));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
