package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostPrepareRequest {
    private String originatorNodeId;
    private UUID operationId;
    private SecretPartMessage secretPartMessage;
}
