package largebeb.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserGraphRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Creates or updates a User node in Neo4j
     */
    public void createOrUpdateUser(String username, String mongoId) {
        String cypher = """
            MERGE (u:User {username: $username})
            SET u.mongoId = $mongoId
            """;
        
        neo4jClient.query(cypher)
                .bind(username).to("username")
                .bind(mongoId).to("mongoId")
                .run();
    }

    /**
     * Deletes a User node and all its relationships from Neo4j
     */
    public void deleteById(String userId) {
        String cypher = """
            MATCH (u:User {mongoId: $mongoId})
            DETACH DELETE u
            """;
        
        neo4jClient.query(cypher)
                .bind(userId).to("mongoId")
                .run();
    }
    
    /**
     * Saves a UserNode to Neo4j
     */
    public void save(largebeb.model.graph.UserNode userNode) {
        createOrUpdateUser(userNode.getUsername(), userNode.getMongoId());
    }
}
