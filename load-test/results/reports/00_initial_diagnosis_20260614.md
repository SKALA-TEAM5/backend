# 배포 서버 부하 테스트 결과 보고서

| 항목 | 내용 |
|---|---|
| 측정 일자 | 2026-06-14 |
| 대상 서비스 | 건설 현장 산업안전보건관리비 사용내역서 관리 백엔드 (Spring Boot 3.5) |
| 측정 환경 | k8s (`skala3-finalproj-class2-team5`) |
| 측정 도구 | Locust 2.44.3, atomic 시나리오 모드 |
| 측정 단계 | Smoke (20u/1m) → Baseline (200u/5m) → Peak (500u/5m) → Stress (1000u/8m) |
| 측정 범위 | Spring Boot REST API 전반. **FastAPI 호출 API 및 `legal_rag` 스키마 조회 API는 제외**. |

---

## 1. Executive Summary

### 핵심 결론

1. **시스템은 200u 부하에서부터 한계 신호를 보임** — Baseline(200u)부터 실패율 11%, p99 31초 발생.
2. **병목의 1차 원인은 ramp-up 구간의 `[setup] login` (bcrypt 부하 + 풀 고갈)** — 모든 단계의 실패가 ramp-up 초반 1~3분에 집중됨. 그 이후 본 endpoint들의 응답시간은 정상 범위.
3. **데이터 정합성은 모든 단계에서 깨끗** — orphan, UNIQUE 위반, page_no 위반 등 0건. Spring 트랜잭션 처리는 시스템 한계 상황에서도 안정적으로 동작.
4. **본 endpoint 자체의 응답시간은 매우 양호** — Stress(1000u)에서도 PK 조회·CRUD는 median 30~50ms로 안정적.

### 한 줄 평가

> **백엔드 비즈니스 로직은 견고하나, 인증·인프라 계층에서 200u부터 병목이 발생함. 단순 인스턴스 증설보다 인증·풀 튜닝을 우선 검토 필요.**

### 권장 우선순위 (상세는 §6 참고)

| 우선 | 항목 | 예상 효과 |
|---|---|---|
| P1 | **HikariCP `maximum-pool-size` 점검 (디폴트 10 추정)** | 동시성 한계 즉각 완화 |
| P1 | **bcrypt strength 검토 + login 분산 시뮬레이션** | ramp-up 5xx·401 제거 |
| P2 | **k8s pod CPU/메모리 리소스 limits 조정** | 처리 capacity 확장 |
| P2 | **`GET /usage-statements` N+1 쿼리 정리** | 목록 응답 안정화 |
| P3 | **`GET /projects?keyword=` LIKE 풀스캔 인덱싱** | 검색 응답시간 단축 |

---

## 2. 테스트 환경

### 2-1. 인프라

| 구성 요소 | 상세 |
|---|---|
| k8s 네임스페이스 | `skala3-finalproj-class2-team5` |
| 백엔드 Pod | `team5-postgres-0` 등 (정확한 Pod 스펙은 운영자 확인 필요) |
| DB | PostgreSQL (`safety` 데이터베이스, `service` 스키마) |
| 스토리지 | MinIO (`safety-files` 버킷) |
| 접근 방식 | `kubectl port-forward` → `http://localhost:8000` |

> **접근 방식 주의**: `kubectl port-forward`는 200u 이상 동시 연결에서 불안정한 신호를 보임 (RemoteDisconnected 다수). 실제 사용자 트래픽은 LoadBalancer/Ingress 경유이므로 본 측정치는 **port-forward overhead가 포함된 보수적 값**으로 해석 필요.

### 2-2. 시드 데이터 (DB 사전 적재)

| 항목 | 규모 |
|---|---|
| 계정 | admin 500명 + user 500명 = 1,000명 |
| 프로젝트 | 2,000개 (`LOAD-CN-0001` ~ `LOAD-CN-2000`) |
| 담당자 배정 (m:n) | 약 8,000행 (admin·user 각 평균 8개 프로젝트) |
| 사용내역서 | 12,000개 (프로젝트당 6개월치, 2026-01~06) |
| 세부항목 | 180,000개 (statement당 15개) |
| agent_logs | 36,000개 (statement당 classi/safety-doc/legal success) |

**상태 분포** (6월=draft 강제, 그 외):
- `draft` 60% / `upload_completed` 20% / `supplement_required` 10% / `review_completed` 10%

### 2-3. 시드 격리 및 sequence 동기화

