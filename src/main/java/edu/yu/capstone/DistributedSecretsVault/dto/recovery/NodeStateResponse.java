package edu.yu.capstone.DistributedSecretsVault.dto.recovery;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Recovery response listing all secret state known by a node.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NodeStateResponse {
    private List<StateSummary> nodeState = new ArrayList<>();
}
