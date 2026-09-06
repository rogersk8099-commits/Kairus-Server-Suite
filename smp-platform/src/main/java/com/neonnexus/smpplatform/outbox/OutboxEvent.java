package com.neonnexus.smpplatform.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A platform event retained until the Central API acknowledges the exact idempotency key. */
public record OutboxEvent(UUID id, String aggregateType, String aggregateId, String eventType, String idempotencyKey,
                           String payloadJson, Status status, int attemptCount, Instant availableAt, Instant leaseUntil,
                           Instant deliveredAt, String lastError, Instant createdAt) {
    public enum Status { PENDING, LEASED, DELIVERED, DEAD }
    public OutboxEvent {
        id = Objects.requireNonNullElseGet(id, UUID::randomUUID);
        aggregateType = required(aggregateType, "aggregateType"); aggregateId = required(aggregateId, "aggregateId");
        eventType = required(eventType, "eventType"); idempotencyKey = required(idempotencyKey, "idempotencyKey");
        payloadJson = required(payloadJson, "payloadJson"); status = Objects.requireNonNull(status, "status");
        if (attemptCount < 0) throw new IllegalArgumentException("attemptCount cannot be negative");
        availableAt = Objects.requireNonNull(availableAt); createdAt = Objects.requireNonNull(createdAt);
    }
    public static OutboxEvent pending(String aggregateType, String aggregateId, String eventType, String idempotencyKey, String payloadJson, Instant now) {
        return new OutboxEvent(UUID.randomUUID(), aggregateType, aggregateId, eventType, idempotencyKey, payloadJson, Status.PENDING, 0, now, null, null, null, now);
    }
    private static String required(String value, String field) { value = Objects.requireNonNull(value, field).trim(); if (value.isEmpty()) throw new IllegalArgumentException(field + " cannot be blank"); return value; }
}
