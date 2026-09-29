# 감정 지도 커스텀 핀 구현 프롬프트

아래 내용 전체를 구현 AI에게 전달한다. 기본 크기·캐시 정책 등은 구현을 위한 초기값이며, API 계약과 사용자 지정 표시 규칙은 반드시 지킨다.

---

너는 이 저장소의 Kotlin Multiplatform 앱에 감정 지도 핀을 구현하는 개발자다. 아래 계획에 따라 실제 코드를 수정하고, Android와 iOS 모두 검증하라. 계획 설명만 하고 종료하지 말고 구현을 완료하라.

## 1. 목표와 범위

- 현재 지도 화면에서 보이는 영역의 감정을 API로 조회하여 해당 좌표에 커스텀 핀을 표시한다.
- `properties.groupStamp != null`이면 응답의 문구·프레임·배경색·글자색을 기존 `GroupStamp` 컴포넌트에 적용한다.
- `properties.groupStamp == null`이면 `properties.state`에 해당하는 기존 감정 아이콘을 표시한다.
- 두 종류 모두 응답의 `rotationDegrees`를 적용하고, 오래된 감정부터 그려 최신 감정이 위에 오게 한다.
- Android와 iOS의 지도 이동, 현재 위치 표시, 위치 권한 처리, 기록 위치 선택 기능을 유지한다.
- 이번 작업에 새로운 그룹 필터 UI, 상세 바텀시트, 등록·수정·삭제 API 구현, 클러스터링, 서버 변경을 포함하지 않는다. 현재 영역 재조회 진입점을 만들어 후속 작업에서 호출할 수 있게 한다.
- 현재 그룹 필터가 없다면 전체 조회를 기본값으로 한다. 데이터 계층은 선택적 `groupId`를 지원한다.

## 2. 먼저 확인할 현재 코드

앱 루트는 `AN/Pheeeew`이고 Kotlin 패키지 루트는 `com.pheeeew`다. 파일명과 구조는 실제 저장소에 맞게 조정하되 아래 코드를 먼저 읽어라.

- `shared/src/commonMain/kotlin/com/pheeeew/App.kt`
- `feature/screens/map/MapViewModel.kt`, `MapUiModel.kt`, `MapScreen.kt`
- `feature/screens/map/renderer/NativeMap.kt`
- `shared/src/androidMain/kotlin/com/pheeeew/feature/screens/map/renderer/NativeMap.android.kt`
- `shared/src/iosMain/kotlin/com/pheeeew/feature/screens/map/renderer/NativeMap.ios.kt`
- 같은 iOS renderer 패키지의 `FoundationIosMapBridge`, `FoundationIosMapFactory`, `FoundationIosMapEventSink`, `FoundationIosMapRenderUiModel`
- `iosApp/iosApp/Map/FoundationMapRenderer.swift`, `FoundationMapHostViewController.swift`
- `feature/component/GroupStamp.kt`, `feature/component/stamp/*`
- `domain/model/group/GroupStamp.kt`, `data/remote/group/GroupResponseMapper.kt`
- `feature/screens/group/data/GroupSummaryUiMapper.kt`
- `feature/screens/map/record/EmotionTypeUiModel.kt`
- `core/network/ApiRequest.kt`, `ApiRequestExecutor.kt`, `ApiResult.kt`
- `core/di/ApiDependencies.kt`, `GroupListDependencies.kt`
- `feature/screens/map/MapRecordFlowCoordinator.kt`, `record/MapRecordViewModel.kt`

상대 Kotlin 경로는 별도 표기가 없으면 `shared/src/commonMain/kotlin/com/pheeeew/` 기준이다. 적용되는 `AGENTS.md`와 기존 테스트·빌드 설정도 확인하라. 현재 지도 기능에 구현하며 `legacy` 기능으로 되돌리지 않는다.

### 클린 아키텍처와 legacy 제한

클린 아키텍처 관점에서 구조를 분리한다. 구조가 이해되지 않으면 `legacy` 패키지를 읽어 계층 분리와 의존성 방향을 참고할 수 있다. **legacy 패키지는 절대 수정하지 않고 import해서도 안 된다.** 기존 legacy 구현을 호출하는 우회 연결도 금지한다. 필요한 동작은 현재 패키지에 독립적으로 구현한다.

