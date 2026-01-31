package largebeb.model.graph;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;

@Node("PropertyGraph") // Etichetta modificata per evitare conflitti con MongoDB
@Data                 // Genera automaticamente getPropertyId()
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PropertyNode {

    @Id
    private String propertyId; // Questo deve corrispondere al metodo cercato dal repository
}