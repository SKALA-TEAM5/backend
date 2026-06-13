# 부하 테스트 계획서

**목적**: 배포 서버 스펙 기준 성능 측정 → 병목 식별 → 개선 전후 비교  
**단계**: 이 문서는 1~3단계(측정·기록·병목 진단)를 다룬다. 개선 작업(4~5단계)은 분리된 문서로 관리.

---

## 1. 전체 로드맵

```
Phase 0: 환경 확인 및 준비
Phase 1: 기준선(Baseline) 측정 — 200 users
Phase 2: 피크 부하 측정 — 500 users
Phase 3: 스트레스 측정 — 1,000 users
Phase 4: 병목 진단 리포트 작성
(Phase 5+: 개선 작업 및 재측정 — 별도 문서)
```

---

## 2. Phase 0 — 환경 확인 및 준비

### 2-1. 서버 스펙 기록

테스트 전 아래 항목을 측정하여 결과 분석 파일에 명시한다.

```bash
# CPU
lscpu | grep -E "Model name|CPU\(s\)|Thread"

# 메모리
free -h

# OS
uname -a

# JVM 설정 (Spring Boot 기동 옵션)
ps aux | grep java | grep -v grep

# PostgreSQL 연결 수 한계
psql -c "SHOW max_connections;"
psql -c "SELECT count(*) FROM pg_stat_activity;"
```

기록 파일: `results/env_snapshot.md`

### 2-2. 테스트 데이터 시딩

```bash
# 최초 1회만
make locust-seed
```

시드 내용 (seed.sql 기준):

| 항목 | 수량 |
|---|---|
| admin 계정 (`LOAD-ADMIN-001` ~ `030`) | 30명 |
| user 계정 (`LOAD-USER-001` ~ `030`) | 30명 |
| 프로젝트 (`LOAD-CN-001` ~ `030`) | 30개 |
| 사용내역서 (프로젝트당 1개, draft) | 30개 |
| 세부항목 (사용내역서당 5개) | 150개 |

### 2-3. 서버 기동 확인

```bash
# 백엔드 응답 확인
curl -s -o /dev/null -w "%{http_code}" http://<서버주소>:8000/categories

# DB 연결 상태 확인
psql -c "SELECT state, count(*) FROM pg_stat_activity GROUP BY state;"
```

### 2-4. 기준 지표 스냅샷 (테스트 전)

```bash
# HikariCP 설정 확인 (application.yaml 또는 환경변수)
grep -r "maximum-pool-size\|connectionTimeout\|hikari" backend/src/

# JVM 힙 설정
ps aux | grep java | grep -oE "\-Xmx[^ ]+"
```

---

## 3. 시나리오 구성

`locustfile.py` 에 이미 정의된 두 시나리오를 그대로 사용한다.

| 시나리오 | 비중 | 주요 API |
|---|---|---|
| `AdminScenario` | 30% | `GET /projects`, `GET /projects?keyword=`, `GET /dashboard`, `GET /dashboard/ai-usage`, `GET /usage-statements/:id`, `PATCH /projects/:id`, `GET /archive/categories` |
| `UserScenario` | 70% | `GET /usage-statements/:id`, `PATCH /items/:id`, `POST /files`, `DELETE /files/:id`, `POST /items/:id/evidence-files`, `PATCH /evidence-file-links/:id`, agent 읽기 3종 |

**AI 호출 API 제외**: `/agents/parse`, `/agents/validate`, `/agents/legal`, `/agents/report` — 외부 FastAPI 의존성으로 별도 측정.

---

## 4. Phase 1~3 — 측정 실행

### 실행 명령

```bash
# Phase 1 — Baseline (200 users)
make locust-save \
  LOCUST_RESULT=baseline_200 \
  LOCUST_USERS=200 \
  LOCUST_RATE=10 \
  LOCUST_TIME=5m

# Phase 2 — Peak (500 users)
make locust-save \
  LOCUST_RESULT=peak_500 \
  LOCUST_USERS=500 \
  LOCUST_RATE=20 \
  LOCUST_TIME=5m

# Phase 3 — Stress (1,000 users)
make locust-save \
  LOCUST_RESULT=stress_1000 \
  LOCUST_USERS=1000 \
  LOCUST_RATE=25 \
  LOCUST_TIME=8m
```

