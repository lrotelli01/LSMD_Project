package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class PropertyResponseDTO {
    private String id;
    private String name;
    private String description;
    private String city;
    private String region;
    private String country;
    
    // Info calcolata: prezzo minimo (es. "A partire da 80€")
    private Double pricePerNight; 
    
    private Double rating;
    private List<String> amenities;
    private List<String> photos;
    
    // POI con coordinate semplici
    private List<PointOfInterestDTO> pois; 
    
    // Per la mappa - coordinate semplici [longitude, latitude]
    private List<Double> coordinates; 
}