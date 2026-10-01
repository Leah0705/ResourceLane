package io.resourcelane.wallet_service.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import io.resourcelane.wallet_service.dto.*;
import io.resourcelane.wallet_service.service.WalletService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(classes = {
        io.resourcelane.wallet_service.WalletServiceApplication.class,
        WalletControllerTest.TestControllerConfig.class
})
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
class WalletControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WalletService walletService;

    private final Long userId = 1L;

    private static final String JWT_SECRET =
            "2b7e151628aed2a6abf7158809cf4f3c2b7e151628aed2a6abf7158809cf4f3c";
    private static final String DEVICE_FINGERPRINT = "test-fingerprint-123";

    @org.springframework.boot.test.context.TestConfiguration
    static class TestControllerConfig {
    }

    private Cookie createJwtCookie(Long userId, String fingerprint) {
        String token = Jwts.builder()
                .claim("userId", userId)
                .claim("deviceFingerprint", fingerprint)
                .signWith(SignatureAlgorithm.HS256,
                        JWT_SECRET.getBytes(StandardCharsets.UTF_8))
                .compact();
        return new Cookie("jwt", token);
    }

    private Cookie createJwtCookie(Long userId) {
        return createJwtCookie(userId, DEVICE_FINGERPRINT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/createWallet
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void createWallet_authenticated_success() throws Exception {
        WalletDTO walletDTO = new WalletDTO();
        walletDTO.setWalletId(10L);
        walletDTO.setUserId(userId);
        walletDTO.setBalance(0.0);
        walletDTO.setUnits(0.0);
        when(walletService.getWallet(userId)).thenReturn(null);
        when(walletService.createWallet(userId)).thenReturn(walletDTO);

        mockMvc.perform(post("/api/wallet/createWallet")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Wallet created successfully"))
                .andExpect(jsonPath("$.wallet.userId").value(userId));

        verify(walletService).createWallet(userId);
    }

    @Test
    void createWallet_authenticated_alreadyExists() throws Exception {
        WalletDTO existing = new WalletDTO();
        existing.setWalletId(10L);
        existing.setUserId(userId);
        existing.setBalance(500.0);
        existing.setUnits(20.0);
        when(walletService.getWallet(userId)).thenReturn(existing);

        mockMvc.perform(post("/api/wallet/createWallet")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Wallet already exists"));

        verify(walletService, never()).createWallet(any());
    }

    @Test
    void createWallet_unauthenticated() throws Exception {
        mockMvc.perform(post("/api/wallet/createWallet"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/wallet/getWallet
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void getWallet_authenticated_found() throws Exception {
        WalletDTO walletDTO = new WalletDTO();
        walletDTO.setWalletId(10L);
        walletDTO.setUserId(userId);
        walletDTO.setBalance(1000.0);
        walletDTO.setUnits(50.0);
        when(walletService.getWallet(userId)).thenReturn(walletDTO);

        mockMvc.perform(get("/api/wallet/getWallet")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(10))
                .andExpect(jsonPath("$.userId").value(userId))
                .andExpect(jsonPath("$.balance").value(1000.0))
                .andExpect(jsonPath("$.units").value(50.0));
    }

    @Test
    void getWallet_authenticated_notFound() throws Exception {
        when(walletService.getWallet(userId)).thenReturn(null);

        mockMvc.perform(get("/api/wallet/getWallet")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Wallet not found"));
    }

    @Test
    void getWallet_unauthenticated() throws Exception {
        mockMvc.perform(get("/api/wallet/getWallet"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/internal/updateWallets
    // ─────────────────────────────────────────────────────────────────────────

    private org.springframework.test.web.servlet.request.RequestPostProcessor internalPath() {
        return request -> {
            request.setServletPath("/api/wallet/internal/updateWallets");
            return request;
        };
    }

    @Test
    void updateWallets_validInternalSecret_success() throws Exception {
        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setBuyUserId("1");
        tradeDTO.setSellUserId("2");
        tradeDTO.setBuyOrderId("B1");
        tradeDTO.setSellOrderId("S1");
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(5);

        mockMvc.perform(post("/api/wallet/internal/updateWallets")
                        .with(internalPath())
                        .header("X-Internal-Secret", "mySecretToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("wallet updated successfully"));

        verify(walletService).updateWallets(any(TradeDTO.class));
    }

    @Test
    void updateWallets_invalidInternalSecret() throws Exception {
        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setBuyUserId("1");
        tradeDTO.setSellUserId("2");
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(5);

        mockMvc.perform(post("/api/wallet/internal/updateWallets")
                        .with(internalPath())
                        .header("X-Internal-Secret", "wrongSecret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Unauthorized internal call"));

        verify(walletService, never()).updateWallets(any());
    }

    @Test
    void updateWallets_exceptionHandling() throws Exception {
        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setBuyUserId("1");
        tradeDTO.setSellUserId("2");
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(5);
        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(walletService).updateWallets(any(TradeDTO.class));

        mockMvc.perform(post("/api/wallet/internal/updateWallets")
                        .with(internalPath())
                        .header("X-Internal-Secret", "mySecretToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(tradeDTO)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Exception in updating wallets: Insufficient balance"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/addMoney
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void addMoney_authenticated_success() throws Exception {
        AddMoneyRequest request = new AddMoneyRequest();
        request.setAmount(250.0);

        mockMvc.perform(post("/api/wallet/addMoney")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Money added successfully"));

        verify(walletService).addMoney(eq(userId), eq(250.0));
    }

    @Test
    void addMoney_unauthenticated() throws Exception {
        AddMoneyRequest request = new AddMoneyRequest();
        request.setAmount(100.0);

        mockMvc.perform(post("/api/wallet/addMoney")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).addMoney(any(), anyDouble());
    }

    @Test
    void addMoney_validation_invalidAmount() throws Exception {
        String invalidJson = "{\"amount\": -50}";

        mockMvc.perform(post("/api/wallet/addMoney")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).addMoney(any(), anyDouble());
    }

    @Test
    void addMoney_validation_missingAmount() throws Exception {
        String invalidJson = "{}";

        mockMvc.perform(post("/api/wallet/addMoney")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidJson))
                .andExpect(status().isBadRequest());

        verify(walletService, never()).addMoney(any(), anyDouble());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/withdrawMoney
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void withdrawMoney_authenticated_success() throws Exception {
        WithdrawMoneyRequest request = new WithdrawMoneyRequest();
        request.setAmount(100.0);

        mockMvc.perform(post("/api/wallet/withdrawMoney")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Money withdrawn successfully"));

        verify(walletService).withdrawMoney(eq(userId), eq(100.0));
    }

    @Test
    void withdrawMoney_authenticated_insufficientFunds() throws Exception {
        WithdrawMoneyRequest request = new WithdrawMoneyRequest();
        request.setAmount(9999.0);
        doThrow(new IllegalArgumentException("Insufficient balance"))
                .when(walletService).withdrawMoney(eq(userId), eq(9999.0));

        mockMvc.perform(post("/api/wallet/withdrawMoney")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Insufficient balance"));
    }

    @Test
    void withdrawMoney_unauthenticated() throws Exception {
        WithdrawMoneyRequest request = new WithdrawMoneyRequest();
        request.setAmount(50.0);

        mockMvc.perform(post("/api/wallet/withdrawMoney")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).withdrawMoney(any(), anyDouble());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/addUnits
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void addUnits_authenticated_success() throws Exception {
        AddUnitsRequestDTO request = new AddUnitsRequestDTO();
        request.setQuantity(10);

        mockMvc.perform(post("/api/wallet/addUnits")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Units added successfully"));

        verify(walletService).addUnits(eq(userId), eq(10));
    }

    @Test
    void addUnits_unauthenticated() throws Exception {
        AddUnitsRequestDTO request = new AddUnitsRequestDTO();
        request.setQuantity(5);

        mockMvc.perform(post("/api/wallet/addUnits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).addUnits(any(), anyInt());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/wallet/withdrawUnits
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void withdrawUnits_authenticated_success() throws Exception {
        WithdrawUnitsRequestDTO request = new WithdrawUnitsRequestDTO();
        request.setQuantity(3);

        mockMvc.perform(post("/api/wallet/withdrawUnits")
                        .cookie(createJwtCookie(userId))
                        .header("X-Device-Fingerprint", DEVICE_FINGERPRINT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Units withdrawn successfully"));

        verify(walletService).withdrawUnits(eq(userId), eq(3));
    }

    @Test
    void withdrawUnits_unauthenticated() throws Exception {
        WithdrawUnitsRequestDTO request = new WithdrawUnitsRequestDTO();
        request.setQuantity(2);

        mockMvc.perform(post("/api/wallet/withdrawUnits")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(walletService, never()).withdrawUnits(any(), anyInt());
    }
}
