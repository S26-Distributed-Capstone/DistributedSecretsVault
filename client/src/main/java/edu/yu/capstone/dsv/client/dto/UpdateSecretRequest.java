package edu.yu.capstone.dsv.client.dto;

public record UpdateSecretRequest(
        String secretCurrentName,
        String secretCurrentValue,
        String secretUpdatedName,
        String secretUpdatedValue,
        String authKey
) {
}


