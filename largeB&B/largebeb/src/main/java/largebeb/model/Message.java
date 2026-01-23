package largebeb.model;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    // IDs are useful for linking to the User collection
    private String senderId;
    private String recipientId;

    private String content;
    private LocalDateTime timestamp;
    
    // Default to false
    @Builder.Default
    private Boolean isRead = false;
}