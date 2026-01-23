package largebeb.utilities;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RatingStats {

    // Main Score (e.g., 4.85)
    @Min(1)
    @Max(5)
    private Double averageRating;

    // Total number of reviews received (e.g., 150)
    private Long totalReviews;

    @Min(1)
    @Max(5)
    private Double cleanliness;   // Cleanliness
    @Min(1)
    @Max(5)
    private Double communication; // Communication with manager
    @Min(1)
    @Max(5)
    private Double location;      // Location
    @Min(1)
    @Max(5)
    private Double value;         // Value (Value for money)
}