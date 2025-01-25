package ua.lastbite.email_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.HttpClientErrorException;

import org.springframework.web.client.RestTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.util.UriComponentsBuilder;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.ServiceUnavailableException;
import ua.lastbite.email_service.exception.UserNotFoundException;

@Service
public class UserServiceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceClient.class);


    private final RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceUrl;

    public UserServiceClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UserEmailResponseDto getEmailInfoByUserId(Integer userId) {

        String url = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{userId}/email/info")
                .buildAndExpand(userId)
                .toUriString();

        try {
            LOGGER.info("Requesting email information for user ID: {}", userId);
            UserEmailResponseDto responseDto = restTemplate.getForObject(url, UserEmailResponseDto.class);

            if (responseDto == null) {
                LOGGER.warn("Received empty response from user-service for user ID: {}", userId);
                throw new ServiceUnavailableException("Received empty response from user-service");
            }

            LOGGER.debug("Email information retrieved: {}", responseDto);
            return responseDto;
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("User not found with ID: {}", userId);
            throw new UserNotFoundException(userId);
        } catch (RestClientException e) {
            LOGGER.error("Error occurred while retrieving email info from user-service for user ID: {}", userId, e);
            throw new ServiceUnavailableException("Failed to retrieve email info from user-service");
        }
    }

    public void markEmailAsVerified(Integer userId) {

        LOGGER.info("Initiating request to mark email as verified for user ID: {}", userId);

        String url = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{userId}/email/verify")
                .buildAndExpand(userId)
                .toUriString();

        try {
            restTemplate.put(url, null);
            LOGGER.info("Successfully marked email as verified for user ID: {}", userId);
        } catch (HttpClientErrorException.NotFound e) {
            LOGGER.error("Failed to mark email as verified: user not found with ID: {}", userId);
            throw new UserNotFoundException(userId);
        } catch (RestClientException e) {
            LOGGER.error("Error while marking email as verified in user-service for user ID: {}", userId, e);
            throw new ServiceUnavailableException("Failed to mark email as verified in user-service");
        }
    }
}
