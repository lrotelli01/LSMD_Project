package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;
import org.springframework.data.mongodb.core.index.TextIndexed;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Room {

    @Field("roomId")
    private String id;

    // Useful for internal reverse lookups
    @Indexed 
    private String propertyId; 
    
    @Indexed
    private String roomType; 
    @TextIndexed(weight = 2)
    private String name;  
    @Indexed   
    @Field("beds")
    private short numBeds; 
    
    // Specific filter for room features (e.g., "En-suite bathroom")
    private List<String> amenities;

    private List<String> photos;
    
    private String status; 
    
    // (Capacity is handled by the @CompoundIndex in Property.java)
    private Long capacityAdults;
    private Long capacityChildren;
    
    // PRICE FILTER
    // Fast Range queries (e.g., "AdultsPrice < 100 && ChildrenPrice < 50")
    private Float pricePerNightAdults;
    private Float pricePerNightChildren;
}