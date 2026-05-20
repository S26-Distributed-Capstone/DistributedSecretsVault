package edu.yu.capstone.DistributedSecretsVault.dto.secret;

import com.fasterxml.jackson.annotation.JsonAlias;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnvFileRequest {
    private String user;

    @JsonAlias({ "content", "env", "envFile" })
    private String envFileContent;
}
