package largebeb.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.neo4j.core.Neo4jClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ReservationGraphRepository {

    private final Neo4jClient neo4jClient;

    /**
     * Creates a BOOKED relationship between a User and a Property in Neo4j
     * Includes reservation details as relationship properties
     */
    public void createReservation(String reservationId, String userId, String propertyId, 
                                  LocalDate checkIn, LocalDate checkOut, double totalPrice) {
        String cypher = """
            MERGE (u:User {userId: $userId})
            MERGE (p:Property {propertyId: $propertyId})
            MERGE (u)-[b:BOOKED {reservationId: $reservationId}]->(p)
            SET b.checkIn = $checkIn,
                b.checkOut = $checkOut,
                b.totalPrice = $totalPrice,
                b.status = 'CONFIRMED'
            """;
        
        neo4jClient.query(cypher)
                .bind(userId).to("userId")
                .bind(propertyId).to("propertyId")
                .bind(reservationId).to("reservationId")
                .bind(checkIn.toString()).to("checkIn")
                .bind(checkOut.toString()).to("checkOut")
                .bind(totalPrice).to("totalPrice")
                .run();
    }

    /**
     * Deletes the BOOKED relationship for a specific reservation
     * Used when a reservation is cancelled
     */
    public void deleteById(String reservationId) {
        String cypher = """
            MATCH ()-[b:BOOKED {reservationId: $reservationId}]->()
            DELETE b
            """;
        
        neo4jClient.query(cypher)
                .bind(reservationId).to("reservationId")
                .run();
    }
}