- 모든 데이터는 `LOAD-*` prefix로 격리 (운영 데이터와 분리)
- 운영 데이터 존재로 BIGSERIAL sequence 충돌 발생 → 시드 시작 시점에 6개 테이블의 sequence를 max(id) 다음으로 자동 동기화
- 운영자 사전 확인: `users.max_id=1026`, `projects.max_id=999` 등 소량의 운영 데이터 존재 (스테이징 환경)

---

## 3. 테스트 시나리오 및 방법론

### 3-1. Locust 시나리오 (atomic 모드)

| 클래스 | weight | wait_time | 특성 |
|---|---|---|---|
| `AdminScenario` | 3 | 3~10s | 검토자 시점 — 목록·dashboard·archive·검토완료·보완요청 등 |
| `UserScenario` | 7 | 2~8s | 작성자 시점 — 항목 CRUD·파일 업로드·증빙 연결·제출 등 |

총 측정 endpoint **약 40종** (인증, 사용자, 프로젝트, 사용내역서, 세부항목, 파일, 증빙, 아카이브, agent 조회, 대시보드).

### 3-2. 측정에서 제외한 API

| 카테고리 | 제외 API | 이유 |
|---|---|---|
| FastAPI 호출 | `POST /agents/{parse,validate,legal,report}` | 외부 의존성 (AI 모델 응답시간 좌우) |
| FastAPI 호출 | `POST /items` (FastAPI classify 호출 포함) | 동일 |
| `legal_rag` 스키마 | `GET /law-changes/recent` | FastAPI 전용 스키마 (Spring 측정 범위 외) |

### 3-3. 정상 응답 분류 (`catch_response`)

부하 테스트 특성상 발생하는 레이스 컨디션 4xx는 **성공으로 분류**:

| API | 허용 코드 | 사유 |
|---|---|---|
| `PATCH .../submit`, `complete-review`, `request-supplement` | 200, 400, 409, 422 | 상태 전이 불가 |
| `POST .../items/:id/evidence-files` | 200, 201, 404, 409 | UNIQUE 위반, item 삭제됨 |
| `PATCH /evidence-file-links/:id` | 200, 404, 409 | 동상 |
| `DELETE /evidence-file-links/:id`, `/files/:id`, `/items/:id` | 200, 204, 404 | 다른 세션이 먼저 삭제 |
| `GET /items/:id/evidence-files`, `requirements` | 200, 404 | delete_item 부작용 |
| `GET /files/:id/preview` | 200, 404, 415 | 텍스트 파일 미리보기 미지원 |
| `GET /agents/legal`, `report` | 200, 404 | 로그 없음 (시드 안 한 statement) |

**5xx는 모두 실패로 분류** — locust 기본 동작.

### 3-4. 단계별 부하 패턴

| Phase | Users | Ramp Rate | Duration | 의미 |
|---|---|---|---|---|
| Smoke | 20 | 5/s | 1m | 환경 검증 |
| Baseline | 200 | 10/s | 5m | 정상 운영 기준선 |
| Peak | 500 | 20/s | 5m | 피크 부하 (정상 2.5배) |
| Stress | 1,000 | 25/s | 8m | 한계 부하 |

각 단계 사이 1~2분 휴식 (백엔드 안정화).

---

## 4. 단계별 결과

### 4-1. Smoke (20u × 1m)

**전반 지표**

| 지표 | 값 |
|---|---|
| 총 요청 | 135 |
| 실패 | 0 (0.00%) |
| RPS | 5.4 |
| p50 / p95 / p99 | 700 / 8,300 / 8,700 ms |

**관찰**

- 실패 0건, 기능 모두 정상 응답
- `[setup] login` median **7,500ms**, max 9,300ms ← **JIT cold start + bcrypt 직격**
- 본 endpoint median 21~520ms — 기능 정상

**판정**: 환경 검증 통과. Baseline 진행 가능.

---

### 4-2. Baseline (200u × 5m) — ⚠️ 시스템 한계 신호 시작

**전반 지표**

| 지표 | 값 | 평가 |
|---|---|---|
| 총 요청 | 5,641 | OK |
| **실패** | **621 (11.01%)** | **위험** (>3%) |
| RPS | 18.85 | 200u 기준 낮음 (실패로 처리량 감소) |
| p50 / p95 / p99 | 34 / **11,000** / **31,000** ms | **p99 위험** |

**실패 분류** (총 621건)

