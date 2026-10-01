package io.resourcelane.auth_service.service;

import io.resourcelane.auth_service.dto.UserDTO;
import io.resourcelane.auth_service.model.User;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Interface for Authentication Service.
 * Defines the core business logic contract for user management, TOTP verification,
 * JWT generation, and integration with external systems like the Wallet service.
 * Separating this interface from its implementation allows for easier testing and loose coupling.
 */
public interface AuthService {

    /**
     * Checks if a user exists by phone number.
     *
     * @param phoneNumber The phone number to check.
     * @return True if the user exists, false otherwise.
     */
    boolean userExistsByPhone(String phoneNumber);

    /**
     * Retrieves a user by phone number.
     *
     * @param phoneNumber The phone number.
     * @return The User entity, or null if not found.
     */
    User getUserByPhone(String phoneNumber);

    /**
     * Generates a new secret key for TOTP.
     *
     * @return The generated secret key.
     */
    String generateSecretKey();

    /**
     * Saves a new user or updates an existing one.
     *
     * @param userName    The username.
     * @param phoneNumber The phone number.
     * @param secretKey   The secret key.
     * @return The saved User entity.
     */
    User saveUser(String userName, String phoneNumber, String secretKey);

    /**
     * Generates a QR code for TOTP setup.
     *
     * @param userName  The username.
     * @param secretKey The secret key.
     * @return A Base64 encoded string of the QR code image.
     */
    String generateQrCode(String userName, String secretKey);

    /**
     * Verifies the provided TOTP code.
     *
     * @param secretKey The user's secret key.
     * @param totp      The TOTP code to verify.
     * @param user      The user entity.
     * @return True if the TOTP is valid, false otherwise.
     */
    boolean verifyTotp(String secretKey, String totp, User user);

    /**
     * Generates a JWT token for the user.
     *
     * @param userId            The user ID.
     * @param deviceFingerprint The device fingerprint.
     * @return The generated JWT string.
     */
    String generateJwt(Long userId, String deviceFingerprint);

    /**
     * Creates a wallet for the user via the Wallet Service.
     *
     * @param user              The user DTO.
     * @param jwt               The JWT token for authentication.
     * @param deviceFingerprint The device fingerprint.
     */
    void createWallet(UserDTO user, String jwt, String deviceFingerprint);

    /**
     * Retrieves a user by ID.
     *
     * @param userId The user ID.
     * @return The UserDTO.
     */
    UserDTO getUserById(Long userId);

    /**
     * Logs out the user by clearing cookies.
     *
     * @param response The HTTP response.
     */
    void logout(HttpServletResponse response);

}