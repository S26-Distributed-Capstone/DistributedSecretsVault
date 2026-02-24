package edu.yu.capstone.DistributedSecretsVault.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import edu.yu.capstone.DistributedSecretsVault.service.secret.*;

@WebMvcTest(SecretController.class)
public class SecretControllerTest {
    
    @MockitoBean
    private GetSecretService getSecretService;
    
    @MockitoBean
    private PostSecretService postSecretService;
    
    @MockitoBean
    private PutSecretService putSecretService;
    
    @MockitoBean
    private DeleteSecretService deleteSecretService;
    
    @Test
    public void testCreateSecretHappyPath() {

    }
}
