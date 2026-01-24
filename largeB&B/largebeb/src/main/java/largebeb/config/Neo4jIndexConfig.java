package largebeb.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.data.neo4j.core.Neo4jClient;

import java.util.List;

@Configuration
public class Neo4jIndexConfig {

    private final Neo4jClient neo4jClient;

    public Neo4jIndexConfig(Neo4jClient neo4jClient) {
        this.neo4jClient = neo4jClient;
    }

    /*
      Executes Cypher commands to create the strict set of constraints 
      defined in the documentation immediately after application startup.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void createIndexesAndConstraints() {
          
        // Mapped 1-to-1 with your LaTeX "Neo4j Indexing Strategy"
        List<String> startupQueries = List.of(
            
            // Property Identity Constraint
            // Anchor Node for recommendations. Ensures O(1) lookup.
            "CREATE CONSTRAINT property_id_unique IF NOT EXISTS FOR (p:Property) REQUIRE p.id IS UNIQUE",

            // User Identity Constraint
            // Essential for Collaborative Filtering to link bookings to unique users.
            "CREATE CONSTRAINT user_id_unique IF NOT EXISTS FOR (u:User) REQUIRE u.id IS UNIQUE",

            // Amenity Identity Constraint
            // Ensures shared features are unique nodes for traversal hops.
            "CREATE CONSTRAINT amenity_name_unique IF NOT EXISTS FOR (a:Amenity) REQUIRE a.name IS UNIQUE"
        );

        // Execute queries sequentially
        startupQueries.forEach(query -> {
            try {
                neo4jClient.query(query).run();
                System.out.println("Neo4j Config Applied: " + query);
            } catch (Exception e) {
                System.err.println("Failed to apply Neo4j config: " + query + " | Error: " + e.getMessage());
            }
        });
    }
}