package largebeb.services;

import largebeb.dto.PointOfInterestDTO;
import largebeb.dto.PropertyResponseDTO;
import largebeb.model.PointOfInterest;
import largebeb.model.Property;
import largebeb.repository.PropertyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final Neo4jClient neo4jClient;
    private final PropertyRepository propertyRepository;
    private final MongoTemplate mongoTemplate;

    // --- 1. COLLABORATIVE FILTERING (Neo4j) ---
    public List<PropertyResponseDTO> getCollaborativeRecommendations(String propertyId) {
        
        String cypherQuery = """
            MATCH (p:Property {propertyId: $propId})<-[:BOOKED]-(u:User)-[:BOOKED]->(other:Property)
            RETURN other.propertyId AS recommendedId, count(*) AS strength
            ORDER BY strength DESC
            LIMIT 5
        """;

        Collection<String> recommendedIds = neo4jClient.query(cypherQuery)
                .bind(propertyId).to("propId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("recommendedId").asString())
                .all();

        if (recommendedIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Property> properties = (List<Property>) propertyRepository.findAllById(recommendedIds);
        return properties.stream().map(this::mapToDTO).collect(Collectors.toList());
    }

    // --- 2. CONTENT-BASED FILTERING (MongoDB) ---
public List<PropertyResponseDTO> getContentBasedRecommendations(String propertyId) {
        
        // Logica della Query Cypher:
        // 1. MATCH (p): Trova il nodo della proprietà corrente usando l'ID.
        // 2. -[:HAS]->(a): Attraversa il grafo per trovare tutti i nodi Amenity collegati (es. Wifi, Pool).
        // 3. <-[:HAS]-(other): Dai nodi Amenity, torna indietro per trovare ALTRE proprietà che hanno gli stessi servizi.
        // 4. count(a): Conta quanti servizi hanno in comune. Più alto è il numero, più sono simili.
        
        String cypherQuery = """
            MATCH (p:Property {propertyId: $propId})-[:HAS]->(a:Amenity)<-[:HAS]-(other:Property)
            WHERE p.propertyId <> other.propertyId
            RETURN other.propertyId AS recommendedId, count(a) AS matchStrength
            ORDER BY matchStrength DESC
            LIMIT 10
        """;

        // Esecuzione della query su Neo4j
        Collection<String> recommendedIds = neo4jClient.query(cypherQuery)
                .bind(propertyId).to("propId")
                .fetchAs(String.class)
                .mappedBy((typeSystem, record) -> record.get("recommendedId").asString())
                .all();

        if (recommendedIds.isEmpty()) {
            return Collections.emptyList();
        }

        // Idratazione dei dati (Hybrid Approach):
        // Neo4j ci dà solo gli ID (velocissimo), ora chiediamo a Mongo i dettagli (Foto, Prezzi, ecc.)
        List<Property> properties = (List<Property>) propertyRepository.findAllById(recommendedIds);
        
        // Trucco tecnico: findAllById NON garantisce l'ordine.
        // Noi dobbiamo mantenere l'ordine di Neo4j (dalla più simile alla meno simile).
        // Creiamo una mappa per riordinare la lista.
        var propertyMap = properties.stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        
        return recommendedIds.stream()
                .map(propertyMap::get)           // Prendi l'oggetto dalla mappa nell'ordine giusto
                .filter(java.util.Objects::nonNull) // Evita crash se Mongo non trova un ID
                .map(this::mapToDTO)             // Converti in DTO
                .collect(Collectors.toList());
    }

    private long countCommon(List<String> l1, List<String> l2) {
        if (l1 == null || l2 == null) return 0;
        List<String> copy = new ArrayList<>(l1);
        copy.retainAll(l2);
        return copy.size();
    }

    // Helper: Mappa Entity -> DTO (CORRETTO IL FLOAT E LA FOTO)
    private PropertyResponseDTO mapToDTO(Property p) {
        Double minPrice = 0.0;
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                .mapToDouble(r -> r.getPricePerNightAdults().doubleValue())
                .min()
                .orElse(0.0);
        }

        // Converti GeoJsonPoint in List<Double> [lon, lat]
        java.util.List<Double> coords = null;
        if (p.getLocation() != null) {
            coords = java.util.List.of(p.getLocation().getX(), p.getLocation().getY());
        }

        // Converti POI in DTO
        List<PointOfInterestDTO> poisDTO = null;
        if (p.getPois() != null) {
            poisDTO = p.getPois().stream()
                .map(this::mapPoiToDTO)
                .collect(Collectors.toList());
        }

        return PropertyResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .city(p.getCity())
                .pricePerNight(minPrice)
                .rating(p.getRatingStats() != null ? p.getRatingStats().getValue() : 0.0)
                .amenities(p.getAmenities())
                .photos(p.getPhotos())
                .pois(poisDTO)
                .coordinates(coords)
                .build();
    }

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
}