| 코드 | 합계 | 주요 endpoint |
|---|---|---|
| **5xx** | **108건 (Server Error)** | `[setup] login` 86, `[setup] categories` 12, `[setup] projects` 9 |
| **401** | **513건 (인증 실패)** | `GET /projects` 144, `GET /users/me` 113, `GET /projects?keyword=` 54, `GET /projects?scope=all` 52, `GET /dashboard` 48, `GET /assignee-candidates` 44, `GET /dashboard/ai-usage` 31, `GET /users` 27 |

**5xx 발생 시각**: 모두 측정 시작 후 **0~26초** (ramp-up 구간). 이후 5xx 발생 없음.

**`[setup] login` 응답시간**: median **30,000ms (30초)**, p95 35,000ms.

**본 endpoint 응답시간** (정상 처리된 요청 기준):

| Endpoint | p50 | p95 | p99 |
|---|---|---|---|
| `GET /usage-statements/:id` | 34 | 78 | 1,200 |
| `GET /files` | 27 | 71 | 800 |
| `GET /dashboard` | 23 | 83 | 490 |
| `POST /files` | 40 | 81 | 620 |
| `PATCH /items/:id` | 35 | 78 | 970 |
| `PATCH /usage-statements/:id/complete-review` | 23 | 58 | 510 |

→ **본 endpoint 자체는 정상 범위**. 큰 문제는 ramp-up과 인증 실패.

---

### 4-3. Peak (500u × 5m)

**전반 지표**

| 지표 | 값 | 평가 |
|---|---|---|
| 총 요청 | 10,096 | — |
| **실패** | **1,716 (17.0%)** | 위험 |
| RPS | 33.74 | — |
| p50 / p95 / p99 | 46 / **32,000** / **66,000** ms | p99 매우 심각 |

**실패 분류**

| 코드 | 합계 |
|---|---|
| **5xx** | **320건** (login 262, categories 33, projects 21, statements 1, dashboard 3) |
| **401** | **1,393건** (전 endpoint에 분포) |
| **연결 중단** | 3건 (RemoteDisconnected) ← port-forward 한계 신호 |

**`[setup] login`**: median **46,000ms (46초)**, p95 73,000ms.

**본 endpoint** (정상 응답 기준):

| Endpoint | p50 | p95 | p99 |
|---|---|---|---|
| `GET /usage-statements/:id` | 44 | 130 | 1,300 |
| `GET /agents/button-states` | 49 | 140 | 2,800 |
| `POST /files` | 47 | 100 | 1,100 |
| `POST /auth/refresh` | 38 | 4,900 | 59,000 ← 위험 |

→ **`/auth/refresh`가 폭증** — 401 받은 사용자가 refresh 시도 → 동시 토큰 갱신 부하.

---

### 4-4. Stress (1000u × 8m)

**전반 지표**

| 지표 | 값 | 평가 |
|---|---|---|
| 총 요청 | 27,336 | — |
| **실패** | **4,997 (18.3%)** | 위험 |
| RPS | 56.89 | — |
| p50 / p95 / p99 | 48 / **43,000** / **112,000** ms | **사실상 사용 불가** |
| **p99.9** | **131,000 ms (131초)** | — |

**실패 분류**

| 코드 | 합계 |
|---|---|
| **5xx** | **678건** (login 572, categories 41, projects 22, statements 1, dashboard 1) |
| **401** | **4,358건** (전 endpoint 분포) |

**`[setup] login`**: median **76,000ms (76초)**, p95 127,000ms, max 138,000ms (138초).

**본 endpoint** (Stress에서도 안정적인 것들 — 정상 응답 기준):

| Endpoint | p50 | p95 | 평가 |
|---|---|---|---|
| `GET /agents/todos` | 40 | 97 | 안정 |
| `GET /agents/button-states` | 52 | 130 | 양호 |
| `GET /files` | 41 | 110 | 양호 |
| `POST /files` | 53 | 120 | 양호 |
| `PATCH /items/:id` | 50 | 110 | 양호 |

→ **PK 조회·단순 CRUD는 Stress에서도 견고함**. 시스템 자체 처리 능력은 충분.

**Stress에서 무너진 것들**:

| Endpoint | p50 | p99 | 원인 추정 |
|---|---|---|---|
| `GET /projects` | 49 | **90,000** | 페이징·정렬·필터 무거움 |
| `GET /projects?keyword=` | 49 | **93,000** | LIKE `%키워드%` 풀스캔 |
| `GET /projects?scope=all` | 48 | **50,000** | 전체 조회 |
| `GET /users/me` | 49 | **93,000** | 401 응답 포함 (인증 실패 누적) |
| `POST /auth/refresh` | 35 | **121,000** | 토큰 갱신 폭증 |
| `GET /dashboard` | 35 | **91,000** | 집계 쿼리 |

