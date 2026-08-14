package com.bank.notification;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class TransferEventListener {

    @KafkaListener(topics = "transfer.completed", groupId = "notification-service")
    public void onTransferCompleted(String payload) {
        // In production: send email/SMS via SendGrid, Twilio, etc.
        log.info("📧 [EMAIL MOCK] Transfer completed: {}", payload);
    }

    @KafkaListener(topics = "transfer.failed", groupId = "notification-service")
    public void onTransferFailed(String payload) {
        log.info("📧 [EMAIL MOCK] Transfer FAILED alert: {}", payload);
    }

    @KafkaListener(topics = "transfer.compensation-required", groupId = "notification-service")
    public void onCompensationRequired(String payload) {
        // In production: page the on-call team — money left the source account
        // and the automatic refund also failed.
        log.error("🚨 [OPS ALERT MOCK] Manual compensation required: {}", payload);
    }
}
