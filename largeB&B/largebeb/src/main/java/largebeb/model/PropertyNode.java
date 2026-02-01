package largebeb.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("PropertyGraph") // Label modified to avoid conflicts with MongoDB
@Data                 // Automatically generates getPropertyId()
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyNode {

    @Id
    private String propertyId; // This must match the method searched by the repository
}