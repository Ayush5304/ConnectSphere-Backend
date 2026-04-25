package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    long countByIsActiveTrue();
    long countByRole(User.Role role);
    Optional<User> findByResetToken(String resetToken);
    long countByLastLoginAtAfter(LocalDateTime since);
}
