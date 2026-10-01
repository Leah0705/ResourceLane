package io.resourcelane.trade_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main entry point for the Trade Service application.
 * Initializes the Spring Boot context and enables scheduling.
 */
@SpringBootApplication
@EnableScheduling
public class TradeServiceApplication {

	/**
	 * Main method to start the Trade Service application.
	 * 
	 * @param args Command line arguments passed to the application.
	 */
	public static void main(String[] args) {
		SpringApplication.run(TradeServiceApplication.class, args);
	}

}
