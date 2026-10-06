# 코드 컨벤션

> Spring Boot 백엔드 팀 코드 컨벤션 문서

---

## 1. 네이밍

### 1.1 DTO / VO / Entity 접미어

| 구분 | 접미어 | 예시 |
| --- | --- | --- |
| 요청 DTO | `Request` | `SighCreateRequest` |
| 응답 DTO | `Response` | `SighResponse` |
| Entity | 없음 | `Sigh` |

### 1.2 정적 팩토리 메서드 네이밍

| 매개변수 개수 | 메서드명 |
| --- | --- |
| 1개 | `from()` |
| 2개 이상 | `of()` |

```java
public record SighResponse(Long id, String memo) {

    public static SighResponse from(Sigh sigh) {
        return new SighResponse(sigh.getId(), sigh.getMemo());
    }

    public static SighResponse of(Long id, String memo) {
        return new SighResponse(id, memo);
    }
}
```

### 1.3 CRUD 메서드 네이밍

| 동작 | 접두어 |
| --- | --- |
| 조회 | `find` |
| 저장 | `save` |
| 수정 | `update` |
| 삭제 | `delete` |

**Controller - Service - Repository의 메서드명은 통일한다.** (단순한 흐름인 경우에만)

```java
SighController.findAll()
    └─ SighService.findAll()
        └─ SighRepository.findAll()
```

---

## 2. 객체 생성

### 2.1 DTO

- DTO는 **`record` 타입**으로 정의한다.
- 프로젝트 코드에서 `record`를 생성할 때는 **항상 정적 팩토리 메서드를 사용**한다. (생성자 직접 호출 금지)
- Spring MVC 데이터 바인딩처럼 프레임워크만 생성하는 요청 DTO에는 사용되지 않는 정적 팩토리 메서드를 추가하지 않는다.

### 2.2 Entity / 도메인 객체

- 생성은 **모두 빌더를 사용**한다.
- 모든 Entity는 `BaseEntity`를 상속해 `createdAt`, `updatedAt`을 공통으로 관리한다.
- Entity에서 감사 필드를 다시 선언하거나 빌더·Setter로 직접 주입하지 않는다.

```java
Sigh sigh = Sigh.builder()
        .requestId(UUID.randomUUID())
        .memo("오늘은 조금 지쳤다")
        .location(approximateLocation)
        .build();
```

---

## 3. 애노테이션
**중요한 애노테이션일수록 맨 아래에 배치한다.**

```java
// Controller
@RequiredArgsConstructor
@RequestMapping("/")
@RestController
public class SighController {
}
```

```java
// Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class SighService {
}
```

```java
// Entity
@Getter
@Builder
@NoArgsConstructor
@Table(name = "sigh")
@Entity
public class Sigh {
}
```

---

## 4. 포맷팅

### 4.1 공백 규칙

- **`Class`, `interface` 내부 첫 줄은 공백으로 시작**한다.
    - `enum`, `record`는 제외한다.
- **메서드 내부 첫 줄은 공백 없이 시작**한다.

```java
class Sigh {

    void test() {
        System.out.println("시작");
    }
}
```

### 4.2 인터페이스

인터페이스의 계약(메서드 선언)은 **모두 한 줄씩 띄운다.**

```java
public interface SighRepository extends JpaRepository<Sigh, Long> {

    Optional<Sigh> findByRequestId(UUID requestId);

    List<Sigh> findAllByStatus(SighStatus status);

    void deleteByRequestId(UUID requestId);
}
```

### 4.3 메서드 매개변수

- Controller 메서드는 **매개변수 개수에 관계없이** 항상 줄바꿈하여 작성한다.
- Service, Repository와 도메인 객체 등 다른 계층의 메서드는 매개변수를 한 줄로 작성한다.
- 다른 계층에서도 메서드 선언이 한 줄에서 읽기 어려울 정도로 길어질 때만 매개변수별로 줄바꿈한다.

