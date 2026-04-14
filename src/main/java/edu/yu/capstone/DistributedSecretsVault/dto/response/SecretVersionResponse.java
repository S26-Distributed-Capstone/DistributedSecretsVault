package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

@Data
public class SecretVersionResponse {
    private long version;
    private String value;
}
