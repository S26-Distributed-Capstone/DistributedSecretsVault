package edu.yu.capstone.DistributedSecretsVault.controller;

import edu.yu.capstone.DistributedSecretsVault.dto.internal.CommitMessage;
import edu.yu.capstone.DistributedSecretsVault.service.communication.CommitPublisher;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/temp-test")
public class TempKafkaTestController {

    private final CommitPublisher commitPublisher;

    public TempKafkaTestController(CommitPublisher commitPublisher) {
        this.commitPublisher = commitPublisher;
    }

    @GetMapping("/kafka")
    public String triggerKafkaMessage() {
        CommitMessage message = new CommitMessage(
                UUID.randomUUID().toString(),
                "test-secret-id",
                CommitMessage.Action.POST,
                "{\"foo\":\"bar\"}",
                Instant.now().toEpochMilli()
        );
        
        commitPublisher.broadcastCommit(message);
        
        return "Message broadcasted explicitly to Kafka! Transaction ID: " + message.getTransactionId();
    }
}
