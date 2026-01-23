package largebeb.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.Set;
import largebeb.utilities.PaymentMethod;

@Data
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true) // Includes parent fields in comparison
public class Customer extends RegisteredUser {

    // Specific field only for Customers
    private PaymentMethod paymentMethod; 
    // Set of favorite Property IDs
    private Set<String> favoredPropertyIds;
}