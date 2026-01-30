package largebeb.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PropertyGraphRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Creates or updates a Property node in Neo4j
     */
    public void createOrUpdateProperty(String propertyId, List<String> amenities) {
        String cypher = """
            MERGE (p:Property {propertyId: $propertyId})
            """;
        
        neo4jClient.query(cypher)
                .bind(propertyId).to("propertyId")
                .run();
        
        // Create relationships with amenities if provided
        if (amenities != null && !amenities.isEmpty()) {
            for (String amenity : amenities) {
                createAmenityRelationship(propertyId, amenity);
            }
        }
    }

    /**
     * Creates HAS relationship between Property and Amenity
     */
    private void createAmenityRelationship(String propertyId, String amenityName) {
        String cypher = """
            MATCH (p:Property {propertyId: $propertyId})
            MERGE (a:Amenity {name: $amenityName})
            MERGE (p)-[:HAS]->(a)
            """;
        
        neo4jClient.query(cypher)
                .bind(propertyId).to("propertyId")
                .bind(amenityName).to("amenityName")
                .run();
    }

    /**
     * Deletes a Property node and all its relationships from Neo4j
     */
    public void deleteById(String propertyId) {
        String cypher = """
            MATCH (p:Property {propertyId: $propertyId})
            DETACH DELETE p
            """;
        
        neo4jClient.query(cypher)
                .bind(propertyId).to("propertyId")
                .run();
    }
    
    /**
     * Saves a PropertyNode to Neo4j
     */
    public void save(largebeb.model.graph.PropertyNode propertyNode) {
        createOrUpdateProperty(
            propertyNode.getPropertyId(), 
            null  // amenities handled separately
        );
    }
}
