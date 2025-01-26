package ua.lastbite.email_service.dto.token;

import jakarta.validation.constraints.NotNull;
import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
public class TokenRequest {

    @NotNull(message = "User ID cannot be empty")
    private Long userId;
}
