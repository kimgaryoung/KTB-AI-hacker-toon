package com.relationshiptemperature.api.auth.application;

import com.relationshiptemperature.api.auth.domain.User;
import com.relationshiptemperature.api.auth.repository.UserRepository;
import com.relationshiptemperature.api.common.error.ApiException;
import com.relationshiptemperature.api.common.error.ErrorCode;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class CurrentUserService {

    private final UserRepository userRepository;

    public CurrentUserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public UUID requireUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AppOAuth2User principal)) {
            throw new ApiException(ErrorCode.AUTH_REQUIRED);
        }
        return principal.userId();
    }

    public User requireUser() {
        return userRepository.findById(requireUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.AUTH_REQUIRED));
    }
}
