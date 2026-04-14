package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.Data;

@Data
public class SecretKey {
    private String ownerId;
    private String name;
}
