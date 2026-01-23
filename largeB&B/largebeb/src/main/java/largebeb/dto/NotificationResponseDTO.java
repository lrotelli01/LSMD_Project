package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class NotificationResponseDTO {
    private String id;
    private String title;
    private String body;
    private String type;
    private String referenceId;
    private boolean read;
    private LocalDateTime timestamp;
}