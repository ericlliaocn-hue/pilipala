package art.pilipala.api.auth.infra;

import java.util.Optional;
import java.util.UUID;

import art.pilipala.api.auth.domain.User;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private final UserMapper mapper;

    public UserRepository(UserMapper mapper) {
        this.mapper = mapper;
    }

    public Optional<User> findByUsername(String username) {
        return Optional.ofNullable(mapper.findByUsername(username));
    }

    public Optional<User> findByPhone(String phone) {
        return Optional.ofNullable(mapper.findByPhone(phone));
    }

    public Optional<User> findByEmail(String email) {
        return Optional.ofNullable(mapper.findByEmail(email));
    }

    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(mapper.findById(id));
    }

    public boolean existsByPhone(String phone) {
        return mapper.countByPhone(phone) > 0;
    }

    public boolean existsByEmail(String email) {
        return mapper.countByEmail(email) > 0;
    }

    public User insert(String phone, String email, String passwordHash, String locale) {
        return mapper.insertIdentity(phone, email, passwordHash, locale);
    }

    public boolean updatePhone(UUID id, String phone) {
        return mapper.updatePhone(id, phone) > 0;
    }

    public boolean updateEmail(UUID id, String email) {
        return mapper.updateEmail(id, email) > 0;
    }

    public boolean updatePasswordHash(UUID id, String passwordHash) {
        return mapper.updatePasswordHash(id, passwordHash) > 0;
    }
}
