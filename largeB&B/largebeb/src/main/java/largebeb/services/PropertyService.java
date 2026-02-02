package largebeb.services;

import largebeb.dto.PointOfInterestDTO;
import largebeb.dto.PropertyResponseDTO;
import largebeb.dto.RoomResponseDTO;
import largebeb.model.Property;
import largebeb.model.Room;
import largebeb.model.PointOfInterest;
import largebeb.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PropertyService {

    private final PropertyRepository propertyRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    // ESSENTIAL INJECTION FOR DYNAMIC QUERIES
    private final MongoTemplate mongoTemplate; 

    // ADVANCED SEARCH (Fix Error 500 & Dirty Data)
    public List<PropertyResponseDTO> searchProperties(String city, Double minPrice, Double maxPrice, List<String> amenities) {
        Query query = new Query();

        // City Filter (Case Insensitive: Rome == rome)
        if (city != null && !city.trim().isEmpty()) {
            query.addCriteria(Criteria.where("city").regex(city, "i"));
        }

        // Price Filter (Search in internal rooms)
        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("pricePerNightAdults");
            if (minPrice != null) priceCriteria.gte(minPrice);
            if (maxPrice != null) priceCriteria.lte(maxPrice);
            
            // elemMatch: Checks if AT LEAST ONE room satisfies the price range
            query.addCriteria(Criteria.where("rooms").elemMatch(priceCriteria));
        }

        // "Dirty" Amenities Filter (Uses Regex to ignore quotes/parentheses in DB)
        if (amenities != null && !amenities.isEmpty()) {
            List<Criteria> amenityCriteria = new ArrayList<>();
            for (String amenity : amenities) {
                // "i" = case insensitive. Finds "Wifi" even inside "[\"Wifi\"]"
                amenityCriteria.add(Criteria.where("amenities").regex(amenity, "i"));
            }
            // AND Operator: Property must have ALL requested amenities
            query.addCriteria(new Criteria().andOperator(amenityCriteria.toArray(new Criteria[0])));
        }

        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // GEOSPATIAL SEARCH (Map)
    public List<PropertyResponseDTO> getPropertiesInArea(double lat, double lon, double radiusKm) {
        // GeoJsonPoint(x=lon, y=lat) - GeoJSON uses [longitude, latitude]
        GeoJsonPoint point = new GeoJsonPoint(lon, lat);
        // Limit maximum radius to 100km to avoid heavy queries
        double maxRadiusKm = Math.min(radiusKm, 100.0);
        // For $nearSphere with GeoJSON and 2dsphere index, $maxDistance is in METERS
        double radiusMeters = maxRadiusKm * 1000;
        
        Query query = new Query();
        query.addCriteria(Criteria.where("location").nearSphere(point).maxDistance(radiusMeters));
        query.limit(100); // Limit results for performance
        
        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    /**
     * Cache: properties with TTL of 1 hour
     * Tracks trending properties and counts views
     */
    @Cacheable(value = "properties", key = "#propertyId")
    public PropertyResponseDTO getPropertyDetails(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        try {
            // Increment visit counter on Redis (Sorted Set)
            redisTemplate.opsForZSet().incrementScore("trending_properties", propertyId, 1);
            
            // Add to user history if needed here
            // But usually we do it in the controller if user is logged in
        } catch (Exception e) {
            System.err.println("Redis error: " + e.getMessage());
        }

        return mapToDTO(property);
    }
    
    public List<PropertyResponseDTO> getTrendingProperties() {
        try {
            // Get top 10 IDs with highest score (Reverse Range)
            var topIds = redisTemplate.opsForZSet().reverseRange("trending_properties", 0, 9);
            if (topIds == null || topIds.isEmpty()) return List.of();

            List<String> ids = topIds.stream().map(Object::toString).collect(Collectors.toList());
            
            // Retrieve details from Mongo
            List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
            
            // Note: findAllById doesn't guarantee order, but it's fine for now
            return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
    
    public List<PropertyResponseDTO> getTopRatedProperties() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "ratingStats.value"));
        query.limit(20);
        
        return mongoTemplate.find(query, Property.class).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // USER HISTORY (Recently Viewed)
    public void addToUserHistory(String userId, String propertyId) {
        try {
            String key = "history:" + userId;
            // Add at the head of the list
            redisTemplate.opsForList().leftPush(key, propertyId);
            // Keep only the last 10 elements
            redisTemplate.opsForList().trim(key, 0, 9);
        } catch (Exception e) {
            System.err.println("Redis error (Add History): " + e.getMessage());
        }
    }

    public List<PropertyResponseDTO> getUserHistory(String userId) {
        try {
            String key = "history:" + userId;
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

    // HELPER: Map Entity -> DTO
    private PropertyResponseDTO mapToDTO(Property p) {
        Double minPrice = 0.0;
        
        // Calculate minimum price "Starting from..." among available rooms
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                .mapToDouble(r -> r.getPricePerNightAdults().doubleValue()) 
                .min()
                .orElse(0.0);
        }

        // Convert GeoJsonPoint to List<Double> [lon, lat]
        List<Double> coords = null;
        if (p.getLocation() != null) {
            coords = List.of(p.getLocation().getX(), p.getLocation().getY());
        }

        // Convert POI to DTO with simple coordinates
        List<PointOfInterestDTO> poisDTO = null;
        if (p.getPois() != null) {
            poisDTO = p.getPois().stream()
                .map(this::mapPoiToDTO)
                .collect(Collectors.toList());
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
                .pois(poisDTO)
                .coordinates(coords)
                .build();
    }

    // HELPER: Map POI Entity -> DTO
    private PointOfInterestDTO mapPoiToDTO(PointOfInterest poi) {
        List<Double> poiCoords = null;
        if (poi.getLocation() != null) {
            poiCoords = List.of(poi.getLocation().getX(), poi.getLocation().getY());
        }
        return PointOfInterestDTO.builder()
                .id(poi.getId())
                .name(poi.getName())
                .category(poi.getCategory())
                .coordinates(poiCoords)
                .build();
    }

    // ROOM SEARCH
    public List<RoomResponseDTO> searchRooms(String city, String roomType, Double minPrice, 
                                              Double maxPrice, Integer minCapacity, List<String> amenities) {
        Query query = new Query();

        // City Filter (Case Insensitive)
        if (city != null && !city.trim().isEmpty()) {
            query.addCriteria(Criteria.where("city").regex(city, "i"));
        }

        // Combine all room criteria in a single $elemMatch
        List<Criteria> roomCriteriaList = new ArrayList<>();
        
        // Room Type Filter
        if (roomType != null && !roomType.trim().isEmpty()) {
            roomCriteriaList.add(Criteria.where("roomType").regex(roomType, "i"));
        }

        // Price Filter (in rooms)
        if (minPrice != null) {
            roomCriteriaList.add(Criteria.where("pricePerNightAdults").gte(minPrice.floatValue()));
        }
        if (maxPrice != null) {
            roomCriteriaList.add(Criteria.where("pricePerNightAdults").lte(maxPrice.floatValue()));
        }

        // Minimum Capacity Filter
        if (minCapacity != null && minCapacity > 0) {
            roomCriteriaList.add(Criteria.where("capacityAdults").gte(minCapacity.longValue()));
        }

        // Room Amenities Filter
        if (amenities != null && !amenities.isEmpty()) {
            for (String amenity : amenities) {
                roomCriteriaList.add(Criteria.where("amenities").regex(amenity, "i"));
            }
        }

        // Apply single combined $elemMatch if there are room criteria
        if (!roomCriteriaList.isEmpty()) {
            Criteria combinedRoomCriteria = new Criteria().andOperator(
                roomCriteriaList.toArray(new Criteria[0])
            );
            query.addCriteria(Criteria.where("rooms").elemMatch(combinedRoomCriteria));
        }

        // Execute the query
        List<Property> properties = mongoTemplate.find(query, Property.class);

        // Extract and filter rooms that match the criteria
        List<RoomResponseDTO> result = new ArrayList<>();
        for (Property property : properties) {
            if (property.getRooms() == null) continue;
            
            for (Room room : property.getRooms()) {
                // Check if room satisfies the criteria
                boolean matches = true;

                // roomType filter
                if (roomType != null && !roomType.trim().isEmpty()) {
                    if (room.getRoomType() == null || 
                        !room.getRoomType().toLowerCase().contains(roomType.toLowerCase())) {
                        matches = false;
                    }
                }

                // Price filter
                if (matches && minPrice != null && room.getPricePerNightAdults() != null) {
                    if (room.getPricePerNightAdults() < minPrice) matches = false;
                }
                if (matches && maxPrice != null && room.getPricePerNightAdults() != null) {
                    if (room.getPricePerNightAdults() > maxPrice) matches = false;
                }

                // Capacity filter
                if (matches && minCapacity != null && room.getCapacityAdults() != null) {
                    if (room.getCapacityAdults() < minCapacity) matches = false;
                }

                // Room amenities filter
                if (matches && amenities != null && !amenities.isEmpty() && room.getAmenities() != null) {
                    for (String amenity : amenities) {
                        boolean found = room.getAmenities().stream()
                            .anyMatch(a -> a.toLowerCase().contains(amenity.toLowerCase()));
                        if (!found) {
                            matches = false;
                            break;
                        }
                    }
                }

                if (matches) {
                    result.add(mapRoomToDTO(room, property));
                }
            }
        }

        return result;
    }

    // HELPER: Map Room Entity -> DTO
    private RoomResponseDTO mapRoomToDTO(Room room, Property property) {
        return RoomResponseDTO.builder()
                .id(room.getId())
                .propertyId(property.getId())
                .propertyName(property.getName())
                .propertyCity(property.getCity())
                .name(room.getName())
                .roomType(room.getRoomType())
                .numBeds(room.getNumBeds())
                .amenities(room.getAmenities())
                .photos(room.getPhotos())
                .status(room.getStatus())
                .capacityAdults(room.getCapacityAdults())
                .capacityChildren(room.getCapacityChildren())
                .pricePerNightAdults(room.getPricePerNightAdults())
                .pricePerNightChildren(room.getPricePerNightChildren())
                .build();
    }
}