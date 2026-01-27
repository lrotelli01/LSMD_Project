package largebeb.repository.UserGraphRepository;

import largebeb.model.graph.UserNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserGraphRepository extends Neo4jRepository<UserNode, String> {
    
}