package io.resourcelane.wallet_service.repository;

import io.resourcelane.wallet_service.model.OrderReservation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderReservationRepository extends JpaRepository<OrderReservation, Long> {
    List<OrderReservation> findByUserIdAndSide(Long userId, String side);
}
