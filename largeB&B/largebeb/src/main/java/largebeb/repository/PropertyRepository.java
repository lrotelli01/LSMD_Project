package largebeb.repository;

import largebeb.model.Property;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends MongoRepository<Property, String> {

    Optional<Property> findByName(String name);

    // Find a Property that contains a Room with the specified Room ID
    @Query("{ 'rooms.roomId': ?0 }")
    Optional<Property> findByRoomsId(String roomId);

    // Find properties that contain at least one room with this status
    List<Property> findByRoomsStatus(String status);

    // Find properties that contain at least one room with this type
    List<Property> findByRoomsRoomType(String roomType);

    // Find a Property by its ID, ensuring it contains a room with the specific name
    @Query("{ '_id': ?0, 'rooms.name': ?1 }")
    Optional<Property> findByIdAndRoomName(String propertyId, String roomName);

    // Find all properties owned by a specific manager
    List<Property> findByManagerId(String managerId);
}