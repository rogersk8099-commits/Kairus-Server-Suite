package com.neonnexus.smpplatform.outbox;

import java.util.concurrent.CompletionStage;

/** A Central API implementation must send event.idempotencyKey() as an idempotency header/value. */
@FunctionalInterface
public interface OutboxDeliveryClient {
    CompletionStage<DeliveryReceipt> deliver(OutboxEvent event);
    record DeliveryReceipt(boolean accepted, String detail) {
        public static DeliveryReceipt acknowledged() { return new DeliveryReceipt(true, "acknowledged"); }
    }
}
