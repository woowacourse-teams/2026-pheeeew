import csv,json,math,statistics
from collections import defaultdict,Counter
from pathlib import Path
P=Path(__file__).resolve().parent
rows=[json.loads(s) for s in (P/'measurements.jsonl').read_text().splitlines() if s]
runs=[r for r in rows if r['kind']=='run' and not r['warmup']]
pages=[r for r in rows if r['kind']=='page' and not r['warmup']]
assert next(r for r in rows if r['kind']=='complete')['trials']==len(runs)==270
assert len({r['run_id'] for r in runs})==270
assert set(r['target_count'] for r in runs)=={0,50,100,199,200,201,400,800,1600}
byrun=defaultdict(list)
for page in pages:
 byrun[page['run_id']].append(page)
 assert page['attempts']==1
 assert page['response_ms']>=0 and page['presentation_ms']>=0
 assert abs(page['request_to_present_ms']-page['response_ms']-page['presentation_ms'])<.001
for r in runs:
 if r['outcome']!='success':
  assert r['received_count']<=r['target_count']
  continue
 assert r['source']=='backend_local'
 assert r['received_count']==r['expected_visible_count']==r['target_count']
 assert r['page_count']==len(byrun[r['run_id']])==max(1,math.ceil(r['target_count']/200))
 assert sum(p['response_count'] for p in byrun[r['run_id']])==r['target_count']
 assert abs(r['total_ms']-r['all_responses_ms']-r['last_response_to_all_visible_ms'])<.001
 if r['target_count']: assert abs(r['total_ms']-r['visible_100_percent_ms'])<.001

def writecsv(name,data):
 keys=list(dict.fromkeys(k for r in data for k in r if k not in {'frames','checkpoints'}))
 with (P/name).open('w',newline='') as f:
  writer=csv.DictWriter(f,keys,extrasaction='ignore',lineterminator='\n');writer.writeheader();writer.writerows(data)
def p95(v):
 v=sorted(x for x in v if x is not None); return v[max(0,math.ceil(len(v)*.95)-1)] if v else None
summary=[];times=[]
for count in sorted(set(r['target_count'] for r in runs)):
 samples=[r for r in runs if r['target_count']==count]; assert len(samples)==30
 group=[r for r in samples if r['outcome']=='success']
 pp=[p for r in group for p in byrun[r['run_id']]]
 item=dict(source='backend_local',target_count=count,n=30,success=len(group),failed=30-len(group))
 for name,values in {
 'response':[p['response_ms'] for p in pp], 'presentation':[p['presentation_ms'] for p in pp],
 'total':[r['total_ms'] for r in group], 'first_pin':[r['first_pin_ms'] for r in group],
 'final_presentation':[r['last_response_to_all_visible_ms'] for r in group],
 'all_responses':[r['all_responses_ms'] for r in group]}.items():
  values=[v for v in values if v is not None]
  item[name+'_p50_ms']=statistics.median(values) if values else None
  item[name+'_p95_ms']=p95(values)
 item['total_max_ms']=max(r['total_ms'] for r in group)
 item['response_max_ms']=max(r['response_ms'] for r in pp)
 summary.append(item)
 for at in [100,250,500,1000,2000,5000]:
  times.append(dict(source='backend_local',target_count=count,elapsed_ms=at,mean_visible_count=statistics.mean(next(c['visible_count'] for c in r['checkpoints'] if c['elapsed_ms']==at) for r in group)))
writecsv('runs.csv',runs);writecsv('pages.csv',pages);writecsv('time-counts.csv',times)
(P/'summary.json').write_text(json.dumps(summary,indent=2)+'\n')
print(json.dumps(dict(validation='270 trials; 30/count; exact response and visible counts, 200/page, time sums and single HTTP attempt verified for successful trials; failures preserved',outcomes=dict(Counter(r['outcome'] for r in runs)),summary=summary),indent=2))

