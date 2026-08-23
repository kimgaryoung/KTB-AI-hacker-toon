package com.relationshiptemperature.api.auth.repository;

import com.relationshiptemperature.api.auth.domain.User;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByKakaoSubject(String kakaoSubject);
}
