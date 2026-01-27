package largebeb.services;

import largebeb.dto.MessageRequestDTO;
import largebeb.model.Message;
import largebeb.model.RegisteredUser;
import largebeb.repository.MessageRepository;
import largebeb.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // Sends a new message
    @Transactional
    public Message sendMessage(String senderId, MessageRequestDTO request) {
        // Content cannot be empty
        if (request.getContent() == null || request.getContent().trim().isEmpty()) {
            throw new IllegalArgumentException("Message content cannot be empty.");
        }

        // Fetch Sender from token ID
        RegisteredUser sender = userRepository.findById(senderId)
                .orElseThrow(() -> new IllegalArgumentException("Sender not found with ID: " + senderId));

        // Fetch Recipient from ID in requestDTO
        RegisteredUser recipient = userRepository.findById(request.getRecipientId())
                .orElseThrow(() -> new IllegalArgumentException("Recipient not found with ID: " + request.getRecipientId()));

        // Security Check (Business Rules)
        validateCommunicationRules(sender, recipient);

        // Save the message using unique User IDs 
        Message message = Message.builder()
                .senderId(sender.getId())       
                .recipientId(recipient.getId())
                .content(request.getContent())
                .timestamp(LocalDateTime.now())
                .isRead(false)
                .build();

        // Capture the saved message object (so we have the generated ID)
        Message savedMessage = messageRepository.save(message);

        // Trigger the notification
        notificationService.sendNewMessageNotification(
            sender.getId(),    // The user who sent the message
            recipient.getId(), // The user who will receive the notification
            savedMessage.getId()      // The ID connecting the notification to this message
        );

        return savedMessage;
    }

    // Retrieves the conversation history between the current user and another user
    public List<Message> getConversation(String currentUserId, String otherUserId) {
        return messageRepository.findConversation(currentUserId, otherUserId);
    }

    // Marks messages as read
    @Transactional
    public void markMessagesAsRead(String recipientId, String senderId) {
        // Fetch unread messages sent by senderId to recipientId
        List<Message> unreadMessages = messageRepository.
        findByRecipientIdAndSenderIdAndIsReadFalse(recipientId, senderId);

        // Update and Save
        if (!unreadMessages.isEmpty()) {
            unreadMessages.forEach(message -> message.setIsRead(true));
            messageRepository.saveAll(unreadMessages);
        }
    }

    private void validateCommunicationRules(RegisteredUser sender, RegisteredUser recipient) {
        String senderRole = sender.getRole();
        String recipientRole = recipient.getRole();

        // Prevent self-messaging
        if (sender.getId().equals(recipient.getId())) {
            throw new IllegalArgumentException("You cannot send messages to yourself.");
        }

        // Customers only to Managers, Managers only to Customers
        if ("CUSTOMER".equalsIgnoreCase(senderRole)) {
            if (!"MANAGER".equalsIgnoreCase(recipientRole)) {
                throw new IllegalArgumentException("Customers can only send messages to the Manager.");
            }
        } else if ("MANAGER".equalsIgnoreCase(senderRole)) {
            if (!"CUSTOMER".equalsIgnoreCase(recipientRole)) {
                throw new IllegalArgumentException("Managers can only send messages to Customers.");
            }
        } else {
            throw new IllegalArgumentException("Unauthorized role for messaging.");
        }
    }
}