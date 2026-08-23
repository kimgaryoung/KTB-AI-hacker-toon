package com.relationshiptemperature.api.auth.application;

import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KakaoUserProvisioningService {

    private final UserRepository userRepository;

    public KakaoUserProvisioningService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public User upsert(String subject, String displayName, String profileImageUrl) {
        return userRepository.findByKakaoSubject(subject)
                .map(existing -> {
                    existing.updateProfile(displayName, profileImageUrl);
                    return existing;
                })
                .orElseGet(() -> userRepository.save(User.kakao(subject, displayName, profileImageUrl)));
    }
}
