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
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import ua.lastbite.email_service.dto.token.TokenRequest;
import ua.lastbite.email_service.dto.token.TokenResponse;
import ua.lastbite.email_service.dto.token.TokenValidationResponse;
import ua.lastbite.email_service.exception.ServiceUnavailableException;
import ua.lastbite.email_service.exception.token.TokenAlreadyUsedException;
import ua.lastbite.email_service.exception.token.TokenExpiredException;
import ua.lastbite.email_service.exception.token.TokenGenerationException;
import ua.lastbite.email_service.exception.token.TokenNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@ActiveProfiles("test")
@SpringBootTest
class TokenServiceClientTest {

    @MockBean
    RestTemplate restTemplate;

    @Autowired
    TokenServiceClient tokenServiceClient;

    @Autowired
    ObjectMapper objectMapper;

    @Value("${token-service.url}")
    private String tokenServiceUrl;

    private static final String TOKEN = "testToken123";
    private MockRestServiceServer mockServer;
    private TokenRequest tokenRequest;
    private TokenResponse tokenResponse;
    private TokenValidationResponse tokenValidationResponse;
    private String urlGenerateToken;
    private String urlValidateToken;

    @BeforeEach
    void setUp() {
        tokenRequest = new TokenRequest(1L);
        tokenResponse = new TokenResponse("tokenValue123");
        tokenValidationResponse = new TokenValidationResponse(true, 1L);
        mockServer = MockRestServiceServer.bindTo(restTemplate).build();
        urlGenerateToken = tokenServiceUrl + "/api/tokens/generate";
    }

    @BeforeEach
    void setUpTokenValidationUrl() {
        urlValidateToken = UriComponentsBuilder.fromHttpUrl(tokenServiceUrl)
                .path("/api/tokens/validate/{token}")
                .buildAndExpand(TOKEN)
                .toString();
    }

    @Test
    void testGenerateToken() throws JsonProcessingException {
        Mockito.when(restTemplate.postForObject(urlGenerateToken, tokenRequest, TokenResponse.class))
                .thenReturn(tokenResponse);

        final String expectedResponse = tokenResponse.getTokenValue();

        mockServer.expect(ExpectedCount.once(), requestTo(urlGenerateToken))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

        String actualResponse = tokenServiceClient.generateToken(tokenRequest);

        assertEquals(expectedResponse, actualResponse);
    }

    @Test
    void testGenerateTokenException() {

        Mockito.when(restTemplate.postForObject(urlGenerateToken, null, TokenResponse.class))
                .thenThrow(TokenGenerationException.class);

        TokenGenerationException exception = assertThrows(TokenGenerationException.class, () -> tokenServiceClient.generateToken(tokenRequest));

        assertEquals("Could not generate token", exception.getMessage());
    }

    @Test
    void testValidateTokenSuccessfully() {
        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenReturn(tokenValidationResponse);

        mockServer.expect(ExpectedCount.once(), requestTo(urlValidateToken))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"valid\":true, \"userId\":1}", MediaType.APPLICATION_JSON));

        TokenValidationResponse actualResponse = tokenServiceClient.verifyToken(TOKEN);

        assertNotNull(actualResponse, "Response should not be null");
        assertEquals(tokenValidationResponse, actualResponse);
        assertTrue(actualResponse.isValid());
    }

    @Test
    void verifyTokenNotFound() {

        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenThrow(HttpClientErrorException.NotFound.create(HttpStatus.NOT_FOUND, "Exception occurred", null, null, null));

        TokenNotFoundException exception = assertThrows(TokenNotFoundException.class, () -> tokenServiceClient.verifyToken(TOKEN));

        assertEquals("Token not found: " + TOKEN, exception.getMessage());
    }

    @Test
    void verifyTokenExpired() {

        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenThrow(HttpClientErrorException.Gone.create(HttpStatus.GONE, "Exception occurred", null, null, null));

        TokenExpiredException exception = assertThrows(TokenExpiredException.class, () -> tokenServiceClient.verifyToken(TOKEN));

        assertEquals("Token expired: " + TOKEN, exception.getMessage());
    }

    @Test
    void verifyTokenIsAlreadyUsed() {

        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenThrow(HttpClientErrorException.Conflict.create(HttpStatus.CONFLICT, "Exception occurred", null, null, null));

        TokenAlreadyUsedException exception = assertThrows(TokenAlreadyUsedException.class, () -> tokenServiceClient.verifyToken(TOKEN));

        assertEquals("Token already used: " + TOKEN, exception.getMessage());
    }

    @Test
    void verifyTokenServiceUnavailable() {

        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenThrow(HttpServerErrorException.InternalServerError.class);

        ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class, () -> tokenServiceClient.verifyToken(TOKEN));

        assertEquals("Token service is currently unavailable", exception.getMessage());
    }

    @Test
    void verifyTokenRestClientException() {

        Mockito.when(restTemplate.getForObject(urlValidateToken, TokenValidationResponse.class))
                .thenThrow(RestClientException.class);

        ServiceUnavailableException exception = assertThrows(ServiceUnavailableException.class, () -> tokenServiceClient.verifyToken(TOKEN));

        assertEquals("Unexpected error during token validation", exception.getMessage());
    }
}
