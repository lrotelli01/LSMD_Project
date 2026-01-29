package largebeb.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import largebeb.dto.MessageRequestDTO;
import largebeb.dto.MessageResponseDTO;
import largebeb.model.Message;
import largebeb.services.MessageService;
import largebeb.utilities.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messaging", description = "Real-time messaging between customers and property managers")
public class MessageController {

    private final MessageService messageService;
    private final JwtUtil jwtUtil;

    @PostMapping("/send")
    public ResponseEntity<?> sendMessage(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MessageRequestDTO request) {

        String senderId = extractUserIdFromToken(token);
        try {
            Message savedMessage = messageService.sendMessage(senderId, request);
            return ResponseEntity.ok(mapToDTO(savedMessage));
        } catch (RuntimeException e) {
            // Handle specific exceptions
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while fetching messages");
        }
    }

    @GetMapping("/conversation/{otherUserId}")
    public ResponseEntity<?> getConversation(
            @RequestHeader("Authorization") String token,
            @PathVariable String otherUserId) {

        // Identify the current user
        String currentUserId = extractUserIdFromToken(token);

        try {
            // Mark messages as read before fetching the history.
            // "currentUserId" is the recipient, "otherUserId" is the sender.
            messageService.markMessagesAsRead(currentUserId, otherUserId);

            // Fetch the updated conversation history
            List<Message> history = messageService.getConversation(currentUserId, otherUserId);

            // Convert to DTOs
            List<MessageResponseDTO> response = history.stream()
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());

            return ResponseEntity.ok(response);

        } catch (RuntimeException e) {
            // Handle specific exceptions
            return ResponseEntity.status(404).body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(500).body("An error occurred while fetching messages");
        }
    }

    // Helper Methods
    private String extractUserIdFromToken(String token) {
        if (token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        // Get id from token
        return jwtUtil.getUserIdFromToken(token); 
    }

    private MessageResponseDTO mapToDTO(Message message) {
        return MessageResponseDTO.builder()
                .id(message.getId())
                .senderId(message.getSenderId())
                .recipientId(message.getRecipientId())
                .content(message.getContent())
                .timestamp(message.getTimestamp())
                .isRead(message.getIsRead())
                .build();
    }
}