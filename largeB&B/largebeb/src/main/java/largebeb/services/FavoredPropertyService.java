package largebeb.services;

import largebeb.dto.FavoredPropertyRequestDTO;
import largebeb.dto.FavoredPropertyResponseDTO;
import largebeb.model.Customer;
import largebeb.repository.PropertyRepository;
import largebeb.model.RegisteredUser;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.HashSet;
import java.util.Set;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FavoredPropertyService {

    private final UserRepository userRepository;
    private final PropertyRepository propertyRepository;
    private final JwtUtil jwtUtil;

    // Get favored properties
    public FavoredPropertyResponseDTO getFavoredProperties(String token) {
        Customer customer = getCustomerFromToken(token);
        
        Set<String> favorites = customer.getFavoredPropertyIds();
        if (favorites == null) {
            favorites = new HashSet<>();
        }
        
        return new FavoredPropertyResponseDTO(favorites);
    }

    // Add favored property
    @Transactional
    public FavoredPropertyResponseDTO addFavoredProperty(String token, FavoredPropertyRequestDTO request) {
        if (request.getPropertyId() == null || request.getPropertyId().trim().isEmpty()) {
            throw new IllegalArgumentException("Property ID cannot be empty.");
        }
        // Check if the property exists
        if (!propertyRepository.existsById(request.getPropertyId())) {
            throw new IllegalArgumentException("Property not found with ID: " + request.getPropertyId());
        }

        Customer customer = getCustomerFromToken(token);

        if (customer.getFavoredPropertyIds() == null) {
            customer.setFavoredPropertyIds(new HashSet<>());
        }

        // Add to Set (handles duplicates automatically)
        customer.getFavoredPropertyIds().add(request.getPropertyId());
        
        userRepository.save(customer); 
        
        return new FavoredPropertyResponseDTO(customer.getFavoredPropertyIds());
    }

    // Remove favored property
    @Transactional
    public FavoredPropertyResponseDTO removeFavoredProperty(String token, String propertyId) {
        Customer customer = getCustomerFromToken(token);
        if (customer.getFavoredPropertyIds() != null) {
            customer.getFavoredPropertyIds().remove(propertyId);
            userRepository.save(customer);
        }

        return new FavoredPropertyResponseDTO(customer.getFavoredPropertyIds());
    }

    // Helper function to extract Customer from token
    private Customer getCustomerFromToken(String token) {
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        // If user is a customer then cast it
        if (user instanceof Customer) {
            return (Customer) user;
        } else {
            // Block Manager 
            throw new SecurityException("Only Customers can have favorite properties.");
        }
    }
}