def fmt(v): return '—' if v is None else f'{v:.1f}'
lines=['# 실제 백엔드 로컬 실행 — 지도 핀 성능 측정','',
'- 측정일: 2026-10-12 / Galaxy S23 FE(SM-S711N), Android 15',
'- 백엔드: 최신 `origin/be/dev`의 `356c40ab`, Spring Boot local 프로필, PostgreSQL 17.11/PostGIS, 실제 Flyway 적용',
'- 클라이언트: 이전 `6de92800`의 공유 렌더러와 계측 코드 + 로컬 연결 패치, 별도 release APK(난독화 없음)',
'- 실제 API: `/api/v1/emotions/map`, USB adb reverse → `127.0.0.1:18080` → 백엔드 → 전용 DB',
'- 0·50·100·199·200·201·400·800·1600개 각각 30회, 총 270회 계획 실행(성공률은 아래 표), 각 실행 구간의 준비 실행 3회씩 총 9회 제외',
'- 확대 14, 고정 카메라, 감정 아이콘 5종, 준비된 지도·이미지 캐시, 이전과 같은 무작위 순서(seed 20261011)',
'- 매 조회 전에 실험용 DB의 핀을 교체하고 ANALYZE 실행. 준비 및 ADB 제어 시간은 측정에서 제외',
'- Docker VM: ARM64, CPU 15개, 메모리 약 7.75GiB. 앱/DB별 추가 자원 제한 없음','',
'## 세 구간의 정의','',
'① 페이지 HTTP 요청 시작→본문 전체 수신. 서버 처리·실제 DB 조회·USB 전송을 포함한다.',
'② 각 페이지 본문 수신→그 페이지까지의 대상 핀이 표시된 첫 native 프레임. JSON 해석·변환·정렬·Compose 갱신·이미지 준비·source 갱신·프레임 대기를 포함한다.',
'③ 첫 HTTP 요청→마지막 페이지의 전체 핀 표시. 프레임 완료 콜백에서 렌더 버전과 실제 feature ID를 확인한다.',
'②는 순수 GPU 시간이 아니다. 페이지 요청과 화면 갱신은 겹칠 수 있고, 각 구간의 중앙값을 더해 ③으로 계산할 수 없다.','',
'## 개수별 결과','',
'단위: ms. 성공한 실행만 사용하며, ①·②는 그 실행들의 모든 페이지 관측값, ③은 조회별 관측값의 중앙값/P95다. 실패 실행은 지연 및 시간별 표시 개수 평균에서 제외한다.','',
'| 핀 수 | 페이지 | 성공 | ① 응답 중앙값 / P95 | ② 표시 중앙값 / P95 | 마지막 응답→전체 표시 중앙값 | ③ 전체 중앙값 / P95 |',
'|---:|---:|---:|---:|---:|---:|---:|']
for s in summary:
 c=s['target_count'];lines.append(f"| {c} | {max(1,math.ceil(c/200))} | {s['success']}/30 | {fmt(s['response_p50_ms'])} / {fmt(s['response_p95_ms'])} | {fmt(s['presentation_p50_ms'])} / {fmt(s['presentation_p95_ms'])} | {fmt(s['final_presentation_p50_ms'])} | {fmt(s['total_p50_ms'])} / {fmt(s['total_p95_ms'])} |")
lines+=['','## 시간별 표시 개수','','요청 시작 후 각 시점의 성공한 실행들의 실제 feature ID 개수 평균. 완료 후에는 최종 표시 개수를 유지한 것으로 계산한다.','',
'| 총 핀 수 | 100ms | 250ms | 500ms | 1초 | 2초 | 5초 |','|---:|---:|---:|---:|---:|---:|---:|']
for s in summary:
 c=s['target_count']; vals=[r['mean_visible_count'] for r in times if r['target_count']==c]
 lines.append('| '+str(c)+' | '+' | '.join(map(fmt,vals))+' |')
