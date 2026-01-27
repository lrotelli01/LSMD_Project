package largebeb.repository.PropertyGraphRepository;

import largebeb.model.graph.PropertyNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PropertyGraphRepository extends Neo4jRepository<PropertyNode, String> {
}