package largebeb.repository;

import largebeb.model.Property;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends MongoRepository<Property, String> {

    // Find property by exact name
    Optional<Property> findByName(String name);

    // Find the property containing a specific room
    @Query("{ 'rooms.roomId': ?0 }")
    Optional<Property> findByRoomsId(String roomId);

    // Find properties with rooms having a specific status
    List<Property> findByRoomsStatus(String status);

    // Find specific property with a specific room
    @Query("{ '_id': ?0, 'rooms.name': ?1 }")
    Optional<Property> findByIdAndRoomName(String propertyId, String roomName);

    // Find all properties of a Manager
    List<Property> findByManagerId(String managerId);

    // NOTE: Complex queries (Search, GeoSpatial) have been moved 
    // to PropertyService using MongoTemplate to avoid syntax errors.
}