package largebeb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class ReviewRequestDTO {

    // Mandatory to link the review
    private String reservationId;

    private String text;

    // Ratings are not @NotNull here to allow Manager to reply without sending ratings back.
    // However, they will be checked programmatically during CREATION.
    @Min(1) @Max(5)
    private Double rating; // Overall

    @Min(1) @Max(5)
    private Double cleanliness;
    
    @Min(1) @Max(5)
    private Double communication;
    
    @Min(1) @Max(5)
    private Double location;
    
    @Min(1) @Max(5)
    private Double value;

    // Manager can set this field alone in an update
    private String managerReply;
}