# 배포 서버 부하 테스트 실행 런북

처음 하는 사람도 따라서 실행할 수 있도록 모든 명령어를 순서대로 정리했습니다.

> **중요**: 이 문서의 명령어는 **위에서 아래 순서대로** 실행하세요. 한 단계 끝나면 결과 확인 후 다음으로 넘어갑니다.

---

## 0. 준비물 체크리스트

테스트 시작 전 아래가 모두 준비되어 있어야 합니다.

- [ ] 작업 디렉터리: `/Users/dongwookim/Github/skala-final` 에서 모든 명령 실행
- [ ] `kubectl` 설치 + k8s 클러스터 접근 가능
- [ ] `locust` 설치 (`pip install locust`)
- [ ] `mc` 설치 (`brew install minio/stable/mc`)
- [ ] 배포 서버 운영자에게 **부하 테스트 시간대 사전 공지**
- [ ] 운영 트래픽이 적은 시간대 선택 (점심·심야 권장)

---

## 1. 사전 작업 (한 번만)

### 1-1. 별도 터미널에서 포트포워딩 실행

**부하 테스트가 끝날 때까지 닫지 마세요.**

```bash
bash port-forward-all.sh
```

### 1-2. k8s 환경 설정 (mc alias 자동 등록)

```bash
bash backend/load-test/locust-k8s.sh setup
```

성공하면 다음과 같이 나옵니다:

```
PostgreSQL 포드: team5-postgres-xxx ✓
MinIO 버킷: safety-files ✓
```

### 1-3. 운영 DB에 LOAD-* 데이터가 없는지 확인

```bash
bash backend/load-test/locust-k8s.sh check
```

**기대 결과**:
```
users     | 0
projects  | 0
files(DB) | 0
MinIO 오브젝트 수: 0
```

> ⚠️ **0이 아니면 멈추세요.** 운영자에게 `LOAD-*` 사번/계약번호 사용 여부 확인 후 진행.

---

## 2. 시드 생성 (한 번만)

테스트용 데이터 — 1,000 계정 + 2,000 프로젝트 + 12,000 사용내역서 + 180,000 항목.

```bash
bash backend/load-test/locust-k8s.sh seed
```

**소요 시간**: 약 1~2분.

**성공 신호**:
```
admins | users | projects | assignments | statements | items  | agent_logs
   500 |   500 |     2000 |        8000 |      12000 | 180000 |      36000
```

---

## 3. Phase 2 — Smoke 테스트 (환경 검증, 1분)

먼저 작은 부하로 환경이 잘 동작하는지 확인.

```bash
LOCUST_USERS=20 LOCUST_RATE=5 LOCUST_TIME=1m LOCUST_RESULT=k8s_smoke \
  bash backend/load-test/locust-k8s.sh save
```

**성공 신호**:
- `Failures: 0` (또는 매우 적음)
- 응답시간이 모두 1초 이내
- 마지막에 `결과 저장: results/k8s_smoke_*.csv` 출력

**실패 신호 → 멈추기**:
- `ConnectionRefused` 다수 → 백엔드 안 떠있음
- `[pool] 계정 풀 로드 실패` → 시드 미실행 또는 환경변수 누락
- 5xx 다수 → 백엔드 로그 확인

결과 파일 위치:
```
backend/load-test/results/k8s_smoke_stats.csv
backend/load-test/results/k8s_smoke_failures.csv
```

> 👉 **여기서 결과 확인 후 다음 단계로**

---

## 4. Phase 3 — Baseline (5분 + 5분, 200u)

정상 운영 부하 수준 측정. 이게 **기준선**입니다.

### 4-A. Atomic 모드 — 엔드포인트별 부하

```bash
LOCUST_USERS=200 LOCUST_RATE=10 LOCUST_TIME=5m \
  LOCUST_RESULT=k8s_baseline_atomic_200 \
  bash backend/load-test/locust-k8s.sh save
```

테스트 끝나면 **1분 휴식** (백엔드 안정화):

```bash
sleep 60
```

### 4-B. Journey 모드 — 실 워크로드 시뮬레이션

```bash
LOCUST_MODE=journey LOCUST_USERS=200 LOCUST_RATE=10 LOCUST_TIME=5m \
  LOCUST_RESULT=k8s_baseline_journey_200 \
  bash backend/load-test/locust-k8s.sh save
```

### 4-C. 정합성 검증

