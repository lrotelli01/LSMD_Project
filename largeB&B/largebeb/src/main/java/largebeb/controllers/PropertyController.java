package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.PropertyResponseDTO;
import largebeb.dto.RoomResponseDTO;
import largebeb.services.PropertyService;
// AGGIUNTO: Import necessario per gestire il token
import largebeb.utilities.JwtUtil; 
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/properties")
@RequiredArgsConstructor
@Tag(name = "Properties", description = "Property search, details, trending, top-rated and recommendations")
public class PropertyController {

    private final PropertyService propertyService;
    private final largebeb.services.RecommendationService recommendationService;
    // ADDED: Needed to extract user ID from token
    private final JwtUtil jwtUtil; 

    // --- 1. PROPERTY DETAILS (Modified to save History) ---
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyResponseDTO> getPropertyDetails(
            @PathVariable String propertyId,
            @RequestHeader(value = "Authorization", required = false) String token) { // Token is optional (Guest)
        
        // 1. Retrieve details (also increments Trending on Redis)
        PropertyResponseDTO property = propertyService.getPropertyDetails(propertyId);

        // 2. If user is logged in, add to history (Redis List)
        if (token != null && token.startsWith("Bearer ")) {
            try {
                String cleanToken = token.substring(7);
                String userId = jwtUtil.getUserIdFromToken(cleanToken);
                propertyService.addToUserHistory(userId, propertyId);
            } catch (Exception e) {
                // If token is invalid or expires, ignore the error:
                // the user must still be able to see the property.
                System.out.println("Unable to update history: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(property);
    }

    // --- 2. USER HISTORY (Read) ---
    // Note: I corrected this method to use token instead of ?userId parameter
    // because it's more secure (each user only sees their own history).
    @GetMapping("/history")
    public ResponseEntity<List<PropertyResponseDTO>> getUserHistory(
            @RequestHeader("Authorization") String token) {
        
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        return ResponseEntity.ok(propertyService.getUserHistory(userId));
    }

    // --- 3. ADVANCED SEARCH ---
    @GetMapping("/search")
    public ResponseEntity<List<PropertyResponseDTO>> searchProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) List<String> amenities) {
        return ResponseEntity.ok(propertyService.searchProperties(city, minPrice, maxPrice, amenities));
    }

    // --- 3B. ROOM SEARCH ---
    @GetMapping("/rooms/search")
    public ResponseEntity<List<RoomResponseDTO>> searchRooms(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String roomType,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) Integer minCapacity,
            @RequestParam(required = false) List<String> amenities) {
        return ResponseEntity.ok(propertyService.searchRooms(city, roomType, minPrice, maxPrice, minCapacity, amenities));
    }

    // --- 4. MAP (GeoSpatial) ---
    @GetMapping("/map")
    public ResponseEntity<List<PropertyResponseDTO>> getPropertiesInArea(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam double radiusKm) {
        return ResponseEntity.ok(propertyService.getPropertiesInArea(lat, lon, radiusKm));
    }

    // --- 5. TRENDING ---
    @GetMapping("/trending")
    public ResponseEntity<List<PropertyResponseDTO>> getTrendingProperties() {
        return ResponseEntity.ok(propertyService.getTrendingProperties());
    }

    // --- 6. TOP RATED ---
    @GetMapping("/top-rated")
    public ResponseEntity<List<PropertyResponseDTO>> getTopRatedProperties() {
        return ResponseEntity.ok(propertyService.getTopRatedProperties());
    }

    // --- 7. RACCOMANDAZIONI COLLABORATIVE ---
    @GetMapping("/{propertyId}/recommendations/collaborative")
    public ResponseEntity<List<PropertyResponseDTO>> getCollaborative(@PathVariable String propertyId) {
        return ResponseEntity.ok(recommendationService.getCollaborativeRecommendations(propertyId));
    }

    // --- 8. RACCOMANDAZIONI SIMILI ---
    @GetMapping("/{propertyId}/recommendations/similar")
    public ResponseEntity<List<PropertyResponseDTO>> getSimilar(@PathVariable String propertyId) {
        return ResponseEntity.ok(recommendationService.getContentBasedRecommendations(propertyId));
    }
}