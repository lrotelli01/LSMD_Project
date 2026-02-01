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
    // AGGIUNTO: Serve per estrarre l'ID utente dal token
    private final JwtUtil jwtUtil; 

    // --- 1. DETTAGLI CASA (Modificato per salvare la Cronologia) ---
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyResponseDTO> getPropertyDetails(
            @PathVariable String propertyId,
            @RequestHeader(value = "Authorization", required = false) String token) { // Il token è opzionale (Guest)
        
        // 1. Recupera i dettagli (incrementa anche il Trending su Redis)
        PropertyResponseDTO property = propertyService.getPropertyDetails(propertyId);

        // 2. Se l'utente è loggato, aggiungi alla cronologia (Redis List)
        if (token != null && token.startsWith("Bearer ")) {
            try {
                String cleanToken = token.substring(7);
                String userId = jwtUtil.getUserIdFromToken(cleanToken);
                propertyService.addToUserHistory(userId, propertyId);
            } catch (Exception e) {
                // Se il token non è valido o scade, ignoriamo l'errore:
                // l'utente deve comunque poter vedere la casa.
                System.out.println("Impossibile aggiornare cronologia: " + e.getMessage());
            }
        }

        return ResponseEntity.ok(property);
    }

    // --- 2. STORICO UTENTE (Lettura) ---
    // Nota: Ho corretto questo metodo per usare il token invece del parametro ?userId
    // perché è più sicuro (ognuno vede solo la sua storia).
    @GetMapping("/history")
    public ResponseEntity<List<PropertyResponseDTO>> getUserHistory(
            @RequestHeader("Authorization") String token) {
        
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        return ResponseEntity.ok(propertyService.getUserHistory(userId));
    }

    // --- 3. RICERCA AVANZATA ---
    @GetMapping("/search")
    public ResponseEntity<List<PropertyResponseDTO>> searchProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) List<String> amenities) {
        return ResponseEntity.ok(propertyService.searchProperties(city, minPrice, maxPrice, amenities));
    }

    // --- 3B. RICERCA STANZE ---
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

    // --- 4. MAPPA (GeoSpatial) ---
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