package edu.yu.capstone.DistributedSecretsVault.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@WebMvcTest
//@MockitoBean() - fill in blank with mock implementation (I think that's what's causing the problem)
public class SecretControllerTest {
    @Test
    public void testCreateSecretHappyPath() {

    }
}