- `domain`: 플랫폼·UI·네트워크 라이브러리에 의존하지 않는 감정/핀/페이지/영역 모델, Repository 인터페이스, 조회 UseCase와 필요한 실패 모델을 둔다. Compose 리소스, Ktor DTO, Bitmap/UIImage, MapLibre 타입을 노출하지 않는다.
- `data`: API·DTO·DTO/domain 변환·Repository 구현을 둔다. 기존 `ApiResult`와 `NetworkFailure`는 이 계층에서 해석하고 domain 계약으로 변환한다.
- `feature`: domain을 UI 모델로 변환하고 ViewModel/coordinator에서 viewport 이벤트, 요청 수명, 페이지 누적, 조회 상태를 관리한다. Composable에서 API나 Repository 구현체를 직접 호출하지 않는다.
- 플랫폼 renderer: 투영·네이티브 이미지 변환·지도 source/layer 갱신을 담당한다. API 조회·DTO 해석·업무 규칙을 넣지 않는다.
- `core/di`와 앱 진입점: 구현체를 생성하고 인터페이스·UseCase를 주입한다. ViewModel이 data 구현체를 직접 생성하지 않는다.
- 의존성 방향은 `feature → domain`, `data → domain`이다. domain이 feature/data를 import하거나 data가 UI 모델을 반환하지 않게 한다. DI만 구현체 조합을 담당한다.
- 현재 코드의 편의 factory가 계층을 섞고 있다면 이번 연결에 필요한 범위에서 DI로 조합을 이동한다. 관계없는 기능을 대규모로 재구성하지 않는다.
- 작업 후 legacy 하위 파일에 변경이 없는지, 이번 작업으로 추가·수정한 코드와 테스트에 legacy import 또는 정규화된 패키지 직접 참조가 없는지 확인한다. 사용자에게서 이미 존재하던 변경은 보존한다.

## 3. API 계약

명세: https://api-dev.pheeeew.com/swagger-ui/index.html#/%EA%B0%90%EC%A0%95/findMap

구현 시 최신 OpenAPI와 가능한 실제 응답 샘플을 다시 확인하라. 확인을 위해 감정을 새로 등록하거나 서버 데이터를 수정하지 않는다. 인증된 조회가 불가능하면 명세와 테스트 fixture로 진행하고 실제 응답 확인 여부를 결과에 기록한다.

- `GET /api/v1/emotions/map`
- 기존 기기 세션의 Bearer 인증을 사용한다.
- 첫 페이지 query: `minLongitude`, `minLatitude`, `maxLongitude`, `maxLatitude`, 선택적 `groupId`.
- 다음 페이지 query: 응답의 `nextCursor`를 `cursor`로 전달한다. 경계 좌표와 `groupId`는 함께 보내지 않는다.
- Swagger에 나타나는 `request` 객체는 요청 필드 묶음이다. 실제 HTTP query 바인딩을 확인하여 좌표들을 개별 파라미터로 전송하고, 임의로 `request=<JSON>`을 만들지 않는다.
- `nextPageRequest`는 명세에 보이는 파생 속성일 수 있다. 설명에 지정된 첫 페이지/커서 파라미터만 전송하며 필수 여부를 실제 계약으로 확인한다.
- 응답은 `items`, `hasNext`, `nextCursor`를 가진다. 각 item은 `type`, `id`, `geometry`, `properties`를 가진다.
- 좌표는 GeoJSON 순서 `[longitude, latitude]`다.
- 지도 properties에서 필요한 값은 `createdAt`, `state`, `rotationDegrees`, nullable `groupStamp`다.
- 스탬프 값은 `text`, `textColor`, `backgroundColor`, `frame`이다.
- 페이지당 최대 200개이며 기간 제한·전체 개수 제한은 없다. `hasNext == false`까지 순차 조회한다.
- 서버 순서는 `(createdAt DESC, id DESC)`이며 클라이언트는 반대 순서로 그린다.
- 첫 조회 이후 새로 작성된 감정은 같은 커서 흐름에 들어오지 않는다. 삭제·차단은 각 페이지 조회 시 반영된다. 이미 받은 항목이 자동으로 제거되는 것은 아니므로 최신 상태 반영에는 첫 페이지 재조회가 필요하다.
- 지도 조회에는 메모·닉네임·녹음 URL·상세 집계가 필요하지 않다.

