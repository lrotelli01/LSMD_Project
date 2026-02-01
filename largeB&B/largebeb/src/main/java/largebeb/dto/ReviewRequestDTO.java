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
    private Double rating; // Overall

    @NotNull
    private Double cleanliness;
    
    @NotNull
    private Double communication;
    
    @NotNull
    private Double location;
    
    @NotNull
    // Value for money
    private Double value;

    // Manager can set this field alone in an update
    private String managerReply;
}