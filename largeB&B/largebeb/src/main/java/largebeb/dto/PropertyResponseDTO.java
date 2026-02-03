package largebeb.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import largebeb.model.Review;

@Data
@Builder
public class PropertyResponseDTO {
    private String id;
    private String name;
    private String description;
    private String city;
    private String region;
    private String country;
    
    // Calculated info: minimum price (e.g., "Starting from 80€")
    private Double pricePerNight; 
    
    private Double rating;
    private List<String> amenities;
    private List<String> photos;
    
    // POI with simple coordinates
    private List<PointOfInterestDTO> pois; 
    
    // For the map - simple coordinates [longitude, latitude]
    private List<Double> coordinates;
    
    // Latest 10 reviews for this property (retrieved from the property document)
    private List<Review> latestReviews;
}