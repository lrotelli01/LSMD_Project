package largebeb.repository;

import largebeb.model.Property;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.mongodb.repository.Aggregation;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
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

    // ==================== AGGREGATION QUERIES ====================

    // Search properties by name with average rating calculation (case-insensitive)
    @Aggregation(pipeline = {
        "{ '$match': { 'name': { $regex: ?0, $options: 'i' } } }",
        "{ '$addFields': { 'averageRating': { '$cond': { 'if': { '$eq': ['$ratingStats.count', 0] }, 'then': 0, 'else': { '$divide': ['$ratingStats.sum', '$ratingStats.count'] } } } } }",
        "{ '$project': { 'id': '$_id', 'name': 1, 'city': 1, 'averageRating': 1 } }"
    })
    Slice<Property> findByNameContaining(String name, Pageable pageable);

    // Search properties by city (case-insensitive)
    @Aggregation(pipeline = {
        "{ '$match': { 'city': { $regex: ?0, $options: 'i' } } }",
        "{ '$addFields': { 'averageRating': { '$cond': { 'if': { '$eq': ['$ratingStats.count', 0] }, 'then': 0, 'else': { '$divide': ['$ratingStats.sum', '$ratingStats.count'] } } } } }"
    })
    List<Property> findByCityContaining(String city);

    // Search properties with all specified amenities
    @Query("{ 'amenities': { $all: ?0 } }")
    List<Property> findByAmenitiesContainingAll(List<String> amenities);

    // Search properties with price range filter on rooms
    @Query("{ 'rooms': { $elemMatch: { 'pricePerNightAdults': { $gte: ?0, $lte: ?1 } } } }")
    List<Property> findByRoomPriceRange(Double minPrice, Double maxPrice);

    // Top rated properties (sorted by rating value descending)
    @Aggregation(pipeline = {
        "{ '$addFields': { 'averageRating': { $cond: { if: { $gt: ['$ratingStats.count', 0] }, then: { $divide: ['$ratingStats.sum', '$ratingStats.count'] }, else: 0 } } } }",
        "{ '$sort': { 'averageRating': -1 } }",
        "{ '$limit': ?0 }"
    })
    List<Property> findTopRatedProperties(int limit);

    // ==================== UPDATE QUERIES ====================

    // Increment rating statistics atomically
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'ratingStats.count': 1, 'ratingStats.sum': ?1 } }")
    void incrementRatingStats(String propertyId, double rating);

    // Decrement rating statistics (for review deletion)
    @Query("{ '_id': ?0 }")
    @Update("{ '$inc': { 'ratingStats.count': -1, 'ratingStats.sum': ?1 } }")
    void decrementRatingStats(String propertyId, double negativeRating);

    // Update room status
    @Query("{ '_id': ?0, 'rooms.roomId': ?1 }")
    @Update("{ '$set': { 'rooms.$.status': ?2 } }")
    void updateRoomStatus(String propertyId, String roomId, String status);

    // ==================== ADVANCED SEARCH QUERIES ====================

    // Search properties with optional filters: city, amenities, price range
    @Aggregation(pipeline = {
        "{ '$match': { $and: [ " +
        "   { $expr: { $or: [ { $eq: [?0, null] }, { $regexMatch: { input: '$city', regex: ?0, options: 'i' } } ] } }, " +
        "   { $expr: { $or: [ { $eq: [?3, null] }, { $eq: [?3, []] }, { $setIsSubset: [?3, '$amenities'] } ] } }, " +
        "   { $expr: { $or: [ " +
        "     { $and: [ { $eq: [?1, null] }, { $eq: [?2, null] } ] }, " +
        "     { $gt: [{ $size: { $filter: { input: '$rooms', as: 'room', cond: { $and: [ " +
        "       { $or: [ { $eq: [?1, null] }, { $gte: ['$$room.pricePerNightAdults', ?1] } ] }, " +
        "       { $or: [ { $eq: [?2, null] }, { $lte: ['$$room.pricePerNightAdults', ?2] } ] } " +
        "     ] } } } }, 0] } " +
        "   ] } } " +
        "] } }"
    })
    List<Property> searchPropertiesAdvanced(String city, Double minPrice, Double maxPrice, List<String> amenities);

    // Geospatial search - find properties near a location
    @Aggregation(pipeline = {
        "{ '$geoNear': { " +
        "   'near': { 'type': 'Point', 'coordinates': [?1, ?0] }, " +
        "   'distanceField': 'distance', " +
        "   'maxDistance': ?2, " +
        "   'spherical': true " +
        "} }"
    })
    List<Property> findPropertiesNearLocation(double latitude, double longitude, double maxDistanceMeters);

    // Top rated properties with limit
    @Aggregation(pipeline = {
        "{ '$addFields': { 'averageRating': { '$cond': { 'if': { '$eq': ['$ratingStats.count', 0] }, 'then': 0, 'else': { '$divide': ['$ratingStats.sum', '$ratingStats.count'] } } } } }",
        "{ '$sort': { 'averageRating': -1 } }",
        "{ '$limit': ?0 }"
    })
    List<Property> findTopRated(int limit);
}