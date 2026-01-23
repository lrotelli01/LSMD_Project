package largebeb.repository;

import largebeb.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

import java.util.Optional;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    
    // Finds a specific review associated with a reservation ID
    Optional<Review> findByReservationId(String reservationId);
    
    // Finds all reviews associated to a property
    List<Review> findByPropertyId(String propertyId);
}