package edu.yu.capstone.DistributedSecretsVault.dto.internal;

import java.util.UUID;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretKey;
import edu.yu.capstone.DistributedSecretsVault.service.internal.ActionType;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommitMessage {
    private UUID operationId;
    private SecretKey secretKey;
    private ActionType actionType;
}
