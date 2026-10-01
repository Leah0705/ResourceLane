package io.resourcelane.auth_service.repository;

import io.resourcelane.auth_service.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository interface for User entity operations.
 * Extends JpaRepository to inherit standard CRUD operations (save, find, delete, etc.)
 * for the User entity without needing boilerplate code. It abstracts the database
 * layer for the authentication service.
 */
public interface AuthRepositoryWrapper extends JpaRepository<User, Long> {

    /**
     * Finds a user by their registered phone number.
     * This method is essential for login and registration flows to check if
     * an account already exists for a given phone number before proceeding.
     *
     * @param phoneNumber The phone number string to search for in the database.
     * @return An Optional containing the User entity if a match is found, or empty if no user exists.
     */
    Optional<User> findByPhoneNumber(String phoneNumber);
}
