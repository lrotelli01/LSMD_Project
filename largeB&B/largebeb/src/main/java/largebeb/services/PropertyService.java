package largebeb.services;

import largebeb.dto.PropertyResponseDTO;
import largebeb.model.Property;
import largebeb.model.Room;
import largebeb.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final MongoTemplate mongoTemplate; 
    private final RedisTemplate<String, Object> redisTemplate;

    // --- 1. RICERCA E FILTRI AVANZATI ---
    public List<PropertyResponseDTO> searchProperties(String city, Double minPrice, Double maxPrice, List<String> amenities) {
        Query query = new Query();

        if (city != null && !city.trim().isEmpty()) {
            query.addCriteria(Criteria.where("city").regex(city, "i"));
        }

        if (amenities != null && !amenities.isEmpty()) {
            query.addCriteria(Criteria.where("amenities").all(amenities));
        }

        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("pricePerNightAdults");
            if (minPrice != null) priceCriteria.gte(minPrice);
            if (maxPrice != null) priceCriteria.lte(maxPrice);
            
            query.addCriteria(Criteria.where("rooms").elemMatch(priceCriteria));
        }

        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // --- 2. MAPPA GEOSPAZIALE ---
    public List<PropertyResponseDTO> getPropertiesInArea(double lat, double lon, double radiusKm) {
        Query query = new Query();
        query.addCriteria(Criteria.where("location").nearSphere(new Point(lon, lat))
                .maxDistance(radiusKm / 6378.1)); 
        
        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // --- 3. DETTAGLI & REDIS ---
    public PropertyResponseDTO getPropertyDetails(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        try {
            redisTemplate.opsForZSet().incrementScore("trending_properties", propertyId, 1);
        } catch (Exception e) {
            System.err.println("Redis error: " + e.getMessage());
        }

        return mapToDTO(property);
    }
    
    // --- 4. CLASSIFICA (Top 10) ---
    public List<PropertyResponseDTO> getTrendingProperties() {
        try {
            var topIds = redisTemplate.opsForZSet().reverseRange("trending_properties", 0, 9);
            if (topIds == null || topIds.isEmpty()) return List.of();

            List<String> ids = topIds.stream().map(Object::toString).collect(Collectors.toList());
            List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
            return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
    
    // --- 5. TOP RATED ---
    public List<PropertyResponseDTO> getTopRatedProperties() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "ratingStats.value"));
        query.limit(20);
        
        return mongoTemplate.find(query, Property.class).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // AGGIUNTA PER IL REQUISITO "RECENTLY VIEWED"
    public void addToUserHistory(String userId, String propertyId) {
        String key = "history:" + userId;
        redisTemplate.opsForList().leftPush(key, propertyId);
        redisTemplate.opsForList().trim(key, 0, 9);
    }

    public List<PropertyResponseDTO> getUserHistory(String userId) {
        String key = "history:" + userId;
        List<Object> historyIds = redisTemplate.opsForList().range(key, 0, -1);
        if (historyIds == null || historyIds.isEmpty()) return List.of();

        List<String> ids = historyIds.stream().map(Object::toString).collect(Collectors.toList());
        List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // Helper: Converte Entity -> DTO (CORRETTO IL FLOAT vs DOUBLE)
    private PropertyResponseDTO mapToDTO(Property p) {
        Double minPrice = 0.0;
        
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                // Conversione esplicita da Float (DB) a Double (Java)
                .mapToDouble(r -> r.getPricePerNightAdults().doubleValue()) 
                .min()
                .orElse(0.0);
        }

        return PropertyResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .city(p.getCity())
                .region(p.getRegion())
                .country(p.getCountry())
                .pricePerNight(minPrice)
                .rating(p.getRatingStats() != null ? p.getRatingStats().getValue() : 0.0)
                .amenities(p.getAmenities())
                .photos(p.getPhotos())
                .pois(p.getPois())
                .coordinates(p.getCoordinates())
                .build();
    }
}