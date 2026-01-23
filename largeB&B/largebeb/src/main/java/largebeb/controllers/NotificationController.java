package largebeb.controllers;

import largebeb.dto.NotificationResponseDTO;
import largebeb.services.NotificationService;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final JwtUtil jwtUtil;

    // Retrieve my notifications
    @GetMapping
    public ResponseEntity<List<NotificationResponseDTO>> getMyNotifications(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(notificationService.getUserNotifications(token));
    }

    // Get count of unread notifications (for the frontend badge)
    @GetMapping("/unread-count")
    public ResponseEntity<Long> getUnreadCount(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(notificationService.countUnread(token));
    }

    // Mark a notification as read (when user clicks it)
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<Void> markAsRead(
            @RequestHeader("Authorization") String token,
            @PathVariable String notificationId) {
        
        notificationService.markAsRead(token, notificationId);
        return ResponseEntity.ok().build();
    }

    // Mark all notifications as read
    @PatchMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead(
            @RequestHeader("Authorization") String token) {
        
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        notificationService.markAllAsRead(userId);
        
        return ResponseEntity.ok().build();
    }
}