---

### 4-5. 단계별 비교 요약

| 지표 | Smoke | Baseline | Peak | Stress |
|---|---|---|---|---|
| Users | 20 | 200 | 500 | 1,000 |
| 실패율 | 0.00% | 11.01% | 17.0% | 18.3% |
| RPS | 5.4 | 18.85 | 33.74 | 56.89 |
| p50 (전체) | 700 | 34 | 46 | 48 |
| p99 (전체) | 8,700 | 31,000 | 66,000 | 112,000 |
| `[setup] login` median | 7,500 | 30,000 | 46,000 | 76,000 |
| 5xx 총건 | 0 | 108 | 320 | 678 |
| 401 총건 | 0 | 513 | 1,393 | 4,358 |

**핵심 관찰**:
1. **부하 ↑ → 실패율 ↑** (11% → 17% → 18%), 다만 Peak·Stress 사이 증가 둔화 (포화 추정)
2. **부하 ↑ → `[setup] login` 응답시간 선형 증가** (30s → 46s → 76s)
3. **본 endpoint p50은 거의 변하지 않음** (34 → 46 → 48) — 시스템 자체 처리 능력은 안정적
4. **본 endpoint p99만 폭증** — 풀 대기·인증 실패 응답이 꼬리에 쌓임

---

## 5. 데이터 정합성 검증 (verify.sql)

모든 단계에서 **데이터 정합성 위반 0건**. Spring `@Transactional` 처리가 한계 상황에서도 안정적.

### 5-1. 정합성 지표 (단계별)

| 검증 | Baseline | Peak | Stress |
|---|---|---|---|
| 고아 `evidence_file_link` | 0/86 | 0/275 | 0/738 |
| 고아 `summary` | 0 | 0 | 0 |
| `agent_logs` UNIQUE 위반 | 0 | 0 | 0 |
| 잘못된 `page_no` | 0 | 0 | 0 |

### 5-2. 상태 전이 발생 검증

| 상태 | Baseline | Peak | Stress |
|---|---|---|---|
| draft | 8,019 (66.8%) | 8,057 (67.1%) | 8,104 (67.5%) |
| upload_completed | 1,988 (16.6%) | 1,966 (16.4%) | 1,939 (16.2%) |
| review_completed | 999 (8.3%) | 997 (8.3%) | 993 (8.3%) |
| supplement_required | 994 (8.3%) | 980 (8.2%) | 964 (8.0%) |

→ 모든 상태에 데이터 존재 = 상태 전이 API(`submit`, `complete-review`, `request-supplement`)가 정상 작동.

### 5-3. 데이터 변경량 (시드 → 측정 후)

| 항목 | 시드 후 | Baseline | Peak | Stress |
|---|---|---|---|---|
| files (생성) | 0 | 177 | 494 | 1,260 |
| distinct uploader | 0 | 65 | 173 | 314 |
| evidence_links | 0 | 86 | 275 | 738 |
| items (delete 누적) | 180,000 | 179,916 | 179,816 | 179,503 |

→ `delete_item`이 누적 동작, 부하 늘수록 더 많은 user가 정상 동작 (`distinct_uploaders`).

---

## 6. 병목 진단 및 의심 가설

### 6-1. P1 — 인증 계층 병목 (가장 강력한 신호)

**증거**:
- 모든 5xx의 80% 이상이 `[setup] login`
- 모든 5xx가 ramp-up 구간(0~30초)에 집중
- 부하 증가 시 login 응답시간 선형 증가 (30s → 46s → 76s)
- 본 endpoint들은 stress에서도 median 35~60ms로 안정

**가설**: bcrypt 비용 + 동시 ramp-up + 풀 고갈 복합 작용

**추정 원인**:
1. **bcrypt strength**: 보통 12 사용 시 코어당 30~80ms. 200u가 4초 동안 일괄 로그인 → 코어당 약 50 req/s 필요. pod CPU가 1~2 vCPU면 처리 가능 30~60 RPS → 큐 폭증.
2. **HikariCP `maximum-pool-size`**: Spring 디폴트 10. 200 동시 user × 1 query/login = 190 user 대기. p99 폭증 직접 원인.
3. **JIT warmup**: smoke에서 본 7.5초 median은 JIT 미컴파일. baseline 30s는 부하까지 겹친 결과.