```bash
sleep 30
LOCUST_RESULT=k8s_baseline_atomic_200 \
  bash backend/load-test/locust-k8s.sh verify
```

**성공 신호**:
- `orphan_by_item: 0`, `orphan_by_file: 0`
- `agent_logs UNIQUE 위반: 0 rows`
- `usage_statements 상태 분포`에 4가지 상태 모두 등장

> 👉 **여기서 결과 분석 후 다음 단계로**

---

## 5. Phase 4 — Peak (5분 + 5분, 500u)

피크 시간대 부하 (정상의 2.5배).

```bash
# 5-A. Atomic
LOCUST_USERS=500 LOCUST_RATE=20 LOCUST_TIME=5m \
  LOCUST_RESULT=k8s_peak_atomic_500 \
  bash backend/load-test/locust-k8s.sh save

sleep 60

# 5-B. Journey
LOCUST_MODE=journey LOCUST_USERS=500 LOCUST_RATE=20 LOCUST_TIME=5m \
  LOCUST_RESULT=k8s_peak_journey_500 \
  bash backend/load-test/locust-k8s.sh save

sleep 30

# 5-C. 정합성 검증
LOCUST_RESULT=k8s_peak_atomic_500 \
  bash backend/load-test/locust-k8s.sh verify
```

> 👉 **결과 확인 후 다음 단계로**

---

## 6. Phase 5 — Stress (8분 + 8분, 1000u)

한계 부하 — 시스템이 어디서 무너지는지 확인.

```bash
# 6-A. Atomic
LOCUST_USERS=1000 LOCUST_RATE=25 LOCUST_TIME=8m \
  LOCUST_RESULT=k8s_stress_atomic_1000 \
  bash backend/load-test/locust-k8s.sh save

sleep 120

# 6-B. Journey
LOCUST_MODE=journey LOCUST_USERS=1000 LOCUST_RATE=25 LOCUST_TIME=8m \
  LOCUST_RESULT=k8s_stress_journey_1000 \
  bash backend/load-test/locust-k8s.sh save

sleep 60

# 6-C. 정합성 검증
LOCUST_RESULT=k8s_stress_atomic_1000 \
  bash backend/load-test/locust-k8s.sh verify
```

---

## 7. Phase 6 (선택) — Spike (7분, 100→800u)

갑작스러운 부하 폭증 회복력 테스트.

```bash
LOCUST_RESULT=k8s_spike \
  bash backend/load-test/locust-k8s.sh spike

sleep 60

LOCUST_RESULT=k8s_spike \
  bash backend/load-test/locust-k8s.sh verify
```

**진행 단계** (자동):
- 0~120s: 100u baseline (warm-up)
- 120~150s: 100→800u 급증
- 150~330s: 800u 유지
- 330~390s: 800→100u 감소

---

## 8. Phase 7 (선택) — Soak (1시간, 200u)

장시간 안정성 — 메모리 누수·커넥션 누수 관측.

```bash
LOCUST_TIME=1h LOCUST_USERS=200 LOCUST_RATE=5 \
  LOCUST_RESULT=k8s_soak_1h \
  bash backend/load-test/locust-k8s.sh soak

LOCUST_RESULT=k8s_soak_1h \
  bash backend/load-test/locust-k8s.sh verify
```

---

## 9. 정리 (마지막)

```bash
# DB + MinIO 데이터 삭제
bash backend/load-test/locust-k8s.sh teardown

# 0/0/0 확인
bash backend/load-test/locust-k8s.sh check
```

**성공 신호**:
```
users     | 0
projects  | 0
files(DB) | 0
MinIO 오브젝트 수: 0
```

---

## 🚨 단계별 STOP 기준 — 다음 단계 가지 말아야 할 신호

| 신호 | 어떻게 알아보나 | 대응 |
|---|---|---|
| **5xx 에러율 > 3%** | `*_failures.csv` 에 500/502/503 다수 | **즉시 멈춤**, 백엔드 로그 확인 |
| **p99 > 5초** | `*_stats.csv` 의 `99%` 컬럼 | DB·풀 점검 |
| **정합성 위반** | verify 결과 `orphan_by_item` 또는 `agent_logs UNIQUE 위반` > 0 | **즉시 멈춤**, 트랜잭션 이슈 |
| **k8s pod restart** | `kubectl get pods` 결과 `RESTARTS` 증가 | 재시작 후 baseline부터 |
| **백엔드 OOM** | pod log에 `OutOfMemoryError` | 재시작 + 메모리 증설 검토 |

