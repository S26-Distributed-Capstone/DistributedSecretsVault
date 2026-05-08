package edu.yu.capstone.DistributedSecretsVault.controller;

import org.junit.jupiter.api.Tag;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.DeleteSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.secret.PutSecretRequest;
import edu.yu.capstone.DistributedSecretsVault.service.secret.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SecretController.class)
@Tag("slice")
public class SecretControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private GetSecretService getSecretService;

        @MockitoBean
        private PostSecretService postSecretService;

        @MockitoBean
        private PutSecretService putSecretService;

        @MockitoBean
        private DeleteSecretService deleteSecretService;

        @Test
        public void testGetSecret() throws Exception {
                when(getSecretService.getVersion(eq("user1"), eq("sec1"), eq(1L)))
                                .thenReturn(ResponseEntity.ok("secret-value"));

                mockMvc.perform(get("/api/v1/secrets/sec1")
                                .param("user", "user1")
                                .param("version", "1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("secret-value"));
        }

        @Test
        public void testGetSecretWithoutVersion() throws Exception {
                when(getSecretService.getVersion(eq("user1"), eq("sec1"), eq(null)))
                                .thenReturn(ResponseEntity.ok("secret-value-latest"));

                mockMvc.perform(get("/api/v1/secrets/sec1")
                                .param("user", "user1"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("secret-value-latest"));
        }

        @Test
        public void testGetAllSecrets() throws Exception {
                when(getSecretService.getAllVersions(eq("user1"), eq("sec1")))
                                .thenReturn(ResponseEntity.ok(Map.of(1L, "val1", 2L, "val2")));

                mockMvc.perform(get("/api/v1/secrets/sec1/all")
                                .param("user", "user1"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$['1']").value("val1"))
                                .andExpect(jsonPath("$['2']").value("val2"));
        }

        @Test
        public void testPostSecret() throws Exception {
                when(postSecretService.execute(any(PostSecretRequest.class)))
                                .thenReturn(ResponseEntity.ok("created-id"));

                mockMvc.perform(post("/api/v1/secrets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"user1\",\"secret\":\"mysec\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("created-id"));
        }

        @Test
        public void testPutSecret() throws Exception {
                when(putSecretService.execute(any(PutSecretRequest.class)))
                                .thenReturn(ResponseEntity.ok("updated"));

                mockMvc.perform(put("/api/v1/secrets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"user1\",\"secretId\":\"sec1\",\"secret\":\"newVal\"}"))
                                .andExpect(status().isOk())
                                .andExpect(content().string("updated"));
        }

        @Test
        public void testDeleteSecret() throws Exception {
                when(deleteSecretService.execute(any(DeleteSecretRequest.class)))
                                .thenReturn(ResponseEntity.noContent().build());

                mockMvc.perform(delete("/api/v1/secrets")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"userId\":\"user1\",\"secretId\":\"sec1\"}"))
                                .andExpect(status().isNoContent());
        }
}
