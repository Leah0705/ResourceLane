package io.resourcelane.wallet_service.service;

import io.resourcelane.wallet_service.dto.TradeDTO;
import io.resourcelane.wallet_service.model.Wallet;
import io.resourcelane.wallet_service.model.OrderReservation;
import io.resourcelane.wallet_service.repository.WalletRepositoryWrapper;
import io.resourcelane.wallet_service.repository.OrderReservationRepository;
import io.resourcelane.wallet_service.repository.WalletSettlementRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class WalletSettlementIntegrationTest {
    @Autowired
    private WalletService walletService;
    @Autowired
    private WalletRepositoryWrapper wallets;
    @Autowired
    private OrderReservationRepository reservations;
    @Autowired
    private WalletSettlementRepository settlements;

    @BeforeEach
    void reset() {
        settlements.deleteAll();
        reservations.deleteAll();
        wallets.deleteAll();
        wallets.save(new Wallet(null, 1L, new BigDecimal("1000.00"), 0.0));
        wallets.save(new Wallet(null, 2L, new BigDecimal("500.00"), 10.0));
        reservations.save(new OrderReservation(100L, 1L, "BUY", "LIMIT", new BigDecimal("100.00"), 10));
        reservations.save(new OrderReservation(200L, 2L, "SELL", "LIMIT", new BigDecimal("100.00"), 10));
    }

    private TradeDTO trade() {
        TradeDTO trade = new TradeDTO();
        trade.setExecutionId("match-42");
        trade.setBuyUserId("1");
        trade.setSellUserId("2");
        trade.setBuyOrderId("100");
        trade.setSellOrderId("200");
        trade.setPrice(100.0);
        trade.setQuantity(5);
        return trade;
    }

    @Test
    void redeliveredExecutionSettlesOnlyOnce() {
        TradeDTO trade = trade();
        walletService.updateWallets(trade);
        walletService.updateWallets(trade);

        assertThat(wallets.findByUserId(1L).getBalance()).isEqualByComparingTo("500.00");
        assertThat(wallets.findByUserId(1L).getUnits()).isEqualTo(5.0);
        assertThat(wallets.findByUserId(2L).getBalance()).isEqualByComparingTo("1000.00");
        assertThat(wallets.findByUserId(2L).getUnits()).isEqualTo(5.0);
        assertThat(settlements.count()).isEqualTo(1);
    }

    @Test
    void reusedExecutionIdWithDifferentPayloadIsRejected() {
        walletService.updateWallets(trade());
        TradeDTO conflicting = trade();
        conflicting.setQuantity(4);

        assertThatThrownBy(() -> walletService.updateWallets(conflicting))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Execution ID reused");
        assertThat(wallets.findByUserId(1L).getBalance()).isEqualByComparingTo("500.00");
        assertThat(settlements.count()).isEqualTo(1);
    }

    @Test
    void insufficientUnitsRollsBackTheWholeTransfer() {
        TradeDTO trade = trade();
        trade.setPrice(50.0);
        trade.setQuantity(11);
        OrderReservation buyHold = reservations.findById(100L).orElseThrow();
        OrderReservation sellHold = reservations.findById(200L).orElseThrow();
        buyHold.setQuantity(11);
        sellHold.setQuantity(11);
        reservations.save(buyHold);
        reservations.save(sellHold);

        assertThatThrownBy(() -> walletService.updateWallets(trade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient units");
        assertThat(wallets.findByUserId(1L).getBalance()).isEqualByComparingTo("1000.00");
        assertThat(wallets.findByUserId(2L).getBalance()).isEqualByComparingTo("500.00");
        assertThat(settlements.count()).isZero();
    }

    @Test
    void withdrawalsCannotSpendReservedCreditsOrUnits() {
        assertThatThrownBy(() -> walletService.withdrawMoney(1L, 0.01))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient balance");
        assertThatThrownBy(() -> walletService.withdrawUnits(2L, 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Insufficient units");

        TradeDTO execution = trade();
        execution.setQuantity(5);
        execution.setPrice(90.0);
        walletService.updateWallets(execution);
        assertThat(reservations.findById(100L).orElseThrow().heldQuantity()).isEqualTo(5);
        assertThat(reservations.findById(200L).orElseThrow().heldQuantity()).isEqualTo(5);
        walletService.withdrawMoney(1L, 50.0);
        assertThat(wallets.findByUserId(1L).getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    void decimalPriceSettlesToExactCents() {
        TradeDTO trade = trade();
        trade.setPrice(0.10);
        trade.setQuantity(3);

        walletService.updateWallets(trade);

        assertThat(wallets.findByUserId(1L).getBalance()).isEqualByComparingTo("999.70");
        assertThat(wallets.findByUserId(2L).getBalance()).isEqualByComparingTo("500.30");
    }

    @Test
    void priceWithFractionalCentsCannotSettle() {
        TradeDTO trade = trade();
        trade.setPrice(0.001);

        assertThatThrownBy(() -> walletService.updateWallets(trade))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("two decimal places");
        assertThat(settlements.count()).isZero();
    }
}
