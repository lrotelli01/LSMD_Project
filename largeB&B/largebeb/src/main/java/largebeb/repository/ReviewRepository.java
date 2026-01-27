package largebeb.repository;

import largebeb.model.Review;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    // ==================== AGGREGATION QUERIES ====================

    // Calculate average rating and statistics for a property
    @Aggregation(pipeline = {
        "{ '$match': { 'propertyId': ?0 } }",
        "{ '$group': { '_id': null, 'averageRating': { $avg: '$rating' }, 'totalReviews': { $sum: 1 }, 'minRating': { $min: '$rating' }, 'maxRating': { $max: '$rating' }, 'variance': { $stdDevPop: '$rating' } } }",
        "{ '$project': { '_id': 0 } }"
    })
    Map<String, Object> getRatingStatistics(String propertyId);

    // Calculate monthly average ratings for rating evolution analysis
    @Aggregation(pipeline = {
        "{ '$match': { 'propertyId': ?0, 'creationDate': { $gte: ?1, $lte: ?2 } } }",
        "{ '$addFields': { 'yearMonth': { $dateToString: { format: '%Y-%m', date: '$creationDate' } } } }",
        "{ '$group': { '_id': '$yearMonth', 'averageRating': { $avg: '$rating' }, 'count': { $sum: 1 } } }",
        "{ '$sort': { '_id': 1 } }",
        "{ '$project': { 'month': '$_id', 'averageRating': 1, 'count': 1, '_id': 0 } }"
    })
    List<Map<String, Object>> getMonthlyRatingEvolution(String propertyId, LocalDate startDate, LocalDate endDate);

    // Get rating distribution (count per rating value)
    @Aggregation(pipeline = {
        "{ '$match': { 'propertyId': ?0 } }",
        "{ '$group': { '_id': '$rating', 'count': { $sum: 1 } } }",
        "{ '$sort': { '_id': 1 } }",
        "{ '$project': { 'rating': '$_id', 'count': 1, '_id': 0 } }"
    })
    List<Map<String, Object>> getRatingDistribution(String propertyId);

    // Calculate aggregate statistics for multiple properties (portfolio overview)
    @Aggregation(pipeline = {
        "{ '$match': { 'propertyId': { $in: ?0 } } }",
        "{ '$group': { '_id': null, 'overallAverageRating': { $avg: '$rating' }, 'totalReviews': { $sum: 1 }, 'excellentCount': { $sum: { $cond: [{ $gte: ['$rating', 4.5] }, 1, 0] } }, 'goodCount': { $sum: { $cond: [{ $and: [{ $gte: ['$rating', 3.5] }, { $lt: ['$rating', 4.5] }] }, 1, 0] } }, 'poorCount': { $sum: { $cond: [{ $lt: ['$rating', 3.5] }, 1, 0] } } } }",
        "{ '$project': { '_id': 0 } }"
    })
    Map<String, Object> getPortfolioRatingStatistics(List<String> propertyIds);

    // Get average rating per property for comparative analysis
    @Aggregation(pipeline = {
        "{ '$match': { 'propertyId': { $in: ?0 } } }",
        "{ '$group': { '_id': '$propertyId', 'averageRating': { $avg: '$rating' }, 'reviewCount': { $sum: 1 } } }",
        "{ '$sort': { 'averageRating': -1 } }",
        "{ '$project': { 'propertyId': '$_id', 'averageRating': 1, 'reviewCount': 1, '_id': 0 } }"
    })
    List<Map<String, Object>> getPropertyRatingsComparison(List<String> propertyIds);

    // ==================== UPDATE QUERIES ====================

    // Update reviewer username (for username change propagation)
    @Query("{ 'userId': ?0 }")
    @Update("{ '$set': { 'reviewerUsername': ?1 } }")
    void updateReviewerUsername(String userId, String newUsername);
}