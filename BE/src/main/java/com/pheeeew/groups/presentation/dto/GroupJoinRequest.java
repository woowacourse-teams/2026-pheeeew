package com.pheeeew.groups.presentation.dto;

import com.pheeeew.groups.application.InviteCodeGenerator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record GroupJoinRequest(
        @NotBlank(message = "초대 코드는 필수입니다.")
        @Pattern(regexp = "^[0-9A-HJKMNP-TV-Z]{6}$", message = "초대 코드 형식이 올바르지 않습니다.")
        String inviteCode
) {
    public GroupJoinRequest {
        inviteCode = InviteCodeGenerator.normalize(inviteCode);
    }
}
