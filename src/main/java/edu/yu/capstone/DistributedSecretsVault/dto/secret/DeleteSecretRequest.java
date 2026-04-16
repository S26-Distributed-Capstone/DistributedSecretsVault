package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteSecretRequest {
    private String deleteName;
}