---

## 🆘 자주 발생하는 문제

### 문제 1: `[pool] 계정 풀 로드 실패`

```bash
# 환경변수 확인
echo "K8S_NAMESPACE: $K8S_NAMESPACE"

# 시드 다시 확인
bash backend/load-test/locust-k8s.sh check
# users·projects 모두 0이면 시드 안 됨
bash backend/load-test/locust-k8s.sh seed
```

### 문제 2: `Connection refused` (백엔드 접근 불가)

```bash
# port-forward가 살아있는지 확인
curl -sf http://localhost:8000/v3/api-docs
# 200 안 나오면 port-forward 다시 시작
```

### 문제 3: teardown이 FK 에러로 실패

이미 수정된 상태(`agent_logs`를 먼저 삭제하는 순서). 그래도 실패하면:

```bash
# 수동 정리
kubectl exec -n skala3-finalproj-class2-team5 \
  $(kubectl get pods -n skala3-finalproj-class2-team5 -l app=team5-postgres \
    -o jsonpath='{.items[0].metadata.name}') -- \
  psql -U safety_user -d safety -f - < backend/load-test/teardown.sql
```

### 문제 4: 명령 도중 Ctrl+C 로 중단했을 때

```bash
# 1) 정합성 확인
LOCUST_RESULT=k8s_interrupted bash backend/load-test/locust-k8s.sh verify

# 2) 깨끗하게 다시 시작하려면
bash backend/load-test/locust-k8s.sh teardown
bash backend/load-test/locust-k8s.sh check
bash backend/load-test/locust-k8s.sh seed
```

---

## 📊 결과 파일 위치

모두 `backend/load-test/results/` 에 저장됩니다.

| 파일 | 내용 |
|---|---|
| `<RESULT>_stats.csv` | 엔드포인트별 응답시간 (p50/p95/p99) · RPS |
| `<RESULT>_stats_history.csv` | 시간별 추이 (그래프용) |
| `<RESULT>_failures.csv` | 실패 요청 목록 (비어있어야 정상) |
| `<RESULT>_exceptions.csv` | Python 예외 (비어있어야 정상) |
| `<RESULT>_verify.txt` | 정합성 검증 결과 |

---

## 📈 결과 해석 빠른 가이드

`*_stats.csv` 를 열면 컬럼:

| 컬럼 | 의미 | 정상 기준 |
|---|---|---|
| `Request Count` | 총 호출 수 | 0이 아니면 OK |
| `Failure Count` | 실패 수 | **0** (또는 1% 미만) |
| `50%` (median) | 평균적 응답시간 | < 200ms |
| `95%` | 상위 5% 응답시간 | < 500ms |
| `99%` | 상위 1% 응답시간 | < 2s |
| `Requests/s` | 초당 처리량 | 부하별 기준 |

**조심 신호**:
- `99%`가 `50%`의 50배 이상 → DB 커넥션 풀 의심
- `Failure Count > 0` 중 5xx 코드 → 서버 에러
- `Request Count` 가 너무 적음 → 환경변수 누락

---

## 🎯 전체 소요 시간 추정

| Phase | 시간 |
|---|---|
| 0~1. 준비·시드 | 5분 |
| 2. Smoke | 1분 |
| 3. Baseline (atomic + journey + verify) | 약 12분 |
| 4. Peak (atomic + journey + verify) | 약 12분 |
| 5. Stress (atomic + journey + verify) | 약 19분 |
| 6. Spike (선택) | 약 8분 |
| 7. Soak (선택) | 약 65분 |
| 8. 정리 | 1분 |
| **합계 (Phase 0~5)** | **약 50분** |
| **합계 (Phase 0~7 전체)** | **약 2시간** |

---

## ✅ 권장 진행 패턴

1. **첫 실행**: Phase 0~3까지 (Smoke + Baseline). 결과 분석 → 병목 파악
2. **개선 작업** (별도 진행): 발견된 병목 수정
3. **재측정**: Phase 3 (Baseline) 만 다시 → 개선 전후 비교
4. **여유 시**: Phase 4~7 (Peak/Stress/Spike/Soak)

각 단계 끝날 때마다 CSV/verify 결과 공유해 주시면 분석 도와드립니다.
