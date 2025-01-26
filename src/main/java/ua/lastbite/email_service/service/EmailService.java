package ua.lastbite.email_service.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ua.lastbite.email_service.dto.email.EmailRequest;
import ua.lastbite.email_service.dto.token.TokenRequest;
import ua.lastbite.email_service.dto.token.TokenValidationResponse;
import ua.lastbite.email_service.dto.user.UserEmailResponseDto;
import ua.lastbite.email_service.exception.EmailAlreadyVerifiedException;
import ua.lastbite.email_service.exception.EmailSendingFailedException;

import java.util.concurrent.CompletableFuture;

@Service
public class EmailService {

    private final JavaMailSender mailSender;
    private final TokenServiceClient tokenServiceClient;
    private final UserServiceClient userServiceClient;

    @Value("${app.verification.base-url}")
    private String verificationBaseUrl;

    @Value("${app.verification.verify-url}")
    private String verificationVerifyUrl;

    private static final Logger LOGGER = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    public EmailService(JavaMailSender mailSender, TokenServiceClient tokenServiceClient, UserServiceClient userServiceClient) {
        this.mailSender = mailSender;
        this.tokenServiceClient = tokenServiceClient;
        this.userServiceClient = userServiceClient;
    }

    @Async
    public CompletableFuture<Void> sendSimpleEmail(EmailRequest request) {

        return CompletableFuture.runAsync(() -> {

            LOGGER.info("Sending email to {}", request.getToEmail());
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(request.getToEmail());
            message.setSubject(request.getSubject());
            message.setText(request.getBody());
            mailSender.send(message);
            LOGGER.info("Email sent successfully to {}", request.getToEmail());

        }).exceptionally(ex -> {
            LOGGER.error("Failed to send email: {}", ex.getMessage());
            throw new EmailSendingFailedException(request.getToEmail());
        }).thenApply(v -> null);
    }

    @Async
    public CompletableFuture<Void> sendVerificationEmail(Long userId) {
        LOGGER.info("Processing email verification request.");

        LOGGER.info("Requesting user information.");
        UserEmailResponseDto responseDto = userServiceClient.getEmailInfoByUserId(userId);
        if (responseDto.isVerified()) {
            LOGGER.error("Email {} is already verified.", responseDto.getEmail());
            throw new EmailAlreadyVerifiedException();
        }

        LOGGER.info("Request generating token for user ID: {}", userId);
        String tokenValue = tokenServiceClient.generateToken(new TokenRequest(userId));
        LOGGER.info("Successfully generated token: {}", tokenValue);

        String subject = "Email Verification";
        String verificationUrl = verificationBaseUrl + verificationVerifyUrl + "?token=" + tokenValue;
        String body = "Please click the following link to verify your email: " + verificationUrl;

        return sendSimpleEmail(new EmailRequest(responseDto.getEmail(), subject, body))

                .exceptionally(ex -> {
                    LOGGER.error("Failed to send verification email: {}", ex.getMessage());
                    throw new EmailSendingFailedException(responseDto.getEmail());
                }).thenApply(v -> null);
    }

    public void verifyEmail(String token) {
        LOGGER.info("Starting email verification process for token: {}", token);

        TokenValidationResponse response = tokenServiceClient.verifyToken(token);
        LOGGER.info("Token validated successfully for user ID: {}", response.getUserId());

        userServiceClient.markEmailAsVerified(response.getUserId());
        LOGGER.info("Email verification status updated successfully for user ID: {}", response.getUserId());
    }
}
