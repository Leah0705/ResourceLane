package io.resourcelane.auth_service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.resourcelane.auth_service.dto.LoginRequest;
import io.resourcelane.auth_service.dto.RegisterRequest;
import io.resourcelane.auth_service.dto.UserDTO;
import io.resourcelane.auth_service.dto.VerifyTotpRequest;
import io.resourcelane.auth_service.exceptionHandler.GlobalExceptionHandler;
import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.security.OtpAuthenticationToken;
import io.resourcelane.auth_service.service.AuthService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    private MockMvc mockMvc;

    @Mock
    private AuthService authService;

    @Mock
    private AuthenticationManager authenticationManager;

    @InjectMocks
    private AuthController controller;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    // --- POST /api/auth/login ---

    @Test
    void login_userExists_returnsOkWithMessage() throws Exception {
        when(authService.userExistsByPhone("1234567890")).thenReturn(true);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("1234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("user is in DB"));
    }

    @Test
    void login_userNotExists_returnsOkWithRedirect() throws Exception {
        when(authService.userExistsByPhone("1234567890")).thenReturn(false);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("1234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.redirect").value("register"));
    }

    @Test
    void login_emptyPhoneNumber_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest())))
                .andExpect(status().isBadRequest());
    }

    // --- POST /api/auth/verify ---

    @Test
    void verify_success_setsCookieAndCreatesWallet() throws Exception {
        User userEntity = new User(1L, "testuser", "1234567890", "secret", "permanent");
        UsernamePasswordAuthenticationToken authResult =
                new UsernamePasswordAuthenticationToken(userEntity, null);

        when(authenticationManager.authenticate(any(OtpAuthenticationToken.class)))
                .thenReturn(authResult);
        when(authService.generateJwt(1L, "device-fp")).thenReturn("test-jwt");

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyTotpRequest("1234567890", "123456", "device-fp"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.User.userName").value("testuser"))
                .andExpect(jsonPath("$.User.phoneNumber").value("1234567890"));

        verify(authService).createWallet(any(UserDTO.class), eq("test-jwt"), eq("device-fp"));
    }

    @Test
    void verify_invalidTotp_returnsUnauthorized() throws Exception {
        when(authenticationManager.authenticate(any(OtpAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("Invalid TOTP"));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyTotpRequest("1234567890", "000000", "device-fp"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid credentials"));
    }

    @Test
    void verify_nullUserEntity_returnsUnauthorized() throws Exception {
        when(authenticationManager.authenticate(any(OtpAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken(null, null));

        mockMvc.perform(post("/api/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new VerifyTotpRequest("1234567890", "123456", "device-fp"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    // --- POST /api/auth/register ---

    @Test
    void register_success_returnsQrCodeAndSecretKey() throws Exception {
        when(authService.userExistsByPhone("1234567890")).thenReturn(false);
        when(authService.generateSecretKey()).thenReturn("SECRETKEY123");
        when(authService.saveUser("testuser", "1234567890", "SECRETKEY123"))
                .thenReturn(new User(1L, "testuser", "1234567890", "SECRETKEY123", "temporary"));
        when(authService.generateQrCode("testuser", "SECRETKEY123"))
                .thenReturn("data:image/png;base64,testqrcode");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("testuser", "1234567890"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.qrCode").value("data:image/png;base64,testqrcode"))
                .andExpect(jsonPath("$.secretKey").value("SECRETKEY123"));
    }

    @Test
    void register_phoneAlreadyExists_returnsBadRequest() throws Exception {
        when(authService.userExistsByPhone("1234567890")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest("testuser", "1234567890"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Demo account ID already registered"))
                .andExpect(jsonPath("$.redirect").value("login"));
    }

    @Test
    void register_missingFields_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RegisterRequest())))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/auth/getUser ---

    @Test
    void getUser_authenticated_returnsUserDTO() throws Exception {
        UserDTO userDTO = new UserDTO(1L, "testuser", "1234567890", "secret", "permanent");
        when(authService.getUserById(1L)).thenReturn(userDTO);

        mockMvc.perform(get("/api/auth/getUser")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userName").value("testuser"))
                .andExpect(jsonPath("$.phoneNumber").value("1234567890"));
    }

    @Test
    void getUser_notAuthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/getUser"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not authenticated"));
    }

    @Test
    void getUser_authenticatedButNotFound_returns404() throws Exception {
        when(authService.getUserById(1L)).thenReturn(null);

        mockMvc.perform(get("/api/auth/getUser")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("User not found"));
    }

    // --- POST /api/auth/logout ---

    @Test
    void logout_authenticated_returnsOk() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout(any());
    }

    @Test
    void logout_notAuthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not authenticated, login first"));
    }

    // --- GET /api/auth/getUserId ---

    @Test
    void getUserId_authenticated_returnsUserId() throws Exception {
        mockMvc.perform(get("/api/auth/getUserId")
                        .principal(new UsernamePasswordAuthenticationToken(1L, null)))
                .andExpect(status().isOk())
                .andExpect(content().string("1"));
    }

    @Test
    void getUserId_notAuthenticated_returnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/getUserId"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("User not authenticated"));
    }
}
