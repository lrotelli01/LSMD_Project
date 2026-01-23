package largebeb.model;

import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

@Data
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "users") // Both Customers and Managers are saved here
public abstract class RegisteredUser {

    @Id
    private String id; // Maps to MongoDB "_id"

    private String username;
    private String email;
    private String password;
    private LocalDate birthdate;
    private String name;
    private String surname;
    private String phoneNumber;
    
    private String role; // e.g., "CUSTOMER" or "MANAGER"
}