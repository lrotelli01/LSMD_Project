package largebeb.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoomRequestDTO {
    
    @NotBlank(message = "Room name is required")
    private String name;
    
    @NotBlank(message = "Room type is required")
    private String roomType; // e.g., "Single", "Double", "Suite"
    
    @NotNull(message = "Number of beds is required")
    @Positive(message = "Number of beds must be positive")
    private Short numBeds;
    
    private List<String> amenities;
    private List<String> photos;
    
    private String status; // "available", "maintenance"
    
    @NotNull(message = "Adult capacity is required")
    @Positive(message = "Adult capacity must be positive")
    private Long capacityAdults;
    
    @NotNull(message = "Children capacity is required")
    private Long capacityChildren;
    
    @NotNull(message = "Price per night for adults is required")
    @Positive(message = "Price must be positive")
    private Float pricePerNightAdults;
    
    @NotNull(message = "Price per night for children is required")
    private Float pricePerNightChildren;
}
