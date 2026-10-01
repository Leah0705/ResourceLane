package io.resourcelane.wallet_service.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * JPA Entity class representing a user's wallet in the system.
 * This entity stores the credit balance and physical/digital units holdings of a user.
 * It is mapped to the "wallets" table in the underlying relational database.
 */
@Entity
@Getter @Setter @ToString
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "wallets")
public class Wallet {

    /**
     * The primary key, auto-generated unique identifier for the wallet record.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long walletId;

    /**
     * The unique identifier of the user who owns this wallet.
     * Typically acts as a foreign key linking to a User entity in an external Identity/User Service.
     */
    @Column(nullable = false, unique = true)
    private Long userId;

    /**
     * The simulated credit balance available for orders.
     */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    /**
     * The number of standardized resource units held by the user.
     */
    private Double units;

}