**연쇄 효과**: login 5xx → cookie 못 받음 → 후속 요청 401 → user가 `/auth/refresh` 시도 → refresh도 동시 폭증 (peak에서 p99 59초, stress에서 121초).

**검증 방법**:
```bash
# bcrypt strength 확인
grep -r "bcrypt\|BCrypt\|strength" backend/src/main/java | head -5

# HikariCP 설정 확인
grep -A3 "hikari\|maximum-pool" backend/src/main/resources/application.yaml

# k8s pod 리소스 limits 확인
kubectl get pod -n skala3-finalproj-class2-team5 -o yaml | grep -A3 "resources:"

# 측정 중 DB connection 사용량 확인
kubectl exec -n skala3-finalproj-class2-team5 team5-postgres-0 -- \
  psql -U safety_user -d safety -c \
  "SELECT state, count(*) FROM pg_stat_activity WHERE datname='safety' GROUP BY state;"
```

---

### 6-2. P2 — 데이터 조회 무거움

**증거** (정상 응답 기준):

| Endpoint | Stress p99 | 의심 |
|---|---|---|
| `GET /projects?keyword=` | 93,000 ms | LIKE `%키워드%` 풀스캔 |
| `GET /projects` | 90,000 ms | 페이징·정렬·필터 누적 |
| `GET /usage-statements` (목록) | 7,000 ms | N+1 의심 |
| `GET /dashboard` | 91,000 ms | 집계 (`reviewNeededProjects`) |
| `GET /archive/categories` | 12,000 ms | 집계 쿼리 |

**`GET /usage-statements` N+1 의심**:
- `UsageStatementService.list()`가 statement별로 `summaryRepository.countByUsageStatementId()` + `itemRepository.countByUsageStatementId()` 호출
- 프로젝트당 6 statement × 2 query = 12회 추가 쿼리
- `linkedCounts`·`unsatisfiedCounts`만 batch 처리됨

**`GET /projects?keyword=` LIKE 풀스캔**:
- 2,000 프로젝트에서 LIKE `%부하%` 검색
- 컬럼 인덱스 있어도 `%키워드%` 패턴은 활용 안 됨
- 대안: trigram 인덱스 (`pg_trgm`) 또는 전문 검색

**`GET /dashboard` 집계 무거움**:
- 과거 이력 포함 `upload_completed` 사용내역서 카운트
- 12,000 statement 풀스캔 추정

---

### 6-3. P3 — 인프라 계층 의심 (검증 필요)

**증거**:
- Peak·Stress에서 `RemoteDisconnected` 발생 (port-forward 연결 끊김)
- 동시 1000 connections가 kubectl port-forward의 처리 한계 의심

**가설**:
1. **port-forward 한계** — 실제 운영 트래픽은 LoadBalancer 경유이므로 이 신호는 측정 환경 특성. 운영 환경에서 동일 부하 시 다를 수 있음.
2. **pod CPU throttling** — k8s `cpu limit` 작으면 throttle 발생. 측정 중 `kubectl top pod` 확인 필요.
3. **JVM 힙/GC** — pod 메모리 작으면 Major GC 빈발. heap dump 확인 필요.

**검증 방법**:
```bash
# 측정 중 pod 리소스 사용량
watch -n 5 kubectl top pod -n skala3-finalproj-class2-team5

# JVM 힙 설정 (백엔드 설정 또는 Dockerfile)
grep -r "Xmx\|Xms\|MaxHeap" backend/

# 운영용 LoadBalancer/Ingress 경로로 재측정 검토
```

---

### 6-4. 의심에서 제외된 가설 (증거 부족)

| 가설 | 제외 이유 |
|---|---|
| DB 자체 부하 한계 | 본 endpoint median 안정적 + 정합성 위반 0건 |
| MinIO 한계 | `POST /files` p99 1,100~2,700ms로 안정. 누적 1,260 업로드 성공 |
| 코드 자체 버그 | 모든 정합성 검증 통과. catch_response가 정상 시나리오만 흡수 |

---

## 7. 개선 우선순위

### P1 — 인증·풀 계층 (Baseline 안정화)

| # | 항목 | 작업 | 예상 효과 |
|---|---|---|---|
| 1 | HikariCP 풀 크기 | `application.yaml`에 `maximum-pool-size: 50` (또는 그 이상) 명시 | 동시성 한계 즉각 완화 |
| 2 | bcrypt strength 검토 | 현재 strength 확인 후, 너무 높으면 10으로 하향 (보안·성능 트레이드오프) | login 처리 RPS 2배+ |
| 3 | pod CPU/메모리 limits 상향 | k8s manifest 점검. CPU 2 vCPU 이상 권장 | 처리 capacity 확장 |
| 4 | JVM 힙 명시 | `-Xms`/`-Xmx` 컨테이너 메모리의 75% 수준 명시 | GC 안정화 |

