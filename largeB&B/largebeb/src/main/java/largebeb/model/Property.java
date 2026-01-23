package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import largebeb.utilities.RatingStats;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "properties")
public class Property {

    @Id
    private String id; // Maps to MongoDB "_id"

    private String name;
    private String address;
    private String description;

    // List of strings (e.g., "WiFi", "Pool", "AC")
    private List<String> amenities;
    
    // List of Image URLs
    private List<String> photos; 

    private String email;
    private String country;
    private String region;
    private String city;

    private String managerId;

    // Array(2): [Longitude, Latitude]
    // MongoDB expects [x, y], so Longitude first
    private List<Double> coordinates;

    private List<Room> rooms;

    // Contains the latest 10 reviews
    private List<Review> latestReviews;
    private List<PointOfInterest> pois;
    // General ratings for this property
    private RatingStats ratingStats;
}