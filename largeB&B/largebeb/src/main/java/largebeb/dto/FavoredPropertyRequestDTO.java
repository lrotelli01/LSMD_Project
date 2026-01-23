package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoredPropertyRequestDTO {
    // The ID of the property the user wants to save as favored
    @NotBlank(message = "Property ID is mandatory")
    private String propertyId;
}