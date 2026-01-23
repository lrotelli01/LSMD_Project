package largebeb.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class PropertyRequestDTO {
    
    @NotBlank(message = "Property name is required")
    private String name;
    
    private String address;
    private String description;
    
    @NotBlank(message = "City is required")
    private String city;
    
    private String region;
    
    @NotBlank(message = "Country is required")
    private String country;
    
    private String email;
    
    private List<String> amenities;
    private List<String> photos;
    
    // Coordinates: [latitude, longitude]
    private List<Double> coordinates;
}
