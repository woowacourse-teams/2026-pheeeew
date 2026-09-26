package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull

@OptIn(ExperimentalCoroutinesApi::class)
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

    @Test
    fun `이름 설명 제한을 API 계약에 맞춰 적용한다`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("가")
        viewModel.onStampLabelChanged("기록")
        viewModel.onCreateClick()
        assertEquals(GroupCreateFieldError.TooShort, viewModel.uiState.value.fieldErrors.name)

        viewModel.onNameChanged("모임")
        viewModel.onDescriptionChanged("설명".repeat(50))
        viewModel.onCreateClick()
        assertIs<GroupCreateSubmissionState.Confirming>(viewModel.uiState.value.submission)

        viewModel.onCancelConfirmation()
        viewModel.onDescriptionChanged("설명".repeat(51))
        viewModel.onCreateClick()
        assertEquals(GroupCreateFieldError.TooLong, viewModel.uiState.value.fieldErrors.description)
    }

    @Test
    fun `불명확한 생성은 목록 확인 전에 POST를 다시 보내지 않고 선택한 ID로 성공을 전달한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var createCalls = 0
                var candidateCalls = 0
                val candidateId = GroupId("10000000-0000-0000-0000-000000000001")
                val viewModel =
                    createViewModel(
                        createAction =
                            CreateGroupAction {
                                createCalls += 1
                                CreateGroupResult.OutcomeUnknown
                            },
                        findCandidatesAction =
                            FindGroupCreateCandidatesAction { name ->
                                candidateCalls += 1
                                assertEquals("기록모임", name)
                                GroupCreateCandidatesResult.Loaded(listOf(GroupCreateCandidate(candidateId, name)))
                            },
                    )

                submitValidDraft(viewModel)
                viewModel.onConfirmCreate()
                runCurrent()
                assertEquals(1, createCalls)
                val failed = assertIs<GroupCreateSubmissionState.Failed>(viewModel.uiState.value.submission)
                assertEquals(GroupCreateFailure.OutcomeUnknown, failed.reason)
                assertNotNull(failed.operationKey)

                viewModel.onRetryFailure()
                viewModel.onRetryUnknownCreation()
                runCurrent()
                assertEquals(1, createCalls)

                viewModel.onCheckGroupsAfterUnknownOutcome()
                runCurrent()
                assertEquals(1, candidateCalls)
                assertIs<GroupCreateRecoveryState.Loaded>(viewModel.uiState.value.recovery)

                viewModel.onSelectRecoveryCandidate(candidateId)
                val succeeded = assertIs<GroupCreateSubmissionState.Succeeded>(viewModel.uiState.value.submission)
                assertEquals(candidateId, succeeded.groupId)
                assertEquals(failed.operationKey, succeeded.operationKey)
                assertEquals(1, createCalls)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `불명확한 생성은 목록을 성공적으로 확인한 뒤 명시한 경우에만 재전송한다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                var createCalls = 0
                val viewModel =
                    createViewModel(
                        createAction =
                            CreateGroupAction {
                                createCalls += 1
                                if (createCalls == 1) {
                                    CreateGroupResult.OutcomeUnknown
                                } else {
                                    CreateGroupResult.Created(GroupId("10000000-0000-0000-0000-000000000002"))
                                }
                            },
                        findCandidatesAction =
                            FindGroupCreateCandidatesAction { GroupCreateCandidatesResult.Loaded(emptyList()) },
                    )

                submitValidDraft(viewModel)
                viewModel.onConfirmCreate()
                runCurrent()
                assertEquals(1, createCalls)

                viewModel.onRetryUnknownCreation()
                runCurrent()
                assertEquals(1, createCalls)

                viewModel.onCheckGroupsAfterUnknownOutcome()
                runCurrent()
                viewModel.onRetryUnknownCreation()
                runCurrent()
                assertEquals(2, createCalls)
                assertIs<GroupCreateSubmissionState.Succeeded>(viewModel.uiState.value.submission)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `중복 이름은 초안을 보존하고 이름을 고치면 중복 오류를 지운다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val viewModel = createViewModel(CreateGroupAction { CreateGroupResult.DuplicateName })
                submitValidDraft(viewModel)
                viewModel.onConfirmCreate()
                runCurrent()

                assertIs<GroupCreateSubmissionState.Editing>(viewModel.uiState.value.submission)
                assertEquals("기록모임", viewModel.uiState.value.draft.name)
                assertEquals(GroupCreateFieldError.Duplicate, viewModel.uiState.value.fieldErrors.name)

                viewModel.onNameChanged("새 모임")
                assertEquals(null, viewModel.uiState.value.fieldErrors.name)
                assertEquals("새 모임", viewModel.uiState.value.draft.name)
            } finally {
                Dispatchers.resetMain()
            }
        }

    @Test
    fun `성공 응답의 서버 ID는 acknowledge될 때까지 유지된다`() =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                val createdId = GroupId("10000000-0000-0000-0000-000000000003")
                val viewModel =
                    createViewModel(CreateGroupAction { CreateGroupResult.Created(createdId) })
                submitValidDraft(viewModel)
                viewModel.onConfirmCreate()
                runCurrent()

                val success = assertIs<GroupCreateSubmissionState.Succeeded>(viewModel.uiState.value.submission)
                assertEquals(createdId, success.groupId)
                viewModel.acknowledgeCreated(success.operationKey)
                assertIs<GroupCreateSubmissionState.Acknowledged>(viewModel.uiState.value.submission)
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun submitValidDraft(viewModel: GroupCreateViewModel) {
        viewModel.onNameChanged("기록모임")
        viewModel.onStampLabelChanged("기록")
        viewModel.onCreateClick()
    }

    private fun createViewModel(
        createAction: CreateGroupAction = CreateGroupAction { CreateGroupResult.Unavailable },
        findCandidatesAction: FindGroupCreateCandidatesAction =
            FindGroupCreateCandidatesAction { GroupCreateCandidatesResult.Unavailable },
    ) = GroupCreateViewModel(
        createGroupAction = createAction,
        errorReporter = GroupCreateErrorReporter {},
        operationKeyAllocator = GroupOperationKeyAllocator("test"),
        findCandidatesAction = findCandidatesAction,
    )
}
