package io.resourcelane.wallet_service.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.resourcelane.wallet_service.dto.TradeDTO;
import io.resourcelane.wallet_service.dto.WalletDTO;
import io.resourcelane.wallet_service.model.Wallet;
import io.resourcelane.wallet_service.model.OrderReservation;
import io.resourcelane.wallet_service.repository.OrderReservationRepository;
import io.resourcelane.wallet_service.repository.WalletRepositoryWrapper;
import io.resourcelane.wallet_service.repository.WalletSettlementRepository;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;

@ExtendWith(MockitoExtension.class)
class WalletServiceImplTest {

    @Mock
    private WalletRepositoryWrapper walletRepository;
    @Mock
    private WalletSettlementRepository settlementRepository;
    @Mock
    private OrderReservationRepository reservationRepository;

    @InjectMocks
    private WalletServiceImpl walletService;

    private Wallet buyerWallet;
    private Wallet sellerWallet;

    private final Long buyerUserId = 1L;
    private final Long sellerUserId = 2L;
    private final Long buyerWalletId = 10L;
    private final Long sellerWalletId = 20L;

    @BeforeEach
    void setUp() {
        buyerWallet = new Wallet(buyerWalletId, buyerUserId, new BigDecimal("1000.00"), 0.0);
        sellerWallet = new Wallet(sellerWalletId, sellerUserId, new BigDecimal("500.00"), 10.0);
        lenient().when(reservationRepository.findById(100L)).thenReturn(Optional.of(
                new OrderReservation(100L, buyerUserId, "BUY", "LIMIT", new BigDecimal("100.00"), 10)));
        lenient().when(reservationRepository.findById(200L)).thenReturn(Optional.of(
                new OrderReservation(200L, sellerUserId, "SELL", "LIMIT", new BigDecimal("100.00"), 10)));
    }

    // ──────────────────────────────────────────────────────────────────────────
    // getWallet
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void getWallet_whenFound_returnsWalletDTO() {
        when(walletRepository.findByUserId(buyerUserId)).thenReturn(buyerWallet);

        WalletDTO result = walletService.getWallet(buyerUserId);

        assertThat(result).isNotNull();
        assertThat(result.getWalletId()).isEqualTo(buyerWalletId);
        assertThat(result.getUserId()).isEqualTo(buyerUserId);
        assertThat(result.getBalance()).isEqualTo(1000.0);
        assertThat(result.getUnits()).isEqualTo(0.0);
    }

    @Test
    void getWallet_whenNotFound_returnsNull() {
        when(walletRepository.findByUserId(buyerUserId)).thenReturn(null);

        WalletDTO result = walletService.getWallet(buyerUserId);

        assertThat(result).isNull();
    }

    // ──────────────────────────────────────────────────────────────────────────
    // createWallet
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void createWallet_createsWithZeroBalanceAndZeroUnits() {
        Wallet savedWallet = new Wallet(buyerWalletId, buyerUserId, new BigDecimal("0.00"), 0.0);
        when(walletRepository.save(any(Wallet.class))).thenReturn(savedWallet);

        WalletDTO result = walletService.createWallet(buyerUserId);

        assertThat(result).isNotNull();
        assertThat(result.getUserId()).isEqualTo(buyerUserId);
        assertThat(result.getBalance()).isEqualTo(0.0);
        assertThat(result.getUnits()).isEqualTo(0.0);

        ArgumentCaptor<Wallet> captor = ArgumentCaptor.forClass(Wallet.class);
        verify(walletRepository).save(captor.capture());
        Wallet passed = captor.getValue();
        assertThat(passed.getUserId()).isEqualTo(buyerUserId);
        assertThat(passed.getBalance()).isEqualByComparingTo("0.00");
        assertThat(passed.getUnits()).isEqualTo(0.0);
    }

    // ──────────────────────────────────────────────────────────────────────────
    // updateWallets
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void updateWallets_successfulTransfer() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setExecutionId("execution-1");
        tradeDTO.setBuyOrderId("100");
        tradeDTO.setSellOrderId("200");
        tradeDTO.setBuyUserId(buyerUserId.toString());
        tradeDTO.setSellUserId(sellerUserId.toString());
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(5);

        walletService.updateWallets(tradeDTO);

        // buyer pays 500 money, gets 5 units
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("500.00");
        assertThat(buyerWallet.getUnits()).isEqualTo(5.0);

        // seller gets 500 money, loses 5 units
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo("1000.00");
        assertThat(sellerWallet.getUnits()).isEqualTo(5.0);

