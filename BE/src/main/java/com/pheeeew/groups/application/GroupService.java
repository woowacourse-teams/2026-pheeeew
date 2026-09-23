package com.pheeeew.groups.application;

import static com.pheeeew.device.exception.DeviceErrorCode.DEVICE_NOT_FOUND;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_INVITE_CODE_UNAVAILABLE;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_MEMBER_REMAINS;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NAME_DUPLICATED;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_NOT_FOUND;
import static com.pheeeew.groups.exception.GroupErrorCode.GROUP_OWNER_ONLY;

import com.pheeeew.device.domain.Device;
import com.pheeeew.device.domain.repository.DeviceRepository;
import com.pheeeew.device.exception.DeviceException;
import com.pheeeew.groups.application.dto.GroupResult;
import com.pheeeew.groups.application.dto.GroupStampCommand;
import com.pheeeew.groups.application.dto.GroupStampResult;
import com.pheeeew.groups.domain.Group;
import com.pheeeew.groups.domain.GroupMember;
import com.pheeeew.groups.domain.GroupRole;
import com.pheeeew.groups.domain.GroupStamp;
import com.pheeeew.groups.domain.repository.GroupMemberRepository;
import com.pheeeew.groups.domain.repository.GroupRepository;
import com.pheeeew.groups.domain.repository.GroupStampRepository;
import com.pheeeew.groups.exception.GroupException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class GroupService {

    private static final int MAX_INVITE_CODE_ATTEMPTS = 10;

    private final GroupRepository groupRepository;
    private final GroupStampRepository groupStampRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final DeviceRepository deviceRepository;
    private final InviteCodeGenerator inviteCodeGenerator;

    @Transactional
    public GroupResult save(UUID devicePublicId, String name, String description, GroupStampCommand stampCommand) {
        Device device = findDevice(devicePublicId);
        String trimmedName = name.strip();
        requireUnusedName(trimmedName);

        Group group = saveWithUniqueInviteCode(trimmedName, description);
        GroupStamp stamp = groupStampRepository.save(newStamp(group, stampCommand));
        groupMemberRepository.save(GroupMember.builder()
                .group(group)
                .device(device)
                .role(GroupRole.OWNER)
                .build());

        return GroupResult.of(group, GroupRole.OWNER, 1, GroupStampResult.from(stamp));
    }

    public List<GroupResult> findMine(UUID devicePublicId) {
        Device device = findDevice(devicePublicId);

        return groupMemberRepository.findByDeviceIdAndLeftAtIsNull(device.getId()).stream()
                .filter(member -> !member.getGroup().isDeleted())
                .sorted(Comparator.comparing(GroupMember::getCreatedAt))
                .map(member -> toResult(member.getGroup(), member.getRole()))
                .toList();
    }

    public GroupResult findOne(UUID groupPublicId, UUID devicePublicId) {
        Group group = findGroup(groupPublicId);
        GroupMember member = requireMember(group, devicePublicId);

        return toResult(group, member.getRole());
    }

    @Transactional
    public GroupResult update(
            UUID groupPublicId,
            UUID devicePublicId,
            String name,
            String description,
            GroupStampCommand stampCommand
    ) {
        Group group = findGroup(groupPublicId);
        requireOwner(group, devicePublicId);

        String trimmedName = name.strip();
        if (!group.getName().equals(trimmedName)) {
            requireUnusedName(trimmedName);
        }
        group.rename(trimmedName, description);
        findStamp(group).change(
                stampCommand.text(),
                stampCommand.textColor(),
                stampCommand.backgroundColor(),
                stampCommand.frame()
        );

        return toResult(group, GroupRole.OWNER);
    }

    @Transactional
    public GroupResult reissueInviteCode(UUID groupPublicId, UUID devicePublicId) {
        Group group = findGroup(groupPublicId);
        requireOwner(group, devicePublicId);
        group.reissueInviteCode(unusedInviteCode());

        return toResult(group, GroupRole.OWNER);
    }

    @Transactional
    public void delete(UUID groupPublicId, UUID devicePublicId) {
        Group group = findGroup(groupPublicId);
        requireOwner(group, devicePublicId);
        if (groupMemberRepository.countByGroupIdAndLeftAtIsNull(group.getId()) > 1) {
            throw new GroupException(GROUP_MEMBER_REMAINS);
        }

        group.delete(Instant.now());
    }

    private Device findDevice(UUID devicePublicId) {
        return deviceRepository.findByPublicId(devicePublicId)
                .orElseThrow(() -> new DeviceException(DEVICE_NOT_FOUND));
    }

    private void requireUnusedName(String name) {
        if (groupRepository.existsByNameAndDeletedAtIsNull(name)) {
            throw new GroupException(GROUP_NAME_DUPLICATED);
        }
    }

    private Group saveWithUniqueInviteCode(String name, String description) {
        try {
            return groupRepository.save(Group.builder()
                    .name(name)
                    .description(description)
                    .inviteCode(unusedInviteCode())
                    .build());
        } catch (DataIntegrityViolationException exception) {
            throw new GroupException(GROUP_NAME_DUPLICATED, exception);
        }
    }

    private String unusedInviteCode() {
        for (int attempt = 0; attempt < MAX_INVITE_CODE_ATTEMPTS; attempt++) {
            String candidate = inviteCodeGenerator.generate();
            if (!groupRepository.existsByInviteCode(candidate)) {
                return candidate;
            }
        }

        throw new GroupException(GROUP_INVITE_CODE_UNAVAILABLE);
    }

    private GroupStamp newStamp(Group group, GroupStampCommand stampCommand) {
        return GroupStamp.builder()
                .group(group)
                .text(stampCommand.text())
                .textColor(stampCommand.textColor())
                .backgroundColor(stampCommand.backgroundColor())
                .frame(stampCommand.frame())
                .build();
    }

    private Group findGroup(UUID groupPublicId) {
        return groupRepository.findByPublicIdAndDeletedAtIsNull(groupPublicId)
                .orElseThrow(() -> new GroupException(GROUP_NOT_FOUND));
    }

    private GroupMember requireMember(Group group, UUID devicePublicId) {
        Device device = findDevice(devicePublicId);

        return groupMemberRepository.findByGroupIdAndDeviceIdAndLeftAtIsNull(group.getId(), device.getId())
                .orElseThrow(() -> new GroupException(GROUP_NOT_FOUND));
    }

    private void requireOwner(Group group, UUID devicePublicId) {
        if (!requireMember(group, devicePublicId).isOwner()) {
            throw new GroupException(GROUP_OWNER_ONLY);
        }
    }

    private GroupStamp findStamp(Group group) {
        return groupStampRepository.findByGroupId(group.getId())
                .orElseThrow(() -> new GroupException(GROUP_NOT_FOUND));
    }

    private GroupResult toResult(Group group, GroupRole role) {
        return GroupResult.of(
                group,
                role,
                groupMemberRepository.countByGroupIdAndLeftAtIsNull(group.getId()),
                GroupStampResult.from(findStamp(group))
        );
    }
}
