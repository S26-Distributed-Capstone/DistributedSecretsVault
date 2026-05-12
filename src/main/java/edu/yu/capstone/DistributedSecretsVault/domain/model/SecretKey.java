package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SecretKey {
    private String ownerId;
    private String name;
}
