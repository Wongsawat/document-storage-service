package com.wpanther.storage.application.port.out;

import com.wpanther.saga.domain.outbox.OutboxEvent;

/**
 * Outbound port for outbox event persistence.
 * <p>
 * This port abstracts the Transactional Outbox pattern repository, implemented by
 * {@link com.wpanther.storage.infrastructure.adapter.out.persistence.outbox.JpaOutboxEventRepository}.
 * </p>
 * <p>
 * The outbox pattern ensures exactly-once message delivery to Kafka by:
 * </p>
 * <ol>
 *   <li>Writing events to the outbox table within the same transaction as domain changes</li>
 *   <li>Having Debezium CDC capture outbox changes and publish to Kafka</li>
 *   <li>Deleting events after successful publication</li>
 * </ol>
 * <p>
 * This decouples the database transaction from message publishing, ensuring that
 * messages are only sent when the transaction commits successfully.
 * </p>
 *
 * @see com.wpanther.saga.domain.outbox.OutboxEvent
 */
public interface OutboxRepositoryPort {

    /**
     * Save an outbox event.
     * <p>
     * Typically called within a @Transactional method to ensure the event is
     * saved atomically with domain changes. Debezium CDC will detect the insert
     * and publish the event to Kafka.
     * </p>
     *
     * @param event the outbox event to save (aggregateId, eventType, payload, topic)
     * @return the saved event with generated ID and timestamp
     */
    OutboxEvent save(OutboxEvent event);

    /**
     * Delete an outbox event by ID.
     * <p>
     * Called by the Debezium CDC connector after successful message publication
     * to prevent reprocessing. Deletion is optional for idempotency - events
     * can be replayed safely if not deleted.
     * </p>
     *
     * @param id the unique outbox event identifier
     */
    void deleteById(String id);
}
