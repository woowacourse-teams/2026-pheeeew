package com.pheeeew.groups.fixture;

import com.pheeeew.device.domain.Device;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.StampFrame;
import java.security.SecureRandom;

public final class GroupFixture {

    private static final String 초대_코드_문자 = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int 초대_코드_길이 = 6;
    private static final SecureRandom 난수 = new SecureRandom();

    private GroupFixture() {
    }

    public static Group.GroupBuilder 기본_그룹_빌더() {
        return Group.builder()
                .name("한숨모임")
                .description("테스트 그룹입니다.")
                .inviteCode(무작위_초대_코드());
    }

    public static GroupStamp.GroupStampBuilder 기본_스탬프_빌더(Group group) {
        return GroupStamp.builder()
                .group(group)
                .text("기본")
                .textColor("#FFFFFF")
                .backgroundColor("#4A90D9")
                .frame(StampFrame.CIRCLE);
    }

    public static GroupMember.GroupMemberBuilder 그룹장_빌더(Group group, Device device) {
        return GroupMember.builder()
                .group(group)
                .device(device)
                .role(GroupRole.OWNER);
    }

    public static GroupMember.GroupMemberBuilder 일반_멤버_빌더(Group group, Device device) {
        return GroupMember.builder()
                .group(group)
                .device(device)
                .role(GroupRole.MEMBER);
    }

    public static String 무작위_초대_코드() {
        StringBuilder code = new StringBuilder(초대_코드_길이);
        for (int index = 0; index < 초대_코드_길이; index++) {
            code.append(초대_코드_문자.charAt(난수.nextInt(초대_코드_문자.length())));
        }

        return code.toString();
    }
}
