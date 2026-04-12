package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.Data;

@Data
public class Secret {
    private SecretKey key;
    private long version;
    private String value;
}