> `make locust-save` 없을 시 직접 실행:
> ```bash
> locust -f load-test/locustfile.py \
>   --host=http://<서버주소>:8000 \
>   --csv=load-test/results/<RESULT_NAME> \
>   --headless -u <USERS> -r <RATE> -t <TIME>
> ```

### 각 Phase 실행 전후 체크리스트

| 시점 | 작업 |
|---|---|
| 실행 전 | DB 연결 수 스냅샷, JVM 힙 사용량, OS load average |
| 실행 중 | `pg_stat_activity` 모니터링, HikariCP 지표 (JMX 또는 `/actuator/metrics`) |
| 실행 후 | CSV 저장 확인, 서버 에러 로그 수집, DB slow query log 수집 |

### 실행 중 DB 모니터링 (별도 터미널)

```bash
# 5초마다 커넥션 상태 스냅샷
watch -n 5 "psql -c \"SELECT state, wait_event_type, count(*) FROM pg_stat_activity WHERE datname='safety' GROUP BY state, wait_event_type;\""

# slow query (500ms 이상)
psql -c "SELECT query, calls, mean_exec_time FROM pg_stat_statements WHERE mean_exec_time > 500 ORDER BY mean_exec_time DESC LIMIT 10;"
```

### 결과 파일 구조

```
load-test/results/
├── env_snapshot.md              # 서버 스펙 기록
├── baseline_200_stats.csv
├── baseline_200_stats_history.csv
├── baseline_200_failures.csv
├── baseline_200_exceptions.csv
├── peak_500_stats.csv
├── peak_500_stats_history.csv
├── peak_500_failures.csv
├── peak_500_exceptions.csv
├── stress_1000_stats.csv
├── stress_1000_stats_history.csv
├── stress_1000_failures.csv
├── stress_1000_exceptions.csv
└── analysis_<날짜>.md           # 병목 진단 리포트
```

CSV는 `.gitignore` 처리. `.md` 파일만 커밋.

---

## 5. Phase 4 — 병목 진단

### 5-1. 지표 해석 기준

| 지표 | 정상 | 주의 | 위험 |
|---|---|---|---|
| 에러율 | < 1% | 1~3% | > 3% |
| p95 | < 200ms | 200~500ms | > 500ms |
| p99 | < 500ms | 500ms~2s | > 2s |
| p99 / p50 배율 | < 10× | 10~50× | > 50× |

> **p50 정상 + p99 폭발** 패턴 → DB 커넥션 대기 또는 락 경합 의심

### 5-2. 병목 유형별 진단 방법

#### Type A — 풀스캔 / 인덱스 누락

증상: 부하와 무관하게 p50부터 높음.

```sql
-- 실행 계획 확인
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM service.projects
WHERE project_name ILIKE '%키워드%';

-- 인덱스 사용 여부
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
WHERE schemaname = 'service'
ORDER BY idx_scan;
```

#### Type B — 커넥션 풀 고갈

증상: 낮은 부하에서 p50 정상 → 고부하에서 p99만 수 초로 폭발.

```bash
# HikariCP 상태 (Spring Actuator)
curl http://localhost:8000/actuator/metrics/hikaricp.connections.active
curl http://localhost:8000/actuator/metrics/hikaricp.connections.pending

# PostgreSQL 커넥션 수 확인
psql -c "SELECT count(*) FROM pg_stat_activity WHERE datname='safety';"

# application.yaml 현재 설정
grep -A5 "hikari" backend/src/main/resources/application.yaml
```

#### Type C — 무거운 집계 쿼리

증상: 특정 엔드포인트만 꾸준히 p50이 높음. RPS 증가 시 비례해서 악화.

```sql
-- 가장 느린 쿼리 Top 10
SELECT LEFT(query, 80) AS query, calls, mean_exec_time, total_exec_time
FROM pg_stat_statements
ORDER BY mean_exec_time DESC
LIMIT 10;
```

#### Type D — N+1 쿼리

