# 부하 테스트 결과 폴더

이 폴더는 백엔드에 부하를 걸어보고 **얼마나 잘 버티는지** 측정한 결과를 모아둔 곳입니다.

---

## 한눈에 보기

```
results/
├── reports/         👈 무엇이 문제고 어떻게 고쳤는지 — 먼저 읽어보세요
├── before/          👈 개선 전 상태 (문제가 많았을 때)
├── round1/          👈 1차 개선 후 측정 결과
├── round2/          👈 2차 개선 후 측정 결과
├── round3/          👈 3차 개선 후 측정 결과 (V15 충돌 해소 + GET /projects 인덱스)
└── archive/         👈 옛날 분석 (참고용)
```

**처음 보시면 이 순서로 읽어보세요**:

1. [`reports/00_initial_diagnosis_20260614.md`](reports/00_initial_diagnosis_20260614.md) — 어떤 문제가 있었는지 진단 보고서
2. [`reports/improvement_log.md`](reports/improvement_log.md) — 어떻게 고쳤는지 작업 로그
3. 아래 "라운드별 결과 비교" 표 — 숫자로 효과 확인

---

## 이 폴더 안에 들어있는 것

### 📁 `reports/` — 문서

| 파일 | 내용 |
|---|---|
| `00_initial_diagnosis_20260614.md` | 첫 번째 측정 결과 분석 보고서. 어떤 endpoint에서 얼마나 느려지고 실패하는지 진단 |
| `improvement_log.md` | 단계별로 코드를 어떻게 고쳤고, 왜 그렇게 고쳤는지 |

### 📁 `before/` — 개선 전 측정

처음 측정했을 때 (코드 손대기 전) 결과. 이게 **비교 기준선** 역할을 합니다.

```
before/
├── smoke_20u/        - 20명으로 살짝 (환경 점검용)
├── baseline_200u/    - 200명으로 (정상 운영 가정)
├── peak_500u/        - 500명으로 (피크 시간 가정)
└── stress_1000u/     - 1000명으로 (한계 부하)
```

`200u` = "200 users" = "동시에 사용자 200명이 들이닥쳤을 때".

### 📁 `round1/` — 1차 개선 후 측정

**로그인 병목을 해소**한 뒤 측정한 결과.

고친 것:
- DB 커넥션 풀 크기 조정
- 로그인 함수에서 트랜잭션 범위 축소

### 📁 `round2/` — 2차 개선 후 측정

**데이터 조회 쿼리를 최적화**한 뒤 측정한 결과.

고친 것:
- 목록 조회 시 N+1 쿼리 제거
- 검색용 인덱스 추가 (`pg_trgm`)
- 대시보드 집계용 부분 인덱스 추가

> ⚠️ round2 측정 시 **검색·대시보드 인덱스가 실제로는 미적용** 상태였던 것이 round3 점검에서 확인됨 (V15 충돌). round3 에서 V16 으로 rename 해 정상 적용. round2 측정 결과는 N+1 정리 효과만 반영된 셈.

### 📁 `round3/` — 3차 개선 후 측정

**마이그레이션 충돌 해소 + `GET /projects` 풀스캔 정리**.

고친 것:
- V15 Flyway 충돌 해소 — round2 의 검색·대시보드 인덱스가 실제 적용되도록 V16 으로 rename
- `GET /projects` 정렬·기간 필터 인덱스 추가 (B-tree on `construction_start_date`, `construction_end_date`, `(project_name, id)`)
- `latest_statement` CTE 의 DISTINCT ON 최적화 — `usage_statements (project_id, report_month DESC, revision_no DESC)` 복합 인덱스

> 참고: 라운드 1+2 배포 후 smoke 재측정에서 `[setup] login` 이 6~12초로 여전히 느림 — **배포 환경 pod CPU 한계**(`requests: 200m`) 가 지배적이라 판정. 백엔드 코드로 더 줄이긴 어려움. 자세한 진단은 [`reports/improvement_log.md`](reports/improvement_log.md) 의 라운드 3 진단 섹션 참고.

---

## 각 측정 폴더 안 파일들

예를 들어 `round2/baseline_200u/` 안에 이런 파일들이 있어요:

| 파일 | 뭔지 |
|---|---|
| `stats.csv` | endpoint별 요약. 몇 번 호출했고, 응답시간이 어땠는지 (p50/p95/p99) |
| `failures.csv` | 실패한 요청들. 어떤 endpoint가 몇 번 실패했는지 |
| `exceptions.csv` | 예외 (에러 메시지 종류) |
| `stats_history.csv` | 시간별 추이 (몇 초 단위로 RPS, 응답시간 변화) |
| `verify.txt` | DB 데이터가 깨졌는지 확인 결과 (orphan, UNIQUE 위반 등) |
| `meta.txt` | 이 측정이 언제, 어떤 commit에서, 어떤 설정으로 돌렸는지 메타정보 |

