package io.resourcelane.auth_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Entity representing a user in the authentication system.
 * Maps to the "users" table in the database.
 */
@Entity
@Getter @Setter @ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "users")
public class User {

    /**
     * Unique identifier for the user.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    /**
     * The user's username.
     */
    private String userName;

    /**
     * The user's phone number.
     */
    private String phoneNumber;

    /**
     * The secret key used for TOTP generation and verification.
     */
    private String secretKey;

    /**
     * The state of the user account (e.g., "temporary", "permanent").
     * Temporary state might imply registration is incomplete or awaiting further verification.
     */
    private String state;
}
