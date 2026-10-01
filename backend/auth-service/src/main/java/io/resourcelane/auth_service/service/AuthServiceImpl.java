package io.resourcelane.auth_service.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;

import io.resourcelane.auth_service.dto.UserDTO;
import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.repository.AuthRepositoryWrapper;

import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.nio.charset.StandardCharsets;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.HttpServletResponse;

import java.util.Date;
import org.apache.commons.codec.binary.Base32;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Implementation of the AuthService interface.
 * Handles core authentication logic including TOTP generation/verification,
 * user management, and communication with other services.
 */
@Service
public class AuthServiceImpl implements AuthService {

    /**
     * Logger instance for recording operational events and errors.
     */
    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

    /**
     * Repository for persisting and retrieving User entities.
     */
    private final AuthRepositoryWrapper authRepository;

    /**
     * REST client for communicating with external services, such as the Wallet Service.
     */
    private final RestTemplate restTemplate;

    /**
     * The URL endpoint for the external Wallet Service.
     * Injected from the application properties.
     */
    @Value("${wallet.service.url}")
    private String walletServiceUrl;

    /**
     * The secret key used for signing and validating JWT tokens.
     * Injected from the application properties.
     */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /**
     * Constructs an AuthServiceImpl with required dependencies.
     * 
     * @param authRepository The repository for user data access.
     * @param restTemplate   The RestTemplate for making external HTTP calls.
     */
    public AuthServiceImpl(AuthRepositoryWrapper authRepository, RestTemplate restTemplate) {
        this.authRepository = authRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * Checks if a user already exists with the given phone number.
     * This is useful during registration to prevent duplicate accounts.
     * 
     * @param phoneNumber The user's phone number.
     * @return true if the user exists, false otherwise.
     */
    @Override
    public boolean userExistsByPhone(String phoneNumber) {
        return authRepository.findByPhoneNumber(phoneNumber).isPresent();
    }

    /**
     * Retrieves a user entity by its associated phone number.
     * Often used during the login and OTP verification processes to load user details.
     * 
     * @param phoneNumber The phone number to search for.
     * @return The User entity if found, or null if it doesn't exist.
     */
    @Override
    public User getUserByPhone(String phoneNumber) {
        Optional<User> userOpt = authRepository.findByPhoneNumber(phoneNumber);
        return userOpt.orElse(null);
    }

    /**
     * Generates a new, secure Time-Based One-Time Password (TOTP) secret key.
     * Uses HMAC-SHA1 to create a strong key and encodes it in Base32, which is
     * the standard format required by authenticator apps like Google Authenticator.
     * 
     * @return The Base32 encoded secret key string.
     */
    @Override
    public String generateSecretKey() {
        try {
            KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA1");
            keyGenerator.init(160);
            SecretKey secretKey = keyGenerator.generateKey();
            // Add time-based randomness
            long timestamp = System.currentTimeMillis();
            byte[] timeBytes = Long.toString(timestamp).getBytes(StandardCharsets.UTF_8);
            byte[] keyBytes = secretKey.getEncoded();
            byte[] combined = new byte[keyBytes.length + timeBytes.length];
            System.arraycopy(keyBytes, 0, combined, 0, keyBytes.length);
            System.arraycopy(timeBytes, 0, combined, keyBytes.length, timeBytes.length);
            // Use Base32 encoding for Google Authenticator compatibility
            Base32 base32 = new Base32();
            return base32.encodeToString(combined).replace("=", "");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate secret key", e);
        }
    }

    /**
     * Creates and persists a new user account with a temporary state.
     * A temporary state indicates the user has registered but hasn't yet completed
     * their initial TOTP verification.
     * 
     * @param userName    The user's chosen display name.
     * @param phoneNumber The user's mobile phone number used for login.
     * @param secretKey   The generated TOTP secret key for this user.
     * @return The newly saved User entity.
     */
    @Override
    public User saveUser(String userName, String phoneNumber, String secretKey) {
        User user = new User();
        user.setUserName(userName);
        user.setPhoneNumber(phoneNumber);
        user.setSecretKey(secretKey);
        user.setState("temporary");
        return authRepository.save(user);
    }

    /**
     * Generates a Base64-encoded QR code image containing the user's TOTP setup URI.
     * This QR code can be scanned by any standard authenticator app to easily add
     * the account without manual key entry.
     * 
     * @param userName  The username to embed in the QR code URI.
     * @param secretKey The secret key to embed in the QR code URI.
     * @return A data URI string representing the PNG image of the QR code.
     */
    @Override
    public String generateQrCode(String userName, String secretKey) {
        String issuer = "ResourceLane";
        // Use Base32 secretKey for QR code
        String qrCodeData = String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", issuer, userName, secretKey,
                issuer);
        try {
            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(qrCodeData, BarcodeFormat.QR_CODE, 250, 250);
            ByteArrayOutputStream pngOutputStream = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(bitMatrix, "PNG", pngOutputStream);
            byte[] pngData = pngOutputStream.toByteArray();
            // Still encode the image as Base64 for data URI
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(pngData);
        } catch (WriterException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /** Wrong codes allowed before the account is locked. */
    static final int MAX_FAILED_TOTP_ATTEMPTS = 5;
    /** How long verification stays locked after too many wrong codes. */
    static final Duration TOTP_LOCK_DURATION = Duration.ofMinutes(15);

    /**
     * Verifies a 6-digit TOTP code against the user's secret key.
     *
     * The code is accepted for the previous, current and next 30-second step to tolerate clock
     * skew. A code is accepted at most once: a step at or before the last accepted one is refused.
     * After {@value #MAX_FAILED_TOTP_ATTEMPTS} wrong codes the account is locked for
     * {@link #TOTP_LOCK_DURATION}, so the 6-digit space cannot be searched online.
     * A temporary (unconfirmed) registration that fails is deleted, as before.
     *
     * The failure counter is committed even though the method then throws, because the
     * transaction does not roll back on authentication failures.
     *
     * @param secretKey The user's Base32-encoded secret key.
     * @param totp      The 6-digit code submitted by the user.
     * @param user      The user attempting verification. Its state is updated on success.
     * @return true when the code is accepted.
     * @throws LockedException         While the account is locked.
     * @throws BadCredentialsException For a wrong or reused code.
     */
    @Override
    @Transactional(noRollbackFor = AuthenticationException.class)
    public boolean verifyTotp(String secretKey, String totp, User user) {
        User current = authRepository.findByIdForUpdate(user.getUserId())
                .orElseThrow(() -> new BadCredentialsException("User not found"));
        Instant now = Instant.now();
        if (current.getTotpLockedUntil() != null && now.isBefore(current.getTotpLockedUntil())) {
            throw new LockedException("Too many failed TOTP attempts");
        }

        long matchedStep = matchingTimeStep(secretKey, totp, now.getEpochSecond() / 30);
        boolean reused = matchedStep >= 0 && current.getLastTotpTimeStep() != null
                && matchedStep <= current.getLastTotpTimeStep();
        if (matchedStep < 0 || reused) {
            if ("temporary".equals(current.getState())) {
                authRepository.deleteById(current.getUserId());
            } else {
                recordFailedAttempt(current, now);
            }
            throw new BadCredentialsException(reused ? "TOTP code already used" : "Invalid TOTP code");
        }

        current.setFailedTotpAttempts(0);
        current.setTotpLockedUntil(null);
        current.setLastTotpTimeStep(matchedStep);
        current.setState("permanent");
        authRepository.save(current);
        user.setState("permanent");
        return true;
    }

    private void recordFailedAttempt(User user, Instant now) {
        int failures = (user.getFailedTotpAttempts() == null ? 0 : user.getFailedTotpAttempts()) + 1;
        if (failures >= MAX_FAILED_TOTP_ATTEMPTS) {
            user.setTotpLockedUntil(now.plus(TOTP_LOCK_DURATION));
            failures = 0;
        }
        user.setFailedTotpAttempts(failures);
        authRepository.save(user);
    }

    /**
     * Returns the 30-second step whose code equals {@code totp}, checking one step either side of
     * {@code currentStep}, or -1 when none matches.
     */
    private static long matchingTimeStep(String secretKey, String totp, long currentStep) {
        if (totp == null) {
            return -1;
        }
        try {
            // Google Authenticator compatible TOTP (RFC 6238, HMAC-SHA1, 6 digits)
            byte[] keyBytes = new Base32().decode(secretKey);
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] submitted = totp.getBytes(StandardCharsets.US_ASCII);
            for (long step = currentStep - 1; step <= currentStep + 1; step++) {
                byte[] data = new byte[8];
                long value = step;
                for (int i = 7; value > 0; i--) {
                    data[i] = (byte) (value & 0xFF);
                    value >>= 8;
                }
                byte[] hash = mac.doFinal(data);
                int offset = hash[hash.length - 1] & 0xF;
                int binary = ((hash[offset] & 0x7F) << 24) |
                        ((hash[offset + 1] & 0xFF) << 16) |
                        ((hash[offset + 2] & 0xFF) << 8) |
                        (hash[offset + 3] & 0xFF);
                byte[] expected = String.format("%06d", binary % 1000000).getBytes(StandardCharsets.US_ASCII);
                if (MessageDigest.isEqual(expected, submitted)) {
                    return step;
                }
            }
            return -1;
        } catch (Exception e) {
            throw new RuntimeException("Failed to verify TOTP", e);
        }
    }

    /**
     * Generates a JSON Web Token (JWT) for an authenticated user.
     * The token includes the user's ID and a device fingerprint to prevent token theft
     * or reuse across different devices. It is signed using HMAC-SHA256 and expires in 1 day.
     * 
     * @param userId            The unique identifier of the authenticated user.
     * @param deviceFingerprint A unique identifier representing the user's current device or browser.
     * @return The signed JWT string.
     */
    @Override
    public String generateJwt(Long userId, String deviceFingerprint) {
        return Jwts.builder()
                .claim("userId", userId)
                .claim("deviceFingerprint", deviceFingerprint)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 86400000)) // 1 day
                .signWith(SignatureAlgorithm.HS256, jwtSecret.getBytes(StandardCharsets.UTF_8))
                .compact();
    }

    /**
     * Asynchronously or synchronously creates a wallet for a newly verified user
     * by calling the external Wallet Service. This ensures the user has a provisioned
     * wallet immediately after their account becomes permanent.
     * The JWT and device fingerprint are passed along to authorize the inter-service request.
     * 
     * @param user              The Data Transfer Object of the user.
     * @param jwt               The JWT token created during the current login/verification session.
     * @param deviceFingerprint The device fingerprint of the client.
     */
    @Override
    public void createWallet(UserDTO user, String jwt, String deviceFingerprint) {
        try {
            if ("permanent".equals(user.getState())) {
                String url = walletServiceUrl; // ensure this is the correct endpoint

                HttpHeaders headers = new HttpHeaders();
                headers.set("Cookie", "jwt=" + jwt); // Send JWT in cookie format
                headers.set("X-Device-Fingerprint", deviceFingerprint);
                headers.setContentType(MediaType.APPLICATION_JSON); // optional, since body is empty

                HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

                ResponseEntity<Map> response = restTemplate.exchange(
                        url,
                        HttpMethod.POST,
                        requestEntity,
                        Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    logger.info("Wallet created successfully for user {}", user.getUserId());
                } else {
                    logger.error("Wallet creation failed for user {} with status: {}", user.getUserId(), response.getStatusCode());
                }
            }

        } catch (HttpClientErrorException.Conflict e) {
            // Login can revisit this provisioning step after the wallet already exists.
            logger.info("Wallet already exists for user {}", user.getUserId());
        } catch (Exception e) {
            logger.error("Failed to create wallet for user {}: {}", user.getUserId(), e.getMessage());
            throw new RuntimeException("Failed to create wallet", e);
        }
    }

    /**
     * Fetches user details by their unique database ID and maps them to a Data Transfer Object.
     * Abstracting the entity behind a DTO prevents exposing sensitive fields (like secret keys)
     * over the network.
     * 
     * @param userId The ID of the user to fetch.
     * @return A UserDTO containing the safe public fields, or null if the user isn't found.
     */
    @Override
    public UserDTO getUserById(Long userId) {

        Optional<User> userOpt = authRepository.findById(userId);

        if (userOpt.isEmpty()) {
            logger.debug("User with ID {} not found", userId);
            return null;
        }

        User user = userOpt.get();
        UserDTO userDTO = new UserDTO();
        BeanUtils.copyProperties(user, userDTO);
        return userDTO;
    }

    /**
     * Logs out the user by instructing the client to invalidate the JWT cookie.
     * This is achieved by setting the "jwt" cookie's max age to 0, effectively deleting it.
     * 
     * @param response The HTTP response object to attach the invalidated cookie to.
     */
    @Override
    public void logout(HttpServletResponse response) {

        // Remove the JWT cookie by setting it with maxAge=0
        ResponseCookie cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .path("/")
                .maxAge(0)
                .sameSite("Strict")
                .build();
        response.addHeader("Set-Cookie", cookie.toString());
    }

}
