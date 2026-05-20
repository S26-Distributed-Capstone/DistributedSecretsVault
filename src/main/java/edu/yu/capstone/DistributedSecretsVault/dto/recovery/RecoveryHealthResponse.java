package edu.yu.capstone.DistributedSecretsVault.dto.recovery;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recovery endpoint health response.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryHealthResponse {
    private boolean ready;
    private String message;
}
