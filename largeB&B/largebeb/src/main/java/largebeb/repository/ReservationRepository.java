package largebeb.repository;

import largebeb.model.Reservation;
// import largebeb.model.ReservationStatus; // Optional if you use strings in custom queries
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.time.LocalDate;
import java.util.List;

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
}