> **빠른 검증 경로**: 위 1~4 조치 후 Baseline(200u/5m) 재측정 → 실패율 1% 미만이 목표.

### P2 — 데이터 조회 최적화

| # | 항목 | 작업 |
|---|---|---|
| 5 | `GET /usage-statements` (list) N+1 정리 | `summaryRepository.countByUsageStatementIdIn(...)` + `itemRepository.countByUsageStatementIdIn(...)` 배치 쿼리화 |
| 6 | `GET /dashboard` 집계 최적화 | `reviewNeededProjects` 사전 집계 또는 인덱스 추가 |
| 7 | `GET /archive/categories` 집계 검토 | `usage_statement_summaries` 없을 때 fallback 경로 점검 |

### P3 — 검색·조회 패턴

| # | 항목 | 작업 |
|---|---|---|
| 8 | `GET /projects?keyword=` LIKE 인덱스 | `pg_trgm` 확장 + `gin_trgm_ops` 인덱스 추가 |
| 9 | `/auth/refresh` 동시성 | RefreshToken UPDATE 동시 폭주 방지 (낙관적 락 또는 token rotation 큐화) |

### 측정 환경 개선 (별도)

| # | 항목 | 작업 |
|---|---|---|
| 10 | 측정 경로 | `port-forward` 대신 LoadBalancer/Ingress 외부 IP 사용. 동시 연결 안정성 확보 |
| 11 | Locust worker | k8s 내부에서 distributed 모드로 실행 (네트워크 latency 제거) |

---

## 8. 보고서 사용 시 유의사항

### 측정 환경의 한계

1. **port-forward overhead 포함** — 본 측정치는 실제 사용자 경험보다 보수적. LoadBalancer 경유 시 더 빠를 수 있음.
2. **JVM cold start 영향** — 측정 시작 직후 응답이 일관되게 무거움. 운영 환경의 ramp-up과 다를 수 있음.
3. **시드 데이터 분포 한정** — 실제 운영 데이터(작성 패턴, 카테고리 분포)와 차이 가능. 실 데이터 기반 재측정 권장.

### 측정의 신뢰 구간

- **본 endpoint 자체의 응답시간**은 신뢰도 높음 — 모든 단계에서 일관됨
- **`[setup] login`의 절대값**은 ramp-up·port-forward 영향이 커서 운영 환경과 다를 수 있음
- **정합성 검증 결과는 신뢰도 매우 높음** — Spring 트랜잭션 처리가 한계 상황에서도 정상 동작 확인

---

## 9. 부록

### 9-1. 측정 결과 파일

`backend/load-test/results/` 에 저장됨.

| 파일 | 내용 |
|---|---|
| `k8s_smoke_*.csv` | Smoke (20u/1m) |
| `k8s_baseline_atomic_200_*.csv` + `_verify.txt` | Baseline (200u/5m) |
| `k8s_peak_atomic_500_*.csv` + `_verify.txt` | Peak (500u/5m) |
| `k8s_stress_atomic_1000_*.csv` + `_verify.txt` | Stress (1000u/8m) |

### 9-2. 측정 시나리오 코드

`backend/load-test/locustfile.py` 에 정의된 `AdminScenario`, `UserScenario` 클래스.

### 9-3. 정합성 검증 쿼리

`backend/load-test/verify.sql` — 7개 검증 항목.

### 9-4. 시드 SQL

`backend/load-test/seed.sql` — sequence 동기화 + 6개 테이블 시드.

### 9-5. 후속 측정 시 권장 시점

1. **P1 개선 후 Baseline 재측정** → 실패율 1% 미만 확인
2. Baseline 안정 시 Peak/Stress 재측정 → 한계점 재확인
3. **Spike(LoadTestShape)·Soak(1h) 측정** → 갑작스러운 부하 회복·장시간 안정성
4. **Journey 모드 측정** (`LOCUST_MODE=journey`) → 실 워크로드 패턴 확인

---

*작성: 부하 테스트 자동화 도구를 통한 단계별 측정 결과 종합*
*문의: 측정 환경·시나리오 상세는 `backend/load-test/README.md`, `PLAN.md`, `K8S_RUNBOOK.md` 참조*
