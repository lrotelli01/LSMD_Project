package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.util.Set;

@Data
@AllArgsConstructor
public class FavoredPropertyResponseDTO {
    // Returns the updated list of all favorite IDs
    private Set<String> favoritePropertyIds;
}