```java
// Controller
public SighResponse findOne(
        @PathVariable Long sighId
) {
}

public CursorResponse<SighResponse> findAll(
        @RequestParam(required = false) Long cursor,
        @RequestParam(defaultValue = "20") int limit
) {
}

// Service - 한 줄 우선
public Sigh findByRequestId(UUID requestId) {
}

// Service - 선언이 한 줄에서 읽기 어려울 정도로 긴 경우
public Sigh create(
        UUID requestId,
        String memo,
        Point approximateLocation
) {
}
```

### 4.4 API 응답

- 성공 응답은 응답 DTO를 직접 반환한다.
- 상태 코드나 헤더를 직접 제어할 때만 `ResponseEntity<T>`를 사용한다.
- 에러 응답은 `code`, `message`만 가지는 공통 `ErrorResponse`를 사용한다.
- 목록 응답은 공통 `CursorResponse<T>`를 사용하고 `items`, `hasNext`, `nextCursor`를 반환한다.
- 성공 응답을 `ApiResponse<T>` 같은 공통 래퍼로 감싸지 않는다.

### 4.5 메서드 내부 단락

- 메서드 내부는 조회, 판단, 변환, 반환처럼 의미 있는 작업 단위가 바뀔 때 빈 줄로 구분한다.
- 하나의 연속된 작업이나 메서드 체인 내부에는 불필요한 빈 줄을 넣지 않는다.
- 한 단락으로 충분한 짧은 메서드에는 빈 줄을 강제하지 않는다.

```java
public SighMapResult findAllWithinBounds(...) {
    List<SighMapProjection> projections = sighRepository.findAllWithinBounds(...);

    boolean truncated = projections.size() > MAX_FIND_COUNT;
    if (truncated) {
        projections = projections.subList(0, MAX_FIND_COUNT);
    }

    List<SighMapItem> sighs = projections.stream()
            .map(...)
            .toList();

    return SighMapResult.of(sighs, truncated);
}
```

---

## 5. 코드 정렬 순서

클래스 내부 멤버는 다음 순서로 배치한다.
1. **private 메서드** — public 메서드 전체 아래에 일괄 배치, **public에서 호출된 순서대로**

```java
public class SighService {

    public void save(...) { validateRequestId(...); }

    public SighResponse findOne(...) { }

    public void update(...) { validateLocation(...); }

    public void delete(...) { }

    private void validateRequestId(...) { }

    private void validateLocation(...) { }

    @Override
    public String toString() { }
}
```

---

## 6. 테스트

### 6.1 작성 규칙

- **given / when / then** 구조로 작성한다.
- 테스트 메서드명은 **한글**로 작성한다.
- 모든 도메인은 **fixture를 만들어 재사용**한다.

```java
@Test
void 요청_식별자로_한숨을_조회할_수_있다() {
    // given
    Sigh sigh = SighFixture.기본_한숨();

    // when
    SighResponse response = sighService.findByRequestId(sigh.getRequestId());

    // then
    assertThat(response.id()).isEqualTo(sigh.getId());
}
```

### 6.2 계층별 테스트 전략

| 계층 | 방식 |
| --- | --- |
| 통합 테스트 | `XXXIntegrationTest`를 만들어서 테스트 |
| Controller | 슬라이스 테스트 — `@WebMvcTest`에서 Service를 모킹 |
| Service | 통합 테스트 — 실제 Repository와 PostgreSQL/PostGIS Testcontainers를 사용 |
| Repository | 단독 테스트를 작성하지 않음 |
| 도메인 | 단위 테스트 |

- Repository 동작과 영속성 매핑은 Service 통합 테스트에서 함께 검증한다.
- Service 테스트는 대상 Service를 import하고 Repository를 모킹하지 않는다.
- Controller 테스트는 HTTP 요청·응답 계약과 Service 위임을 검증한다.
- 공간 타입 매핑과 공간 조회는 실제 PostgreSQL/PostGIS 환경에서 검증하고 H2로 대체하지 않는다.

#### 테스트 선별 원칙

