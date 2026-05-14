package edu.yu.capstone.DistributedSecretsVault.domain.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SecretKey {
    private String ownerId;
    private String name;
}
