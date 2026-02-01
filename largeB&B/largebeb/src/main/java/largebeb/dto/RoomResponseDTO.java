package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class RoomResponseDTO {
    private String id;
    private String propertyId;
    private String propertyName;  // Property name
    private String propertyCity;  // Property city
    private String name;
    private String roomType;
    private Short numBeds;
    private List<String> amenities;
    private List<String> photos;
    private String status;
    private Long capacityAdults;
    private Long capacityChildren;
    private Float pricePerNightAdults;
    private Float pricePerNightChildren;
}
