package ua.lastbite.email_service.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.ServiceUnavailableException;
import ua.lastbite.email_service.exception.UserNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ActiveProfiles("test")
@SpringBootTest
class UserServiceClientTest {

    @Autowired
    private UserServiceClient userServiceClient;

    @MockBean
    private RestTemplate restTemplate;

    @Value("${user-service.url}")
    private String userServiceUrl;

    @Autowired
    private ObjectMapper objectMapper;

    private static final Integer USER_ID = 1;
    private MockRestServiceServer mockServer;
    private String getEmailInfoUrl;
    private String markEmailAsVerifiedUrl;
    private UserEmailResponseDto expectedResponse;


    @BeforeEach
    void setUp() {
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        expectedResponse = new UserEmailResponseDto("email@example.com", false);
    }

    @BeforeEach
    void setUpUrl() {
        getEmailInfoUrl = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{userId}/email/info")
                .buildAndExpand(USER_ID)
                .toUriString();

        markEmailAsVerifiedUrl = UriComponentsBuilder.fromHttpUrl(userServiceUrl)
                .path("/api/users/{userId}/email/verify")
                .buildAndExpand(USER_ID)
                .toUriString();
    }

    @Test
    void testGetEmailInfoSuccessfully() throws JsonProcessingException {

        Mockito.when(restTemplate.getForObject(getEmailInfoUrl, UserEmailResponseDto.class))
                .thenReturn(expectedResponse);

        UserEmailResponseDto actualResponse = userServiceClient.getEmailInfoByUserId(USER_ID);

        assertNotNull(actualResponse);
        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void testGetEmailInfoServiceUnavailable() {

        Mockito.doThrow(RestClientException.class)
                .when(restTemplate).getForObject(getEmailInfoUrl, UserEmailResponseDto.class);

        ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class, () -> userServiceClient.getEmailInfoByUserId(USER_ID));

        assertEquals("Failed to retrieve email info from user-service", exception.getMessage());
    }

    @Test
    void testGetEmailInfoUserNotFound() {
        Mockito.when(restTemplate.getForObject(getEmailInfoUrl, UserEmailResponseDto.class))
                .thenThrow(HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Exception occurred", null, null, null));

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> userServiceClient.getEmailInfoByUserId(USER_ID));

        assertEquals("User with ID 1 not found", exception.getMessage());
    }

    @Test
    void testMarkEmailAsVerified_Successfully() {
        mockServer.expect(ExpectedCount.once(), requestTo(markEmailAsVerifiedUrl))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess());

        assertDoesNotThrow(() -> userServiceClient.markEmailAsVerified(USER_ID));
    }

    @Test
    void testMarkEmailAsVerified_UserNotFound() {

        Mockito.doThrow(HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Exception occurred", null, null, null))
                .when(restTemplate).put(markEmailAsVerifiedUrl, null);

        UserNotFoundException exception = assertThrows(UserNotFoundException.class,
                () -> userServiceClient.markEmailAsVerified(USER_ID));

        assertEquals("User with ID 1 not found", exception.getMessage());
    }

    @Test
    void testMarkEmailAsVerified_ServiceUnavailable() {

        Mockito.doThrow(RestClientException.class)
                .when(restTemplate).put(markEmailAsVerifiedUrl, null);

        ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class, () -> userServiceClient.markEmailAsVerified(USER_ID));

        assertEquals("Failed to mark email as verified in user-service", exception.getMessage());
    }
}

