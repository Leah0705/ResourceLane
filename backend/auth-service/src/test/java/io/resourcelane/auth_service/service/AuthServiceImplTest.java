package io.resourcelane.auth_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.resourcelane.auth_service.dto.UserDTO;
import io.resourcelane.auth_service.model.User;
import io.resourcelane.auth_service.repository.AuthRepositoryWrapper;

import jakarta.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.Optional;

import org.apache.commons.codec.binary.Base32;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpClientErrorException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthRepositoryWrapper authRepository;

    @Mock
    private RestTemplate restTemplate;

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String JWT_SECRET = "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";
    private static final String WALLET_URL = "http://localhost:8081/api/wallet/createWallet";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(authService, "walletServiceUrl", WALLET_URL);
    }

    @Test
    void userExistsByPhone_whenUserExists_returnsTrue() {
        when(authRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.of(new User()));

        boolean result = authService.userExistsByPhone("1234567890");

        assertThat(result).isTrue();
    }

    @Test
    void userExistsByPhone_whenUserNotExists_returnsFalse() {
        when(authRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.empty());

        boolean result = authService.userExistsByPhone("1234567890");

        assertThat(result).isFalse();
    }

    @Test
    void getUserByPhone_whenUserExists_returnsUser() {
        User user = new User(1L, "testuser", "1234567890", "secret", "temporary");
        when(authRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.of(user));

        User result = authService.getUserByPhone("1234567890");

        assertThat(result).isSameAs(user);
    }

    @Test
    void getUserByPhone_whenUserNotExists_returnsNull() {
        when(authRepository.findByPhoneNumber("1234567890")).thenReturn(Optional.empty());

        User result = authService.getUserByPhone("1234567890");

        assertThat(result).isNull();
    }

    @Test
    void generateSecretKey_returnsNonNullBase32String() {
        String secretKey = authService.generateSecretKey();

        assertThat(secretKey).isNotNull().isNotEmpty();
        Base32 base32 = new Base32();
        assertThat(base32.isInAlphabet(secretKey)).isTrue();
    }

    @Test
    void saveUser_savesWithStateTemporary() {
        User savedUser = new User(1L, "testuser", "1234567890", "secretKey", "temporary");
        when(authRepository.save(any(User.class))).thenReturn(savedUser);

        User result = authService.saveUser("testuser", "1234567890", "secretKey");

        assertThat(result.getState()).isEqualTo("temporary");
        assertThat(result.getUserName()).isEqualTo("testuser");
        assertThat(result.getPhoneNumber()).isEqualTo("1234567890");
        assertThat(result.getSecretKey()).isEqualTo("secretKey");
        verify(authRepository).save(any(User.class));
    }

    @Test
    void generateQrCode_returnsDataUriString() {
        String qrCode = authService.generateQrCode("testuser", "JBSWY3DPEHPK3PXP");

        assertThat(qrCode).isNotNull().startsWith("data:image/png;base64,");
    }

    private User storedUser(String secretKey, String state) {
        User user = new User(1L, "testuser", "1234567890", secretKey, state);
        lenient().when(authRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        return user;
    }

    private static long currentStep() {
        return System.currentTimeMillis() / 1000 / 30;
    }

    @Test
    void verifyTotp_validTotp_setsStateToPermanent() {
        String secretKey = authService.generateSecretKey();
        User user = storedUser(secretKey, "temporary");
        String validTotp = computeTotp(secretKey, currentStep());
        when(authRepository.save(any(User.class))).thenReturn(user);

        boolean result = authService.verifyTotp(secretKey, validTotp, user);

        assertThat(result).isTrue();
        assertThat(user.getState()).isEqualTo("permanent");
        assertThat(user.getFailedTotpAttempts()).isZero();
        assertThat(user.getLastTotpTimeStep()).isNotNull();
        verify(authRepository).save(user);
    }

    @Test
    void verifyTotp_invalidTotpAndTemporaryUser_deletesUserAndThrows() {
        User user = storedUser("JBSWY3DPEHPK3PXP", "temporary");

        assertThatThrownBy(() -> authService.verifyTotp("JBSWY3DPEHPK3PXP", "000000", user))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid TOTP code");

        verify(authRepository).deleteById(1L);
    }

    @Test
    void verifyTotp_invalidTotpAndPermanentUser_countsFailureWithoutDeleting() {
        User user = storedUser("JBSWY3DPEHPK3PXP", "permanent");

        assertThatThrownBy(() -> authService.verifyTotp("JBSWY3DPEHPK3PXP", "000000", user))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid TOTP code");

        verify(authRepository, never()).deleteById(anyLong());
        assertThat(user.getFailedTotpAttempts()).isEqualTo(1);
        assertThat(user.getTotpLockedUntil()).isNull();
    }

    @Test
    void verifyTotp_fifthWrongCodeLocksTheAccount() {
        String secretKey = authService.generateSecretKey();
        User user = storedUser(secretKey, "permanent");
        user.setFailedTotpAttempts(AuthServiceImpl.MAX_FAILED_TOTP_ATTEMPTS - 1);

        assertThatThrownBy(() -> authService.verifyTotp(secretKey, "000000", user))
                .isInstanceOf(BadCredentialsException.class);

        assertThat(user.getTotpLockedUntil()).isAfter(java.time.Instant.now());
        assertThat(user.getFailedTotpAttempts()).isZero();
        String validTotp = computeTotp(secretKey, currentStep());
        assertThatThrownBy(() -> authService.verifyTotp(secretKey, validTotp, user))
                .isInstanceOf(LockedException.class);
        assertThat(user.getState()).isEqualTo("permanent");
    }

    @Test
    void verifyTotp_expiredLockAllowsAValidCode() {
        String secretKey = authService.generateSecretKey();
        User user = storedUser(secretKey, "permanent");
        user.setTotpLockedUntil(java.time.Instant.now().minusSeconds(1));

        assertThat(authService.verifyTotp(secretKey, computeTotp(secretKey, currentStep()), user)).isTrue();
        assertThat(user.getTotpLockedUntil()).isNull();
    }

    @Test
    void verifyTotp_sameCodeCannotBeUsedTwice() {
        String secretKey = authService.generateSecretKey();
        User user = storedUser(secretKey, "permanent");
        String validTotp = computeTotp(secretKey, currentStep());

        assertThat(authService.verifyTotp(secretKey, validTotp, user)).isTrue();
        assertThatThrownBy(() -> authService.verifyTotp(secretKey, validTotp, user))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("TOTP code already used");
        assertThat(user.getFailedTotpAttempts()).isEqualTo(1);
    }

    @Test
    void verifyTotp_nullSecretKey_throwsRuntimeException() {
        User user = storedUser(null, "temporary");

        assertThatThrownBy(() -> authService.verifyTotp(null, "000000", user))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to verify TOTP");
    }

    @Test
    void generateJwt_returnsNonNullJwt() {
        String jwt = authService.generateJwt(1L, "device-fingerprint");

        assertThat(jwt).isNotNull().isNotEmpty();
    }

    @Test
    void createWallet_permanentState_callsRestTemplate() {
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setState("permanent");
        String jwt = "test-jwt";
        String deviceFingerprint = "device-fp";

        when(restTemplate.exchange(
                eq(WALLET_URL),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(Map.class)
        )).thenReturn(new ResponseEntity<>(Map.of("status", "created"), HttpStatus.OK));

        authService.createWallet(user, jwt, deviceFingerprint);

        ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                eq(WALLET_URL),
                eq(HttpMethod.POST),
                captor.capture(),
                eq(Map.class));
        HttpHeaders headers = captor.getValue().getHeaders();
        assertThat(headers.getFirst("Cookie")).isEqualTo("jwt=" + jwt);
        assertThat(headers.getFirst("X-Device-Fingerprint")).isEqualTo(deviceFingerprint);
    }

    @Test
    void createWallet_temporaryState_skipsWalletCreation() {
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setState("temporary");

        authService.createWallet(user, "test-jwt", "device-fp");

        verify(restTemplate, never()).exchange(anyString(), any(HttpMethod.class), any(HttpEntity.class), any(Class.class));
    }

    @Test
    void createWallet_existingWallet_allowsRepeatLogin() {
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setState("permanent");

        when(restTemplate.exchange(eq(WALLET_URL), eq(HttpMethod.POST), any(HttpEntity.class), eq(Map.class)))
                .thenThrow(HttpClientErrorException.create(HttpStatus.CONFLICT, "Conflict", HttpHeaders.EMPTY, null, null));

        authService.createWallet(user, "test-jwt", "device-fp");
    }

    @Test
    void createWallet_whenRestTemplateThrows_rethrowsRuntimeException() {
        UserDTO user = new UserDTO();
        user.setUserId(1L);
        user.setState("permanent");

        when(restTemplate.exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                any(Class.class)
        )).thenThrow(new RuntimeException("Connection error"));

        assertThatThrownBy(() -> authService.createWallet(user, "test-jwt", "device-fp"))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Failed to create wallet");
    }

    @Test
    void getUserById_whenUserExists_returnsUserDTO() {
        User user = new User(1L, "testuser", "1234567890", "secret", "permanent");
        when(authRepository.findById(1L)).thenReturn(Optional.of(user));

        UserDTO result = authService.getUserById(1L);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(1L);
        assertThat(result.getUserName()).isEqualTo("testuser");
        assertThat(result.getPhoneNumber()).isEqualTo("1234567890");
    }

    @Test
    void getUserById_whenUserNotExists_returnsNull() {
        when(authRepository.findById(999L)).thenReturn(Optional.empty());

        UserDTO result = authService.getUserById(999L);

        assertThat(result).isNull();
    }

    @Test
    void logout_setsCookieWithMaxAgeZero() {
        HttpServletResponse response = mock(HttpServletResponse.class);

        authService.logout(response);

        ArgumentCaptor<String> headerNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> headerValueCaptor = ArgumentCaptor.forClass(String.class);
        verify(response).addHeader(headerNameCaptor.capture(), headerValueCaptor.capture());

        assertThat(headerNameCaptor.getValue()).isEqualTo("Set-Cookie");
        assertThat(headerValueCaptor.getValue()).contains("jwt=");
        assertThat(headerValueCaptor.getValue()).contains("Max-Age=0");
        assertThat(headerValueCaptor.getValue()).contains("Path=/");
        assertThat(headerValueCaptor.getValue()).contains("SameSite=Strict");
    }

    private String computeTotp(String secretKey, long timeIndex) {
        try {
            Base32 base32 = new Base32();
            byte[] keyBytes = base32.decode(secretKey);
            SecretKeySpec signKey = new SecretKeySpec(keyBytes, "HmacSHA1");
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(signKey);

            byte[] data = new byte[8];
            long value = timeIndex;
            for (int i = 7; value > 0; i--) {
                data[i] = (byte) (value & 0xFF);
                value >>= 8;
            }
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % 1000000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
