package largebeb.utilities;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BillingAddress {

    // Street name and building number
    private String street;

    // Town or city name
    private String city;

    // Postal code or zip code
    private String zipCode;

    // Country name or code
    private String country;

    // Regional state or province
    private String stateProvince;
}