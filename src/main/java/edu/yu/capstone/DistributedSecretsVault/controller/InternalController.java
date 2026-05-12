package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.domain.model.SecretPart;
import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GetShardService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GiveShardService;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
public class InternalController {

    private final GetShardService getShardService;
    private final GiveShardService giveShardService;

    @Autowired
    public InternalController(GetShardService getShardService, GiveShardService giveShardService) {
        this.getShardService = getShardService;
        this.giveShardService = giveShardService;
    }

    @GetMapping("/shard/{id}")
    public ResponseEntity<SecretPart> getShard(@PathVariable String id,
            @RequestParam(value = "user") String user,
            @RequestParam(value = "version", required = false) Long version) {
        return getShardService.getVersion(user, id, version);
    }

    @GetMapping("/shard/{id}/all")
    public ResponseEntity<Map<Long, SecretPart>> getAllVersions(@PathVariable String id,
            @RequestParam(value = "user") String user) {
        return getShardService.getAllVersions(user, id);
    }

    @PostMapping("/shard")
    public ResponseEntity<Void> giveShard(@RequestBody SecretPartMessage shardData) {
        giveShardService.giveShard(shardData);
        return ResponseEntity.ok().build();
    }
}
