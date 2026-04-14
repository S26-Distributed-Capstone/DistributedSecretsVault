package edu.yu.capstone.DistributedSecretsVault.dto.response;

import lombok.Data;

@Data
public class SecretResponse {
    private String key;
    private long version;
    private String value;
}
