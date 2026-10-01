package io.resourcelane.trade_service.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "order_reservations")
@Getter @Setter @NoArgsConstructor
public class OrderReservation {
    @Id
    private Long orderId;
    @Column(nullable = false)
    private Long userId;
    @Column(nullable = false)
    private String side;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;
    @Column(nullable = false)
    private int quantity;
    private Integer matchedQuantity;
    @Column(nullable = false)
    private int settledQuantity;

    public OrderReservation(Long orderId, Long userId, String side, String type,
                            BigDecimal price, int quantity) {
        this.orderId = orderId;
        this.userId = userId;
        this.side = side;
        this.type = type;
        this.price = price;
        this.quantity = quantity;
    }

    public int heldQuantity() {
        return (matchedQuantity == null ? quantity : matchedQuantity) - settledQuantity;
    }
}
