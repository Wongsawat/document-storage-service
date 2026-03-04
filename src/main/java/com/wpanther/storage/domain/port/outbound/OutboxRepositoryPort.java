package com.wpanther.storage.domain.port.outbound;

import com.wpanther.saga.domain.outbox.OutboxEvent;

/**
 * Outbound port for outbox event persistence.
 * Implemented by JpaOutboxAdapter.
 */
public interface OutboxRepositoryPort {
    OutboxEvent save(OutboxEvent event);
    void deleteById(String id);
}