### 응답시간 용어가 처음이라면

- **p50 (median)** — 절반의 요청이 이 시간 안에 끝남. 보통 사용자 경험.
- **p95** — 95%의 요청이 이 시간 안에 끝남. 평균보다 느린 끝자락.
- **p99** — 99%의 요청이 이 시간 안에 끝남. **여기가 무너지면 일부 사용자가 멈춤을 경험**.

좋은 시스템은 p50뿐 아니라 p99도 안정적입니다.

---

## 라운드별 결과 비교

> **주의**: round1, round2 단독 측정은 진행하지 않음. 라운드 2 의 V15 마이그레이션 충돌이 라운드 3 시점에 발견됐고, 그 전까지 round1·round2 의 DB 변경이 실제로 배포에 적용되지 않은 상태였기 때문. 그래서 아래 표는 **before vs round3 직접 비교** 가 의미 있음. (자세한 경위: [`reports/improvement_log.md`](reports/improvement_log.md) 의 라운드 3 진단 섹션)

### Baseline (200명 동시 사용, 5분)

| 지표 | before (개선 전) | round3 (1+2+3 통합) |
|---|---|---|
| 실패율 | **11.01%** ❌ | **0.00%** ✅ |
| 전체 응답시간 p99 | **31초** ❌ | 63초 ⚠️ (login 영향) |
| 로그인 응답시간 (중간값) | **30초** ❌ | 59초 ⚠️ (CPU 환경 한계) |
| 500 에러 건수 | 108건 | **0건** ✅ |
| 인증 실패 (401) 건수 | 513건 | **0건** ✅ |
| RPS (처리량) | 18.85 | 28.87 (+53%) |

### Peak (500명 동시 사용, 5분)

| 지표 | before | round3 |
|---|---|---|
| 실패율 | 17.0% ❌ | **0.00%** ✅ |
| 전체 응답시간 p99 | 66초 ❌ | 176초 ⚠️ (login 영향) |
| 로그인 응답시간 (중간값) | 46초 | 123초 ⚠️ |
| `POST /auth/refresh` p99 | **59초** ❌ | **5.3초** ✅ (-91%) |
| `GET /projects?keyword=` p99 | (Peak 미수집) | 11초 |
| `GET /dashboard` p99 | (Peak 미수집) | 17초 |

### Stress (1000명 동시 사용, 8분) — 본 검증

| 지표 | before | round3 | 변화 |
|---|---|---|---|
| 실패율 | 18.3% ❌ | **0.00%** ✅ | 완전 해소 |
| 5xx 건수 | 678건 | **0건** ✅ | |
| 401 건수 | 4,358건 | **0건** ✅ | |
| 로그인 응답시간 (중간값) | 76초 | 210초 ⚠️ | CPU 환경 한계 |
| `POST /auth/refresh` p99 | **121초** ❌ | **15초** ✅ | **-88%** |
| `GET /projects?keyword=` p99 | **93초** ❌ | **19초** ✅ | **-80%** |
| `GET /projects` p99 | **90초** ❌ | **18초** ✅ | **-80%** |
| `GET /projects?scope=all` p99 | **50초** ❌ | **18초** ✅ | **-64%** |
| `GET /dashboard` p99 | **91초** ❌ | **22초** ✅ | **-76%** |
| `GET /usage-statements` p99 | 7초 | 20초 ⚠️ | login 큐 영향 (해석은 improvement_log) |
| 정합성 위반 | 0 | **0** ✅ | 유지 |

→ **인덱스가 직접 작용하는 4개 endpoint 모두 64~80% p99 단축** — 라운드 2/3 효과 실측 확정.
→ **로그인 + 의존성 폭증 차단** — refresh 121초 → 15초 (-88%) 가 라운드 1 의 강력한 증거.
→ **응답시간 절대값 일부 악화는 "허위 빠름" 사라진 부작용** — before 의 18.3% 실패가 시스템 부담을 줄여줬던 것. 운영상은 round3 가 압도적 우위 (모두 성공).

---

### 한 줄 요약

- **인증·정합성**: 라운드 1 효과 ✅ — baseline·peak·stress 모두 5xx/401 완전 해소, 정합성 100% 유지
- **인덱스 효과**: ✅ **확정** — stress 비교에서 keyword/dashboard/projects/scope=all 모두 -64~80%
- **N+1 정리**: 코드 적용 OK 이나 측정상 login 큐 영향에 묻힘 — 운영 환경(replica 증설 후)에서 재확인 필요
- **login**: 환경(pod CPU) 한계가 지배적. 백엔드 코드로 더 못 줄임 — `requests.cpu` 200m 상향 및 replica 증설 필요 (매니페스트는 `SKALA-TEAM5/deploy` 레포)

