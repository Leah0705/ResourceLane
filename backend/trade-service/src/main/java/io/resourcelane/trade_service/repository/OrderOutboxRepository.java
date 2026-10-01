package io.resourcelane.trade_service.repository;

import io.resourcelane.trade_service.model.OrderOutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.LockModeType;

import java.util.List;

public interface OrderOutboxRepository extends JpaRepository<OrderOutboxEvent, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderOutboxEvent e where e.publishedAt is null order by e.orderId")
    List<OrderOutboxEvent> findPending(Pageable pageable);

    /** Cancels whose order has already been published, so the matcher always sees the order first. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from OrderOutboxEvent e where e.cancelPayload is not null and e.cancelPublishedAt is null"
            + " and e.publishedAt is not null order by e.orderId")
    List<OrderOutboxEvent> findPendingCancels(Pageable pageable);
}
