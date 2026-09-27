package br.com.fiap.ford.journey.security;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DemoUserDirectory {
    private final PasswordEncoder encoder;
    private final Map<String, User> users;

    public DemoUserDirectory(PasswordEncoder encoder) {
        this.encoder = encoder;
        String password = encoder.encode("Ford@123");
        this.users = Map.of(
                "admin@ford.com", new User("admin@ford.com", password, Set.of("ADMIN")),
                "adviser@ford.com", new User("adviser@ford.com", password, Set.of("ADVISER")),
                "customer@ford.com", new User("customer@ford.com", password, Set.of("CUSTOMER")),
                "technician@ford.com", new User("technician@ford.com", password, Set.of("TECHNICIAN")),
                "vehicle@ford.com", new User("vehicle@ford.com", password, Set.of("VEHICLE")));
    }

    public Optional<User> authenticate(String username, String password) {
        User user = users.get(username.toLowerCase());
        return user != null && encoder.matches(password, user.passwordHash()) ? Optional.of(user) : Optional.empty();
    }

    public record User(String username, String passwordHash, Set<String> roles) {}
}
