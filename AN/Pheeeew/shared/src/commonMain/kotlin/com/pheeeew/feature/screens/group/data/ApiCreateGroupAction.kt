package com.pheeeew.feature.screens.group.data

import com.pheeeew.domain.model.group.GroupStamp
import com.pheeeew.domain.model.group.StampColor
import com.pheeeew.domain.repository.GroupCreateCommand
import com.pheeeew.domain.repository.GroupCreateRepository
import com.pheeeew.domain.repository.GroupCreateRepositoryResult
import com.pheeeew.feature.component.stamp.toDomainFrame
import com.pheeeew.feature.screens.group.create.CreateGroupAction
import com.pheeeew.feature.screens.group.create.CreateGroupResult
import com.pheeeew.feature.screens.group.create.FindGroupCreateCandidatesAction
import com.pheeeew.feature.screens.group.create.GroupCreateCandidate
import com.pheeeew.feature.screens.group.create.GroupCreateCandidatesResult
import com.pheeeew.feature.screens.group.create.GroupCreateDraft
import com.pheeeew.feature.screens.group.home.GroupListResult
import com.pheeeew.feature.screens.group.home.GroupListSource
import com.pheeeew.feature.screens.group.model.GroupId

class ApiCreateGroupAction(
    private val repository: GroupCreateRepository,
) : CreateGroupAction {
    override suspend fun create(draft: GroupCreateDraft): CreateGroupResult {
        val textColor = StampColor.fromArgb(draft.stamp.textArgb) ?: return CreateGroupResult.Unavailable
        val backgroundColor = StampColor.fromArgb(draft.stamp.fillArgb) ?: return CreateGroupResult.Unavailable
        val command =
            GroupCreateCommand(
                name = draft.name,
                description = draft.description.takeIf { it.isNotEmpty() },
                stamp =
                    GroupStamp(
                        text = draft.stamp.label,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        frame = draft.stamp.shape.toDomainFrame(),
                    ),
            )

        return when (val result = repository.create(command)) {
            is GroupCreateRepositoryResult.Created -> CreateGroupResult.Created(GroupId(result.groupId.value))
            GroupCreateRepositoryResult.DuplicateName -> CreateGroupResult.DuplicateName
            GroupCreateRepositoryResult.InvalidInput -> CreateGroupResult.InvalidInput
            GroupCreateRepositoryResult.RateLimited -> CreateGroupResult.RateLimited
            GroupCreateRepositoryResult.Unavailable -> CreateGroupResult.Unavailable
            GroupCreateRepositoryResult.OutcomeUnknown -> CreateGroupResult.OutcomeUnknown
        }
    }
}

/** Reads membership summaries and offers only exact-name matches as recovery candidates. */
class GroupListCreateRecoveryAction(
    private val groupListSource: GroupListSource,
) : FindGroupCreateCandidatesAction {
    override suspend fun findCandidates(groupName: String): GroupCreateCandidatesResult =
        when (val result = groupListSource.loadGroups()) {
            is GroupListResult.Success -> {
                GroupCreateCandidatesResult.Loaded(
                    result.groups
                        .filter { it.name == groupName }
                        .map {
                            GroupCreateCandidate(
                                groupId = it.id,
                                name = it.name,
                            )
                        },
                )
            }

            GroupListResult.Unavailable -> {
                GroupCreateCandidatesResult.Unavailable
            }
        }
}
