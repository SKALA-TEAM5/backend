# 부하 테스트 분석 v1

**테스트 일시**: 2026-06-11  
**환경**: 로컬 (Spring Boot + Locust 동일 머신)  
**도구**: Locust 2.34.0  
**대상**: `http://localhost:8000`

## 1. 테스트 시나리오

| 구분 | 유저 수 | Ramp-up | 실행 시간 | 결과 파일 |
|---|---|---|---|---|
| 보통 부하 | 200 | 10/s | 3m | `load_200_*.csv` |
| 피크 부하 | 500 | 20/s | 5m | `load_500_*.csv` |
| 스트레스 | 1,000 | 30/s | 8m | `stress_1000_*.csv` |

**시나리오 구성**
- `AdminScenario` (30%) — 프로젝트·사용내역서 조회/수정, 대시보드, 아카이브, 담당자 조회
- `UserScenario` (70%) — 항목 CRUD, 파일 업로드/삭제, 증빙 연결/이동/삭제, 사용내역서 제출, Agent 읽기

## 2. 전체 지표 비교

- p50~p90은 3단계 전반에 걸쳐 안정적이나, p99가 1000 유저 구간에서 급격히 폭발

| 지표 | load_200 | load_500 | stress_1000 |
|---|---|---|---|
| 총 요청 수 | 11,244 | 46,496 | 143,511 |
| 실패 수 | 57 | 395 | 1,278 |
| **에러율** | **0.51%** | **0.85%** | **0.89%** |
| p50 (중앙값) | 5ms | 4ms | 4ms |
| p90 | 42ms | 39ms | 42ms |
| p95 | 70ms | 76ms | 89ms |
| **p99** | **320ms** | **410ms** | **4,200ms** |
| p99.9 | 400ms | 730ms | 6,700ms |
| 최대 응답시간 | 430ms | 1,022ms | 18,413ms |
| **처리량** | **63 req/s** | **155 req/s** | **299 req/s** |


## 3. 에러 분석

- 모든 실패는 서버 버그가 아닌 부하 테스트 특성상의 레이스 컨디션

| 에러 코드 | 엔드포인트 | 원인 | load_200 | load_500 | stress_1000 |
|---|---|---|---|---|---|
| 409 | POST /items/:id/evidence-files | 동시에 같은 파일-항목 쌍 연결 시도 (UNIQUE 제약) | 26 | 175 | 540 |
| 409 | PATCH /evidence-file-links/:id | 동시에 같은 링크 이동 시도 | 10 | 41 | 239 |
| 404 | PATCH /evidence-file-links/:id | 타 유저가 먼저 삭제한 linkId 재사용 | 11 | 103 | 313 |
| 404 | DELETE /evidence-file-links/:id | 타 유저가 먼저 삭제한 linkId 재사용 | 10 | 76 | 186 |

## 4. 엔드포인트별 응답시간 (ms)

### 읽기 (GET)

| 엔드포인트 | 200u p50 | 200u p95 | 500u p50 | 500u p95 | 1000u p50 | 1000u p95 | 1000u p99 |
|---|---|---|---|---|---|---|---|
| GET /users/me | 3 | 5 | 2 | 5 | 2 | 7 | 1,800 |
| GET /agents/logs | 3 | 7 | 2 | 5 | 3 | 8 | 1,200 |
| GET /agents/warnings | 4 | 7 | 3 | 6 | 3 | 8 | 1,800 |
| GET /agents/todos | 3 | 7 | 3 | 6 | 3 | 8 | 1,100 |
| GET /agents/button-states | 4 | 9 | 3 | 8 | 3 | 11 | 3,900 |
| GET /usage-statements/:id | 4 | 9 | 3 | 8 | 4 | 11 | 1,900 |
| GET /dashboard | 4 | 8 | 3 | 7 | 3 | 12 | 3,600 |
| GET /files | 7 | 16 | 6 | 11 | 6 | 15 | 2,300 |
| GET /archive/categories | 9 | 13 | — | — | — | — | — |
| **GET /dashboard/ai-usage** | **28** | **49** | **23** | **35** | **24** | **77** | **3,700** |
| **GET /projects** | **42** | **65** | **38** | **58** | **41** | **110** | **4,000** |
| **GET /projects?keyword=** | **70** | **97** | **69** | **92** | **76** | **150** | **4,300** |

### 쓰기 (POST / PATCH / DELETE)

| 엔드포인트 | 200u p50 | 200u p95 | 500u p50 | 500u p95 | 1000u p50 | 1000u p95 | 1000u p99 |
|---|---|---|---|---|---|---|---|
| PATCH /usage-statements/:id/submit | 3 | 7 | 3 | 6 | 3 | 8 | 2,200 |
| PATCH /items/:id/category | 5 | 10 | 4 | 12 | 4 | 10 | 540 |
| DELETE /files/:id | 6 | 13 | 4 | 8 | 4 | 11 | 45 |
| PATCH /items/:id | 5 | 11 | 4 | 8 | 4 | 12 | 1,100 |
| POST /files | 8 | 15 | 5 | 13 | 6 | 16 | 1,400 |

## 5. 병목 진단

### 병목 1 — `GET /projects?keyword=` : 전 구간 가장 느린 일반 API

```
load_200:    p50=70ms  p95=97ms
load_500:    p50=69ms  p95=92ms
stress_1000: p50=76ms  p95=150ms  p99=4,300ms
```

- 200 유저 구간부터 이미 p50 70ms. 키워드 LIKE 검색(`%keyword%`)에 인덱스가 없어 풀스캔이 발생하는 것으로 추정
- 부하가 높아질수록 p99가 급격히 악화됨.

### 병목 2 — `GET /dashboard/ai-usage` : 집계 쿼리 응답 무거움

```
load_200:    p50=28ms  p95=49ms
load_500:    p50=23ms  p95=35ms
stress_1000: p50=24ms  p95=77ms  p99=3,700ms
```

- `agent_usage_records` 테이블 전체를 `GROUP BY agent_type_code`, `user_id`, `project_id`로 집계하는 구조
- 조회마다 풀스캔이 일어나며, 결과가 자주 바뀌지 않아 캐싱 여지가 큼

### 병목 3 — p99 전체 폭발 (1,000 유저) : DB 커넥션 풀 고갈

```
GET /agents/button-states: p50=3ms  →  p99=3,900ms
GET /projects:             p50=41ms →  p99=4,000ms
GET /usage-statements/:id: p50=4ms  →  p99=1,900ms
```

- p50은 정상이고 p99만 1,000+ ms로 튀는 패턴은 커넥션 대기 큐가 쌓이는 증상으로 의심
- HikariCP 기본 `maximum-pool-size=10`으로 300 req/s를 처리하면서 커넥션 경합 발생

## 6. 개선 계획

| 우선순위 | 개선 항목 | 방법 | 예상 효과 |
|---|---|---|---|
| **P1** | HikariCP 커넥션 풀 확장 | `maximum-pool-size: 10 → 20` | p99 전반 개선 |
| **P2** | `/dashboard/ai-usage` 캐싱 | `@Cacheable` TTL 5분 | p50 28ms → 목표 5ms 이하 |
| **P3** | 프로젝트 키워드 검색 인덱스 | Flyway 마이그레이션 (`GIN` 또는 `LIKE` 인덱스) | p50 70ms → 목표 20ms 이하 |

## 7. 다음 단계

1. 위 개선 사항 적용
2. 동일 조건(load_200 / load_500 / stress_1000)으로 재측정
3. before/after p50·p95·p99 비교표 작성
4. 개발 서버에서 최종 성능 수치 측정 (포폴 제출용)
