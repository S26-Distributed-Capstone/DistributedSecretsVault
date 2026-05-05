package edu.yu.capstone.DistributedSecretsVault.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ClusterController.class)
class ClusterControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testListNodes() throws Exception {
        mockMvc.perform(get("/api/v1/cluster/nodes")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testStatus() throws Exception {
        mockMvc.perform(get("/api/v1/cluster/status")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }
}
