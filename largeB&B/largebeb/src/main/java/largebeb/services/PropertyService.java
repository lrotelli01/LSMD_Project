package largebeb.services;

import largebeb.dto.PropertyResponseDTO;
import largebeb.model.Property;
import largebeb.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.geo.Point;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final MongoTemplate mongoTemplate;
    private final RedisTemplate<String, Object> redisTemplate;

    // ADVANCED SEARCH and FILTERING
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

    // GEOSPATIAL SEARCH
    public List<PropertyResponseDTO> getPropertiesInArea(double lat, double lon, double radiusKm) {
        Query query = new Query();
        // Use nearSphere for accurate distance calculation on Earth's surface
        query.addCriteria(Criteria.where("location").nearSphere(new Point(lon, lat))
                .maxDistance(radiusKm / 6378.1)); 
        
        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // DETAILS and CACHING (With Trending Tracking)
    // @Cacheable: Checks Redis "properties" cache first. 
    // If found, returns immediately. If not, runs method and saves to Redis.
    @Cacheable(value = "properties", key = "#propertyId")
    public PropertyResponseDTO getPropertyDetails(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        // Update the "Trending" score in Redis manually
        try {
            redisTemplate.opsForZSet().incrementScore("trending_properties", propertyId, 1);
        } catch (Exception e) {
            System.err.println("Redis error (Trending increment): " + e.getMessage());
        }

        return mapToDTO(property);
    }
    
    // TRENDING PROPERTIES (Top 10)
    public List<PropertyResponseDTO> getTrendingProperties() {
        try {
            // Retrieve top 10 IDs with highest scores (reverse range)
            Set<Object> topIds = redisTemplate.opsForZSet().reverseRange("trending_properties", 0, 9);
            
            if (topIds == null || topIds.isEmpty()) return List.of();

            List<String> ids = topIds.stream().map(Object::toString).collect(Collectors.toList());
            List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
            
            return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Redis error (Trending list): " + e.getMessage());
            return List.of();
        }
    }
    
    // TOP RATED PROPERTIES
    @Cacheable(value = "top_rated", key = "'global'")
    public List<PropertyResponseDTO> getTopRatedProperties() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "ratingStats.value"));
        query.limit(20);
        
        return mongoTemplate.find(query, Property.class).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // RECENTLY VIEWED (User History) 
    public void addToUserHistory(String userId, String propertyId) {
        String key = "history:" + userId;
        try {
            redisTemplate.opsForList().leftPush(key, propertyId);
            // Keep only the latest 10 items
            redisTemplate.opsForList().trim(key, 0, 9);
        } catch (Exception e) {
            System.err.println("Redis error (Add History): " + e.getMessage());
        }
    }

    public List<PropertyResponseDTO> getUserHistory(String userId) {
        String key = "history:" + userId;
        try {
            List<Object> historyIds = redisTemplate.opsForList().range(key, 0, -1);
            if (historyIds == null || historyIds.isEmpty()) return List.of();

            List<String> ids = historyIds.stream().map(Object::toString).collect(Collectors.toList());
            List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
            return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (Exception e) {
             System.err.println("Redis error (Get History): " + e.getMessage());
             return List.of();
        }
    }

    // --- HELPER: Entity to DTO Mapping ---
    private PropertyResponseDTO mapToDTO(Property p) {
        Double minPrice = 0.0;
        
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                // Explicit conversion from Float (DB) to Double (Java)
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