- 테스트가 보호하는 프로젝트 동작, 계약 또는 회귀 위험을 한 문장으로 설명할 수 있을 때만 작성한다. 테스트 개수나 커버리지를 늘리기 위한 테스트는 작성하지 않는다.
- Swagger/OpenAPI 라이브러리 버전, Swagger UI의 단순 노출 여부처럼 의존성이 제공하는 사실만 확인하는 테스트는 작성하지 않는다. 프로젝트가 직접 추가한 문서화 동작이나 회귀 위험이 있을 때만 검증한다.
- 애플리케이션이 단순히 실행되는지 확인하는 `contextLoads()`와 Bean 생성·주입 여부만 확인하는 테스트는 작성하지 않는다. 프로젝트의 초기화 로직이나 설정 조합 자체가 실제 계약일 때만 검증한다.
- 데이터베이스나 외부 시스템에 연결된다는 사실만 확인하는 테스트는 작성하지 않는다. 영속성 매핑, 공간 조회, 트랜잭션과 실패 처리처럼 프로젝트가 직접 책임지는 동작을 함께 검증할 때만 통합 테스트를 작성한다.
- 프레임워크의 기본 보안 동작이나 보안 Bean 존재 여부만 반복 검증하지 않는다. 권한 우회, 위치정보 노출, 요청 위변조, 멱등성 충돌처럼 Pheeeew가 직접 정의한 보안·개인정보·정합성 경계는 반드시 테스트한다.

---

## 7. 패키지 구조

**도메인형 → 계층형** 구조를 따른다.

```
com.pheeeew
├── common/            # 공통 설정, 예외, 에러 응답, 페이지네이션 등
└── sigh/
    ├── presentation/
    │   ├── dto/
    │   └── SighController
    ├── application/
    │   └── SighService
    ├── domain/
    │   ├── repository/
    │   │   └── SighRepository
    │   └── Sigh
    └── infra/
```

---

## 8. OpenAPI 문서화

OpenAPI 문서는 클라이언트 개발자가 별도의 코드 확인 없이 API의 요청 조건과 동작을 이해할 수 있도록 작성한다.

### 8.1 API 설명

- Controller마다 `{도메인}ControllerApi` 인터페이스를 만들고 실제 Controller가 이를 구현한다.
- `@Tag`, `@Operation`, `@ApiResponses`, `@Parameter` 등 OpenAPI 문서화 애노테이션은 `ControllerApi` 인터페이스에 작성한다.
- `@RequestMapping`, `@GetMapping`, `@PostMapping` 등 Spring MVC 매핑 애노테이션과 실제 요청 처리 로직은 Controller 구현체에 작성한다.
- `ControllerApi`와 Controller 구현체의 메서드명, 매개변수와 반환 타입은 동일하게 유지한다.
- 모든 API에 `@Operation`을 사용하여 `summary`와 `description`을 작성한다.
- `summary`에는 API의 목적을 간결하게 작성한다.
- `description`에는 다음과 같이 요청 처리에 필요한 핵심 규칙을 작성한다.
  - 요청 형태에 따른 동작 차이
  - 필수 조건과 제약 조건
  - 권한에 따른 처리 범위
  - 서버가 자동으로 결정하거나 저장하는 값
- 설명할 내용이 여러 개라면 Markdown 문단이나 목록으로 구분한다.
- 서로 다른 주제는 빈 줄로 나누고, 한 문장에 모든 규칙을 나열하지 않는다.
- 구현 방식이나 내부 클래스 구조처럼 클라이언트가 알 필요 없는 내용은 작성하지 않는다.

예시:

```java
@Tag(name = "한숨", description = "한숨 등록과 조회 API")
public interface SighControllerApi {

    @Operation(
            summary = "한숨 등록",
            description = """
                    ### 중복 요청

                    - 한 번의 등록 시도마다 새로운 `requestId`를 사용합니다.
                    - 같은 `requestId`로 재시도하면 기존 한숨을 반환합니다.
                    - 서버는 재시도 요청의 위치가 달라도 최초 등록 결과를 반환합니다.

                    ### 위치

                    - `latitude`와 `longitude`에는 기기에서 흐린 근사 좌표를 전달합니다.
                    - 서버는 좌표의 유효 범위를 검증하지만 실제 위치가 흐려졌는지는 판별할 수 없습니다.

                    ### 한숨 내용

                    - `memo`는 생략할 수 있습니다.
                    - 빠른 MVP에서는 실제 음성을 서버로 전송하거나 저장하지 않습니다.
                    """
    )
    ResponseEntity<SighResponse> save(SighCreateRequest request);
}

@RequiredArgsConstructor
@RequestMapping("/api/v1/sighs")
@RestController
public class SighController implements SighControllerApi {

    private final SighService sighService;

    @Override
    @PostMapping
    public ResponseEntity<SighResponse> save(
            @Valid @RequestBody SighCreateRequest request
    ) {
        SighResponse response = sighService.save(request);
        return ResponseEntity.status(CREATED).body(response);
    }
}
```

