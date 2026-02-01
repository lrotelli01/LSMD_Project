package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PointOfInterestDTO {
    private String id;
    private String name;
    private String category;
    // Coordinate semplici [longitude, latitude]
    private List<Double> coordinates;
}
