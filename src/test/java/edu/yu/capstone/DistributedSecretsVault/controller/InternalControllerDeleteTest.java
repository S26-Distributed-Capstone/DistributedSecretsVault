package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeleteCommitRequest;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeleteCommitHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GetShardService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GiveShardService;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalController.class)
@Tag("slice")
public class InternalControllerDeleteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetShardService getShardService;

    @MockitoBean
    private GiveShardService giveShardService;

    @MockitoBean
    private DeletePrepareHandler deletePrepareHandler;

    @MockitoBean
    private DeleteCommitHandler deleteCommitHandler;

    @Test
    void testPrepareDeleteReturnsOk() throws Exception {
        doNothing().when(deletePrepareHandler).handle(any(DeletePrepareRequest.class));

        mockMvc.perform(delete("/internal/prepare")
                .param("originatorNodeId", "node-1")
                .param("operationId", "op-123")
                .param("secretKeyOwnerId", "user1")
                .param("secretKeyName", "secret1"))
                .andExpect(status().isOk());

        verify(deletePrepareHandler).handle(any(DeletePrepareRequest.class));
    }

    @Test
    void testCommitDeleteReturnsOk() throws Exception {
        doNothing().when(deleteCommitHandler).handle(any(DeleteCommitRequest.class));

        mockMvc.perform(delete("/internal/commit")
                .param("operationId", "op-123")
                .param("secretKeyOwnerId", "user1")
                .param("secretKeyName", "secret1"))
                .andExpect(status().isOk());

        verify(deleteCommitHandler).handle(any(DeleteCommitRequest.class));
    }

    @Test
    void testPrepareDeleteReturnsBadRequestOnValidationFailure() throws Exception {
        doThrow(new IllegalArgumentException("Operation ID is required"))
                .when(deletePrepareHandler).handle(any(DeletePrepareRequest.class));

        mockMvc.perform(delete("/internal/prepare")
                .param("originatorNodeId", "node-1")
                .param("operationId", "")
                .param("secretKeyOwnerId", "user1")
                .param("secretKeyName", "secret1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCommitDeleteReturnsBadRequestOnValidationFailure() throws Exception {
        doThrow(new IllegalArgumentException("Secret key is required"))
                .when(deleteCommitHandler).handle(any(DeleteCommitRequest.class));

        mockMvc.perform(delete("/internal/commit")
                .param("operationId", "op-123")
                .param("secretKeyOwnerId", "user1")
                .param("secretKeyName", "secret1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testPrepareDeleteReturnsBadRequestWhenParamsMissing() throws Exception {
        // Missing required query params should return 400
        mockMvc.perform(delete("/internal/prepare")
                .param("originatorNodeId", "node-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testCommitDeleteReturnsBadRequestWhenParamsMissing() throws Exception {
        // Missing required query params should return 400
        mockMvc.perform(delete("/internal/commit")
                .param("operationId", "op-123"))
                .andExpect(status().isBadRequest());
    }
}
