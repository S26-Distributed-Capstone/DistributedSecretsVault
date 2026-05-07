package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessage {
    
    public enum Action {
        PUT, POST, DELETE
    }

    private String transactionId;
    private String secretId;
    private Action action;
    private String payload;
    private long timestamp;
}
