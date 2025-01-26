package ua.lastbite.email_service.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ua.lastbite.email_service.dto.email.EmailRequest;
import ua.lastbite.email_service.dto.token.TokenRequest;
import ua.lastbite.email_service.dto.token.TokenValidationResponse;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.EmailSendingFailedException;
import ua.lastbite.email_service.exception.ServiceUnavailableException;
import ua.lastbite.email_service.exception.UserNotFoundException;
import ua.lastbite.email_service.exception.token.TokenExpiredException;
import ua.lastbite.email_service.exception.token.TokenGenerationException;
import ua.lastbite.email_service.exception.token.TokenNotFoundException;
import ua.lastbite.email_service.service.TokenServiceClient;
import ua.lastbite.email_service.service.UserServiceClient;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class EmailControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserServiceClient userServiceClient;

    @MockBean
    private TokenServiceClient tokenServiceClient;

    @MockBean
    private JavaMailSender mailSender;

    private static final long USER_ID = 1L;
    private static final String TOKEN = "tokenValue123";
    private static final String EMAIL = "email@example.com";
    private EmailRequest emailRequest;
    private UserEmailResponseDto userEmailResponseDto;
    private TokenValidationResponse tokenValidationResponse;

    @BeforeEach
    void setUp() {
        emailRequest = new EmailRequest(EMAIL, "Test Subject", "Test Body");
        userEmailResponseDto = new UserEmailResponseDto(EMAIL, false);
        tokenValidationResponse = new TokenValidationResponse(true, USER_ID);
    }

    @Test
    void testSendEmail_Success() throws Exception {

        Mockito.doNothing()
                .when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailRequest)))
                .andExpect(status().isOk())
                .andExpect(content().string("Email sent successfully"));

        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendEmail_ValidationFailure() throws Exception {

        emailRequest.setToEmail("");
        emailRequest.setSubject(null);
        emailRequest.setBody("   ");

        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.toEmail").value("Email cannot be empty"))
                .andExpect(jsonPath("$.subject").value("Subject cannot be empty"))
                .andExpect(jsonPath("$.body").value("Body cannot be empty"));

        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendEmail_FailureDueToMailException() throws Exception {
        Mockito.doThrow(new EmailSendingFailedException(emailRequest.getToEmail()) {
                })
                .when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/emails")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(emailRequest)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to send email to " + emailRequest.getToEmail()));

        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendVerificationEmail_Success() throws Exception {
        Mockito.doReturn(userEmailResponseDto)
                .when(userServiceClient).getEmailInfoByUserId(USER_ID);

        Mockito.doReturn(TOKEN)
                .when(tokenServiceClient).generateToken(new TokenRequest(USER_ID));

        mockMvc.perform(post("/api/emails/verification/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Email Verification sent successfully"));

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(Mockito.any(TokenRequest.class));
        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendVerificationEmail_AlreadyVerified() throws Exception {
        userEmailResponseDto.setVerified(true);

        Mockito.doReturn(userEmailResponseDto)
                .when(userServiceClient).getEmailInfoByUserId(USER_ID);

        Mockito.doReturn(TOKEN)
                .when(tokenServiceClient).generateToken(new TokenRequest(USER_ID));

        mockMvc.perform(post("/api/emails/verification/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(content().string("Email already verified"));

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.never()).generateToken(Mockito.any(TokenRequest.class));
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendVerificationEmail_TokenGenerationFailed() throws Exception {
        Mockito.doReturn(userEmailResponseDto)
                .when(userServiceClient).getEmailInfoByUserId(USER_ID);

        Mockito.doThrow(new TokenGenerationException("Failed to generate token"))
                .when(tokenServiceClient).generateToken(new TokenRequest(USER_ID));

        mockMvc.perform(post("/api/emails/verification/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to generate token"));

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(Mockito.any(TokenRequest.class));
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void testSendVerificationEmail_MailSendingFailed() throws Exception {
        Mockito.doReturn(userEmailResponseDto)
                .when(userServiceClient).getEmailInfoByUserId(USER_ID);

        Mockito.doReturn(TOKEN)
                .when(tokenServiceClient).generateToken(new TokenRequest(USER_ID));

        Mockito.doThrow(new EmailSendingFailedException(EMAIL))
                .when(mailSender).send(Mockito.any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/emails/verification/users/{id}", USER_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Failed to send email to " + EMAIL));

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(Mockito.any(TokenRequest.class));
        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(SimpleMailMessage.class));
    }


    @Test
    void verifyEmail_Success() throws Exception {
        Mockito.doReturn(tokenValidationResponse)
                .when(tokenServiceClient).verifyToken(TOKEN);

        Mockito.doNothing().when(userServiceClient).markEmailAsVerified(USER_ID);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string("Email successfully verified"));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmail_UserNotFound() throws Exception {
        Mockito.doReturn(tokenValidationResponse)
                .when(tokenServiceClient).verifyToken(TOKEN);

        Mockito.doThrow(new UserNotFoundException(USER_ID))
                .when(userServiceClient).markEmailAsVerified(USER_ID);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("User with ID 1 not found"));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmail_TokenNotFound() throws Exception {
        Mockito.doThrow(new TokenNotFoundException(TOKEN))
                .when(tokenServiceClient).verifyToken(TOKEN);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(content().string("Token not found: " + TOKEN));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmail_TokenExpired() throws Exception {
        Mockito.doThrow(new TokenExpiredException(TOKEN))
                .when(tokenServiceClient).verifyToken(TOKEN);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isGone())
                .andExpect(content().string("Token expired: " + TOKEN));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmail_TokenServiceUnavailable() throws Exception {
        Mockito.doThrow(new ServiceUnavailableException("Service Unavailable"))
                .when(tokenServiceClient).verifyToken(TOKEN);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TOKEN)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Service Unavailable"));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmail_UserServiceUnavailable() throws Exception {
        Mockito.doReturn(tokenValidationResponse)
                .when(tokenServiceClient).verifyToken(TOKEN);

        Mockito.doThrow(new ServiceUnavailableException("Service Unavailable"))
                .when(userServiceClient).markEmailAsVerified(USER_ID);

        mockMvc.perform(post("/api/emails/verification/{token}", TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(TOKEN)))
                .andExpect(status().isInternalServerError())
                .andExpect(content().string("Service Unavailable"));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }
}