현재 Swagger는 지도·상세 응답이 동일한 이름의 `Properties` 스키마를 참조하여 상세 필드가 지도 응답에도 있는 것처럼 보일 수 있다. 지도 DTO에 상세 전용 필드를 필수로 요구하지 말라. `groupStamp`는 설명상 객체 또는 null인데 스키마의 type 표기가 모순될 수 있으므로 `$ref`와 설명, 확인 가능한 응답을 함께 검토한다.

## 4. 데이터 계층

다음 역할을 추가한다. 실제 이름은 기존 관례에 맞게 조정한다.

- `data/remote/emotion/EmotionMapApi`: 첫 페이지·다음 페이지 조회.
- 지도 전용 직렬화 DTO: 페이지, Feature, geometry, properties.
- `domain/model/emotion`: 감정 상태, 지도 핀, 페이지, 조회 영역 모델.
- `domain/repository/EmotionMapRepository`와 data 구현체.
- `domain/usecase`의 지도 핀 한 페이지 조회 UseCase. ViewModel은 이를 통해 조회한다.
- `core/di`에서 앱 소유의 `ApiDependencies.client.requests`를 주입한다.

규칙:

1. 기존 `ApiRequestExecutor`, `ApiResult`, `RequestKind.READ`를 사용한다. 토큰 갱신을 ViewModel에서 중복 구현하지 않고 기존 인증 복구 흐름에 맡긴다.
2. Repository는 한 페이지를 반환한다. 페이지 순회·화면 상태·요청 취소는 ViewModel 또는 지도 조회 전용 coordinator가 관리한다.
3. DTO에서 domain으로 변환할 때 ID, `Point` geometry, 좌표 개수·유한값·범위, 작성 시각, 감정 상태, 회전 각도, 스탬프 프레임·색상을 검증한다.
4. 서버 색상은 `#RRGGBB` 또는 `#RRGGBBAA`다. `StampColor.parseServerValue`를 재사용하며 8자리 값을 ARGB로 오해하지 않는다.
5. 알 수 없는 상태나 잘못된 좌표 등 개별 item의 변환 실패는 해당 item만 제외하고 나머지를 표시한다. 이를 정상 빈 결과와 구분할 수 있게 유효하지 않은 항목 수를 보관한다. 유효한 스탬프를 기본 아이콘으로 조용히 대체하지 않는다.
6. 페이지 envelope의 역직렬화 실패는 전체 페이지 실패다. `hasNext == true`인데 cursor가 없거나 비어 있거나 이미 사용한 cursor가 반복되면 계약 오류로 종료한다. 무한 요청을 만들지 않는다.
7. `CancellationException`을 일반 오류로 바꾸지 않는다. 인증 정보와 원본 좌표 응답을 로그에 출력하지 않는다.
8. 공개된 좌표 계약에서 날짜변경선 처리 방식을 확인한다. 지원한다면 서쪽 경도가 동쪽 경도보다 큰 영역을 유지하며 경도를 단순 min/max 정렬하지 않는다.

## 5. 표시 모델과 감정 매핑

지도 핀 UI 모델은 다음 정보를 가진다.

- `id`, `latitude`, `longitude`, `createdAt`, `rotationDegrees`
- 표시 종류: `GroupStamp(appearance)` 또는 `EmotionIcon(state)`
- 캐시 조회에 사용할 안정적인 이미지 키
- 필요하다면 렌더링 순서용 정수 rank

스탬프가 존재하면 감정 상태와 관계없이 스탬프를 우선한다. null인 경우만 감정 아이콘을 사용한다.

Git 이력을 확인한 결과, `EmotionTypeUiModel.Stuck`은 BaekCCI가 커밋 `030da709`(`feat: 감정 버블 선택 UI 구현`)에서 처음 구현했다. 현재 아이콘 연결도 BaekCCI의 `cc5a9c34`(`refactor: 감정 아이콘 이름 변경`)에서 수정했다. 따라서 이번 구현에서는 UI enum 식별자를 서버 값과 정확히 같게 정리한다. 이 enum의 나머지 항목도 같은 작성자가 구현한 것으로 확인되었으므로 다섯 항목을 함께 변경하여 기존 ‘좌절’ 항목과 이름이 충돌하지 않게 한다.

