package edu.yu.capstone.DistributedSecretsVault.service.secret;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import edu.yu.capstone.DistributedSecretsVault.dto.secret.PostSecretRequest;

import com.codahale.shamir.Scheme;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;

@Service
public class PostSecretService implements SecretCommand<PostSecretRequest, String> {

    @Override
    public ResponseEntity<String> execute(PostSecretRequest input) {
        // Create Shamir's Secret Sharing scheme: 5 total parts, 3 needed to reconstruct
        // USUALLY, PASS IN N AND K THROUGH CONFIG
        final Scheme scheme = new Scheme(new SecureRandom(), 5, 3);
        
        // Convert secret to bytes (WE NEED TO SEE IF UTF_8 IS ENOUGH)
        final byte[] secret = input.getSecretValue().getBytes(StandardCharsets.UTF_8);
        
        // Split the secret into parts
        final Map<Integer, byte[]> parts = scheme.split(secret);
        
        // Format the parts for the response
        StringBuilder partsInfo = new StringBuilder();
        for (Map.Entry<Integer, byte[]> entry : parts.entrySet()) {
            String base64Value = Base64.getEncoder().encodeToString(entry.getValue());
            partsInfo.append("Part ").append(entry.getKey())
                     .append(": ").append(base64Value).append(", ");
        }
        if (partsInfo.length() > 0) {
            partsInfo.setLength(partsInfo.length() - 2); // Remove trailing comma and space
        }
        
        // TODO: Implement actual secret storage logic (store parts in distributed nodes)

        byte[] recovered = scheme.join(parts);
        
        return ResponseEntity.ok("Secret '" + input.getSecretName() + 
                "' stored successfully. Split into " + parts.size() + " parts: " + partsInfo.toString() +
            ". this is recoverable into: " + new String(recovered, StandardCharsets.UTF_8));
    }

}
