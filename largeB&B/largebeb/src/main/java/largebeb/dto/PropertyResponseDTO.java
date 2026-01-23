package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import largebeb.model.PointOfInterest;
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
    
    // I tuoi requisiti specifici
    private List<PointOfInterest> pois; 
    
    // Per la mappa
    private List<Double> coordinates; 
}