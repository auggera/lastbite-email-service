package ua.lastbite.email_service.dto.token;

import lombok.*;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@EqualsAndHashCode
@ToString
public class TokenValidationResponse {

    private boolean valid;
    private Long userId;
}