package largebeb.services;

import largebeb.dto.NotificationResponseDTO;
import largebeb.model.Notification;
import largebeb.repository.NotificationRepository;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import largebeb.repository.UserRepository;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final UserRepository userRepository;
    private final NotificationRepository notificationRepository;
    private final JwtUtil jwtUtil;

    // Send a notification of a received message (to the manager or customer)
    public void sendNewMessageNotification(String senderId, String recipientId, String messageId) {
        String title = "New Message";
        // Get the sender's username for the notification body
        String senderUsername = userRepository.findById(senderId)
                .map(u -> u.getUsername())
                .orElse("Someone");
        String body = "User " + senderUsername + " wrote to you"; 
        
        saveNotification(recipientId, title, body, "MESSAGE", messageId);
    }

    // Send a notification about a reservation event to the manager
    
    // Customer creates a booking
    public void notifyManagerOfNewBooking(String managerId, String customerId, String reservationId) {
        String title = "New Booking made";
        // Get the sender's username for the notification body
        String customerUsername = userRepository.findById(customerId)
                .map(u -> u.getUsername())
                .orElse("Someone");
        String body = "Customer " + customerUsername + " has made a new reservation.";

        saveNotification(managerId, title, body, "RESERVATION_CREATED", reservationId);
    }

    // Customer cancels a booking 
    public void notifyManagerOfCancellation(String managerId, String customerId, String reservationId) {
        String title = "Booking Cancelled";
        // Get the sender's username for the notification body
        String customerUsername = userRepository.findById(customerId)
                .map(u -> u.getUsername())
                .orElse("Someone");
        String body = "Customer " + customerUsername + " has cancelled their reservation.";
        
        saveNotification(managerId, title, body, "RESERVATION_CANCELLED", reservationId);
    }

    // Customer modifies a booking
    public void notifyManagerOfModification(String managerId, String customerId, String reservationId) {
        String title = "Booking Modified";
        // Get the sender's username for the notification body
        String customerUsername = userRepository.findById(customerId)
                .map(u -> u.getUsername())
                .orElse("Someone");
        String body = "Customer " + customerUsername + " has modified their reservation details.";
        
        saveNotification(managerId, title, body, "RESERVATION_MODIFIED", reservationId);
    }

    // Marks all notifications as read for a specific user ID
    public void markAllAsRead(String userId) {
        // Fetch all notifications for the user
        List<Notification> userNotifications = notificationRepository.findByRecipientIdOrderByTimestampDesc(userId);
        
        // Filter only the ones that are currently unread
        List<Notification> unreadNotifications = userNotifications.stream()
                .filter(n -> !n.isRead())
                .collect(Collectors.toList());

        // Update and save if there are any unread ones
        if (!unreadNotifications.isEmpty()) {
            unreadNotifications.forEach(n -> n.setRead(true));
            notificationRepository.saveAll(unreadNotifications);
        }
    }

    // Helper Method 
    private void saveNotification(String recipientId, String title, String body, String type, String refId) {
        if (recipientId == null) return; // Basic safety check
        Notification notification = new Notification(recipientId, title, body, type, refId);
        notificationRepository.save(notification);
    }

    // Retrieval Methods
    public List<NotificationResponseDTO> getUserNotifications(String token) {
        String userId = getUserIdFromToken(token);
        return notificationRepository.findByRecipientIdOrderByTimestampDesc(userId)
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public void markAsRead(String token, String notificationId) {
        String userId = getUserIdFromToken(token);
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found"));

        // Ensure the user owns this notification
        if (!notification.getRecipientId().equals(userId)) {
             throw new SecurityException("You are not authorized to access this notification.");
        }

        notification.setRead(true);
        notificationRepository.save(notification);
    }

    public long countUnread(String token) {
        String userId = getUserIdFromToken(token);
        return notificationRepository.countByRecipientIdAndReadFalse(userId);
    }

    private NotificationResponseDTO mapToDTO(Notification n) {
        return new NotificationResponseDTO(
                n.getId(), n.getTitle(), n.getBody(), n.getType(), 
                n.getReferenceId(), n.isRead(), n.getTimestamp()
        );
    }

    private String getUserIdFromToken(String token) {
        return jwtUtil.getUserIdFromToken(token.replace("Bearer ", ""));
    }
}