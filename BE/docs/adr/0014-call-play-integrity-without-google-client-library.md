# ADR-0014: Play Integrity 호출에 구글 클라이언트 라이브러리를 쓰지 않는다

## Status

Accepted (2026-09-13)

## Context

- 무결성 증명 토큰은 암호화되어 있어 서버가 직접 풀 수 없어요. 구글 `decodeIntegrityToken` 에 맡겨야 해요.
- 그 호출에는 서비스 계정 자격증명이 필요해요. 개인키로 assertion 을 서명하고, `jwt-bearer` 그랜트로 구글 access token 을 받고, 그것으로 API 를 불러요. 만료되면 다시 받아야 해서 캐시도 필요해요.
- 구글 클라이언트 라이브러리의 실제 크기를 재 봤어요. `google-auth-library-oauth2-http` 와 `google-api-services-playintegrity` 를 더하면 **jar 25개, 6.64MB** 이고, 전이로 **Apache HttpClient 4.5**, Guava 33, Gson, gRPC-api, OpenCensus 가 들어와요.
- 그중 Apache HttpClient 4.5 는 이 프로젝트가 쓰지 않는 계열이에요. `spring-web` 의 `HttpComponentsClientHttpRequestFactory` 는 HttpClient 5 만 참조하므로 4.5 는 스프링이 쓸 수 없는 채로 클래스패스에 남아요. 우리가 `JdkClientHttpRequestFactory` 를 명시 주입하므로 자동 선택이 끼어들 여지도 없어요.
- 필요한 기구가 이미 있어요. RS256 서명은 `auth/infra/jwt` 가 쓰는 Nimbus JOSE 로, HTTP 는 `RestClient` 로, JSON 은 Jackson 으로 할 수 있어요.

## Decision

라이브러리를 추가하지 않고 이미 있는 것으로 직접 구현해요. 서비스 계정 assertion 서명, 토큰 교환, 캐시와 갱신, `decodeIntegrityToken` 호출을 우리 코드로 처리해요.

## Alternatives

- 구글 클라이언트 라이브러리 사용 — 우리 코드가 몇 줄로 줄어들지만, API 호출 하나를 위해 jar 25개와 쓰지 않는 HttpClient 4.5 를 클래스패스에 들여야 해서 선택하지 않았어요.

## Consequences

- (+) 새 의존성이 없어요. 쓰지 않는 라이브러리가 클래스패스에 남지 않아요.
- (+) RS256 서명 기구를 access token 발급 쪽과 공유해요.
- (-) **우리가 관리할 코드가 312줄 늘어나요.** 토큰 교환, 서비스 계정 키 파싱, 응답 해석을 합한 값이고, 그중 토큰 캐시와 갱신이 실수하기 쉬운 자리예요.
- (-) 구글이 인증 절차를 바꾸면 우리가 따라가야 해요. 라이브러리를 썼다면 버전만 올리면 됐을 일이에요.
- (=) `sub` 클레임과 `typ` 헤더처럼 문서에 명시되지 않은 세부를 우리가 직접 확인해야 해요. 실제로 구글 문서와 공식 Java, Python 클라이언트를 대조해 `sub` 를 빼고 `typ` 을 넣었어요.
- (=) `kid` 헤더는 넣지 않았어요. 공식 클라이언트와 다른 점으로 남아 있어요.
- 재검토: 구글 API 를 여러 개 부르게 되거나, 인증 절차가 바뀌어 따라가는 비용이 커지면 다시 판단해요.

## Compliance

- 토큰 캐시의 재사용, 만료 경계 갱신, 동시 요청 단일 발급, 상류 오류 뒤 깨진 토큰 미잔류를 통합 테스트로 확인해요.
- assertion 의 클레임 키 집합과 `typ` 헤더, RS256 서명을 테스트로 고정해요. `sub` 가 없는 것도 함께 확인해요.
- **구글 실호출은 자격증명이 없어 검증하지 못했어요.** 시크릿을 등록한 뒤 실기기로 한 번 확인해야 해요.
