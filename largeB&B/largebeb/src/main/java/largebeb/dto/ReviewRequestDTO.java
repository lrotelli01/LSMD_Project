package largebeb.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReviewRequestDTO {

    // Mandatory to link the review
    private String reservationId;

    private String text;

    @NotNull
    @Min(1) @Max(5)
    private Double rating; // Overall

    @NotNull
    @Min(1) @Max(5)
    private Double cleanliness;
    
    @NotNull
    @Min(1) @Max(5)
    private Double communication;
    
    @NotNull
    @Min(1) @Max(5)
    private Double location;
    
    @NotNull
    @Min(1) @Max(5)
    // Value for money
    private Double value;

    // Manager can set this field alone in an update
    private String managerReply;
}