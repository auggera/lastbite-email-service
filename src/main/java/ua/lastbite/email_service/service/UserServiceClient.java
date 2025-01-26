package ua.lastbite.email_service.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.ServiceUnavailableException;
import ua.lastbite.email_service.exception.UserNotFoundException;

@Service
@Slf4j
public class UserServiceClient {

    private final RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceUrl;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UserEmailResponseDto getEmailInfoByUserId(Long userId) {

        String url = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{id}/email/info")
                .buildAndExpand(userId)
                .toUriString();

        try {
            log.debug("Making request to user-service at URL: {}", url);
            UserEmailResponseDto responseDto = restTemplate.getForObject(url, UserEmailResponseDto.class);

            if (responseDto == null) {
                log.warn("Empty response from user-service for user ID: {}", userId);
                throw new ServiceUnavailableException("Empty response from user-service");
            }

            log.debug("Email information retrieved: {}", responseDto);
            return responseDto;
        } catch (HttpClientErrorException.NotFound e) {
            log.error("User not found in user-service for user ID: {}", userId, e);
            throw new UserNotFoundException(userId);
        } catch (RestClientException e) {
            log.error("Error communicating with user-service for user ID: {}", userId, e);
            throw new ServiceUnavailableException("Failed to communicate with user-service");
        }
    }

    public void markEmailAsVerified(Long userId) {

        log.info("Initiating request to mark email as verified for user ID: {}", userId);

        String url = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{id}/email/verify")
                .buildAndExpand(userId)
                .toUriString();

        try {
            restTemplate.put(url, null);
            log.info("Successfully marked email as verified for user ID: {}", userId);
        } catch (HttpClientErrorException.NotFound e) {
            log.error("Failed to mark email as verified: user not found with ID: {}", userId);
            throw new UserNotFoundException(userId);
        } catch (RestClientException e) {
            log.error("Error while marking email as verified in user-service for user ID: {}", userId, e);
            throw new ServiceUnavailableException("Failed to mark email as verified in user-service");
        }
    }
}
