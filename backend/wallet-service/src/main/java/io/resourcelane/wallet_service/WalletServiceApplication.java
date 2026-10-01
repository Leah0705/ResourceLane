package io.resourcelane.wallet_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Wallet Service.
 * This service is responsible for managing user wallets, tracking demo credit balances and units holdings,
 * and orchestrating demo-unit settlement within ResourceLane.
 */
@SpringBootApplication
public class WalletServiceApplication {

    /**
     * The main entry point for the Wallet Service application.
     * Bootstraps the Spring Context and starts the embedded web server.
     *
     * @param args Command-line arguments passed to the application.
     */
	public static void main(String[] args) {
		SpringApplication.run(WalletServiceApplication.class, args);
	}

}
