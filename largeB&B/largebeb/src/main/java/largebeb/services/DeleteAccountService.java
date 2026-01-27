package largebeb.services;

import java.time.LocalDate;

import org.springframework.stereotype.Service;
import largebeb.model.RegisteredUser;
import largebeb.repository.ReservationRepository;
import largebeb.repository.UserGraphRepository; // IMPORT ADDED
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DeleteAccountService {

    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository; 
    private final UserGraphRepository userGraphRepository;
    private final JwtUtil jwtUtil;
        
    public void deleteAccount(String token) {
        // Handle Bearer prefix if present
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;

        // Validate the token integrity and expiration
        if (!jwtUtil.validateToken(cleanToken)) {
            throw new IllegalArgumentException("Token is invalid or expired.");
        }

        // Extract user id from the token to identify the user
        String userId = jwtUtil.getUserIdFromToken(cleanToken);

        // Check if the user exists in the database
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found with id: " + userId));

        // Check for active bookings
        // You cannot delete an account if there are future reservations pending
        if (reservationRepository.hasActiveBookings(user.getId(), LocalDate.now())) {
            throw new IllegalStateException("Cannot delete account. You have active or future bookings.");
        }

        // Delete the user from MongoDB
        userRepository.delete(user);

        // Delete user from Neo4j
        // We perform this after MongoDB deletion to ensure the main record is gone first.
        try {
            // This will detach relationships (if cascade is configured) and delete the node
            userGraphRepository.deleteById(userId);
            System.out.println("Graph: User node " + userId + " deleted from Neo4j.");
        } catch (Exception e) {
            // We log the error but do not throw exception to ensure the HTTP response 
            // reflects that the account was successfully deleted from the main DB (Mongo).
            System.err.println("Graph Error: Failed to delete user from Neo4j. " + e.getMessage());
        }

        // Blacklist the token in Redis immediately
        // This ensures the token cannot be used for any other request even if it hasn't expired yet
        jwtUtil.blacklistToken(cleanToken);
    }
}