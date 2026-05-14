package edu.yu.capstone.DistributedSecretsVault.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.PostPrepareRequest;
import edu.yu.capstone.DistributedSecretsVault.service.internal.DeletePrepareHandler;
import edu.yu.capstone.DistributedSecretsVault.service.internal.InternalGetService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.PostPrepareHandler;

@WebMvcTest(InternalController.class)
@Tag("slice")
public class InternalControllerPostTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private InternalGetService internalGetService;

    @MockitoBean
    private PostPrepareHandler postPrepareHandler;

    @MockitoBean
    private DeletePrepareHandler deletePrepareHandler;

    @Test
    void testPreparePostReturnsNoContent() throws Exception {
        doNothing().when(postPrepareHandler).handle(any(PostPrepareRequest.class));

        mockMvc.perform(post("/internal/prepare")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "originatorNodeId": "node-1",
                          "operationId": "11111111-1111-1111-1111-111111111111",
                          "secretPartMessage": {
                            "key": {"ownerId": "user1", "name": "secret1"},
                            "version": 1,
                            "shard": "AQID",
                            "timestampMillis": 1,
                            "partIndex": 1
                          }
                        }
                        """))
                .andExpect(status().isNoContent());

        verify(postPrepareHandler).handle(any(PostPrepareRequest.class));
    }

}
