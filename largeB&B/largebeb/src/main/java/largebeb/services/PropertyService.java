package largebeb.services;

import largebeb.dto.PropertyResponseDTO;
import largebeb.model.Property;
import largebeb.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
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
    // INIEZIONE FONDAMENTALE PER LE QUERY DINAMICHE
    private final MongoTemplate mongoTemplate; 

    // --- 1. RICERCA AVANZATA (Fix Error 500 & Dati Sporchi) ---
    public List<PropertyResponseDTO> searchProperties(String city, Double minPrice, Double maxPrice, List<String> amenities) {
        Query query = new Query();

        // A. Filtro Città (Case Insensitive: Rome == rome)
        if (city != null && !city.trim().isEmpty()) {
            query.addCriteria(Criteria.where("city").regex(city, "i"));
        }

        // B. Filtro Prezzo (Cerca nelle stanze interne)
        if (minPrice != null || maxPrice != null) {
            Criteria priceCriteria = Criteria.where("pricePerNightAdults");
            if (minPrice != null) priceCriteria.gte(minPrice);
            if (maxPrice != null) priceCriteria.lte(maxPrice);
            
            // elemMatch: Controlla se ALMENO UNA stanza soddisfa il range di prezzo
            query.addCriteria(Criteria.where("rooms").elemMatch(priceCriteria));
        }

        // C. Filtro Amenities "Sporche" (Usa Regex per ignorare virgolette/parentesi nel DB)
        if (amenities != null && !amenities.isEmpty()) {
            List<Criteria> amenityCriteria = new ArrayList<>();
            for (String amenity : amenities) {
                // "i" = case insensitive. Trova "Wifi" anche dentro "[\"Wifi\"]"
                amenityCriteria.add(Criteria.where("amenities").regex(amenity, "i"));
            }
            // AND Operator: La casa deve avere TUTTI i servizi richiesti
            query.addCriteria(new Criteria().andOperator(amenityCriteria.toArray(new Criteria[0])));
        }

        // Esegue la query sicura
        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // --- 2. RICERCA GEOSPAZIALE (Mappa) ---
    public List<PropertyResponseDTO> getPropertiesInArea(double lat, double lon, double radiusKm) {
        // GeoJsonPoint(x=lon, y=lat) - GeoJSON usa [longitude, latitude]
        GeoJsonPoint point = new GeoJsonPoint(lon, lat);
        // Limita il raggio massimo a 100km per evitare query troppo pesanti
        double maxRadiusKm = Math.min(radiusKm, 100.0);
        // Per $nearSphere con GeoJSON e indice 2dsphere, $maxDistance è in METRI
        double radiusMeters = maxRadiusKm * 1000;
        
        Query query = new Query();
        query.addCriteria(Criteria.where("location").nearSphere(point).maxDistance(radiusMeters));
        query.limit(100); // Limita i risultati per performance
        
        List<Property> properties = mongoTemplate.find(query, Property.class);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // --- 3. DETTAGLI & REDIS (Trending) ---
    public PropertyResponseDTO getPropertyDetails(String propertyId) {
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found"));

        try {
            // Incrementa il contatore visite su Redis (Sorted Set)
            redisTemplate.opsForZSet().incrementScore("trending_properties", propertyId, 1);
            
            // (Opzionale) Aggiunge alla cronologia utente se servisse qui
            // Ma di solito lo facciamo nel controller se l'utente è loggato
        } catch (Exception e) {
            System.err.println("Redis error: " + e.getMessage());
        }

        return mapToDTO(property);
    }
    
    // --- 4. CLASSIFICA (Top 10 Trending) ---
    public List<PropertyResponseDTO> getTrendingProperties() {
        try {
            // Prendi i primi 10 ID con punteggio più alto (Reverse Range)
            var topIds = redisTemplate.opsForZSet().reverseRange("trending_properties", 0, 9);
            if (topIds == null || topIds.isEmpty()) return List.of();

            List<String> ids = topIds.stream().map(Object::toString).collect(Collectors.toList());
            
            // Recupera i dettagli da Mongo
            List<Property> properties = (List<Property>) propertyRepository.findAllById(ids);
            
            // Nota: findAllById non garantisce l'ordine, ma per ora va bene
            return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
    
    // --- 5. TOP RATED (Da MongoDB) ---
    public List<PropertyResponseDTO> getTopRatedProperties() {
        Query query = new Query();
        query.with(Sort.by(Sort.Direction.DESC, "ratingStats.value"));
        query.limit(20);
        
        return mongoTemplate.find(query, Property.class).stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // --- 6. STORICO UTENTE (Recently Viewed) ---
    public void addToUserHistory(String userId, String propertyId) {
        try {
            String key = "history:" + userId;
            // Aggiungi in testa alla lista
            redisTemplate.opsForList().leftPush(key, propertyId);
            // Mantieni solo gli ultimi 10 elementi
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

    // --- HELPER: Mappa Entity -> DTO ---
    private PropertyResponseDTO mapToDTO(Property p) {
        Double minPrice = 0.0;
        
        // Calcola il prezzo minimo "A partire da..." tra le stanze disponibili
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                .mapToDouble(r -> r.getPricePerNightAdults().doubleValue()) 
                .min()
                .orElse(0.0);
        }

        // Converti GeoJsonPoint in List<Double> [lon, lat]
        List<Double> coords = null;
        if (p.getLocation() != null) {
            coords = List.of(p.getLocation().getX(), p.getLocation().getY());
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
                .coordinates(coords)
                .build();
    }
}