> 빈 칸 `_`은 아직 측정 안 한 부분. 측정하면서 채워가는 표입니다.

---

## 직접 측정해보고 싶다면

### 사전 준비 (한 번만)

1. `kubectl`로 k8s 접근 가능한 상태
2. **별도 터미널**에서 포트포워딩 실행:
   ```bash
   bash backend/load-test/port-forward-all.sh
   ```
3. 최초 1회 setup:
   ```bash
   bash backend/load-test/locust-k8s.sh setup
   ```
4. 테스트용 시드 데이터 넣기:
   ```bash
   bash backend/load-test/locust-k8s.sh seed
   ```

### 측정 (메인)

**예: round2의 baseline 측정**

```bash
LOCUST_OUT_DIR=round2/baseline_200u \
LOCUST_USERS=200 \
LOCUST_RATE=10 \
LOCUST_TIME=5m \
bash backend/load-test/locust-k8s.sh save
```

위 명령이 하는 일:
- 200명이 5분 동안 (초당 10명씩 늘려서) 백엔드에 요청을 보냄
- 결과를 `results/round2/baseline_200u/` 폴더 안에 자동 저장
- `stats.csv`, `failures.csv`, `meta.txt` 등이 자동 생성

### 측정 후 — DB 정합성 검증

```bash
LOCUST_OUT_DIR=round2/baseline_200u \
bash backend/load-test/locust-k8s.sh verify
```

→ `verify.txt`도 같은 폴더에 자동 저장.

### 환경변수 의미

| 이름 | 뜻 | 예시 |
|---|---|---|
| `LOCUST_OUT_DIR` | 결과를 저장할 폴더 이름 | `round2/baseline_200u` |
| `LOCUST_USERS` | 동시 사용자 수 | `200` |
| `LOCUST_RATE` | 1초에 몇 명씩 늘릴지 (ramp-up) | `10` |
| `LOCUST_TIME` | 얼마나 오래 돌릴지 | `5m`, `30s` |

### 라운드별 권장 측정값

| 단계 | USERS | RATE | TIME |
|---|---|---|---|
| Smoke | 20 | 5 | 1m |
| Baseline | 200 | 10 | 5m |
| Peak | 500 | 20 | 5m |
| Stress | 1000 | 25 | 8m |

폴더 이름은 `<round>/<phase>_<users>u` 규칙: `round2/baseline_200u`, `round2/peak_500u`, …

---

## 측정 끝나고 결과 확인하는 법

### stats.csv 보는 법

엑셀이나 VS Code로 열면 됩니다. 컬럼 의미:

| 컬럼 | 뜻 |
|---|---|
| Name | endpoint 이름 (예: `GET /projects`) |
| Request Count | 호출 횟수 |
| Failure Count | 실패 횟수 |
| Median Response Time | 응답시간 중간값 (ms) |
| 99% | p99 응답시간 (ms) |
| Requests/s | 초당 요청 수 (RPS) |

**먼저 볼 것**:
1. Failure Count 가 0이 아닌 endpoint
2. 99% 가 1000ms (1초) 넘는 endpoint

### meta.txt 보는 법

```
phase: baseline           ← 어느 단계
users: 200                ← 동시 사용자 수
duration: 5m              ← 실행 시간
commit: a1b2c3d           ← 이 측정 당시 코드의 git commit
timestamp: 2026-06-14...  ← 측정 시각
```

→ "이 결과가 어느 시점, 어느 코드에서 나왔는지" 즉시 확인 가능.

---

## 자주 묻는 것

**Q. 측정이 끝났는데 폴더 안에 파일이 비어있어요.**
→ 측정 도중 백엔드가 죽었거나 포트포워딩이 끊겼을 가능성. `port-forward-all.sh` 실행 중인지 확인.

**Q. `_tmp_stats.csv` 같은 파일이 남아있어요.**
→ 측정 종료가 비정상이라 rename이 안 된 경우. 수동으로 `mv _tmp_stats.csv stats.csv` 해주세요.

**Q. round1, round2 라는 이름이 정확히 무슨 기준?**
→ "한 묶음의 코드 변경 + 측정" 단위. `reports/improvement_log.md`에서 각 라운드에 뭘 고쳤는지 확인할 수 있어요.

**Q. before와 round1의 측정값이 같으면?**
→ 변경이 효과가 없거나, 실제 배포가 안 됐거나(코드 변경 후 docker image 재빌드/배포 빼먹은 경우 흔함), port-forward로 측정해서 환경 노이즈가 너무 클 수도 있어요. `meta.txt`의 commit hash 확인부터.

---

*마지막 업데이트: 2026-06-14 (round3 stress 까지 모든 phase 결과 반영 — 측정 사이클 종료)*
