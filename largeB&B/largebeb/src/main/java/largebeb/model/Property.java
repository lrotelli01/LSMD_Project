package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexType;
import org.springframework.data.mongodb.core.index.GeoSpatialIndexed;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import largebeb.utilities.RatingStats;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "properties")
// COMPOUND INDEXES (For complex multi-field filtering)
@CompoundIndexes({
    // Location Hierarchy: Optimizes drill-down "City -> Region -> Country"
    @CompoundIndex(name = "location_idx", def = "{'city': 1, 'region': 1, 'country': 1}"),

    // Room Capacity: Optimizes "Find property with room for X adults & Y children"
    // Indexes fields inside the embedded 'rooms' list.
    @CompoundIndex(name = "room_capacity_idx", def = "{'rooms.capacityAdults': 1, 'rooms.capacityChildren': 1}"),

    // Rating Sort: Optimizes "Sort by Highest Rated" (-1 = Descending)
    @CompoundIndex(name = "rating_sort_idx", def = "{'ratingStats.value': -1}"),

    // Price Range: Optimizes "Find property with room price under X for adults & Y for children"
    @CompoundIndex(
        name = "idx_rooms_price_adults_children", 
        def = "{'rooms.pricePerNightAdults': 1, 'rooms.pricePerNightChildren': 1}"
    )
})
public class Property {

    @Id
    private String id;

    // TEXT SEARCH INDEXES
    // Weight = 2 means matches in 'name' rank higher than matches in 'description'
    @TextIndexed(weight = 3)
    private String name;

    private String address;

    @TextIndexed
    private String description;

    // Property features filter
    private List<String> amenities;
    
    private List<String> photos; 

    private String email;
    private String country;
    private String region;
    
    // Included in Text Index for generic searches (e.g., "Hotels in Rome")
    @TextIndexed
    private String city;

    // MANAGER DASHBOARD INDEX
    // Fast lookup for "My Properties" view
    @Indexed
    private String managerId;

    // GEOSPATIAL INDEX
    // Required for $near, $nearSphere, and Map views
    @GeoSpatialIndexed(type = GeoSpatialIndexType.GEO_2DSPHERE)
    private List<Double> coordinates;

    private List<Room> rooms;

    private List<Review> latestReviews;
    private List<PointOfInterest> pois;
    
    private RatingStats ratingStats;
}