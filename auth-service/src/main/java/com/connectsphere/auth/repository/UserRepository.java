package com.connectsphere.auth.repository;

import com.connectsphere.auth.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;
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
    java.util.List<User> findByReportedTrue();
    @Modifying
    @Transactional
    @Query("""
        update User u
           set u.bio = :bio,
               u.fullName = :fullName,
               u.username = :username,
               u.profilePicture = :profilePicture,
               u.coverPicture = :coverPicture
         where u.userId = :userId
    """)
    int updateProfileFields(@Param("userId") Long userId,
                            @Param("bio") String bio,
                            @Param("fullName") String fullName,
                            @Param("username") String username,
                            @Param("profilePicture") String profilePicture,
                            @Param("coverPicture") String coverPicture);
}
