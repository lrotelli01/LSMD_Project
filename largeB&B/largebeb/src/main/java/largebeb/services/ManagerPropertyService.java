package largebeb.services;

import largebeb.dto.*;
import largebeb.model.Property;
import largebeb.model.RegisteredUser;
import largebeb.model.Reservation;
import largebeb.model.Room;
import largebeb.model.graph.PropertyNode; // Import Neo4j Node
import largebeb.repository.PropertyGraphRepository; // Import Neo4j Repository
import largebeb.repository.PropertyRepository;
import largebeb.repository.ReservationRepository;
import largebeb.repository.UserRepository;
import largebeb.utilities.JwtUtil;
import largebeb.utilities.RatingStats;
import lombok.RequiredArgsConstructor;
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

        // 1. Save to MongoDB
        Property saved = propertyRepository.save(property);

        // 2. Save to Neo4j
        try {
            PropertyNode propertyNode = PropertyNode.builder()
                    .propertyId(saved.getId()) // Use Mongo ID as Graph Key
                    .name(saved.getName())
                    .city(saved.getCity())
                    .build();
            
            propertyGraphRepository.save(propertyNode);
        } catch (Exception e) {
            System.err.println("Error saving property to Neo4j: " + e.getMessage());
            // Optional: throw exception to rollback transaction if graph consistency is strict
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

        // 1. Delete from MongoDB
        propertyRepository.delete(property);

        // 2. Delete from Neo4j
        try {
            propertyGraphRepository.deleteById(propertyId);
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
        boolean updateGraph = false; // Flag to check if we need to update Neo4j

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
        if (request.getAmenities() != null) property.setAmenities(request.getAmenities());
        if (request.getPhotos() != null) property.setPhotos(request.getPhotos());
        
        // Handle coordinates
        if (request.getCoordinates() != null && request.getCoordinates().size() == 2) {
            Double lat = request.getCoordinates().get(0);
            Double lon = request.getCoordinates().get(1);
            property.setCoordinates(Arrays.asList(lon, lat));
        }

        // 1. Save to MongoDB
        Property saved = propertyRepository.save(property);

        // 2. Update Neo4j (only if critical fields changed)
        if (updateGraph) {
            try {
                // Since we use propertyId as @Id in Neo4j, calling save() works as an "upsert" (update if exists)
                PropertyNode node = PropertyNode.builder()
                        .propertyId(saved.getId())
                        .name(saved.getName())
                        .city(saved.getCity())
                        .build();
                propertyGraphRepository.save(node);
            } catch (Exception e) {
                System.err.println("Error updating property in Neo4j: " + e.getMessage());
            }
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

    // (Room logic remains unchanged as rooms are embedded in Property on Mongo
    // and usually not modeled as separate Nodes in this simplified Graph approach)

    /**
     * Add a room to a property (Manager only)
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