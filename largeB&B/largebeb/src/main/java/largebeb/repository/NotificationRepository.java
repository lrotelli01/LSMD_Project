package largebeb.repository;

import largebeb.model.Notification;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface NotificationRepository extends MongoRepository<Notification, String> {
    
    // Find all notifications for a specific user ordered by newest first
    List<Notification> findByRecipientIdOrderByTimestampDesc(String recipientId);
    
    // Count unread notifications (for the UI badge in front-end)
    long countByRecipientIdAndReadFalse(String recipientId);
}