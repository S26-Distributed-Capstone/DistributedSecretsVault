package edu.yu.capstone.dsv.client.dto;

public record CreateSecretRequest(String secretName, String secretValue, String authKey) {
}