| 서버 state / 변경 후 UI enum | 변경 전 UI enum | 유지할 label | 유지할 drawable |
|---|---|---|---|
| FRUSTRATED | Stuck | 답답 | ic_emotion_frustrated |
| IRRITATED | Annoyed | 짜증 | ic_emotion_irritated |
| EXHAUSTED | Exhausted | 지침 | ic_emotion_exhausted |
| DISCOURAGED | Frustrated | 좌절 | ic_emotion_discouraged |
| ANGRY | Angry | 분노 | ic_emotion_angry |

이 변경은 식별자 정렬이며 사용자에게 보이는 label·아이콘·감정 의미는 그대로 유지한다. 특히 기존 `Frustrated`는 ‘좌절’이므로 `DISCOURAGED`로 바꾸고 기존 `Stuck`을 `FRUSTRATED`로 바꾼다. 문자열 치환으로 두 감정을 뒤바꾸지 말라. legacy 밖의 호출부, Preview, 테스트, 기본값과 `when` 분기를 함께 갱신한다. enum 이름을 저장하거나 직렬화하는 기존 경로가 있다면 호환성도 확인한다. 다른 작성자의 값은 유지한다는 조건에 맞춰 구현 시 실제 체크아웃의 이력을 다시 확인하며, 이력이 달라졌다면 다른 작성자의 식별자를 임의로 변경하지 않는다.

domain 감정 상태와 UI enum은 계층별로 분리하며 명시적 매핑으로 연결한다. 기존 record 패키지 enum 의존성이 부적절하면 감정 리소스 매핑을 공통 feature 위치로 최소한 이동하여 재사용한다. 서버 이름과 같아졌다는 이유로 UI 타입을 data/domain에 import하지 않는다.

프레임 매핑은 기존 `GroupStampFrame.toUiShape()`를 그대로 사용한다. 그룹 화면에 private으로 존재하는 appearance 변환과 DTO 스탬프 변환은 필요 시 적절한 공통 위치로 추출하고 같은 매핑을 복제하지 않는다.

## 6. 지도 영역 이벤트와 조회 상태

공통 `NativeMap`에 `onViewportChanged(bounds)`를 추가한다. 기존 기록 위치 선택용 `onRecordViewportChanged(centerX, centerY, radius)`와 다른 이벤트로 유지한다.

- Android: 최초 지도 준비·유효한 레이아웃 완료 후와 카메라 idle에서 화면 경계를 전달한다.
- iOS: 스타일·레이아웃 준비 후와 `regionDidChangeAnimated`에서 화면 경계를 전달한다.
- 지도 회전·기울임이 있을 때도 보이는 영역을 포함하는 경계를 SDK의 투영 정보로 산출한다. 폭·높이가 0이거나 잘못된 범위이면 요청하지 않는다.
- 수동 이동, 현재 위치 이동, 초기 fallback 중심에서 실제 위치로 이동, 화면 크기 변경을 모두 처리한다.
- ViewModel이 지도 좌표를 화면 좌표로 투영하거나 카메라를 직접 조작하지 않게 한다.

조회 흐름:

1. 최초 유효한 viewport를 받으면 첫 페이지를 조회한다.
2. 카메라 idle 이벤트에 약 250ms debounce를 적용하고 동일 영역·그룹의 중복 이벤트를 억제한다. 단순 중복 억제가 명시적 새로고침까지 막지 않게 한다.
3. debounce를 기다리는 동안에도 새로운 영역 이벤트를 받으면 이전 요청을 무효화한다. job 취소와 증가하는 요청 generation을 함께 사용한다.
4. 응답 적용 직전에 generation과 query identity를 확인한다. 취소를 따르지 않는 이전 요청의 응답도 화면 상태를 변경할 수 없어야 한다.
5. 첫 페이지를 표시한 뒤 후속 페이지를 순차 누적한다. ID 중복은 제거한다. 임의의 전체 개수·페이지 수 상한을 두지 않는다.
6. 핀은 `createdAt ASC, id ASC`로 정렬한다. 작성 시각은 날짜 문자열의 단순 비교 대신 파싱된 시각을 사용한다.
7. 새 query의 첫 페이지 성공 시 해당 query의 새 스냅샷으로 교체한다. 같은 영역 재조회라도 이전 핀과 합치지 않아 삭제·차단된 핀이 남지 않게 한다. 이후 페이지는 새 스냅샷에만 누적한다.
8. 영역 변경 시 이전 영역의 핀은 새 결과 적용 전까지 잠시 유지할 수 있지만 이전 query의 데이터임을 내부 상태에서 구분한다. 요청 실패 시 이를 최신 결과로 취급하지 않는다.
9. 첫 페이지 실패: 기존 표시를 보존하고 조회 오류와 재시도 경로를 제공한다.
10. 후속 페이지 실패: 받은 핀을 유지하고 일부만 조회되었음을 표시한다. 같은 generation·query가 유지되는 동안 실패한 cursor로 재시도한다. 영역이 변경되었으면 첫 페이지부터 시작한다.
11. 정상 빈 응답은 이전 query의 핀을 제거하고 오류 없이 빈 지도로 표시한다.
12. `refreshPins()`는 현재 query에서 새 generation으로 첫 페이지부터 조회한다. 진행 중인 페이지 작업도 무효화한다.

`MapUiModel`에는 핀 목록과 초기 조회·추가 페이지 조회·부분 결과·조회 오류·재시도에 필요한 상태를 추가한다. 기존 `mapError`는 지도 렌더러 오류로 유지한다. API 오류 때문에 지도를 검게 덮는 기존 지도 실패 UI를 띄우지 말라. 지도 조작을 막지 않는 작은 조회 안내와 재시도 UI를 기존 디자인에 맞게 제공한다.

기록 위치 선택 중에는 기존 조회 핀 레이어를 숨기고 진행 중 조회를 취소·무효화한다. 드래그마다 감정 조회를 하지 않는다. 선택을 종료하면 현재 viewport를 다시 전달하고 조회 핀 레이어를 복원한다. 현재 기록 흐름의 `confirmedRecord`가 실제 서버 등록 성공인지 확인하고, 단순 로컬 확인을 서버 성공으로 간주하지 말라. 실제 성공 이벤트가 이미 있을 때만 `refreshPins()`를 연결한다.

## 7. 이미지 생성과 캐시

렌더링 기본 방식은 ‘공통 Compose 표현 → 투명 이미지 → MapLibre 이미지 등록 → SymbolLayer’다. 핀마다 Compose View/UIView를 지도에 붙이는 방식으로 구현하지 않는다.

먼저 작은 검증을 수행한다:

- 기존 `GroupStamp`의 텍스트·벡터 레이어가 모두 준비된 상태로 이미지 캡처가 되는지 확인한다.
- 감정 아이콘을 원본 색상과 비율로 이미지에 그릴 수 있는지 확인한다.
- Android Bitmap과 iOS UIImage로 전환하여 지도에 표시하고 양쪽 화면 배율에서 선명도를 확인한다.
- Compose 1.11.1 및 저장소의 MapLibre 버전에서 실제 사용 가능한 API를 확인한다. 존재하지 않는 캡처·변환 함수를 가정하지 않는다.

공통 캡처가 한 플랫폼에서 지원되지 않으면 장애 원인을 확인한 뒤 같은 리소스·shape catalog·텍스트 배치를 재사용하는 플랫폼 어댑터로 해결한다. 두 플랫폼에 서로 다른 스탬프 디자인을 새로 만들지 않는다.

이미지 규칙:

- 스탬프는 기존 `GroupStamp(appearance, size)`를 재사용한다.
- 감정은 기존 XML drawable을 Compose 리소스로 렌더링한다. 원본 색상을 유지하고 새 tint를 적용하지 않는다.
- 초기 크기는 두 종류 모두 62dp/pt 정사각형 슬롯으로 하고 원본 비율을 유지한다. 공통 설정 하나로 크기를 바꿀 수 있게 한다.
- 핀 중심을 실제 좌표에 맞춘다. 회전 중심도 이미지 중심이다.
- 회전 각도는 이미지에 굽지 않고 SymbolLayer 속성으로 적용한다.
- 지도 zoom이 바뀌어도 화면상 크기는 일정하게 유지한다. 회전·기울임에 대한 정렬 정책은 화면 기준으로 통일하여 카메라 회전이 응답 각도에 추가되지 않게 한다.
- logical 크기와 픽셀 크기, Android density 및 iOS scale을 분리한다. 고배율 이미지를 등록해도 화면에서 두 배 크기로 표시되지 않게 한다.