baseline=P.parent/'map-pins-2026-10-11/summary.json'
if baseline.exists():
 old={s['target_count']:s for s in json.loads(baseline.read_text()) if s['source']=='fixture_local'}
 lines+=['','## 이전 고정 응답 서버와 비교','','단위: ms. 중앙값 비교다.','',
 '| 핀 수 | 이전 응답 | 이번 응답 | 이전 전체 | 이번 전체 | 전체 차이 |','|---:|---:|---:|---:|---:|---:|']
 for s in summary:
  c=s['target_count']; b=old[c]
  lines.append(f"| {c} | {fmt(b['response_p50_ms'])} | {fmt(s['response_p50_ms'])} | {fmt(b['total_p50_ms'])} | {fmt(s['total_p50_ms'])} | {s['total_p50_ms']-b['total_p50_ms']:+.1f} |")
 lines+=['','이전에는 Python 서버가 고정 JSON을 반환했고 이번에는 실제 백엔드가 DB 조회·페이지 커서·직렬화를 처리한다. 이전 고정 응답 조건은 HttpURLConnection, 이번 실제 API 조건은 앱의 Ktor 클라이언트를 사용한다. 날짜·응답 ID·서버 예열/부하도 달라서 차이를 순수 DB 비용이나 렌더러 개선/악화로 단정할 수 없다.']
lines+=['','## 검증 및 한계','',
'- 270개 고유 실행, 조건별 30회 검증. 성공한 실행의 응답 수·화면 표시 수·페이지 수 일치, 단일 HTTP 시도, 구간 시간 합계 검증 통과',
'- 첫 실행 201회는 모두 제외했다. 두 번째 실행의 95회 기록에서 재개해 232회까지 수집한 뒤, 실패 기록도 재시도 없이 보존하도록 보강하고 마지막 38회를 이어서 실행했다. 최종 통계는 중복 없는 총 270회다. 재개할 때마다 50개 핀 3회 준비 실행을 수행했다.',
'- 1,600개/14번 실행은 5페이지(1,000개) 수신 후 6번째 요청에서 약 15초 뒤 HTTP 응답 수신에 실패했다. 기기 진단의 HTTP status는 null이며, 수집한 서버 로그에는 해당 시점 오류가 없다. 서버·USB·클라이언트 중 원인은 이 기록만으로 확정할 수 없다. 실패를 대체하거나 재시도하지 않았다.',
'- 성공한 조회의 최대 전체 시간은 400개/13번의 4,612.7ms였다. 첫 페이지 응답 수신에 4,125.1ms가 걸렸다. 이 관측값은 제외하지 않았으며, 표의 P95는 이 최대값을 포함하는 상한이 아니다.',
'- P95는 성공한 관측값(조건별 29~30회)에서 계산한 탐색적 값이다. 실패를 포함한 전체 요청 지연 상한은 아니다.',
'- 실제 백엔드 구현을 실행했지만 서버는 Mac의 Docker VM이며 네트워크는 USB다. 배포된 개발/운영 서버의 절대 지연을 나타내지 않는다.',
'- DB 전체 행 수가 목표 핀 수와 같다. 대규모 배경 데이터, 동시 사용자, 실제 네트워크 및 S3 스탬프 로딩은 재현하지 않았다.',
'- 매 실행 사이 DB DELETE/INSERT를 수행한다. 삭제된 행·인덱스와 autovacuum의 영향이 있을 수 있다. 데이터 준비 시간은 제외해도 DB 상태가 배포 환경과 같다는 뜻은 아니다.',
'- 최신 백엔드의 호환 v1 API를 사용한다. v2 콘텐츠 지도 API나 행정구역 클러스터링은 별도 실험이다.',
'- 계측 feature 조회와 파일 기록의 오버헤드가 포함된다. 핀 겹침을 허용해 ID 개수와 육안으로 구별되는 핀 수는 다르다.',
'- 사용자 배포 앱의 절대 성능 측정이 아니며, iOS·첫 실행·이동 중 지도·다양한 그룹 스탬프는 측정하지 않았다.','',
'[개수별 시간 그래프](latency-by-count.svg) · [시간별 개수 그래프](visible-count-by-time.svg)','',
'[원시 기록](measurements.jsonl) · [환경](environment.json) · [재현 방법](README.md)']
(P/'report.md').write_text('\n'.join(lines)+'\n')
