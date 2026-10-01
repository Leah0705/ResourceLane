package io.resourcelane.auth_service.controller;

import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import io.resourcelane.auth_service.security.OtpAuthenticationToken;
import io.resourcelane.auth_service.service.AuthService;
import io.resourcelane.auth_service.dto.VerifyTotpRequest;
import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.dto.LoginRequest;
import io.resourcelane.auth_service.dto.RegisterRequest;
import io.resourcelane.auth_service.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * REST Controller for Authentication services.
 * Handles user login, registration, verification, and session management.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * Logger instance for recording controller events and operational flows.
     */
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    /**
     * Service layer dependency for business logic related to authentication.
     */
    private final AuthService authService;

    /**
     * Spring Security component used to authenticate users based on tokens.
     */
    private final AuthenticationManager authenticationManager;

    /**
     * Constructs the AuthController with required dependencies.
     * 
     * @param authService           The service handling authentication business logic.
     * @param authenticationManager The manager handling authentication processes.
     */
    public AuthController(AuthService authService, AuthenticationManager authenticationManager) {
        this.authService = authService;
        this.authenticationManager = authenticationManager;
    }

    /**
     * Initiates the login process by checking if the user exists.
     *
     * @param loginRequest The login request containing the phone number.
     * @return A response entity containing a redirect instruction or success message.
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        String phoneNumber = loginRequest.getPhoneNumber();

        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Demo account ID is required"));
        }

        boolean exists = authService.userExistsByPhone(phoneNumber);
        if (!exists) {
            return ResponseEntity.ok(Map.of("redirect", "register"));
        }

        String maskedPhone = phoneNumber.replaceAll(".(?=.{4})", "*");
        logger.info("User with phone number {} exists, proceeding to verification", maskedPhone);
        return ResponseEntity.ok(Map.of("message", "user is in DB"));
    }

    /**
     * Verifies the TOTP code and logs the user in if successful.
     *
     * @param request  The verification request containing phone number and TOTP.
     * @param response The HTTP servlet response to set cookies.
     * @return A response entity containing the user details and a success message, or an error.
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verify(@Valid @RequestBody VerifyTotpRequest request, HttpServletResponse response) {
        try {
            OtpAuthenticationToken authRequest = new OtpAuthenticationToken(request.getPhoneNumber(),
                    request.getTotp());
            Authentication authentication = authenticationManager.authenticate(authRequest);

            User userEntity = (User) authentication.getPrincipal();
            if (userEntity == null) {
                String maskedPhone = request.getPhoneNumber().replaceAll(".(?=.{4})", "*");
                logger.warn("Authentication successful but user entity is null for phone: {}", maskedPhone);
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not found"));
            }

            UserDTO user = new UserDTO();
            BeanUtils.copyProperties(userEntity, user);

            String deviceFingerprint = request.getDeviceFingerprint();
            String jwt = authService.generateJwt(user.getUserId(), deviceFingerprint);

            authService.createWallet(user, jwt, deviceFingerprint);

            ResponseCookie cookie = ResponseCookie.from("jwt", jwt)
                    .httpOnly(true)
                    .path("/")
                    .sameSite("Strict")
                    .build();
            response.addHeader("Set-Cookie", cookie.toString());

            logger.info("User {} logged in successfully", user.getUserId());
            return ResponseEntity.ok(Map.of("message", "Login successful", "User", user));
        } catch (LockedException e) {
            String maskedPhone = request.getPhoneNumber().replaceAll(".(?=.{4})", "*");
            logger.warn("TOTP verification locked for phone: {}", maskedPhone);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(Map.of("error", "Too many failed attempts. Try again later."));
        } catch (AuthenticationException e) {
            String maskedPhone = request.getPhoneNumber().replaceAll(".(?=.{4})", "*");
            logger.error("Authentication failed for phone: {}", maskedPhone, e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
    }

    /**
     * Registers a new user.
     *
     * @param request The registration request containing user details.
     * @return A response entity containing the QR code data and secret key.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        String userName = request.getUserName();
        String phoneNumber = request.getPhoneNumber();
        if (userName == null || userName.isEmpty() || phoneNumber == null || phoneNumber.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Username and demo account ID are required"));
        }
        boolean exists = authService.userExistsByPhone(phoneNumber);
        if (exists) {
            String maskedPhone = phoneNumber.replaceAll(".(?=.{4})", "*");
            logger.warn("Registration attempt failed: Phone number {} already registered", maskedPhone);
            return ResponseEntity.badRequest()
                    .body(Map.of("redirect", "login", "error", "Demo account ID already registered"));
        }
        // Generate a new secret key for TOTP
        String secretKey = authService.generateSecretKey();
        // Save user with state 'temporary'
        authService.saveUser(userName, phoneNumber, secretKey);
        // Generate QR code for the secret key
        String qrCodeData = authService.generateQrCode(userName, secretKey);

        logger.info("New user registered temporarily: {}", userName);
        return ResponseEntity.ok(Map.of("qrCode", qrCodeData, "secretKey", secretKey));
    }

    /**
     * Retrieves the authenticated user's details.
     *
     * @param authentication The authentication object.
     * @return The user details.
     */
    @GetMapping("/getUser")
    public ResponseEntity<?> getUser(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "User not authenticated"));
        }
        // The principal is userId (Long) set by JwtAuthenticationFilter
        Long userId = (Long) authentication.getPrincipal();
        UserDTO userDTO = authService.getUserById(userId);

        if (userDTO == null) {
            logger.error("User ID {} not found in database", userId);
            return ResponseEntity.status(404).body(Map.of("error", "User not found"));
        }

        logger.debug("Fetched user details for ID: {}", userId);
        return ResponseEntity.ok(userDTO);
    }

    /**
     * Logs out the user by clearing the authentication cookie.
     *
     * @param response       The HTTP servlet response.
     * @param authentication The authentication object.
     * @return A success message.
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletResponse response, Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated, login first"));
        }

        authService.logout(response);
        logger.info("User logged out successfully");

        return ResponseEntity.ok(Map.of("message", "Logged out successfully"));
    }

    /**
     * Retrieves the authenticated user's ID.
     *
     * @param authentication The authentication object.
     * @return The user ID.
     */
    @GetMapping("/getUserId")
    public ResponseEntity<?> getUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            return ResponseEntity.status(401).body(Map.of("error", "User not authenticated"));
        }

        Long userId = (Long) authentication.getPrincipal();
        logger.debug("Fetched User ID: {}", userId);
        return ResponseEntity.ok(userId);
    }

}
