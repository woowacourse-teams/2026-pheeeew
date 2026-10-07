# ADR-0030: 그룹 상세 조회를 전체 공개로 열고 초대 코드 노출을 감수한다

## Status

Accepted (2026-10-06)

## Context

- `GET /api/v2/groups/{groupId}`는 그 그룹에 가입한 기기만 조회할 수 있고, 아니면 `GROUP-008`, 403으로 끊어요.
- 그룹 랭킹 세 엔드포인트는 이미 전체 공개이고 응답에 `groupId`가 들어 있어요. 그래서 클라이언트는 그룹을 발견할 수 있는데 눌러서 들어가면 403을 받아요.
- 상세 응답에는 `inviteCode`가 들어 있고, 초대 코드는 지금 `POST /api/v2/groups/join`의 유일한 관문이에요.
- `GroupRole`은 `group_members.role`에 `@Enumerated(STRING)`으로 저장되고, `uk_group_members_owner` 부분 유니크 인덱스가 `role = 'OWNER'`를 참조해요.
- 클라이언트는 `OWNER`도 `MEMBER`도 아니면 가입자용 화면 요소를 감춰요. 그래서 응답이 비가입자임을 구분해 줘야 해요.
- [이슈 #655](https://github.com/woowacourse-teams/2026-pheeeew/issues/655)가 이 변경을 요구하고, [이슈 #647](https://github.com/woowacourse-teams/2026-pheeeew/issues/647)이 공개, 비공개 가입 방식을 상위 묶음으로 다뤄요.

## Decision

`findOne`에서 멤버십 검사를 없애 가입 여부와 무관하게 상세를 조회할 수 있게 하고, **`inviteCode`를 역할과 무관하게 모두에게 담아요.** 초대 코드가 사실상 비밀이 아니게 되는 것을 알고 감수해요.
비가입자의 역할은 응답 전용 `GroupViewerRole`(`OWNER`, `MEMBER`, `NONE`)로 표현하고 도메인 `GroupRole`은 그대로 둬요.
상세 응답에서 프레스 세 필드(`todayPresses`, `weeklyPresses`, `weeklyPressRank`)를 제거해 스탬프 횟수와 스탬프 랭킹만 남겨요.

## Alternatives

- 현상 유지 — 랭킹에서 그룹을 발견해도 열 수 없는 상태가 남아요.
- 비가입자 응답에서 `inviteCode`를 빼기 — 코드를 지키지만, 이번 범위에서 좁히지 않기로 했어요. #647에서 다시 결정해요.
- 도메인 `GroupRole`에 `NONE` 추가 — `group_members.role`에 저장될 수 없는 값이 영속 enum에 생겨요.
- `role`을 nullable `GroupRole`로 두기 — OpenAPI 타입이 하나로 유지되지만, null이 "비가입자"와 "서버가 값을 못 채움"을 구분하지 못하고 그 약속이 타입이 아니라 매핑 코드에만 남아요.
- 응답 전용 enum을 `presentation`에 두기 — 역할을 판정하는 주체는 서비스인데 타입 위치가 갈려요.

## Consequences

- (+) 랭킹에서 발견한 그룹을 가입 여부와 무관하게 열어볼 수 있어요.
- (+) 프레스 랭킹 계산이 빠지면서 상세 한 번에 전 그룹을 집계하는 횟수가 두 번에서 한 번으로 줄어요.
- (-) **누구나 상세를 열어 초대 코드를 읽고 어느 그룹에나 가입할 수 있어요.** 초대 코드는 더 이상 가입을 통제하지 못해요.
- (-) OpenAPI 스키마에 `GroupRole`과 `GroupViewerRole`이 함께 노출되어 생성 클라이언트에 역할 타입이 둘 생겨요.
- (=) 조회를 열어도 수정, 삭제, 초대 코드 재발급, 프레스, 나가기는 기존 권한 제한을 그대로 유지해요. 인증 자체도 유지하므로 비로그인 공개는 아니에요.
- (=) 그룹을 나간 기기는 비가입자와 같게 `NONE`이에요. 멤버 판정이 `left_at IS NULL`을 보기 때문이에요.
- (=) 프레스 집계는 `GET /api/v2/groups/{groupId}/presses`에만 남고, 그 엔드포인트는 멤버 전용이에요.
- 재검토: **#647 착수 시점에 `inviteCode` 노출 범위를 다시 결정해요.** 공개, 비공개 가입 방식을 도입하면 비공개 그룹의 코드를 가릴 이유가 생겨요.
