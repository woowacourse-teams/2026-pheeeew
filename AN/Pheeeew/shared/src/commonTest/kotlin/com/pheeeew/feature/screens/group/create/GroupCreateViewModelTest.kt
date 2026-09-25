package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GroupCreateViewModelTest {
    @Test
    fun `정규화 후 최소 길이보다 짧은 스탬프 문구는 확인 단계로 넘어가지 않는다`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("히유")
        viewModel.onStampLabelChanged("가 ")

        viewModel.onCreateClick()

        assertEquals(GroupCreateFieldError.TooShort, viewModel.uiState.value.fieldErrors.stampLabel)
        assertIs<GroupCreateSubmissionState.Editing>(viewModel.uiState.value.submission)
    }

    @Test
    fun `정규화 후 최대 길이 이내인 그룹명은 확인 단계로 넘어간다`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged(" ${"가".repeat(10)} ")
        viewModel.onStampLabelChanged("기록")

        viewModel.onCreateClick()

        assertEquals(null, viewModel.uiState.value.fieldErrors.name)
        assertIs<GroupCreateSubmissionState.Confirming>(viewModel.uiState.value.submission)
    }

    private fun createViewModel() =
        GroupCreateViewModel(
            createGroupAction = CreateGroupAction { CreateGroupResult.Unavailable },
            errorReporter = GroupCreateErrorReporter {},
            operationKeyAllocator = GroupOperationKeyAllocator("test"),
        )
}
