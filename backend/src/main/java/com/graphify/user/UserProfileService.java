package com.graphify.user;

import com.graphify.common.dto.ApiResponse;
import com.graphify.common.exception.GraphifyException;
import com.graphify.history.HistoryService;
import com.graphify.user.dto.ChangePasswordRequest;
import com.graphify.user.dto.MessageResponseDto;
import com.graphify.user.dto.UpdatePromptRequest;
import com.graphify.user.dto.UserMeDto;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class UserProfileService {

    private final UserRepository userRepository;
    private final UserAuthProviderRepository userAuthProviderRepository;
    private final UserPreferenceRepository userPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    public UserProfileService(
            UserRepository userRepository,
            UserAuthProviderRepository userAuthProviderRepository,
            UserPreferenceRepository userPreferenceRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.userAuthProviderRepository = userAuthProviderRepository;
        this.userPreferenceRepository = userPreferenceRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public ApiResponse<UserMeDto> getMe() {
        Long userId = HistoryService.requireCurrentUserId();
        User user = findUserOrThrow(userId);
        return ApiResponse.ok(toMeDto(user));
    }

    @Transactional
    public ApiResponse<MessageResponseDto> changePassword(ChangePasswordRequest request) {
        Long userId = HistoryService.requireCurrentUserId();
        User user = findUserOrThrow(userId);

        if (user.getPasswordHash() == null) {
            throw new GraphifyException(
                    "ERR_USER_002",
                    "이메일 로그인 계정이 아닙니다. 소셜 로그인을 이용해 주세요.",
                    HttpStatus.BAD_REQUEST
            );
        }

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new GraphifyException(
                    "ERR_USER_003",
                    "현재 비밀번호가 올바르지 않습니다.",
                    HttpStatus.BAD_REQUEST
            );
        }

        validateNewPassword(request.newPassword());

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new GraphifyException(
                    "ERR_USER_004",
                    "새 비밀번호는 현재 비밀번호와 달라야 합니다.",
                    HttpStatus.BAD_REQUEST
            );
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);

        return ApiResponse.ok(new MessageResponseDto(
                "비밀번호가 변경되었습니다. 보안을 위해 다시 로그인해 주세요."
        ));
    }

    @Transactional
    public ApiResponse<MessageResponseDto> updatePrompt(UpdatePromptRequest request) {
        Long userId = HistoryService.requireCurrentUserId();
        User user = findUserOrThrow(userId);

        if (!user.isPremium()) {
            throw new GraphifyException(
                    "ERR_USER_005",
                    "Premium 구독자만 커스텀 프롬프트를 사용할 수 있습니다.",
                    HttpStatus.FORBIDDEN
            );
        }

        String prompt = request.customPrompt() == null ? "" : request.customPrompt().trim();
        if (prompt.length() > 4000) {
            throw new GraphifyException(
                    "ERR_USER_006",
                    "프롬프트는 4000자 이하여야 합니다.",
                    HttpStatus.BAD_REQUEST
            );
        }

        UserPreference preference = userPreferenceRepository.findById(userId)
                .orElseGet(() -> new UserPreference(userId));
        preference.setCustomPrompt(prompt.isEmpty() ? null : prompt);
        preference.touchUpdatedAt();
        userPreferenceRepository.save(preference);

        return ApiResponse.ok(new MessageResponseDto("프롬프트가 저장되었습니다."));
    }

    private UserMeDto toMeDto(User user) {
        String authProvider = resolveAuthProvider(user);
        String customPrompt = userPreferenceRepository.findById(user.getId())
                .map(UserPreference::getCustomPrompt)
                .orElse(null);
        return new UserMeDto(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                authProvider,
                user.isPremium(),
                customPrompt,
                user.isTradingEnabled(),
                user.getTradingMode()
        );
    }

    private String resolveAuthProvider(User user) {
        if (user.getPasswordHash() != null) {
            return "email";
        }
        return userAuthProviderRepository.findFirstByUser_Id(user.getId())
                .map(UserAuthProvider::getProvider)
                .orElse("email");
    }

    private User findUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new GraphifyException(
                        "ERR_USER_001",
                        "사용자 정보를 찾을 수 없습니다.",
                        HttpStatus.NOT_FOUND
                ));
    }

    private static void validateNewPassword(String password) {
        if (password == null || password.length() < 8) {
            throw new GraphifyException(
                    "ERR_USER_007",
                    "비밀번호는 8자 이상이어야 합니다.",
                    HttpStatus.BAD_REQUEST
            );
        }
    }
}
