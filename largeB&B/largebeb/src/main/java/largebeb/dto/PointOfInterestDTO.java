package largebeb.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PointOfInterestDTO {
    private String id;
    private String name;
    private String category;
    // [longitude, latitude]
    private List<Double> coordinates;
}
