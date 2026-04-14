package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PutSecretRequest {
    private String secretCurrentName;
    private String secretCurrentValue;
    private String secretUpdatedName;
    private String secretUpdatedValue;
}
