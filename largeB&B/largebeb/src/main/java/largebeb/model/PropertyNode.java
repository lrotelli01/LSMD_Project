package largebeb.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Property;

@Node("Property")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyNode {

    // The Primary Key for the Neo4j Node.
    @Id
    private String propertyId; // This holds the MongoDB "_id"
}