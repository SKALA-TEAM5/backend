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

AI 호출 API(`/agents/parse`, `/agents/validate`, `/agents/legal`, `/agents/report`)는 외부 의존성으로 인해 제외됩니다.

| 시나리오 | 비중 | 주요 흐름 |
|---|---|---|
| `AdminScenario` | 30% | 프로젝트 조회·수정, 사용내역서 조회, 검토완료·보완요청, 대시보드, 아카이브, agent 로그·경고 조회 |
| `UserScenario` | 70% | 항목 CRUD·카테고리 이동, 파일 업로드·삭제, 증빙 연결·이동·삭제, agent 읽기(button-states/todos/logs/warnings), 사용내역서 제출 |

### 테스트 데이터

`seed` 실행 시 아래 데이터가 생성됩니다. 모두 `LOAD-` prefix로 식별되어 운영 데이터와 섞이지 않습니다.

| 항목 | 수량 |
|---|---|
| admin 계정 (`LOAD-ADMIN-001` ~ `030`) | 30명 |
| user 계정 (`LOAD-USER-001` ~ `030`) | 30명 |
| 프로젝트 (`LOAD-CN-001` ~ `030`) | 30개 |
| 사용내역서 (프로젝트당 1개, `draft`) | 30개 |
| 세부항목 (사용내역서당 5개) | 150개 |

공통 비밀번호: `P@ssw0rd123!`

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
