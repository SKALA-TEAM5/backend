# 부하 테스트 가이드

Locust 기반 백엔드 API 부하 테스트입니다.  
로컬 Docker 환경과 k8s 배포 환경 두 가지를 지원합니다.

---

## 목차

1. [사전 준비](#1-사전-준비)
2. [로컬 환경에서 테스트](#2-로컬-환경에서-테스트)
3. [k8s 환경에서 테스트](#3-k8s-환경에서-테스트)
4. [파라미터 조정](#4-파라미터-조정)
5. [결과 파일](#5-결과-파일)
6. [시나리오 설명](#6-시나리오-설명)

---

## 1. 사전 준비

아래 도구를 먼저 설치하세요. **최초 1회만** 필요합니다.

```bash
# Locust 설치
pip install locust

# MinIO Client(mc) 설치
brew install minio/stable/mc

# 로컬 MinIO alias 등록
mc alias set minio http://localhost:9000 minioadmin minioadmin
```

> **k8s 환경**은 추가로 `kubectl`이 필요합니다.  
> mc alias는 `locust-k8s.sh setup` 명령이 자동으로 설정해줍니다.

---

## 2. 로컬 환경에서 테스트

> Docker가 실행 중이어야 합니다 (`docker compose up -d db minio`).

### 한 번에 전체 실행

```bash
bash load-test/locust-local.sh run
```

`seed → 헤드리스 테스트 → teardown → 원복 확인` 순서로 자동 진행됩니다.

### 단계별 실행

필요한 경우 각 단계를 따로 실행할 수 있습니다.

```bash
# 1. 테스트 데이터 생성
bash load-test/locust-local.sh seed

# 2-A. 웹 UI로 직접 실행 (브라우저에서 localhost:8089 접속)
bash load-test/locust-local.sh up

# 2-B. 헤드리스로 실행 + CSV 결과 저장
bash load-test/locust-local.sh save

# 3. 테스트 데이터 전체 삭제 (DB + MinIO)
bash load-test/locust-local.sh teardown

# 현황 확인 (데이터가 남아있는지 확인할 때)
bash load-test/locust-local.sh check
```

> **MinIO 정리 경고** — `mc` 미설치 또는 alias 미설정 상태에서 teardown을 돌리면 DB 행만 삭제되고 MinIO 오브젝트는 누적된다. teardown 출력에 "MinIO 오브젝트는 수동 정리 필요" 경고가 뜨면 사전 준비 단계의 mc 설치/alias 등록부터 마치고 다시 실행하거나, 안내된 `mc rm --recursive --force ...` 명령을 수동으로 실행해야 한다.

---

## 3. k8s 환경에서 테스트

> `port-forward-all.sh`가 실행 중이어야 합니다.

### 0. 포트포워딩 시작 (별도 터미널)

```bash
bash port-forward-all.sh
```

터미널을 닫으면 포트포워딩이 끊기므로, 테스트가 끝날 때까지 유지하세요.

### 1. 최초 1회 — 환경 설정

```bash
bash load-test/locust-k8s.sh setup
```

k8s secret에서 MinIO 크레덴셜을 자동으로 읽어 mc alias를 등록합니다.  
자동 추출에 실패하면 직접 입력하라는 안내가 나옵니다.

### 2. 전체 자동 실행

```bash
bash load-test/locust-k8s.sh run
```

setup이 되어 있지 않으면 자동으로 먼저 실행합니다.

### 단계별 실행

로컬과 동일한 명령어를 사용합니다.

```bash
bash load-test/locust-k8s.sh seed
bash load-test/locust-k8s.sh up       # 웹 UI
bash load-test/locust-k8s.sh save     # 헤드리스
bash load-test/locust-k8s.sh teardown
bash load-test/locust-k8s.sh check
```

---

## 4. 파라미터 조정

### 방법 A — 스크립트 상단 직접 수정

`locust-local.sh` 또는 `locust-k8s.sh` 파일을 열면 상단에 설정 블록이 있습니다.

```bash
# ================================================================
#  테스트 설정 — 여기서 값을 바꾸거나 환경변수로 override 가능
# ================================================================
LOCUST_HOST="${LOCUST_HOST:-http://localhost:8000}"  # 백엔드 주소
LOCUST_USERS="${LOCUST_USERS:-20}"                   # 동시 사용자 수
LOCUST_RATE="${LOCUST_RATE:-5}"                      # 초당 사용자 증가 수 (ramp-up)
LOCUST_TIME="${LOCUST_TIME:-1m}"                     # 테스트 실행 시간
LOCUST_RESULT="${LOCUST_RESULT:-local_날짜시각}"      # 결과 CSV 파일명
```

### 방법 B — 환경변수로 override

스크립트를 수정하지 않고 실행 시 앞에 붙여서 넘길 수 있습니다.

```bash
LOCUST_USERS=100 LOCUST_RATE=10 LOCUST_TIME=5m \
bash load-test/locust-k8s.sh run
```

### 파라미터 설명

| 변수 | 기본값 | 설명 |
|---|---|---|
| `LOCUST_HOST` | `http://localhost:8000` | 테스트 대상 백엔드 주소 |
| `LOCUST_USERS` | `20` | 최대 동시 사용자 수 |
| `LOCUST_RATE` | `5` | 초당 사용자 증가 수 (ramp-up) |
| `LOCUST_TIME` | `1m` | 테스트 총 실행 시간 (`30s`, `5m`, `1h` 형식) |
| `LOCUST_RESULT` | `local_날짜시각` | 결과 CSV 파일명 (겹치면 덮어씀) |

### 권장 파라미터 (단계별)

| 단계 | USERS | RATE | TIME | 용도 |
|---|---|---|---|---|
| 파이프라인 검증 | `20` | `5` | `1m` | 로컬에서 동작 확인용 |
| Baseline | `200` | `10` | `5m` | 정상 부하 기준선 측정 |
| Peak | `500` | `20` | `5m` | 피크 부하 측정 |
| Stress | `1000` | `25` | `8m` | 한계 부하 측정 |

> **주의:** 로컬 환경에서는 서버와 Locust가 같은 머신에서 동작하므로  
> 성능 측정 목적이라면 반드시 k8s 환경에서 실행하세요.

---

## 5. 결과 파일

`save` 또는 `run` 실행 후 `results/` 디렉터리에 CSV 4종이 저장됩니다.

| 파일 | 내용 |
|---|---|
| `*_stats.csv` | 엔드포인트별 응답시간 · RPS 요약 |
| `*_stats_history.csv` | 시간 흐름에 따른 RPS · 응답시간 추이 |
| `*_failures.csv` | 실패 요청 목록 |
| `*_exceptions.csv` | 예외 스택트레이스 |

> CSV는 `.gitignore` 처리되어 있습니다. 분석 메모(`.md`)만 커밋하세요.

---

## 6. 시나리오 설명

AI 호출 API(`/agents/parse`, `/agents/validate`, `/agents/legal`, `/agents/report`, `POST /items`)와 `legal_rag` 스키마 조회 API(`/law-changes/recent`)는 외부 의존성으로 인해 제외됩니다.

### 모드 (`LOCUST_MODE` 환경변수)

| 모드 | 클래스 | 용도 |
|---|---|---|
| `atomic` (기본) | `AdminScenario`, `UserScenario` | 단위 endpoint 부하 측정 — smoke·baseline |
| `journey` | `WriterJourney`, `ReviewerJourney`, `BrowsingLoad` | 실제 비즈니스 여정 측정 — peak·stress·soak |
| `mixed` | 전체 5종 동시 | 운영 패턴 근접 |

```bash
# Journey 모드로 baseline 측정
LOCUST_MODE=journey bash load-test/locust-local.sh save
```

### Atomic 시나리오

| 클래스 | weight | 특성 |
|---|---|---|
| `AdminScenario` | 3 | read-heavy (read:write ≈ 70:30). 검토자 시점. wait 3~10s |
| `UserScenario` | 7 | balanced. 작성자 시점. wait 2~8s |

### Journey 시나리오

| 클래스 | weight | 한 세션 시퀀스 |
|---|---|---|
| `WriterJourney` | 4 | login → detail → requirements×3 → upload+link×3 → submit. wait 5~15s |
| `ReviewerJourney` | 2 | 목록 → dashboard → detail → button-states → todos → confirm×3 → complete-review or supplement. wait 8~20s |
| `BrowsingLoad` | 4 | read-only (admin·user 50/50). projects·dashboard·archive·files 순회. wait 2~5s |

### Spike (LoadTestShape)

```bash
LOCUST_RESULT=spike_p1 bash load-test/locust-local.sh spike
```

7분 구성: 100u baseline → 800u spike → 800u sustained → 100u drain. `SPIKE_*` 환경변수로 조정.

### Soak

```bash
LOCUST_RESULT=soak_1h LOCUST_TIME=1h LOCUST_USERS=200 \
bash load-test/locust-local.sh soak
```

장시간 sustained — 메모리·커넥션 누수·MinIO 누적 관측.

### 정합성 검증

```bash
LOCUST_RESULT=baseline_200 bash load-test/locust-local.sh verify
```

`verify.sql` 실행 → orphan link/summary, agent_logs UPSERT 위반, status 분포, files 카운트 출력. 결과는 `results/<RESULT>_verify.txt`.

### 테스트 데이터

`seed` 실행 시 아래 데이터가 생성됩니다. 모두 `LOAD-` prefix로 식별되어 운영 데이터와 섞이지 않습니다.

| 항목 | 수량 |
|---|---|
| admin 계정 (`LOAD-ADMIN-001` ~ `500`) | 500명 |
| user 계정 (`LOAD-USER-001` ~ `500`) | 500명 |
| 프로젝트 (`LOAD-CN-0001` ~ `LOAD-CN-2000`) | 2,000개 |
| 담당자 배정 (각 프로젝트당 admin 2 + user 2, m:n) | 약 8,000행 |
| 사용내역서 (프로젝트당 6개월치, 2026-01 ~ 2026-06) | 12,000개 |
| 세부항목 (statement당 15개) | 180,000개 |
| agent_logs (statement당 classi/safety-doc/legal success) | 36,000개 |

공통 비밀번호: `P@ssw0rd123!`

**상태 분포** (6월은 항상 `draft`, 그 외 월):
- `draft` 60% / `upload_completed` 20% / `supplement_required` 10% / `review_completed` 10%

**매핑 규칙** (결정론적):
- 프로젝트 k → admin (((k-1) % 500) + 1) + admin ((k+249) % 500 + 1)
- 프로젝트 k → user 동일 패턴
- 각 admin/user는 평균 8개 프로젝트에 직접 배정 → `scope=assigned` 응답 다양화

> **agent_logs 시딩 이유**: `PATCH /usage-statements/{id}/{request-supplement,complete-review}`는 legal agent 로그를 강제(`requireLegalCompleted`)합니다. FastAPI를 호출하지 않는 부하 테스트에서 상태 전이가 정상 측정되도록 가상 success 로그를 시드합니다.

### 정상 실패 처리

상태 전이 API는 조건 불충족 시 4xx를 반환하는데, 이는 예상된 동작이므로 Locust에서 성공으로 처리합니다.

| API | 허용 응답 코드 |
|---|---|
| `PATCH /usage-statements/:id/submit` | 200, 400, 409, 422 |
| `PATCH /usage-statements/:id/complete-review` | 200, 400, 409, 422 |
| `PATCH /usage-statements/:id/request-supplement` | 200, 400, 409, 422 |
| `POST /items/:id/evidence-files` | 200, 409 |
| `PATCH /evidence-file-links/:id` | 200, 404, 409 |
| `DELETE /evidence-file-links/:id` | 200, 404 |