캐시:

- 스탬프 키: 표시 종류, 문구, frame, 배경 ARGB, 글자 ARGB, logical 크기, 화면 배율. 출력에 영향을 주는 다른 설정이 있으면 포함한다.
- 감정 키: 표시 종류, state, logical 크기, 화면 배율.
- 감정 ID·좌표·회전 각도는 이미지 키에 넣지 않는다.
- 같은 키의 동시 생성은 하나로 합친다. 리소스 로딩·측정·캡처 완료 후 등록하고, 실패 결과를 성공 이미지로 캐시하지 않는다.
- CPU 이미지 캐시에는 메모리 사용량 기준의 상한을 둔다. 네이티브 스타일 이미지도 활성 source가 참조하지 않는 키를 정리한다.
- 현재 source에 참조되는 이미지를 임의로 제거하지 않는다. 고유한 스탬프가 매우 많은 경우 활성 이미지 사용량을 측정하고 한계를 보고한다. 캐시 상한을 이유로 API 결과를 조용히 자르지 않는다.
- 화면 해제 시 이미지 생성 작업·리스너·네이티브 자원을 해제한다.
- API 응답을 영속 캐시하거나 장시간 재사용하는 기능은 이번 단계에 추가하지 않는다. 이미지 재사용과 API 최신성은 별개의 문제다.

## 8. Android와 iOS 렌더러

Android에서는 핀 전용 GeoJSON Source와 SymbolLayer를 추가한다. iOS에서는 `FoundationIosMapRenderUiModel`과 bridge에 핀 표시 정보를 전달하고 `MLNShapeSource`, `MLNSymbolStyleLayer`를 사용한다. Bitmap/UIImage 등 플랫폼 타입을 common domain 모델에 넣지 않는다.

각 Feature에 필요한 값은 ID, 좌표, 이미지 키, 회전 각도, 표시 순서다. 새 이미지를 등록한 뒤 그 이미지를 참조하는 source를 갱신한다.

- SDK가 지원하는 데이터 기반 이미지·회전·정렬 속성을 버전에 맞게 사용한다.
- collision으로 핀이 생략되지 않도록 overlap 관련 설정을 적용한다.
- 겹친 핀을 오래된 순서로 그려 최신 핀이 위에 오게 한다. 배열 정렬만으로 보장된다고 가정하지 말고 양쪽 플랫폼에서 검증한다. 별도 스탬프/감정 레이어를 쓴다면 종류 때문에 시간 순서가 뒤집히지 않게 한다.
- 가능하면 한 source와 한 symbol layer로 두 종류를 함께 표시한다.
- 현재 위치 레이어와 기존 기록 overlay의 우선순위를 유지한다.
- 이미지가 준비된 핀부터 단계적으로 표시할 수 있으며, 최종적으로 모든 유효한 핀이 표시되어야 한다.
- 조회 로딩 플래그만 바뀌면 핀 source를 다시 만들지 않는다. 데이터·이미지 준비 revision을 기준으로 갱신한다.
- 누적 페이지 업데이트가 불필요하게 매 프레임 실행되지 않도록 합친다. image 생성·문자열 변환·정렬 등의 비용을 UI 스레드에서 반복하지 않는다. SDK 수정은 필요한 메인 스레드에서 수행한다.
- 스타일 재로드 시 이미지·source·layer를 다시 설치하고 마지막 정상 상태를 복원한다. 스타일 재설치만으로 같은 영역 API 요청이 반복되지 않게 한다.
- callback은 최신 ViewModel/람다를 사용하고 해제된 renderer에는 늦은 결과를 적용하지 않는다.

핀 선택 처리는 이번 필수 범위가 아니다. 기존 선택 흐름이 있으면 ID로 연결하되 상세 화면을 새로 구현하지 않는다.

## 9. 작업 순서

