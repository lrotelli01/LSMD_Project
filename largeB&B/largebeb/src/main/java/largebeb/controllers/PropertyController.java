package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.PropertyResponseDTO;
import largebeb.services.PropertyService;
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

    // 1. RICERCA AVANZATA (Filtri)
    // Esempio: GET /api/properties/search?city=Roma&minPrice=50&maxPrice=150&amenities=WiFi,AC
    @GetMapping("/search")
    public ResponseEntity<List<PropertyResponseDTO>> searchProperties(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) Double minPrice,
            @RequestParam(required = false) Double maxPrice,
            @RequestParam(required = false) List<String> amenities) {
        
        List<PropertyResponseDTO> results = propertyService.searchProperties(city, minPrice, maxPrice, amenities);
        return ResponseEntity.ok(results);
    }

    // 2. MAPPA GEOSPAZIALE
    // Esempio: GET /api/properties/map?lat=41.90&lon=12.49&radius=5
    @GetMapping("/map")
    public ResponseEntity<List<PropertyResponseDTO>> getPropertiesOnMap(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "10.0") double radius) { // Default 10km radius
        
        List<PropertyResponseDTO> results = propertyService.getPropertiesInArea(lat, lon, radius);
        return ResponseEntity.ok(results);
    }

    // 3. DETTAGLI CASA (Con POI e Statistiche)
    // Esempio: GET /api/properties/{id}
    @GetMapping("/{propertyId}")
    public ResponseEntity<PropertyResponseDTO> getPropertyDetails(@PathVariable String propertyId) {
        // Questo metodo incrementa anche le views su Redis (Trending)
        PropertyResponseDTO property = propertyService.getPropertyDetails(propertyId);
        return ResponseEntity.ok(property);
    }

    // 4. TRENDING (Redis Top 10)
    // Esempio: GET /api/properties/trending
    @GetMapping("/trending")
    public ResponseEntity<List<PropertyResponseDTO>> getTrendingProperties() {
        return ResponseEntity.ok(propertyService.getTrendingProperties());
    }

    // 5. TOP RATED (Filtro per stelle)
    // Esempio: GET /api/properties/top-rated
    @GetMapping("/top-rated")
    public ResponseEntity<List<PropertyResponseDTO>> getTopRatedProperties() {
        return ResponseEntity.ok(propertyService.getTopRatedProperties());
    }
    // IMPORTANTE: Inietta anche il nuovo service
    private final largebeb.services.RecommendationService recommendationService; 

    // 6. RACCOMANDAZIONI "CHI HA PRENOTATO QUESTO..." (Neo4j)
    @GetMapping("/{propertyId}/recommendations/collaborative")
    public ResponseEntity<List<PropertyResponseDTO>> getCollaborative(@PathVariable String propertyId) {
        return ResponseEntity.ok(recommendationService.getCollaborativeRecommendations(propertyId));
    }

    // 7. RACCOMANDAZIONI "SIMILI A QUESTO" (MongoDB Content-Based)
    @GetMapping("/{propertyId}/recommendations/similar")
    public ResponseEntity<List<PropertyResponseDTO>> getSimilar(@PathVariable String propertyId) {
        return ResponseEntity.ok(recommendationService.getContentBasedRecommendations(propertyId));
    }
}