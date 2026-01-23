package largebeb.repository;

import largebeb.model.Message;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MessageRepository extends MongoRepository<Message, String> {

    /* Finds the conversation history between two users, regardless of who sent the message.
     Sorts the results by timestamp (oldest to newest).
    */
    @Query(value = "{$or: [ { 'senderId': ?0, 'recipientId': ?1 }, { 'senderId': ?1, 'recipientId': ?0 } ]}", sort = "{ 'timestamp' : 1 }")
    List<Message> findConversation(String user1, String user2);

    // Finds all unread messages for a specific user.
    List<Message> findByRecipientIdAndSenderIdAndIsReadFalse(String recipientId, String senderId);
}
