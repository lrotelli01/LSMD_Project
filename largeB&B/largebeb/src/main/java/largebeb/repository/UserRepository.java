package largebeb.repository;

import largebeb.model.RegisteredUser;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<RegisteredUser, String> {

    // Finds a user by their email
    Optional<RegisteredUser> findByEmail(String email);

    // Deletes a user directly by their email
    void deleteByEmail(String email);

    // Deletes a user by their RegisteredUser object
    void delete(RegisteredUser user);

    // Find user by username
    Optional<RegisteredUser> findByUsername(String username);
}