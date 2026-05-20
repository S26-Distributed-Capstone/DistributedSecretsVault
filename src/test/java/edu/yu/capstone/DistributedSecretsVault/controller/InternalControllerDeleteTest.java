package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.DeletePrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PutPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.RepairPrepareHandler;
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
    private static final String OPERATION_ID = "11111111-1111-1111-1111-111111111111";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalGetService internalGetService;

    @MockitoBean
    private PostPrepareHandler postPrepareHandler;

    @MockitoBean
    private PutPrepareHandler putPrepareHandler;

    @MockitoBean
    private DeletePrepareHandler deletePrepareHandler;

    @MockitoBean
    private RepairPrepareHandler repairPrepareHandler;

    @Test
    void testPrepareDeleteReturnsNoContent() throws Exception {
        doNothing().when(deletePrepareHandler).handle(any(DeletePrepareRequest.class));

        mockMvc.perform(delete("/internal/prepare")
                .param("originatorNodeId", "node-1")
                .param("operationId", OPERATION_ID)
                .param("secretKeyOwnerId", "user1")
                .param("secretKeyName", "secret1"))
                .andExpect(status().isNoContent());

        verify(deletePrepareHandler).handle(any(DeletePrepareRequest.class));
    }

    @Test
    void testPrepareDeleteReturnsBadRequestOnValidationFailure() throws Exception {
        doThrow(new IllegalArgumentException("Operation ID is required"))
                .when(deletePrepareHandler).handle(any(DeletePrepareRequest.class));

        mockMvc.perform(delete("/internal/prepare")
                .param("originatorNodeId", "node-1")
                .param("operationId", "not-a-uuid")
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

}
