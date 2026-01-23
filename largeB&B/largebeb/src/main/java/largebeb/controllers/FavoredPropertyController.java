package largebeb.controllers;

import largebeb.dto.FavoredPropertyRequestDTO;
import largebeb.dto.FavoredPropertyResponseDTO;
import largebeb.model.Message;
import largebeb.services.FavoredPropertyService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/favored")
@RequiredArgsConstructor
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
            @RequestBody FavoredPropertyRequestDTO request) {
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