1. API 계약과 현재 의존성·렌더러·기록 흐름 확인.
2. 공통 스탬프/아이콘 이미지 생성과 네이티브 지도 표시를 최소 검증하여 기술적 불확실성 해소.
3. 클린 아키텍처에 맞춰 DTO·domain·Repository·UseCase·DI 및 상태/스탬프 매핑 구현. 작성자 확인에 따른 UI enum 이름 정렬과 legacy 밖의 관련 호출부 갱신.
4. viewport 이벤트와 ViewModel 조회 상태·취소·generation·페이지 순회 구현.
5. 이미지 생성·재사용·정리 흐름 구현.
6. Android 핀 레이어와 iOS bridge/핀 레이어 구현.
7. 조회 안내·재시도·기록 위치 선택과의 연결.
8. 필요한 테스트와 양쪽 플랫폼 빌드·화면 검증.

사용자 변경을 보존하고, 필요한 최소 범위의 공통화만 한다. 새로운 라이브러리가 필요하면 기존 의존성으로 해결 가능한지 먼저 확인한다. 완료 전에는 가짜 핀 데이터나 임시 검증 UI를 제거한다.

## 10. 테스트와 완료 기준

의미 있는 동작을 검증하는 테스트를 추가한다. 구현의 단순 복제 테스트는 피한다.

API/변환:

- 첫 요청의 경계 query와 다음 요청의 cursor-only query.
- 실제 지도 응답 형태의 fixture로 상세 필드 없이 decoding 성공.
- nullable groupStamp, GeoJSON 경도/위도 순서, 64bit ID.
- 다섯 감정 상태의 정확한 리소스 매핑.
- UI enum이 서버 이름과 동일하고 이름 변경 후에도 기존 label·아이콘·감정 선택 의미가 유지되는지 확인.
- 기존 10개 frame 매핑과 `#RRGGBB`/`#RRGGBBAA` 색상 재사용.
- 개별 불량 item 제외 및 페이지 envelope/커서 계약 오류 구분.

조회 상태:

- 200개를 넘는 여러 페이지를 끝까지 누적하고 ID 중복 제거.
- 영역 A에서 B로 바뀐 뒤 늦게 도착한 A 응답 무시. 취소에 비협조적인 fake도 사용.
- debounce 대기 중 이전 query의 응답이 최신 상태를 덮지 않음.
- 첫 페이지 실패, 후속 페이지 실패와 해당 cursor 재시도.
- hasNext/nextCursor 불일치와 반복 cursor에서 무한 루프 방지.
- 빈 성공 결과로 이전 핀 제거.
- 같은 영역 새로고침으로 삭제·차단된 핀 제거.
- 오래된 감정 우선 정렬 및 동일 시각 ID tie-break.
- 기록 위치 선택 중 조회 억제, 종료 후 재개.

이미지/지도:

- 같은 appearance는 이미지 하나 재사용. 회전·좌표만 달라져도 재생성되지 않음.
- 같은 그룹의 문구·색상·frame이 바뀌면 다른 이미지 사용.
- 스탬프 있음 → null 전환 시 이전 스탬프 대신 감정 아이콘 표시.
- Android/iOS에서 스탬프·아이콘 크기, 중심, 투명 배경, 회전, 고배율 선명도 확인.
- 서로 다른 핀 종류가 같은 좌표에 겹쳐도 최신 감정이 위에 표시.
- 이미지 준비 전 응답 도착, 스타일 재로드, 화면 재진입·해제 시 유실·충돌 없음.
- 다량의 감정 및 고유한 스탬프 fixture로 스크롤·zoom 반응과 이미지 메모리 증가 확인.

실제 Gradle task를 확인하고 저장소에 맞는 테스트·Android 빌드·iOS framework 및 앱 빌드를 수행한다. README에 안내된 후보는 `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`, `:androidApp:assembleDebug`다. 실행하지 못한 검증은 환경 제약과 함께 명시하고 성공했다고 표현하지 않는다.

완료 시 변경된 흐름, 주요 파일, 실행한 검증과 결과, 남은 제약을 간결하게 보고한다. 계층별 책임과 의존성 방향, legacy를 수정하거나 참조하지 않았다는 확인도 포함한다. API 조회부터 스탬프/아이콘 표시까지 연결된 상태가 완료 기준이며 한 플랫폼만 구현한 상태를 완료로 보고하지 않는다.
