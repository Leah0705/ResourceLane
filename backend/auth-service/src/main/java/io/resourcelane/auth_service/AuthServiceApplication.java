package io.resourcelane.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Authentication Service application.
 * This class bootstraps the Spring Boot application, initializing the embedded web server,
 * Spring context, and all configured beans necessary for handling authentication and authorization.
 */
@SpringBootApplication
public class AuthServiceApplication {

	/**
	 * Main method to start the Spring Boot application.
	 * 
	 * @param args Command line arguments passed during application startup.
	 */
	public static void main(String[] args) {
		SpringApplication.run(AuthServiceApplication.class, args);
	}
}
