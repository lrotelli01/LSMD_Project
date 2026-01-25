package largebeb.repository;

import largebeb.model.Review;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReviewRepository extends MongoRepository<Review, String> {
    
    // Finds a specific review associated with a reservation ID
    Optional<Review> findByReservationId(String reservationId);
    
    // Finds all reviews associated to a property
    List<Review> findByPropertyId(String propertyId);
    
    // Finds reviews by property within a date range (for rating evolution)
    @Query("{ 'propertyId': ?0, 'creationDate': { $gte: ?1, $lte: ?2 } }")
    List<Review> findByPropertyIdAndCreationDateBetween(String propertyId, LocalDate startDate, LocalDate endDate);
    
    // Finds all reviews for multiple properties (for comparative analysis)
    List<Review> findByPropertyIdIn(List<String> propertyIds);
    
    // Finds reviews ordered by creation date
    List<Review> findByPropertyIdOrderByCreationDateAsc(String propertyId);
}