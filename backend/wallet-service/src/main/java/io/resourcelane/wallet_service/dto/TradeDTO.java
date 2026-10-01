package io.resourcelane.wallet_service.dto;


import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Data Transfer Object (DTO) encapsulating the details of a successfully executed trade.
 * Used internally across microservices to process the wallet impact (credit and unit transfers)
 * on the wallets of the users involved in the trade.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TradeDTO {

    /** Unique ID of this individual match, used to deduplicate settlement. */
    private String executionId;

    /**
     * The unique identifier of the user acting as the buyer in the trade.
     * This user will pay demo credits and receive units.
     */
    private String buyUserId;  //buyer

    /**
     * The unique identifier of the user acting as the seller in the trade.
     * This user will surrender units and receive demo credits.
     */
    private String sellUserId; // seller

    /**
     * The identifier of the specific buy order that was matched in this trade.
     */
    private String buyOrderId;  //buyer

    /**
     * The identifier of the specific sell order that was matched in this trade.
     */
    private String sellOrderId;  // seller

    /**
     * The agreed-upon execution price per unit of units for this trade.
     */
    private Double price;

    /**
     * The total quantity of units exchanged in this specific trade execution.
     */
    private int quantity;

}


