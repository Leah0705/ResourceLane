package io.resourcelane.auth_service.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.NoArgsConstructor;
import java.time.Instant;

/**
 * Entity representing a user in the authentication system.
 * Maps to the "users" table in the database.
 */
@Entity
@Getter @Setter @ToString
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

    /** Wrong codes since the last success or lockout. */
    private Integer failedTotpAttempts;

    /** Verification is refused until this time after too many wrong codes. */
    private Instant totpLockedUntil;

    /** 30-second time step of the last accepted code; a code from this step or earlier is refused. */
    private Long lastTotpTimeStep;

    public User(Long userId, String userName, String phoneNumber, String secretKey, String state) {
        this.userId = userId;
        this.userName = userName;
        this.phoneNumber = phoneNumber;
        this.secretKey = secretKey;
        this.state = state;
    }
}