### 8.2 요청 및 응답 필드 설명

- 모든 필드에 설명을 반복해서 작성하지 않는다.
- 이름만으로 의미와 사용 방법을 명확히 알기 어려운 필드에 `@Schema` 설명을 작성한다.
- 다음에 해당하는 필드는 반드시 설명한다.
  - 특정 리소스나 소속 관계를 가리키는 ID
  - 단위나 허용 범위가 있는 값
  - 다른 필드의 값에 따라 필수 여부가 달라지는 값
  - 생략과 `null`의 처리 방식이 중요한 값
  - 서버가 자동으로 계산하거나 변환하는 값
- 조건을 설명할 때는 관련된 실제 필드명과 값을 명시한다.
- “반복 일정 필드”, “관련 값”처럼 어떤 필드를 가리키는지 불분명한 표현은 사용하지 않는다.
- Bean Validation으로 표현할 수 있는 제약 조건은 검증 애너테이션과 `@Schema`에 동일하게 반영한다.

예시:

```java
@Schema(
        description = """
                한 번의 한숨 등록 시도를 식별하는 값입니다.

                네트워크 재시도에는 같은 값을 사용하고,
                사용자가 등록 버튼을 다시 누르면 새로운 값을 사용합니다.
                """,
        example = "8e0e5d17-fcf8-486e-8878-39c8fcfb4c25"
)
UUID requestId;
```

### 8.3 요청 예시

- 조건에 따라 요청 구조가 달라지는 API는 주요 요청 형태별 예시를 제공한다.
- 예시는 실제 요청 가능한 값과 필드 조합으로 작성한다.
- 메모 포함 등록과 메모 없는 등록처럼 요청 형태가 달라지면 각각 구분하여 보여준다.

### 8.4 Swagger UI 표시

- 재사용 가능한 요청 및 응답 모델은 `components/schemas`로 정의한다.
- Swagger UI 하단의 전체 `Schemas` 영역은 기본 설정대로 표시한다.
- Swagger UI 전역 설정을 변경할 때는 다른 API 문서의 가독성에 미치는 영향도 확인한다.

---

## 9. Flyway Migration

### 9.1 버전

- 파일명은 `VyyyyMMdd_순번__설명.sql` 형식을 사용한다.
- **순번은 해당 날짜의 첫 migration부터 `_1`로 시작한다.** 같은 날짜에 추가하면 `_2`, `_3`으로 이어 붙인다.
- 설명은 소문자 snake_case로 작성한다.

예시:

```text
V20260831_1__create_sighs_location_gist_index.sql
V20260831_2__add_sigh_status.sql
V20260831_3__create_sigh_status_index.sql
```

**첫 migration의 순번을 생략하던 규칙을 2026-10-06에 바꿨다.** 그때 날짜가 유일한 파일 9개 중 3개가 이미
`_1`을 달고 있어 문서와 관례가 갈라져 있었고, 생략 규칙은 같은 날 두 번째 migration이 생길 때
9.2(머지된 migration 개명 금지)와 충돌한다. 첫 파일이 `_1`이면 그 충돌이 사라진다.

순번을 생략한 기존 파일(`V20260909__create_devices.sql` 등)은 9.2에 따라 그대로 둔다.

### 9.2 변경과 충돌

- 공유 브랜치에 병합되었거나 어느 환경에 적용된 migration은 파일명과 내용을 수정하지 않는다.
- 적용된 migration을 변경해야 하면 새로운 버전의 migration을 추가한다.
- 같은 버전이 충돌하면 먼저 공유 브랜치에 병합된 파일을 유지하고, 후속 변경의 순번을 조정한다.
