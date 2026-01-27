package largebeb.repository;

import largebeb.model.Reservation;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Repository
public interface ReservationRepository extends MongoRepository<Reservation, String> {

    // Check if a user has active bookings (not cancelled and check-out date in the future)
    @Query(value = "{ 'userId': ?0, 'status': 'confirmed', 'dates.checkOut': { $gt: ?1 } }", exists = true)
    boolean hasActiveBookings(String userId, LocalDate today);

    List<Reservation> findByUserId(String userId);

    List<Reservation> findByRoomId(String roomId);

    // Finds reservations matching a list of Room IDs
    List<Reservation> findByRoomIdIn(List<String> roomIds);
    
    // Find a reservation by ID
    Optional<Reservation> findById(String id);

    /*
     Finds reservations that overlap with a requested date range,
     used to check if a room is available
    */
    @Query("{ 'roomId': ?0, 'status': { $ne: 'cancelled' }, 'dates.checkIn': { $lt: ?2 }, 'dates.checkOut': { $gt: ?1 } }")
    List<Reservation> findOverlappingReservations(String roomId, LocalDate newCheckIn, LocalDate newCheckOut);

    // ==================== AGGREGATION QUERIES ====================

    // Get reservation statistics by status for multiple rooms within a date range
    @Aggregation(pipeline = {
        "{ '$match': { 'roomId': { $in: ?0 }, 'dates.checkIn': { $gte: ?1, $lte: ?2 } } }",
        "{ '$group': { '_id': null, 'totalReservations': { $sum: 1 }, 'confirmed': { $sum: { $cond: [{ $eq: ['$status', 'CONFIRMED'] }, 1, 0] } }, 'cancelled': { $sum: { $cond: [{ $eq: ['$status', 'CANCELLED'] }, 1, 0] } }, 'completed': { $sum: { $cond: [{ $eq: ['$status', 'COMPLETED'] }, 1, 0] } }, 'totalAdults': { $sum: '$adults' }, 'totalChildren': { $sum: '$children' } } }",
        "{ '$project': { '_id': 0 } }"
    })
    Map<String, Object> getReservationStatistics(List<String> roomIds, LocalDate startDate, LocalDate endDate);

    // Get monthly revenue aggregation for a list of rooms
    @Aggregation(pipeline = {
        "{ '$match': { 'roomId': { $in: ?0 }, 'status': { $ne: 'CANCELLED' }, 'dates.checkIn': { $gte: ?1, $lte: ?2 } } }",
        "{ '$addFields': { 'yearMonth': { $dateToString: { format: '%Y-%m', date: '$dates.checkIn' } } } }",
        "{ '$group': { '_id': '$yearMonth', 'revenue': { $sum: '$totalPrice' }, 'count': { $sum: 1 } } }",
        "{ '$sort': { '_id': 1 } }",
        "{ '$project': { 'month': '$_id', 'revenue': 1, 'count': 1, '_id': 0 } }"
    })
    List<Map<String, Object>> getMonthlyRevenueByRooms(List<String> roomIds, LocalDate startDate, LocalDate endDate);

    // Get total revenue for rooms within date range
    @Aggregation(pipeline = {
        "{ '$match': { 'roomId': { $in: ?0 }, 'status': { $ne: 'CANCELLED' }, 'dates.checkIn': { $gte: ?1, $lte: ?2 } } }",
        "{ '$group': { '_id': null, 'totalRevenue': { $sum: '$totalPrice' } } }",
        "{ '$project': { '_id': 0, 'totalRevenue': 1 } }"
    })
    Map<String, Object> getTotalRevenue(List<String> roomIds, LocalDate startDate, LocalDate endDate);

    // Get reservations grouped by day of week (booking patterns)
    @Aggregation(pipeline = {
        "{ '$match': { 'roomId': { $in: ?0 } } }",
        "{ '$addFields': { 'dayOfWeek': { $dayOfWeek: '$dates.checkIn' } } }",
        "{ '$group': { '_id': '$dayOfWeek', 'count': { $sum: 1 } } }",
        "{ '$sort': { '_id': 1 } }",
        "{ '$project': { 'dayOfWeek': '$_id', 'count': 1, '_id': 0 } }"
    })
    List<Map<String, Object>> getBookingPatternsByDayOfWeek(List<String> roomIds);

    // ==================== UPDATE QUERIES ====================

    // Update reservation status
    @Query("{ '_id': ?0 }")
    @Update("{ '$set': { 'status': ?1 } }")
    void updateStatus(String reservationId, String status);

    // Cancel reservation (set status to CANCELLED)
    @Query("{ '_id': ?0, 'userId': ?1 }")
    @Update("{ '$set': { 'status': 'CANCELLED' } }")
    long cancelReservation(String reservationId, String userId);
}