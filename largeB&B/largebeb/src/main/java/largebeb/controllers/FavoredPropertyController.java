package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.FavoredPropertyRequestDTO;
import largebeb.dto.FavoredPropertyResponseDTO;
import largebeb.model.Message;
import largebeb.services.FavoredPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/favored")
@RequiredArgsConstructor
@Tag(name = "Favorites", description = "Manage customer's favorite/wishlist properties")
public class FavoredPropertyController {

    private final FavoredPropertyService favoredPropertyService;

    // Retrieve the list of favorite property IDs
    @GetMapping
    public ResponseEntity<FavoredPropertyResponseDTO> getFavoredProperties(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(favoredPropertyService.getFavoredProperties(token));
    }

    // Add a property to the favorites list
    @PostMapping
    public ResponseEntity<?> addFavoredProperty(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody FavoredPropertyRequestDTO request) {
        try {
            return ResponseEntity.ok(favoredPropertyService.addFavoredProperty(token, request));
        } catch (RuntimeException e) {
            // Handle specific exceptions
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred in adding a property as favored.");
        }
    }

    // Remove a property from the favorites list
    @DeleteMapping("/{propertyId}")
    public ResponseEntity<?> removeFavoredProperty(
            @RequestHeader("Authorization") String token,
            @PathVariable String propertyId) {
        try {
            return ResponseEntity.ok(favoredPropertyService.removeFavoredProperty(token, propertyId)); 
        } catch (RuntimeException e) {
            // Handle specific exceptions
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred in removing a property as favored.");
        }
    }
}