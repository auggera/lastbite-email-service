package ua.lastbite.email_service.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import ua.lastbite.email_service.dto.email.*;
import ua.lastbite.email_service.dto.token.*;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.*;
import ua.lastbite.email_service.exception.token.*;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.*;

@ActiveProfiles("test")
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private TokenServiceClient tokenServiceClient;

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private JavaMailSender mailSender;

    @InjectMocks
    private EmailService emailService;

    @Value("${app.verification.base-url}")
    private String verificationBaseUrl;

    @Value("${app.verification.verify-url}")
    private String verificationVerifyUrl;

    private TokenValidationResponse tokenValidationResponse;
    private UserEmailResponseDto userEmailResponseDto;
    private EmailRequest emailRequest;

    private static final long USER_ID = 1L;
    private static final String TOKEN = "tokenValue123";
    private static final String EMAIL = "email@example.com";

    @BeforeEach
    void setUp() {
        tokenValidationResponse = new TokenValidationResponse(true, USER_ID);
        userEmailResponseDto = new UserEmailResponseDto(EMAIL, false);
        emailRequest = new EmailRequest("recipient@example.com", "Test Subject", "Test Body");
    }

    @Test
    void testVerifyEmailSuccess() {

        Mockito.doReturn(tokenValidationResponse)
                .when(tokenServiceClient).verifyToken(TOKEN);

        emailService.verifyEmail(TOKEN);

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailTokenNotFound() {

        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenThrow(new TokenNotFoundException(TOKEN));

        TokenNotFoundException exception = assertThrows(TokenNotFoundException.class, () -> emailService.verifyEmail(TOKEN));

        assertEquals("Token not found: " + TOKEN, exception.getMessage());

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailTokenExpired() {

        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenThrow(new TokenExpiredException(TOKEN));

        TokenExpiredException exception = assertThrows(TokenExpiredException.class, () -> emailService.verifyEmail(TOKEN));

        assertEquals("Token expired: " + TOKEN, exception.getMessage());

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailTokenAlreadyUsed() {

        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenThrow(new TokenAlreadyUsedException(TOKEN));

        TokenAlreadyUsedException exception = assertThrows(TokenAlreadyUsedException.class, () -> emailService.verifyEmail(TOKEN));

        assertEquals("Token already used: " + TOKEN, exception.getMessage());

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailServiceUnavailable() {
        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenThrow(ServiceUnavailableException.class);

        assertThrows(ServiceUnavailableException.class, () -> emailService.verifyEmail(TOKEN));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.never()).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailUserNotFound() {

        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenReturn(tokenValidationResponse);

        Mockito.doThrow(new UserNotFoundException(USER_ID))
                .when(userServiceClient).markEmailAsVerified(USER_ID);

        UserNotFoundException exception = assertThrows(UserNotFoundException.class, () -> emailService.verifyEmail(TOKEN));

        assertEquals("User with ID 1 not found", exception.getMessage());

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }

    @Test
    void testVerifyEmailFailedMarkAsVerified() {

        Mockito.when(tokenServiceClient.verifyToken(TOKEN))
                .thenReturn(tokenValidationResponse);

        Mockito.doThrow(ServiceUnavailableException.class)
                .when(userServiceClient).markEmailAsVerified(USER_ID);

        assertThrows(ServiceUnavailableException.class, () -> emailService.verifyEmail(TOKEN));

        Mockito.verify(tokenServiceClient, Mockito.times(1)).verifyToken(TOKEN);
        Mockito.verify(userServiceClient, Mockito.times(1)).markEmailAsVerified(USER_ID);
    }

    @Test
    void sendSimpleEmailSuccessfulSend() throws Exception {

        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailRequest.getToEmail());
        message.setSubject(emailRequest.getSubject());
        message.setText(emailRequest.getBody());

        CompletableFuture<Void> future = emailService.sendSimpleEmail(emailRequest);
        future.get();

        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void sendSimpleEmail_EmailSendingFailedException() {

        Mockito.doThrow(new MailException("Failed to send email") {
                }).when(mailSender)
                .send(Mockito.any(SimpleMailMessage.class));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> emailService.sendSimpleEmail(emailRequest).join());

        Throwable cause = exception.getCause();
        assertInstanceOf(EmailSendingFailedException.class, cause);
        assertTrue(cause.getMessage().contains("Failed to send email to recipient@example.com"));
    }

    @Test
    void sendVerificationEmailSuccess() throws Exception {

        Mockito.when(userServiceClient.getEmailInfoByUserId(USER_ID)).thenReturn(userEmailResponseDto);
        Mockito.when(tokenServiceClient.generateToken(new TokenRequest(USER_ID))).thenReturn(TOKEN);

        String expectedVerificationUrl = verificationBaseUrl + verificationVerifyUrl + "?token=" + TOKEN;
        String expectedBody = "Please click the following link to verify your email: " + expectedVerificationUrl;

        CompletableFuture<Void> future = emailService.sendVerificationEmail(USER_ID);
        future.get();

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(new TokenRequest(USER_ID));

        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.argThat((SimpleMailMessage message) ->
                Objects.requireNonNull(message.getTo())[0].equals(EMAIL) &&
                        Objects.equals(message.getSubject(), "Email Verification") &&
                        Objects.equals(message.getText(), expectedBody)
        ));
    }

    @Test
    void sendVerificationEmailFailed() {

        Mockito.when(userServiceClient.getEmailInfoByUserId(USER_ID)).thenReturn(userEmailResponseDto);
        Mockito.doThrow(new TokenGenerationException("Failed to generate token")).when(tokenServiceClient).generateToken(Mockito.any());

        TokenGenerationException exception = assertThrows(TokenGenerationException.class,
                () -> emailService.sendVerificationEmail(USER_ID));

        assertEquals("Failed to generate token", exception.getMessage());
        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void sendVerificationEmailIsAlreadyUsed() {

        userEmailResponseDto.setVerified(true);

        Mockito.when(userServiceClient.getEmailInfoByUserId(USER_ID)).thenReturn(userEmailResponseDto);

        EmailAlreadyVerifiedException exception = assertThrows(EmailAlreadyVerifiedException.class, () -> emailService.sendVerificationEmail(USER_ID));

        assertEquals("Email already verified", exception.getMessage());

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
        Mockito.verify(tokenServiceClient, Mockito.never()).generateToken(Mockito.any());
    }

    @Test
    void sendVerificationEmailTokenGenerationFailed() {

        Mockito.when(userServiceClient.getEmailInfoByUserId(USER_ID)).thenReturn(userEmailResponseDto);

        Mockito.when(tokenServiceClient.generateToken(new TokenRequest(USER_ID)))
                .thenThrow(new TokenGenerationException("Failed to generate token"));

        TokenGenerationException exception = assertThrows(TokenGenerationException.class, () -> emailService.sendVerificationEmail(USER_ID));

        assertEquals("Failed to generate token", exception.getMessage());

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(Mockito.any());
        Mockito.verify(mailSender, Mockito.never()).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    void sendVerificationEmailMailSendingFailed() {

        Mockito.when(userServiceClient.getEmailInfoByUserId(USER_ID)).thenReturn(userEmailResponseDto);
        Mockito.when(tokenServiceClient.generateToken(new TokenRequest(USER_ID))).thenReturn(TOKEN);

        String expectedVerificationUrl = verificationBaseUrl + verificationVerifyUrl + "?token=" + TOKEN;
        String expectedBody = "Please click the following link to verify your email: " + expectedVerificationUrl;

        Mockito.doThrow(new MailException("Failed to send email") {
                }).when(mailSender)
                .send(Mockito.any(SimpleMailMessage.class));

        CompletionException exception = assertThrows(CompletionException.class,
                () -> emailService.sendVerificationEmail(USER_ID).join());

        Throwable cause = exception.getCause();
        assertInstanceOf(EmailSendingFailedException.class, cause);
        assertTrue(cause.getMessage().contains("Failed to send email to " + EMAIL));

        Mockito.verify(userServiceClient, Mockito.times(1)).getEmailInfoByUserId(USER_ID);
        Mockito.verify(tokenServiceClient, Mockito.times(1)).generateToken(new TokenRequest(USER_ID));

        Mockito.verify(mailSender, Mockito.times(1)).send(Mockito.argThat((SimpleMailMessage message) ->
                Objects.requireNonNull(message.getTo())[0].equals(EMAIL) &&
                        Objects.equals(message.getSubject(), "Email Verification") &&
                        Objects.equals(message.getText(), expectedBody)
        ));
    }
}
