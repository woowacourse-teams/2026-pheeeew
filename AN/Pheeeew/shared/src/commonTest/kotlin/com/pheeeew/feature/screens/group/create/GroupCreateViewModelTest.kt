package com.pheeeew.feature.screens.group.create

import com.pheeeew.feature.component.stamp.StampShapeId
import com.pheeeew.feature.screens.group.create.model.StampTextColorOption
import com.pheeeew.feature.screens.group.model.GroupId
import com.pheeeew.feature.screens.group.model.GroupOperationKeyAllocator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupCreateViewModelTest {
    @Test
    fun `정규화 후 한 글자 스탬프 문구는 확인 단계로 넘어간다`() =
        runViewModelTest {
            val viewModel = createViewModel()
            viewModel.onNameChanged("히유")
            viewModel.onStampLabelChanged("가")

            viewModel.onCreateClick()

            assertEquals(null, viewModel.uiState.value.fieldErrors.stampLabel)
            assertIs<GroupCreateSubmissionState.Confirming>(viewModel.uiState.value.submission)
        }

    @Test
    fun `정규화 후 최대 길이 이내인 그룹명은 확인 단계로 넘어간다`() =
        runViewModelTest {
            val viewModel = createViewModel()
            viewModel.onNameChanged(" ${"가".repeat(10)} ")
            viewModel.onStampLabelChanged("기록")

            viewModel.onCreateClick()

            assertEquals(null, viewModel.uiState.value.fieldErrors.name)
            assertIs<GroupCreateSubmissionState.Confirming>(viewModel.uiState.value.submission)
        }

    @Test
    fun `그룹 이름과 설명은 입력 단계에서 API 길이 제한을 적용한다`() =
        runViewModelTest {
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
            assertEquals(100, viewModel.formRules.count(viewModel.uiState.value.draft.description))
            viewModel.onCreateClick()
            assertIs<GroupCreateSubmissionState.Confirming>(viewModel.uiState.value.submission)
        }

    @Test
    fun `불명확한 생성은 목록 확인 전에 POST를 다시 보내지 않고 선택한 ID로 성공을 전달한다`() =
        runViewModelTest {
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

            viewModel.onRetryUnknownCreation()
            runCurrent()
            assertEquals(1, createCalls)

            viewModel.onSelectRecoveryCandidate(candidateId)
            val succeeded = assertIs<GroupCreateSubmissionState.Succeeded>(viewModel.uiState.value.submission)
            assertEquals(candidateId, succeeded.groupId)
            assertEquals(failed.operationKey, succeeded.operationKey)
            assertEquals(1, createCalls)
        }

    @Test
    fun `불명확한 생성은 목록을 성공적으로 확인하고 후보가 없을 때 명시한 경우에만 재전송한다`() =
        runViewModelTest {
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
                        FindGroupCreateCandidatesAction {
                            GroupCreateCandidatesResult.Loaded(
                                emptyList(),
                            )
                        },
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
        }

    @Test
    fun `중복 이름은 초안을 보존하고 이름을 고치면 중복 오류를 지운다`() =
        runViewModelTest {
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
        }

    @Test
    fun `성공 응답의 서버 ID는 acknowledge될 때까지 유지된다`() =
        runViewModelTest {
            val createdId = GroupId("10000000-0000-0000-0000-000000000003")
            val store = InMemoryGroupCreateSessionStore()
            val viewModel =
                createViewModel(
                    CreateGroupAction { CreateGroupResult.Created(createdId) },
                    sessionStore = store,
                )
            submitValidDraft(viewModel)
            viewModel.onConfirmCreate()
            runCurrent()

            val success = assertIs<GroupCreateSubmissionState.Succeeded>(viewModel.uiState.value.submission)
            assertEquals(createdId, success.groupId)
            viewModel.acknowledgeCreated(success.operationKey)
            assertIs<GroupCreateSubmissionState.Acknowledged>(viewModel.uiState.value.submission)
            runCurrent()
            assertEquals(null, store.snapshot)
        }

    @Test
    fun `초안은 마지막 수정 후 7일 동안 복원되고 만료되면 한 번 안내한 뒤 삭제된다`() =
        runViewModelTest {
            val store = InMemoryGroupCreateSessionStore()
            var now = 1_000_000L
            val edited = createViewModel(sessionStore = store, nowMillis = { now })
            edited.onNameChanged("작성 중인 모임")
            runCurrent()
            assertEquals(now, store.snapshot?.draftUpdatedAtEpochMillis)

            now += GroupCreateSessionSnapshot.DRAFT_TTL_MILLIS - 1
            val restored = createViewModel(sessionStore = store, nowMillis = { now })
            assertEquals("작성 중인 모임", restored.uiState.value.draft.name)
            assertFalse(restored.uiState.value.isDraftExpiredNoticeVisible)

            now += 1
            val expired = createViewModel(sessionStore = store, nowMillis = { now })
            assertEquals(DEFAULT_GROUP_CREATE_DRAFT, expired.uiState.value.draft)
            assertEquals(true, expired.uiState.value.isDraftExpiredNoticeVisible)
            assertEquals(null, store.snapshot)

            val reopened = createViewModel(sessionStore = store, nowMillis = { now })
            assertFalse(reopened.uiState.value.isDraftExpiredNoticeVisible)
            assertEquals(DEFAULT_GROUP_CREATE_DRAFT, reopened.uiState.value.draft)
        }

    @Test
    fun `빈 폼은 바로 나가고 수정된 초안은 확인 후에만 저장 내용을 버린다`() =
        runViewModelTest {
            val store = InMemoryGroupCreateSessionStore()
            val empty = createViewModel(sessionStore = store)
            assertTrue(empty.onBackRequested())
            assertFalse(empty.uiState.value.isDiscardConfirmationVisible)

            val edited = createViewModel(sessionStore = store)
            edited.onNameChanged("버릴 모임")
            runCurrent()
            assertEquals("버릴 모임", store.snapshot?.draft?.name)

            assertFalse(edited.onBackRequested())
            assertTrue(edited.uiState.value.isDiscardConfirmationVisible)
            edited.onCancelDraftDiscard()
            assertFalse(edited.uiState.value.isDiscardConfirmationVisible)
            assertEquals("버릴 모임", store.snapshot?.draft?.name)

            assertFalse(edited.onBackRequested())
            assertTrue(edited.confirmDraftDiscardAndExit())
            assertEquals(null, store.snapshot)
            assertEquals(DEFAULT_GROUP_CREATE_DRAFT, edited.uiState.value.draft)
            assertFalse(edited.uiState.value.isDiscardConfirmationVisible)
        }

    @Test
    fun `초안 저장소 삭제에 실패하면 화면을 나가지 않고 폐기 확인을 유지한다`() =
        runViewModelTest {
            val store = InMemoryGroupCreateSessionStore()
            val viewModel = createViewModel(sessionStore = store)
            viewModel.onNameChanged("다시 시도할 모임")
            runCurrent()
            assertFalse(viewModel.onBackRequested())

            store.writeFailure = IllegalStateException("storage unavailable")
            assertFalse(viewModel.confirmDraftDiscardAndExit())
            assertTrue(viewModel.uiState.value.isDiscardConfirmationVisible)
            assertTrue(viewModel.uiState.value.discardFailed)
            assertEquals("다시 시도할 모임", viewModel.uiState.value.draft.name)
            assertEquals("다시 시도할 모임", store.snapshot?.draft?.name)

            store.writeFailure = null
            assertTrue(viewModel.confirmDraftDiscardAndExit())
            assertEquals(null, store.snapshot)
        }

    @Test
    fun `7일이 지난 초안이어도 미확정 생성 operation은 보존하고 POST를 재전송하지 않는다`() =
        runViewModelTest {
            val createdAt = 2_000_000L
            val pendingDraft = PersistedGroupCreateDraft(name = "결과 미확정 모임")
            val pending = PersistedGroupCreateOperation("previous-session", 5L, pendingDraft)
            val store =
                InMemoryGroupCreateSessionStore(
                    GroupCreateSessionSnapshot(
                        schemaVersion = 1,
                        draft = pendingDraft,
                        draftUpdatedAtEpochMillis = createdAt,
                        pendingOperation = pending,
                    ),
                )
            var createCalls = 0
            val now = createdAt + GroupCreateSessionSnapshot.DRAFT_TTL_MILLIS + 1
            val restored =
                createViewModel(
                    createAction =
                        CreateGroupAction {
                            createCalls += 1
                            CreateGroupResult.Created(GroupId("unexpected"))
                        },
                    findCandidatesAction =
                        FindGroupCreateCandidatesAction {
                            GroupCreateCandidatesResult.Loaded(emptyList())
                        },
                    sessionStore = store,
                    nowMillis = { now },
                )
            runCurrent()

            assertEquals(0, createCalls)
            assertEquals("결과 미확정 모임", restored.uiState.value.draft.name)
            assertFalse(restored.uiState.value.isDraftExpiredNoticeVisible)
            assertEquals(pending, store.snapshot?.pendingOperation)
            assertEquals(GroupCreateSessionSnapshot.CURRENT_SCHEMA_VERSION, store.snapshot?.schemaVersion)
        }

    @Test
    fun `미확정 생성의 draft와 operation은 재진입 뒤 복원되고 POST를 자동 재전송하지 않는다`() =
        runViewModelTest {
            val store = InMemoryGroupCreateSessionStore()
            var createCalls = 0
            var recoveryCalls = 0
            val first =
                createViewModel(
                    createAction =
                        CreateGroupAction {
                            createCalls += 1
                            CreateGroupResult.OutcomeUnknown
                        },
                    sessionStore = store,
                )
            first.onNameChanged("기록모임")
            first.onDescriptionChanged("설명 보존")
            first.onStampLabelChanged("기록")
            first.onStampShapeChanged(StampShapeId.TICKET)
            first.onStampTextColorChanged(StampTextColorOption.WHITE)
            submitActionReadyDraft(first)
            first.onConfirmCreate()
            runCurrent()
            assertEquals(1, createCalls)
            first.onDismissFailure()
            assertIs<GroupCreateSubmissionState.Failed>(first.uiState.value.submission)
            assertEquals(false, first.uiState.value.isRecoveryDialogVisible)
            assertNotNull(store.snapshot?.pendingOperation)
            assertTrue(first.onBackRequested())

            val restored =
                createViewModel(
                    createAction =
                        CreateGroupAction {
                            createCalls += 1
                            CreateGroupResult.Created(GroupId("10000000-0000-0000-0000-000000000004"))
                        },
                    findCandidatesAction =
                        FindGroupCreateCandidatesAction {
                            recoveryCalls += 1
                            GroupCreateCandidatesResult.Loaded(emptyList())
                        },
                    sessionStore = store,
                )
            runCurrent()

            assertEquals(1, createCalls)
            assertEquals(1, recoveryCalls)
            assertEquals("기록모임", restored.uiState.value.draft.name)
            assertEquals("설명 보존", restored.uiState.value.draft.description)
            assertEquals(StampShapeId.TICKET, restored.uiState.value.draft.stamp.shape)
            assertEquals(StampTextColorOption.WHITE.argb, restored.uiState.value.draft.stamp.textArgb)
            assertEquals(true, restored.uiState.value.isRecoveryDialogVisible)
            assertEquals(
                GroupCreateFailure.OutcomeUnknown,
                assertIs<GroupCreateSubmissionState.Failed>(restored.uiState.value.submission).reason,
            )

            restored.onShowRecoveryDialog()
            runCurrent()
            assertEquals(1, createCalls)
            restored.onRetryUnknownCreation()
            runCurrent()
            assertEquals(2, createCalls)
        }

    private fun TestScope.submitValidDraft(viewModel: GroupCreateViewModel) {
        viewModel.onNameChanged("기록모임")
        viewModel.onStampLabelChanged("기록")
        viewModel.onCreateClick()
    }

    private fun TestScope.submitActionReadyDraft(viewModel: GroupCreateViewModel) {
        viewModel.onCreateClick()
    }

    private fun TestScope.createViewModel(
        createAction: CreateGroupAction = CreateGroupAction { CreateGroupResult.Unavailable },
        findCandidatesAction: FindGroupCreateCandidatesAction =
            FindGroupCreateCandidatesAction { GroupCreateCandidatesResult.Unavailable },
        sessionStore: GroupCreateSessionStore = InMemoryGroupCreateSessionStore(),
        nowMillis: () -> Long = { 1_000L },
    ): GroupCreateViewModel =
        GroupCreateViewModel(
            createGroupAction = createAction,
            errorReporter = GroupCreateErrorReporter {},
            operationKeyAllocator = GroupOperationKeyAllocator("test"),
            findCandidatesAction = findCandidatesAction,
            sessionStore = sessionStore,
            nowMillis = nowMillis,
        ).also { runCurrent() }

    private fun runViewModelTest(block: suspend TestScope.() -> Unit) =
        runTest {
            Dispatchers.setMain(StandardTestDispatcher(testScheduler))
            try {
                block()
            } finally {
                Dispatchers.resetMain()
            }
        }

    private class InMemoryGroupCreateSessionStore(
        var snapshot: GroupCreateSessionSnapshot? = null,
    ) : GroupCreateSessionStore {
        var writeFailure: Exception? = null

        override suspend fun read(): GroupCreateSessionSnapshot? = snapshot

        override suspend fun write(snapshot: GroupCreateSessionSnapshot?) {
            writeFailure?.let { throw it }
            this.snapshot = snapshot
        }
    }
}
