package largebeb.services;

import largebeb.dto.*;
import largebeb.model.Property;
import largebeb.model.RegisteredUser;
import largebeb.model.Reservation;
import largebeb.model.Room;
import largebeb.model.graph.PropertyNode;
import largebeb.repository.PropertyGraphRepository; 
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import largebeb.utilities.RatingStats;
import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagerPropertyService {

    private final PropertyRepository propertyRepository;      // MongoDB
    private final PropertyGraphRepository propertyGraphRepository; // Neo4j
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final Neo4jClient neo4jClient; // Client to execute custom Cypher queries

    /**
     * Add a new property (Manager only)
     */
    @Transactional // Recommended for multi-db operations
    public PropertyResponseDTO addProperty(String token, PropertyRequestDTO request) {
        RegisteredUser manager = getManagerFromToken(token);

        Property property = new Property();
        property.setName(request.getName());
        property.setAddress(request.getAddress());
        property.setDescription(request.getDescription());
        property.setCity(request.getCity());
        property.setRegion(request.getRegion());
        property.setCountry(request.getCountry());
        property.setEmail(request.getEmail());
        property.setAmenities(request.getAmenities() != null ? request.getAmenities() : new ArrayList<>());
        property.setPhotos(request.getPhotos() != null ? request.getPhotos() : new ArrayList<>());
        property.setManagerId(manager.getId());
        property.setRooms(new ArrayList<>());
        property.setLatestReviews(new ArrayList<>());
        property.setPois(new ArrayList<>());
        
        // Initialize rating stats
        RatingStats stats = new RatingStats();
        stats.setCleanliness(0.0);
        stats.setCommunication(0.0);
        stats.setLocation(0.0);
        stats.setValue(0.0);
        property.setRatingStats(stats);

        // Handle coordinates (input: [lat, lon] -> stored: [lon, lat] for MongoDB)
        if (request.getCoordinates() != null && request.getCoordinates().size() == 2) {
            Double lat = request.getCoordinates().get(0);
            Double lon = request.getCoordinates().get(1);
            property.setCoordinates(Arrays.asList(lon, lat));
        }

        // Save to MongoDB
        Property saved = propertyRepository.save(property);

        // Save to Neo4j
        try {
            // Save the main Property Node using the Repository 
            PropertyNode propertyNode = PropertyNode.builder()
                    .propertyId(saved.getId()) // Use Mongo ID as Graph Key
                    .name(saved.getName())
                    .city(saved.getCity())
                    .build();
            
            propertyGraphRepository.save(propertyNode);

            // Handle the Amenities (Services) relationships using custom Cypher.
            // MERGE the service node (create if not exists), then link Property -> Service.
            if (saved.getAmenities() != null && !saved.getAmenities().isEmpty()) {
                String cypherQuery = """
                    MATCH (p:Property {propertyId: $propId})
                    UNWIND $amenitiesList AS amenityName
                    MERGE (s:Service {name: amenityName}) 
                    MERGE (p)-[:HAS_SERVICE]->(s)
                """;

                neo4jClient.query(cypherQuery)
                        .bind(saved.getId()).to("propId")
                        .bind(saved.getAmenities()).to("amenitiesList")
                        .run();
            }

        } catch (Exception e) {
            System.err.println("Error saving property to Neo4j: " + e.getMessage());
        }

        return mapPropertyToDTO(saved);
    }

    /**
     * Delete a property (Manager only - must own the property)
     */
    @Transactional
    public void deleteProperty(String token, String propertyId) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only delete your own properties.");
        }

        // Check for active reservations
        if (property.getRooms() != null && !property.getRooms().isEmpty()) {
            List<String> roomIds = property.getRooms().stream()
                    .map(Room::getId)
                    .collect(Collectors.toList());
            
            List<Reservation> activeReservations = reservationRepository.findByRoomIdIn(roomIds).stream()
                    .filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus()) && 
                                 r.getDates().getCheckOut().isAfter(LocalDate.now()))
                    .collect(Collectors.toList());
            
            if (!activeReservations.isEmpty()) {
                throw new IllegalStateException("Cannot delete property with " + activeReservations.size() + 
                        " active reservations. Please cancel them first.");
            }
        }

        // Delete from MongoDB
        propertyRepository.delete(property);

        // Delete from Neo4j
        try {
            // Logic: 
            // 1. Match the property and its services.
            // 2. Detach and delete the property.
            // 3. Check the services that were connected. If they have NO other connections, delete them.
            String cypherQuery = """
                MATCH (p:Property {propertyId: $propId})
                OPTIONAL MATCH (p)-[:HAS_SERVICE]->(s:Service)
                DETACH DELETE p
                WITH s
                WHERE s IS NOT NULL
                OPTIONAL MATCH (s)<-[r:HAS_SERVICE]-(other:Property)
                WITH s, count(r) AS connections
                WHERE connections = 0
                DELETE s
            """;

            neo4jClient.query(cypherQuery)
                    .bind(propertyId).to("propId")
                    .run();
            
        } catch (Exception e) {
            System.err.println("Error deleting property from Neo4j: " + e.getMessage());
        }
    }

    /**
     * Modify property information (Manager only - must own the property)
     */
    @Transactional
    public PropertyResponseDTO modifyProperty(String token, String propertyId, PropertyRequestDTO request) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only modify your own properties.");
        }

        // Update fields (only if provided)
        boolean updateGraph = false; // Flag to check if we need to update Neo4j basic info
        boolean updateAmenitiesGraph = false; // Flag to check if amenities changed

        if (request.getName() != null) {
            property.setName(request.getName());
            updateGraph = true;
        }
        if (request.getCity() != null) {
            property.setCity(request.getCity());
            updateGraph = true;
        }

        if (request.getAddress() != null) property.setAddress(request.getAddress());
        if (request.getDescription() != null) property.setDescription(request.getDescription());
        if (request.getRegion() != null) property.setRegion(request.getRegion());
        if (request.getCountry() != null) property.setCountry(request.getCountry());
        if (request.getEmail() != null) property.setEmail(request.getEmail());
        
        if (request.getAmenities() != null) {
            property.setAmenities(request.getAmenities());
            updateAmenitiesGraph = true;
        }
        
        if (request.getPhotos() != null) property.setPhotos(request.getPhotos());
        
        // Handle coordinates
        if (request.getCoordinates() != null && request.getCoordinates().size() == 2) {
            Double lat = request.getCoordinates().get(0);
            Double lon = request.getCoordinates().get(1);
            property.setCoordinates(Arrays.asList(lon, lat));
        }

        // Save to MongoDB
        Property saved = propertyRepository.save(property);

        // Update Neo4j
        try {
            // A. Update basic Node info if needed
            if (updateGraph) {
                PropertyNode node = PropertyNode.builder()
                        .propertyId(saved.getId())
                        .name(saved.getName())
                        .city(saved.getCity())
                        .build();
                propertyGraphRepository.save(node); // This works as an upsert based on ID
            }

            // Update Amenities if changed
            if (updateAmenitiesGraph) {
                // Logic:
                // 1. Find old services linked to this property.
                // 2. Remove the relationship.
                // 3. If those old services are now orphans, delete them.
                // 4. Add new services (create if not exist) and link them.
                String cypherQuery = """
                    MATCH (p:Property {propertyId: $propId})
                    
                    // Step 1: Handle removals
                    OPTIONAL MATCH (p)-[rOld:HAS_SERVICE]->(oldS:Service)
                    DELETE rOld
                    WITH p, collect(oldS) AS oldServices
                    
                    // Step 2: Handle additions
                    UNWIND $newAmenities AS newAmenityName
                    MERGE (newS:Service {name: newAmenityName})
                    MERGE (p)-[:HAS_SERVICE]->(newS)
                    
                    // Step 3: Clean up orphans from the old list
                    WITH oldServices
                    UNWIND oldServices AS s
                    OPTIONAL MATCH (s)<-[r:HAS_SERVICE]-(other:Property)
                    WITH s, count(r) AS connections
                    WHERE connections = 0
                    DELETE s
                """;

                neo4jClient.query(cypherQuery)
                        .bind(saved.getId()).to("propId")
                        .bind(saved.getAmenities()).to("newAmenities")
                        .run();
            }
        } catch (Exception e) {
            System.err.println("Error updating property in Neo4j: " + e.getMessage());
        }

        return mapPropertyToDTO(saved);
    }

    /**
     * Get all properties owned by the manager
     */
    public List<PropertyResponseDTO> getMyProperties(String token) {
        RegisteredUser manager = getManagerFromToken(token);
        
        return propertyRepository.findByManagerId(manager.getId()).stream()
                .map(this::mapPropertyToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Add a room to a property
     */
    public RoomResponseDTO addRoom(String token, String propertyId, RoomRequestDTO request) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only add rooms to your own properties.");
        }

        // Generate unique room ID
        String roomId = UUID.randomUUID().toString();

        Room room = new Room();
        room.setId(roomId);
        room.setPropertyId(propertyId);
        room.setName(request.getName());
        room.setRoomType(request.getRoomType());
        room.setNumBeds(request.getNumBeds());
        room.setAmenities(request.getAmenities() != null ? request.getAmenities() : new ArrayList<>());
        room.setPhotos(request.getPhotos() != null ? request.getPhotos() : new ArrayList<>());
        room.setStatus(request.getStatus() != null ? request.getStatus() : "available");
        room.setCapacityAdults(request.getCapacityAdults());
        room.setCapacityChildren(request.getCapacityChildren());
        room.setPricePerNightAdults(request.getPricePerNightAdults());
        room.setPricePerNightChildren(request.getPricePerNightChildren());

        // Add room to property's room list
        if (property.getRooms() == null) {
            property.setRooms(new ArrayList<>());
        }
        property.getRooms().add(room);
        
        propertyRepository.save(property);
        return mapRoomToDTO(room, propertyId);
    }

    /**
     * Delete a room from a property
     */
    public void deleteRoom(String token, String propertyId, String roomId) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only delete rooms from your own properties.");
        }

        // Find the room
        Room roomToDelete = property.getRooms().stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + roomId));

        // Check for active reservations
        List<Reservation> activeReservations = reservationRepository.findByRoomId(roomId).stream()
                .filter(r -> "CONFIRMED".equalsIgnoreCase(r.getStatus()) && 
                             r.getDates().getCheckOut().isAfter(LocalDate.now()))
                .collect(Collectors.toList());

        if (!activeReservations.isEmpty()) {
            throw new IllegalStateException("Cannot delete room with " + activeReservations.size() + 
                    " active reservations. Please cancel them first.");
        }

        // Remove room from property
        property.getRooms().remove(roomToDelete);
        propertyRepository.save(property);
    }

    /**
     * Modify room information (Manager only)
     */
    public RoomResponseDTO modifyRoom(String token, String propertyId, String roomId, RoomRequestDTO request) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only modify rooms in your own properties.");
        }

        // Find the room
        Room room = property.getRooms().stream()
                .filter(r -> r.getId().equals(roomId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Room not found with id: " + roomId));

        // Update fields (only if provided)
        if (request.getName() != null) room.setName(request.getName());
        if (request.getRoomType() != null) room.setRoomType(request.getRoomType());
        if (request.getNumBeds() != null) room.setNumBeds(request.getNumBeds());
        if (request.getAmenities() != null) room.setAmenities(request.getAmenities());
        if (request.getPhotos() != null) room.setPhotos(request.getPhotos());
        if (request.getStatus() != null) room.setStatus(request.getStatus());
        if (request.getCapacityAdults() != null) room.setCapacityAdults(request.getCapacityAdults());
        if (request.getCapacityChildren() != null) room.setCapacityChildren(request.getCapacityChildren());
        if (request.getPricePerNightAdults() != null) room.setPricePerNightAdults(request.getPricePerNightAdults());
        if (request.getPricePerNightChildren() != null) room.setPricePerNightChildren(request.getPricePerNightChildren());

        propertyRepository.save(property);
        return mapRoomToDTO(room, propertyId);
    }

    /**
     * Get all rooms for a property
     */
    public List<RoomResponseDTO> getRooms(String token, String propertyId) {
        RegisteredUser manager = getManagerFromToken(token);
        
        Property property = propertyRepository.findById(propertyId)
                .orElseThrow(() -> new IllegalArgumentException("Property not found with id: " + propertyId));

        // Verify ownership
        if (!property.getManagerId().equals(manager.getId())) {
            throw new SecurityException("You can only view rooms of your own properties.");
        }

        if (property.getRooms() == null) {
            return new ArrayList<>();
        }

        return property.getRooms().stream()
                .map(r -> mapRoomToDTO(r, propertyId))
                .collect(Collectors.toList());
    }

    // HELPER METHODS

    private RegisteredUser getManagerFromToken(String token) {
        String cleanToken = token.replace("Bearer ", "");
        String userId = jwtUtil.getUserIdFromToken(cleanToken);
        
        RegisteredUser user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!"MANAGER".equalsIgnoreCase(user.getRole())) {
            throw new SecurityException("Access Denied: Only managers can perform this action.");
        }

        return user;
    }

    private PropertyResponseDTO mapPropertyToDTO(Property p) {
        Double minPrice = 0.0;
        
        if (p.getRooms() != null && !p.getRooms().isEmpty()) {
            minPrice = p.getRooms().stream()
                .filter(r -> r.getPricePerNightAdults() != null)
                .mapToDouble(r -> r.getPricePerNightAdults().doubleValue())
                .min()
                .orElse(0.0);
        }

        return PropertyResponseDTO.builder()
                .id(p.getId())
                .name(p.getName())
                .description(p.getDescription())
                .city(p.getCity())
                .region(p.getRegion())
                .country(p.getCountry())
                .pricePerNight(minPrice)
                .rating(p.getRatingStats() != null ? p.getRatingStats().getValue() : 0.0)
                .amenities(p.getAmenities())
                .photos(p.getPhotos())
                .pois(p.getPois())
                .coordinates(p.getCoordinates())
                .build();
    }

    private RoomResponseDTO mapRoomToDTO(Room r, String propertyId) {
        return RoomResponseDTO.builder()
                .id(r.getId())
                .propertyId(propertyId)
                .name(r.getName())
                .roomType(r.getRoomType())
                .numBeds(r.getNumBeds())
                .amenities(r.getAmenities())
                .photos(r.getPhotos())
                .status(r.getStatus())
                .capacityAdults(r.getCapacityAdults())
                .capacityChildren(r.getCapacityChildren())
                .pricePerNightAdults(r.getPricePerNightAdults())
                .pricePerNightChildren(r.getPricePerNightChildren())
                .build();
    }
}