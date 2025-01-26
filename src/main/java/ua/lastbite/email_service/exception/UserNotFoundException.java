package ua.lastbite.email_service.exception;

public class UserNotFoundException extends RuntimeException {
    public UserNotFoundException(long id) {
        super("User with ID " + id + " not found");
    }
}
