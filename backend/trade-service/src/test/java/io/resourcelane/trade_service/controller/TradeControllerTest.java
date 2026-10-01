package io.resourcelane.trade_service.controller;

import io.resourcelane.trade_service.dto.CancelOrderRequest;
import io.resourcelane.trade_service.service.TradeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.NoSuchElementException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TradeControllerTest {

    @Mock
    private TradeService tradeService;

    @InjectMocks
    private TradeController controller;

    private final Authentication user = new UsernamePasswordAuthenticationToken(7L, null, List.of());

    @Test
    void cancelIsAcceptedAndQueued() throws Exception {
        ResponseEntity<?> response = controller.cancelOrder(new CancelOrderRequest(42L), user);

        assertThat(response.getStatusCode().value()).isEqualTo(202);
        verify(tradeService).requestCancel(42L, 7L);
    }

    @Test
    void unknownOrderIsNotFound() throws Exception {
        doThrow(new NoSuchElementException("Order not found")).when(tradeService).requestCancel(42L, 7L);

        assertThat(controller.cancelOrder(new CancelOrderRequest(42L), user).getStatusCode().value()).isEqualTo(404);
    }

    @Test
    void orderThatCannotBeCancelledIsAConflict() throws Exception {
        doThrow(new IllegalArgumentException("Cancel already requested")).when(tradeService).requestCancel(42L, 7L);

        assertThat(controller.cancelOrder(new CancelOrderRequest(42L), user).getStatusCode().value()).isEqualTo(409);
    }

    @Test
    void anonymousCallerIsRejected() {
        assertThat(controller.cancelOrder(new CancelOrderRequest(42L), null).getStatusCode().value()).isEqualTo(401);
        verifyNoInteractions(tradeService);
    }
}
