package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.SecretPartMessage;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GetShardService;
import edu.yu.capstone.DistributedSecretsVault.service.internal.GiveShardService;
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
    public ResponseEntity<SecretPartMessage> getShard(@PathVariable String id, @RequestParam("user") String user) {
        SecretPartMessage shard = getShardService.getShard(user, id);
        return ResponseEntity.ok(shard);
    }

    @PostMapping("/shard")
    public ResponseEntity<Void> giveShard(@RequestBody SecretPartMessage shardData) {
        giveShardService.giveShard(shardData);
        return ResponseEntity.ok().build();
    }
}
