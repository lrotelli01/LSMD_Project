package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDate;

@Data
@AllArgsConstructor
public class ReviewResponseDTO {
    private String id;
    private String reservationId;
    private String userId;
    private LocalDate creationDate;
    private String text;
    private Long rating;
    private Double cleanliness;
    private Double communication;
    private Double location;
    private Double value;
    private String managerReply;
}