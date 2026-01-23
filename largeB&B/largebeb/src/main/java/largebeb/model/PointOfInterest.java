package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PointOfInterest {
    private String name;
    private String category; // Museums, Parks, etc.
    private List<Double> coordinates; // [Longitude, Latitude]
}