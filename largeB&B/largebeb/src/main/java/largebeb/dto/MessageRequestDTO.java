package largebeb.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequestDTO {
    @NotBlank(message = "Recipient ID is mandatory")
    private String recipientId;
    @NotBlank(message = "Content is mandatory")
    private String content;
}