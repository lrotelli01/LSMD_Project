package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.geo.GeoJsonPoint;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointOfInterest {
    @Field("id")
    private String id;
    private String name;
    private String category; // Museums, Parks, historical, restaurant, bar, etc.
    // GeoJSON format: { "type": "Point", "coordinates": [lon, lat] }
    private GeoJsonPoint location;
}