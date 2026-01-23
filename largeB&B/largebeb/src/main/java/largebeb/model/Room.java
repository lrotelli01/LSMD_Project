package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import org.springframework.data.mongodb.core.mapping.Field;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Field("roomId")
    private String id;

    // Links this room to a specific Property (B&B/Hotel)
    private String propertyId; 
    
    private String roomType; // e.g., "Single", "Double", "Suite"
    
    private String name;     // e.g., "Blue Room with Sea View"
    
    // number of beds
    private short numBeds; 
    
    private List<String> amenities;
    private List<String> photos;
    
    // Room status ("available", "maintenance", etc.)
    private String status; 
    
    // Capacity
    private Long capacityAdults;
    private Long capacityChildren;
    
    // Pricing
    private Float pricePerNightAdults;
    private Float pricePerNightChildren;
}