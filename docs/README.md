# Pheeeew 법적 고지 배포 체크리스트

이 디렉터리에는 개인정보 처리방침과 오픈소스 라이선스처럼 GitHub Pages에 공개할 정적 문서가 있습니다. `play-console-data-safety.md`는 Google Play Console 답변 작성을 위한 **내부 초안**이며 공개 페이지가 아닙니다. Play Console에 제출하는 답변과 App Store Connect의 App Privacy 항목은 실제 배포 앱의 모든 버전·SDK 동작을 확인한 뒤 각각 작성해야 합니다.

## 공개 전 필수 작업

1. Play Console 개발자 정보와 개인정보 처리방침에 기재된 운영 주체(`Byeolter`)가 정확히 일치하는지 확인합니다.
2. 개인정보 문의 주소 `contact@pheeeew.com`에서 실제로 메일을 수신할 수 있는지 확인합니다.
3. 저장소 Settings > Pages에서 배포 소스를 기본 브랜치의 `/docs`로 지정합니다.
4. 아래 URL을 로그인하지 않은 브라우저와 시크릿 창에서 확인합니다.
   - `https://woowacourse-teams.github.io/2026-pheeeew/privacy-policy.html`
   - `https://woowacourse-teams.github.io/2026-pheeeew/open-source-licenses.html`
5. 첫 번째 URL을 Play Console의 정책 및 프로그램 > 앱 콘텐츠 > 개인정보처리방침에 입력합니다.
6. 앱의 설정 > 개인정보 처리방침 및 오픈소스 라이선스 메뉴가 각 URL을 여는지 릴리스 빌드에서 확인합니다.
7. Play Console 데이터 보안 답변은 [`play-console-data-safety.md`](play-console-data-safety.md)를 참고하되, 실제 출시 AAB·서버 동작·SDK 설정과 Play의 최신 정의에 따라 검증하고 확정합니다. 이 초안 자체가 Play Console 답변을 대신하지 않습니다.
8. App Store Connect의 App Privacy 답변도 iOS 출시 빌드와 현재 개인정보 처리방침을 대조해 별도로 확인합니다.

## 출시 빌드 또는 데이터 처리 방식이 바뀔 때

- 감정 좌표를 보내거나 저장하는 방식, 오디오 녹음·업로드·재생, 콘텐츠 공개 범위, 보유·삭제 방식이 바뀌면 개인정보 처리방침, Google Play 데이터 보안 답변 초안, Play Console 답변, App Store App Privacy를 함께 검토합니다.
- 분석·크래시 수집·광고·푸시·계정·문의 SDK를 추가하거나 설정을 바꾸면 SDK가 자동 또는 선택적으로 보내는 데이터까지 확인해 관련 문서와 스토어 답변을 갱신합니다.
- AWS S3, PostHog, Sentry, Grafana Cloud, OpenFreeMap 등 외부 서비스의 처리 범위나 제공자가 바뀌면 제3자 제공·처리 위탁·국외 처리 안내와 스토어 공개 내용을 다시 확인합니다.
- 개인정보 삭제 요청을 받는 경로 또는 실제 처리 절차가 바뀌면 처리방침과 Play Console의 데이터 삭제 요청 안내를 함께 확인합니다.
- 라이브러리 또는 글꼴을 추가·변경하면 `open-source-licenses.html`의 버전과 라이선스를 갱신합니다.
