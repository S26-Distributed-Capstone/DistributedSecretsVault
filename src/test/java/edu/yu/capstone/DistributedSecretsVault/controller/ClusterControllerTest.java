package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.config.ScaleCubeConfig.PingService;
import io.scalecube.services.Microservices;
import io.scalecube.services.ServiceCall;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import reactor.core.publisher.Mono;

import java.util.Collections;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@Tag("slice")
class ClusterControllerTest {

    private MockMvc mockMvc;

    @Mock
    private Microservices microservices;

    @Mock
    private ServiceCall serviceCall;

    @Mock
    private PingService pingService;

    @BeforeEach
    void setUp() {
        ClusterController clusterController = new ClusterController();
        ReflectionTestUtils.setField(clusterController, "microservices", microservices);
        mockMvc = MockMvcBuilders.standaloneSetup(clusterController).build();
    }

    @Test
    void testListNodes() throws Exception {
        when(microservices.serviceEndpoints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/cluster/nodes")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testStatus() throws Exception {
        when(microservices.serviceEndpoints()).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/cluster/status")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalNodes").value(0))
                .andExpect(jsonPath("$.healthyNodes").value(0))
                .andExpect(jsonPath("$.suspectNodes").value(0))
                .andExpect(jsonPath("$.failedNodes").value(0));
    }

    @Test
    void testPing() throws Exception {
        when(microservices.call()).thenReturn(serviceCall);
        when(serviceCall.api(PingService.class)).thenReturn(pingService);
        when(pingService.ping("Hello")).thenReturn(Mono.just("Pong from cluster test"));

        mockMvc.perform(get("/api/v1/cluster/ping")
                .accept(MediaType.TEXT_PLAIN))
                .andExpect(status().isOk())
                .andExpect(content().string("Pong from cluster test"));
    }
}
