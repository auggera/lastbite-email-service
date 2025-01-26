package ua.lastbite.email_service.controller;

import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ua.lastbite.email_service.dto.email.EmailRequest;
import ua.lastbite.email_service.service.EmailService;

import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/emails")
@Slf4j
public class EmailController {

    private final EmailService emailService;

    @Autowired
    public EmailController(EmailService emailService) {
        this.emailService = emailService;
    }

    @PostMapping()
    public ResponseEntity<String> sendEmail(@Valid @RequestBody EmailRequest request) {
        log.info("Request received: POST /api/emails - Send email");
        try {
            emailService.sendSimpleEmail(request).get();
            log.info("Email sent successfully to: {}", request.getToEmail());
            return ResponseEntity.ok("Email sent successfully");
        } catch (InterruptedException ex) {
            log.error("Failed to send email to: {}", request.getToEmail(), ex);
            Thread.currentThread().interrupt();
            throw new CompletionException(ex);
        } catch (ExecutionException ex) {
            log.error("Failed to send email to: {}", request.getToEmail(), ex);
            throw new CompletionException(ex);
        }
    }

    @PostMapping("/verification/users/{id}")
    public ResponseEntity<String> sendVerificationEmail(@PathVariable Long id) {
        log.info("Request received: POST /api/emails/verification - Sending verification email for user ID: {}", id);
        try {
            emailService.sendVerificationEmail(id).get();
            log.info("Verification email sent successfully for user ID: {}", id);
            return ResponseEntity.ok("Email Verification sent successfully");
        } catch (InterruptedException ex) {
            log.error("Failed to send verification email for user ID: {}", id, ex);
            Thread.currentThread().interrupt();
            throw new CompletionException(ex);
        } catch (ExecutionException ex) {
            log.error("Failed to send verification email for user ID: {}", id, ex);
            throw new CompletionException(ex);
        }
    }

    @PostMapping("/verification/{token}")
    public ResponseEntity<String> verifyEmail(@PathVariable("token") String token) {
        log.info("Request received: POST /api/emails/verification/{} - Verifying email", token);
        emailService.verifyEmail(token);
        log.info("Email verification successful for token: {}", token);
        return ResponseEntity.ok("Email successfully verified");
    }
}
