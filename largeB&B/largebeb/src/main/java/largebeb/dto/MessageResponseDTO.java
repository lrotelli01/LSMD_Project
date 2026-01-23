package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {
    private String id;
    private String senderId;
    private String recipientId;
    private String content;
    private LocalDateTime timestamp;
    private Boolean isRead;
}