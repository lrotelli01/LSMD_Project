package largebeb.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.AllArgsConstructor;

import largebeb.utilities.BillingAddress;

@Data
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Manager extends RegisteredUser {

    // Manager iban
    private String iban;

    // Business tax identification number for managers
    private String vatNumber;

    // Official billing address for tax documents
    private BillingAddress billingAddress;
}