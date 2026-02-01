package largebeb.repository;

import largebeb.model.Property;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface PropertyRepository extends MongoRepository<Property, String> {

    // Trova proprietà per nome esatto
    Optional<Property> findByName(String name);

    // Trova la proprietà che contiene una certa stanza
    @Query("{ 'rooms.roomId': ?0 }")
    Optional<Property> findByRoomsId(String roomId);

    // Trova proprietà che hanno stanze con un certo status
    List<Property> findByRoomsStatus(String status);

    // Trova proprietà specifica con una stanza specifica
    @Query("{ '_id': ?0, 'rooms.name': ?1 }")
    Optional<Property> findByIdAndRoomName(String propertyId, String roomName);

    // Trova tutte le proprietà di un Manager
    List<Property> findByManagerId(String managerId);

    // NOTA: Le query complesse (Search, GeoSpatial) sono state spostate 
    // nel PropertyService usando MongoTemplate per evitare errori di sintassi.
}