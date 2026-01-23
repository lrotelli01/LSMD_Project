package largebeb.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@Document(collection = "notifications")
public class Notification {

    @Id
    private String id;

    private String recipientId; // The user who receives the notification
    private String title;             // e.g., "New Booking"
    private String body;              // e.g., "Customer X created a reservation"
    private String type;              // e.g., "MESSAGE", "RESERVATION_CREATED", "RESERVATION_CANCELLED", "RESERVATION_MODIFIED"
    private String referenceId;       // ID of the related Message or Reservation
    
    private boolean read = false;     // Read status (false = unread)
    private LocalDateTime timestamp = LocalDateTime.now();

    public Notification(String recipientId, String title, String body, String type, String referenceId) {
        this.recipientId = recipientId;
        this.title = title;
        this.body = body;
        this.type = type;
        this.referenceId = referenceId;
    }
}