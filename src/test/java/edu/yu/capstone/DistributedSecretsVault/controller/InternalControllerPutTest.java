package edu.yu.capstone.DistributedSecretsVault.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.PutPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PutPrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.RepairPrepareHandler;

@WebMvcTest(InternalController.class)
@Tag("slice")
public class InternalControllerPutTest {
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
    void testPreparePutReturnsNoContent() throws Exception {
        doNothing().when(putPrepareHandler).handle(any(PutPrepareRequest.class));

        mockMvc.perform(put("/internal/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "originatorNodeId": "node-1",
                          "operationId": "11111111-1111-1111-1111-111111111111",
                          "secretPartMessage": {
                            "key": {"ownerId": "user1", "name": "secret1"},
                            "version": 2,
                            "shard": "AQID",
                            "timestampMillis": 1,
                            "partIndex": 1
                          }
                        }
                        """))
                .andExpect(status().isNoContent());

        verify(putPrepareHandler).handle(any(PutPrepareRequest.class));
    }
}