증상: 응답 크기가 클수록 응답시간 비례 증가. JDBC 로그 확인.

```yaml
# application.yaml (로깅 임시 활성화)
logging:
  level:
    org.hibernate.SQL: DEBUG
    org.hibernate.orm.jdbc.bind: TRACE
```

#### Type E — MinIO / 파일 I/O

증상: `POST /files` 계열만 느림. DB 지표는 정상.

```bash
# MinIO 응답시간 직접 측정
time curl -X PUT http://localhost:9000/<bucket>/test-file \
  --upload-file /tmp/test.txt
```

### 5-3. 주요 관찰 API (선행 분석에서 식별된 후보)

로컬 테스트(stress_1000_v5, load_test_analysis_v1) 기준으로 이미 식별된 병목 후보. 배포 서버에서 동일 패턴 확인 필요.

| 우선순위 | 엔드포인트 | 의심 원인 | 확인 방법 |
|---|---|---|---|
| P1 | `GET /projects?keyword=` | `LIKE '%...%'` 풀스캔 | `EXPLAIN ANALYZE`, `pg_stat_user_indexes` |
| P1 | `GET /usage-statements/:id` | 요청 수 최다, 응답 크기 큼, N+1 가능성 | JDBC 쿼리 카운트, `EXPLAIN ANALYZE` |
| P2 | `GET /dashboard/ai-usage` | 집계 풀스캔 (`agent_usage_records`) | `pg_stat_statements` |
| P2 | `[setup] login` | bcrypt 비용 + 1,000명 동시 ramp-up | 비중 낮추거나 별도 시나리오 분리 |
| P3 | p99 전체 폭발 (1000u 구간) | HikariCP `max-pool-size` 기본값(10) | HikariCP Actuator, `pg_stat_activity` |

### 5-4. 리포트 작성 양식

분석 완료 후 `results/analysis_<날짜>.md` 에 아래 구조로 작성.

```
## 1. 테스트 환경
## 2. 전체 지표 비교 (baseline / peak / stress)
## 3. 에러 분석
## 4. 엔드포인트별 응답시간 (읽기 / 쓰기 분리)
## 5. 병목 진단 (유형 + 근거 + 증거 쿼리 결과)
## 6. 개선 우선순위 (P1/P2/P3 테이블)
```

---

## 6. 정상 실패 처리 기준

부하 테스트 특성상 발생하는 레이스 컨디션 에러는 성능 에러로 집계하지 않는다.

| API | 허용 코드 | 원인 |
|---|---|---|
| `PATCH /usage-statements/:id/submit` | 400, 409 | 이미 제출된 상태 |
| `PATCH /usage-statements/:id/complete-review` | 400, 409 | 상태 전이 불가 |
| `PATCH /usage-statements/:id/request-supplement` | 400, 409 | 상태 전이 불가 |
| `POST /items/:id/evidence-files` | 409 | 동시 중복 연결 (UNIQUE 제약) |
| `PATCH /evidence-file-links/:id` | 404, 409 | 타 유저가 먼저 삭제 또는 이동 충돌 |
| `DELETE /evidence-file-links/:id` | 404 | 타 유저가 먼저 삭제 |

---

## 7. 제약 및 주의사항

- **AI 호출 API 제외**: FastAPI 호출 API는 외부 모델 응답시간에 좌우되므로 이 테스트에서 제외. 별도 `AgentScenario`로 분리 측정 권장.
- **Locust와 서버 동일 머신 금지**: 로컬 환경에서는 CPU 경합으로 측정값이 왜곡됨. 배포 서버(또는 별도 부하 생성 머신)에서 실행.
- **시드 계정 수(30명) vs 동시 사용자(1000명)**: 계정 풀이 작아 동일 계정으로 다중 세션이 발생. bcrypt 캐시 히트율이 올라가 로그인 성능이 실제보다 좋게 나올 수 있음.
- **pg_stat_statements 활성화 필요**: `CREATE EXTENSION IF NOT EXISTS pg_stat_statements;` 후 `postgresql.conf`에 `shared_preload_libraries = 'pg_stat_statements'` 설정.