        verify(walletRepository, times(2)).save(any(Wallet.class));
        verify(settlementRepository).saveAndFlush(any());
    }

    @Test
    void updateWallets_insufficientBuyerBalance_throwsException() {
        buyerWallet.setBalance(new BigDecimal("50.00"));
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setExecutionId("execution-2");
        tradeDTO.setBuyOrderId("100");
        tradeDTO.setSellOrderId("200");
        tradeDTO.setBuyUserId(buyerUserId.toString());
        tradeDTO.setSellUserId(sellerUserId.toString());
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(1);

        assertThatThrownBy(() -> walletService.updateWallets(tradeDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void updateWallets_buyerWalletNotFound_throwsException() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(null);
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setExecutionId("execution-3");
        tradeDTO.setBuyOrderId("100");
        tradeDTO.setSellOrderId("200");
        tradeDTO.setBuyUserId(buyerUserId.toString());
        tradeDTO.setSellUserId(sellerUserId.toString());
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(1);

        assertThatThrownBy(() -> walletService.updateWallets(tradeDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found");
    }

    @Test
    void updateWallets_sellerWalletNotFound_throwsException() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setExecutionId("execution-4");
        tradeDTO.setBuyOrderId("100");
        tradeDTO.setSellOrderId("200");
        tradeDTO.setBuyUserId(buyerUserId.toString());
        tradeDTO.setSellUserId(sellerUserId.toString());
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(1);

        assertThatThrownBy(() -> walletService.updateWallets(tradeDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found for user");
    }

    @Test
    void updateWallets_insufficientSellerUnits_throwsException() {
        sellerWallet.setUnits(2.0);
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        TradeDTO tradeDTO = new TradeDTO();
        tradeDTO.setExecutionId("execution-5");
        tradeDTO.setBuyOrderId("100");
        tradeDTO.setSellOrderId("200");
        tradeDTO.setBuyUserId(buyerUserId.toString());
        tradeDTO.setSellUserId(sellerUserId.toString());
        tradeDTO.setPrice(100.0);
        tradeDTO.setQuantity(5);

        assertThatThrownBy(() -> walletService.updateWallets(tradeDTO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient units");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // addMoney
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void addMoney_success() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        walletService.addMoney(buyerUserId, 250.0);

        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("1250.00");
        verify(walletRepository).save(buyerWallet);
    }

    @Test
    void addMoney_walletNotFound_throws() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(null);

        assertThatThrownBy(() -> walletService.addMoney(buyerUserId, 100.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found for user");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // withdrawMoney
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void withdrawMoney_success() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        walletService.withdrawMoney(buyerUserId, 300.0);

        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("700.00");
        verify(walletRepository).save(buyerWallet);
    }

    @Test
    void withdrawMoney_insufficientBalance_throws() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        assertThatThrownBy(() -> walletService.withdrawMoney(buyerUserId, 2000.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    @Test
    void withdrawMoney_walletNotFound_throws() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(null);

        assertThatThrownBy(() -> walletService.withdrawMoney(buyerUserId, 50.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // addUnits
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void addUnits_success() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        walletService.addUnits(buyerUserId, 7);

        assertThat(buyerWallet.getUnits()).isEqualTo(7.0);
        verify(walletRepository).save(buyerWallet);
    }

    @Test
    void addUnits_walletNotFound_throws() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(null);

        assertThatThrownBy(() -> walletService.addUnits(buyerUserId, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Wallet not found for user");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // withdrawUnits
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void withdrawUnits_success() {
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        walletService.withdrawUnits(sellerUserId, 4);

        assertThat(sellerWallet.getUnits()).isEqualTo(6.0);
        verify(walletRepository).save(sellerWallet);
    }

    @Test
    void withdrawUnits_insufficientUnits_throws() {
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        assertThatThrownBy(() -> walletService.withdrawUnits(sellerUserId, 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient units");
    }

    @Test
    void withdrawUnits_walletNotFound_throws() {
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(null);

        assertThatThrownBy(() -> walletService.withdrawUnits(sellerUserId, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient units");
    }

    // ──────────────────────────────────────────────────────────────────────────
    // Edge cases
    // ──────────────────────────────────────────────────────────────────────────

    @Test
    void addMoney_withZeroAmount_rejected() {
        assertThatThrownBy(() -> walletService.addMoney(buyerUserId, 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        verifyNoInteractions(walletRepository);
    }

    @Test
    void withdrawMoney_exactBalance_success() {
        when(walletRepository.findByUserIdForUpdate(buyerUserId)).thenReturn(buyerWallet);

        walletService.withdrawMoney(buyerUserId, 1000.0);

        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("0.00");
        verify(walletRepository).save(buyerWallet);
    }

    @Test
    void addUnits_withZeroQuantity_rejected() {
        assertThatThrownBy(() -> walletService.addUnits(buyerUserId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("positive");
        verifyNoInteractions(walletRepository);
    }

    @Test
    void withdrawUnits_exactUnits_success() {
        when(walletRepository.findByUserIdForUpdate(sellerUserId)).thenReturn(sellerWallet);

        walletService.withdrawUnits(sellerUserId, 10);

        assertThat(sellerWallet.getUnits()).isEqualTo(0.0);
        verify(walletRepository).save(sellerWallet);
    }
}
