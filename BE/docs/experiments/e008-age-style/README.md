# E008 D/G × A/B 나이별 표현 비교

2026-09-07 사용자가 **실험용 24시간 노출 수명**으로 전체 비교 진행을 승인했어요. 제품 수명·API·DB·클라이언트 정책을 확정하거나 변경하지 않아요. 기존 미커밋 E006/E007은 보존해요.

## 실행 전 고정한 비교

E007 원본 CSV의 hotspots-4500, hotspots-45000, uniform-45000에서 D/G 좌표를 그대로 읽어요. 좌표를 다시 뽑지 않아요. 동일 scene/seed/index의 D/G는 같은 합성 실제 위치와 생성 시간을 공유하고 각 모델의 A/B는 최종 표시 좌표까지 같아요. 모델별 고유 좌표 94,500개, 총 189,000행이에요. D와 G 자체의 표시 위치는 달라요.

| 화면 | 위치 | 색상 | 투명도·만료 |
| --- | --- | --- | --- |
| 좌상 D-A | E007 D | 노란색 #FFE2A3 고정 | 모든 화면 공통 |
| 우상 D-B | E007 D | 파랑 #A8D8FF → 노랑 → 붉은색 #FF8C78 | 모든 화면 공통 |
| 좌하 G-A | E007 G | 노란색 고정 | 모든 화면 공통 |
| 우하 G-B | E007 G | 파랑 → 노랑 → 붉은색 | 모든 화면 공통 |

나이 a=관측시각-생성시각(시간), 0≤a<24만 조회 대상이라고 **모의**해요. 음수는 아직 생성되지 않은 별이고 a≥24는 조회·그리기 모두 제외해요. 0~2h alpha=1, 2~24h는 u=(a-2)/22, alpha=1-3u²+2u³예요. B 색상은 a=0 파랑, a=12 노랑, a=24 붉은색으로 sRGB 채널 선형 보간해요. A/B alpha는 동일하지만 색상별 지각 밝기·상대 휘도는 같게 맞추지 않았어요. B는 실제 별의 천문학적 진화 모델이 아니라 UI 색상 비유예요.

## 시간과 관측

- cohort: 모든 별이 기준시각 t=0에 생성돼요. 이후 새 별 유입이 없어요. t=0/6/12/18/24h를 그려요. t=24h에는 네 패널 모두 비어야 해요.
- steady: 각 고유 별의 생성시각은 [-24,+24)h 균등으로 고정해요. SHA-256("e008|birth|scene|seed|index") 첫 8바이트 big-endian long으로 SplittableRandom seed를 만들고 48U-24를 계산해요. D/G/A/B 모두 같은 값이에요. t=0/6/12/18/24h에 진행하며 새 별과 만료 별이 교체돼요. 포아송 도착이나 실제 서비스 트래픽 추정이 아니에요.
- 기준시각은 합성 2026-09-07T00:00:00Z예요. CSV의 created_offset_hours를 더하면 생성시각이에요. 생성을 제외한 별은 이동·재추첨하지 않아요. 동일 위치·나이에 대한 스타일 외 랜덤 변화는 없어요.

## 그림 계약과 질문

같은 위치 분포에서 A/B의 시각 차이, D/G 차이, 계속 유입될 때 과밀이 남는지를 봐요. 예상은 cohort의 표시 부담 감소이고 steady의 무조건적인 해소·색상 선호는 미정이에요. 정량 합격선과 블라인드 선호 판정은 없어요.

정적 Java2D PNG 30장(3장면×2시간모델×5시점), GIF 6개, 시간표 contact sheet 6장과 색상·alpha 범례 1장을 만들어요. 2×2 패널은 각각 700px, 전체 1600×1860px예요. x/y ±1,200m 고정, 별 6px, 배경은 기존 E001 어두운 배경이에요. E001 8×8 RGBA sprite의 alpha mask를 재사용하고 RGB를 A/B 공통 방식으로 착색해요. 크기·흐림·발광·줌 효과는 바꾸지 않아요. 나이 범례 외 그림에서 alpha/color를 별도 과장하지 않아요. GIF는 5개의 정적 시점에 프레임당 1.2초이며 루프해요. 연속 애니메이션 또는 실제 앱 성능의 증거가 아니에요. GIF 색상 양자화가 있으므로 정적 PNG가 색상의 기준이에요.

metrics.csv에는 scene/시간모델/관측시간/위치모델/style/미생성·노출·만료 수/alpha합/평균 alpha/alpha≥0.5 개수를 남겨요. alpha합은 투명도 가중 개수일 뿐 실제 화면 밝기나 겹침량이 아니에요. 모든 별이 동시에 늙는 cohort만 보고 지속 유입 서비스의 과밀이 해결됐다고 결론내리지 않아요.

## 실행과 검증

새 결과 디렉터리만 만들어요. 소스·E007 입력 hash·HEAD·환경을 실행 전에 기록하고 실행 후 대조해요. 두 번 생성한 CSV·PNG·GIF checksum이 같아야 해요. 가까운 테스트로 2h/24h 경계·미생성·alpha 단조성·색상 경유점을 검증해요. CSV 독립 검증으로 좌표·생성시각 공유, E007 원본 보존, A/B alpha 일치, 24h 만료를 검사하고 그림을 직접 열어요. DB 조회 만료를 구현하거나 테스트한 것은 아니에요.

```bash
./gradlew test --tests 'com.pheeeew.sigh.experiment.e001.E008AgeStyleExperimentTest' --no-daemon
java -Djava.awt.headless=true -cp build/classes/java/test:build/resources/test com.pheeeew.sigh.experiment.e001.E008AgeStyleExperiment docs/experiments/e007-client-location/results/2026-09-07/raw/coordinates.csv.gz build/reports/experiments/e008-age-style
```
