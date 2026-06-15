# 부하 테스트로 Spring Boot 백엔드 병목 진단·개선

> 건설 현장 산업안전보건관리비 사용내역서 관리 백엔드의 부하 테스트 설계·진단·개선 사이클을 정리한 글이다. 측정 한 번 돌리고 끝낸 게 아니라, **Before → Round 1 → Round 2 → Round 3** 네 번의 측정을 거치며 가설을 하나씩 검증·기각·재시도한 과정을 담았다.
>
> 사용 기술: Spring Boot 3.5 · PostgreSQL · Flyway · HikariCP · Locust · k8s

---

## 한눈에 보기

| 지표 (Stress 1,000u / 8m) | Before | After (Round 3) | 변화 |
|---|---|---|---|
| 실패율 | **18.3%** | **0.00%** | 완전 해소 |
| 5xx 건수 | 678 | **0** | 완전 해소 |
| 401 건수 | 4,358 | **0** | 완전 해소 |
| 성공 RPS (Stress) | 46.5 | 26.5 | login 인프라 한계로 throughput cap (§8) |
| `POST /auth/refresh` p99 | 121,000 ms | **15,000 ms** | **-88%** |
| `GET /projects?keyword=` p99 | 93,000 ms | **19,000 ms** | **-80%** |
| `GET /projects` p99 | 90,000 ms | **18,000 ms** | **-80%** |
| `GET /dashboard` p99 | 91,000 ms | **22,000 ms** | **-76%** |
| 데이터 정합성 위반 | 0 | **0** | 한계 부하에서도 유지 |

**정상 운영 기준선(Baseline 200u)에서의 처리량**: 16.8 → **28.9 성공 RPS (+72%)**. Stress 1000u의 raw RPS가 외형상 줄어든 것은 Before가 18% 요청을 즉시 5xx/401로 떨어뜨려 RPS를 부풀린 결과(=§7 "허위 빠름")로, 같은 부하에서 모든 요청을 살려내면서 throughput은 login bcrypt가 인프라 CPU에 묶인 천장으로 수렴한다.

가장 임팩트 컸던 변경은 다음 4가지였다.

1. `AuthService.login()`의 `@Transactional` 제거 — bcrypt CPU 작업 중 DB 커넥션을 잡고 있던 패턴 해소
2. `LegalDataSourceConfig`의 숨은 버그 수정 — `application.yaml`의 HikariCP 설정이 실제로는 binding되지 않고 있던 문제
3. `pg_trgm` GIN 인덱스 — LIKE `%키워드%` 풀스캔 해소
4. **Flyway V15 description 충돌 발견** — Round 2의 마이그레이션이 실제로는 배포에 적용되지 않고 있었다는 사실을 Round 3에서 발견

---

## 1. 왜 부하 테스트가 필요했나

배포가 끝난 후 기능은 모두 동작했지만, **이 시스템이 몇 명까지 견디는지** 아무도 답할 수 없는 상태였다. 운영자에게 "괜찮습니다"라고 말하려면 근거가 필요했다.

세운 목표는 단순했다.

- **얼마까지 견디나** — 단계별 부하(200u → 500u → 1,000u)에서 어디서 무너지는지 측정
- **무엇이 병목인가** — endpoint별 p99·실패율을 보고 후보를 좁히기
- **고쳤더니 정말 나아졌나** — 코드 변경 전후를 같은 시드로 직접 비교

성능 글은 측정 없이는 쓸 가치가 없다는 게 처음 세운 원칙이었다.

---

## 2. 측정 환경과 도구

### 도구

**Locust**를 선택했다. 이유는 단순하다.

- 시나리오를 **Python으로** 쓸 수 있다 — `weight`, `wait_time`, 동적 ID 분기 같은 실제 워크로드 모방이 한 파일에 들어간다
- `catch_response`로 **레이스 컨디션 4xx를 정상 응답으로 분류**할 수 있다 — 부하 테스트 특성상 동시에 같은 리소스를 건드리는 정상적 4xx까지 실패로 잡으면 신호가 노이즈에 묻힌다
- Spring Boot 자체와 다른 언어/프로세스에서 돌아가서 **서버 CPU를 빼앗지 않는다**

JMeter도 후보였지만, 시나리오를 JSON으로 정의해야 하는 점과 동적 분기가 어려운 점 때문에 Locust로 갔다.

### 시드 데이터

`seed.sql`로 다음 규모의 데이터를 사전 적재했다.

| 항목 | 규모 |
|---|---|
| 계정 (admin·user) | 1,000명 |
| 프로젝트 | 2,000개 (`LOAD-CN-0001` ~ `LOAD-CN-2000`) |
| 사용내역서 | 12,000개 |
| 세부항목 | 180,000개 |
| agent_logs | 36,000개 |

**모든 데이터를 `LOAD-*` prefix로 격리**했다. 운영 데이터와 섞이지 않도록 식별·삭제가 한 번에 가능해야 했다. 또 시드 시작 시점에 `BIGSERIAL` sequence를 6개 테이블에서 동기화한다 — 운영 데이터가 이미 있는 환경에서 자동 발행 ID가 충돌하면 INSERT가 실패하기 때문이다.

상태 전이 API(`/submit`, `/complete-review`, `/request-supplement`)는 사전 조건으로 legal agent 로그 존재를 요구한다. 부하 테스트는 FastAPI를 호출하지 않으므로 **가상 success 로그를 함께 시드**해, 상태 전이 측정이 정상 진행되도록 했다.

### 시나리오 — Atomic / Journey 분리

`locustfile.py`에 두 모드를 정의했다.

| 모드 | 클래스 | 용도 |
|---|---|---|
| **Atomic** (기본) | `AdminScenario` (weight 3) / `UserScenario` (weight 7) | 단위 endpoint 부하 측정. read 70 : write 30 비율로 endpoint별 p50/p99를 깔끔하게 본다 |
| **Journey** | `WriterJourney` / `ReviewerJourney` / `BrowsingLoad` | "login → detail → upload → submit" 같은 비즈니스 여정 측정. peak·stress·soak에서 실제 사용자 패턴 재현 |

Atomic은 "어떤 endpoint가 무거운가" 측정용, Journey는 "운영 환경 워크로드에 가까운 부하" 측정용으로 의도를 분리했다.

### 단계별 부하 패턴

| Phase | Users | Ramp | Duration | 의미 |
|---|---|---|---|---|
| Smoke | 20 | 5/s | 1m | 환경 검증 |
| Baseline | 200 | 10/s | 5m | 정상 운영 기준선 |
| Peak | 500 | 20/s | 5m | 피크 부하 (정상 2.5배) |
| Stress | 1,000 | 25/s | 8m | 한계 부하 |

각 단계 사이 1~2분 휴식으로 백엔드 안정화를 보장했다.

---

## 3. Before — 첫 측정과 진단

먼저 코드는 손대지 않고 **Before** 측정을 돌렸다. 결과는 충격적이었다.

| 지표 | Smoke (20u) | Baseline (200u) | Peak (500u) | Stress (1000u) |
|---|---|---|---|---|
| 실패율 | 0.00% | **11.01%** | **17.0%** | **18.3%** |
| p99 (전체) | 8.7 s | **31 s** | **66 s** | **112 s** |
| `[setup] login` median | 7.5 s | **30 s** | **46 s** | **76 s** |
| 5xx 건수 | 0 | 108 | 320 | 678 |
| 처리량 (RPS, 전체) | 5.4 | 18.8 | 33.7 | 56.9 |
| 처리량 (성공 RPS) | 5.4 | 16.8 | 28.0 | 46.5 |

**Baseline 200u에서부터 실패율 11%, p99 31초** — 이미 운영 불가 영역이었다.

### 단서

세 가지 신호가 한 방향을 가리키고 있었다.

**1. 5xx의 80% 이상이 `[setup] login`에 집중**

failures.csv를 분류하니 `POST /auth/login`이 압도적으로 실패하고 있었다. 단순 login 자체만의 문제는 아닐 수 있다 — 부하 ramp-up 초반에 모든 가상 사용자가 동시에 로그인을 시도하는 패턴이었다.

**2. 5xx 발생 시각이 모두 ramp-up 구간(0~30초)에 집중**

stats_history.csv에서 시간축으로 펼쳐보니 30초 이후로는 5xx가 거의 발생하지 않았다. 정상 운영 중의 갑작스러운 폭주가 아니라, **초기 동시 도착 시 풀이 견디지 못하는 패턴**이었다.

**3. 본 endpoint는 Stress(1000u)에서도 median 30~60ms**

흥미로운 점은, PK 조회·단순 CRUD endpoint들(`GET /usage-statements/:id`, `POST /files`, `PATCH /items/:id`)이 1,000u Stress에서도 **median 30~60ms로 안정적**이었다는 것. 시스템 처리 능력 자체는 충분했다. 무너진 건 **인증 계층**과 **검색/집계 쿼리**였다.

### 가설 정리

| 우선 | 가설 | 근거 |
|---|---|---|
| P1 | HikariCP `maximum-pool-size` 디폴트 10 추정 | 200u가 한 번에 들이닥치면 190명이 풀 대기 |
| P1 | `login()` 메서드 트랜잭션이 bcrypt 동안 커넥션을 잡고 있음 | 한 번의 login = SELECT + bcrypt(50~100ms) + INSERT를 하나의 `@Transactional`로 묶음 |
| P2 | `GET /projects?keyword=`의 LIKE `%키워드%` 풀스캔 | 일반 B-tree는 `%키워드%` 패턴에서 인덱스 활용 불가 |
| P2 | `GET /dashboard`의 집계 풀스캔 | 12,000 statement에서 status 필터 조회 |
| P2 | `GET /usage-statements` 목록의 N+1 | statement별 count 쿼리 2개씩 호출 |

이 진단을 들고 Round 1으로 들어갔다.

---

## 4. Round 1 — 인증 계층 병목 해소

> "Baseline 200u에서 실패율 0%, login p99 1초 미만"이 목표였다.

### 변경 1 — HikariCP `maximum-pool-size: 20`

`application.yaml`에 명시적으로 풀 크기를 적었다. 단순히 키운 게 아니라 **왜 20인가** 근거를 직접 확인했다.

```bash
psql -c "SHOW max_connections;"  # → 100
```

현재 사용량을 헤아려보니 service 10 + legal 10 + worker 4 + 운영 2 = **26**. 백엔드 pod가 2개로 늘어나도 service pool 40 + legal 10 + 여유 50 = **100 한도 안에 안전**. 그래서 20으로 결정했다.

> 풀 크기는 "크게 잡으면 좋은 것"이 아니라 PG `max_connections` 한도와 다른 클라이언트 사용량을 빼고 결정해야 한다는 걸 배웠다. 풀을 100으로 잡고 PG가 50인 환경에 배포하면 그 자체가 새로운 장애 원인이 된다.

### 변경 2 — `LegalDataSourceConfig`의 숨은 버그 수정 (★)

가장 의외였던 발견. `application.yaml`에 HikariCP 설정을 추가했는데도, 측정 직전 PG에서 확인하니 여전히 idle 커넥션이 정확히 **10개**였다 — Spring Boot 디폴트값과 일치.

원인을 추적하니 `LegalDataSourceConfig`가 DataSource bean을 직접 생성하면서 Spring Boot의 자동 hikari 설정이 비활성화되어 있었다.

```java
// Before — application.yaml의 hikari.* 가 binding 안 됨
@Bean @Primary
public DataSource primaryDataSource() {
    return primaryDataSourceProperties().initializeDataSourceBuilder().build();
}
```

`@ConfigurationProperties`로 binding을 명시하고 `HikariDataSource` 타입까지 못박았다.

```java
// After
@Bean @Primary
@ConfigurationProperties(prefix = "spring.datasource.hikari")
public DataSource primaryDataSource() {
    return primaryDataSourceProperties()
            .initializeDataSourceBuilder()
            .type(HikariDataSource.class)
            .build();
}
```

이 변경이 없었다면 Round 1의 모든 조치가 무력화될 수 있었다. "설정을 바꿨다"가 아니라 "**설정이 실제로 적용되었는지**" 확인하는 게 별도 검증이라는 걸 알게 됐다.

### 변경 3 — `legal-datasource` 풀 축소 (10 → 5)

`legal_rag` 스키마 API는 이 측정 범위에서 제외됐고 실 사용량도 거의 0이었다. idle 10개로 잡아둘 이유가 없어 PG 커넥션을 5개 회수했다.

### 변경 4 — `AuthService.login()`의 `@Transactional` 제거 (★ 가장 임팩트 큰 변경)

```java
// Before
@Transactional
public AuthResult login(LoginRequest request) {
    User user = userRepository.findByEmployeeNo(...)  // SELECT
    passwordEncoder.matches(...)                      // bcrypt 50~100ms — DB는 놀고 있음
    return authResult(user);                          // INSERT (refreshToken)
}
```

**bcrypt 50~100ms 동안 DB 커넥션을 점유**하고 있던 게 핵심 문제였다. 풀 20 × 점유 100ms = 이론적으로 200 RPS — 200u가 동시에 들이닥치면 즉시 포화.

`@Transactional`을 제거해도 안전한지 검증 단계를 거쳤다.

- `User` entity에 lazy 필드/관계 없음 (전부 일반 `@Column`)
- `ProfileResponse.from(user)` 접근 필드: id, employeeNo, realName, roleCode, createdAt, updatedAt — 전부 eager
- `createRefreshToken(user)` 내부에 자체 `@Transactional` 존재 → INSERT 원자성 보장
- **트랜잭션으로 묶어 보호해야 할 무결성이 없다**

`refresh()`와 `logout()`은 UPDATE+INSERT 원자성이 필요해서 `@Transactional`을 유지했다. **"모든 메서드에 `@Transactional` 붙이기"라는 관행적 패턴이 성능 측면에서 어떻게 작용하는지** 직접 본 첫 사례였다.

### 변경 후 — 이론값

| 지표 | Before | After (이론) |
|---|---|---|
| HikariCP 풀 | 10 (그것도 binding 안 됨) | 20 (실제 적용 보장) |
| login당 커넥션 점유 시간 | ~100ms | **~5ms** |
| 이론적 login 처리량 | 100 RPS | **4,000 RPS** |

---

## 5. Round 2 — 데이터 조회 최적화

### 변경 5 — `GET /usage-statements` N+1 정리

`UsageStatementService.list()`가 statement별로 count 쿼리 2개씩 호출하는 패턴.

```
프로젝트당 6 statement × (summaryCount + itemCount) = 12회 추가 쿼리
```

이미 `linkedFileCountsByStatement`, `unsatisfiedRequirementCountsByStatement`가 IN 절 배치 패턴으로 구현돼 있었다 — 같은 패턴을 두 카운트에도 적용했다.

```java
// CodeLookupService — 추가
public Map<Long, Long> summaryCountsByStatement(List<Long> statementIds) {
    // SELECT usage_statement_id, count(*) ... GROUP BY ...
}
public Map<Long, Long> itemCountsByStatement(List<Long> statementIds) { ... }
```

쿼리 수 `2N + 2` → **상수 4회**.

### 변경 6 — `pg_trgm` GIN 인덱스 (Flyway V15 → 나중에 V16으로 rename)

LIKE `%키워드%` 검색의 본질적 문제: **일반 B-tree는 좌측 와일드카드에서 인덱스를 활용할 수 없다**. 2,000 프로젝트 풀스캔이 Stress p99 93초의 직접 원인이었다.

해법은 `pg_trgm` 확장 + GIN 인덱스 (`gin_trgm_ops`).

```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_projects_project_name_trgm
    ON service.projects USING gin (LOWER(project_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_projects_contract_no_trgm
    ON service.projects USING gin (LOWER(contract_no) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_real_name_trgm
    ON service.users USING gin (LOWER(real_name) gin_trgm_ops);
```

trigram 인덱스는 3글자 단위로 쪼개 인덱싱하므로 `%키워드%` 패턴에서도 인덱스 스캔이 가능하다. `LOWER(...)` expression 인덱스로 잡은 건 쿼리가 `LOWER(p.project_name) LIKE :pattern` 형태였기 때문 — **인덱스는 쿼리의 형태와 정확히 일치해야 옵티마이저가 선택한다**.

`CREATE EXTENSION`은 superuser 권한이 필요해서, 운영자에게 사전 확인을 요청해야 한다는 점도 한 번 부딪혀 알게 됐다.

### 변경 7 — `GET /dashboard` partial index

```sql
-- AdminDashboardService.querySummary()의 reviewSql 패턴에 맞춤
CREATE INDEX IF NOT EXISTS idx_usage_statements_review_needed
    ON service.usage_statements (project_id)
    WHERE status_code IN ('upload_completed', 'supplement_required');
```

**Partial index**의 장점은 두 가지.

- review-needed 상태 행만 인덱스에 포함 → 인덱스 크기가 작음
- EXISTS의 `WHERE us.project_id = p.id AND us.status_code IN (...)` 패턴에 정확히 매칭 → Index Only Scan 가능

12,000 statement 전체에 인덱스를 만들 필요가 없는 케이스였다.

---

## 6. Round 3 — 숨겨진 충돌 발견과 인덱스 추가

여기서 가장 큰 교훈을 얻었다.

### 진단 — Round 1+2 배포 후 smoke 재측정

Round 1+2 코드를 image로 빌드해 배포한 뒤 smoke를 두 번 돌렸다.

| 측정 | login median | login p99 |
|---|---|---|
| 1차 (round2/smoke_20u 초기) | **6,300 ms** | 8,400 ms |
| 2차 (재배포 후) | **12,000 ms** | 14,000 ms |

목표는 `<500ms`였는데 5~12초가 나왔다. 두 측정 사이의 절대값 편차(6s → 12s)도 컸다.

login만 무거웠고, 다른 endpoint들의 응답시간은 모두 정상 범위였다. 한참 들여다본 끝에 다른 원인을 짚었다.

**배포 환경 pod CPU 한계**.

```
backend pod resources:
  requests: cpu 200m / memory 512Mi
  limits:   cpu 1 / memory 1Gi
```

- 20 동시 bcrypt × 100ms CPU = 2,000ms의 순수 CPU work
- `requests: 200m` 보장 환경에서 노드 경합 시 wall time 10초+ 가능
- `@Transactional` 제거는 DB 커넥션 점유 시간만 줄여줌 — **bcrypt CPU 부담은 코드로 더 줄일 수 없음**

login 환경 한계는 인프라 영역의 문제로 결론. 백엔드 코드는 더 손대지 않고, 매니페스트 변경(`requests.cpu` 상향, replica 증설)을 운영자에게 요청하기로 했다.

### Flyway V15 description 충돌 — 가장 큰 발견

login 진단 중에 `db/migrations/` 디렉터리를 우연히 확인하다가 같은 버전 두 파일을 발견했다.

```
V15__law_log_change_type_none.sql    ← 기존 (legal_rag 관련)
V15__add_search_indexes.sql          ← Round 2 신규 (pg_trgm + dashboard partial)
```

**Flyway는 같은 버전 번호의 description이 두 개면 거부한다**. Round 2의 마이그레이션이 실제 배포에 적용되지 않은 상태였다.

소름이 돋았다. Round 2 가설 C·D(`GET /projects?keyword=`와 `GET /dashboard`의 p99 단축)가 측정으로 검증되지 않았던 이유가 여기 있었다. "**코드가 적용됐다**"와 "**실제 DB에 반영됐다**"가 별개라는 걸 뒤늦게 배웠다.

### 변경 8 — V15 → V16 rename

`V15__law_log_change_type_none.sql`는 `legal_rag` 스키마 관련(CLAUDE.md의 "절대 건드리지 않음" 영역)이라 기존 V15를 정본으로 인정하고, Round 2의 마이그레이션을 **V16으로 rename**했다.

```bash
mv db/migrations/V15__add_search_indexes.sql \
   db/migrations/V16__add_search_indexes.sql
```

운영 DB의 `flyway_schema_history`에 V15(법령) → V16(검색 인덱스) 순서로 적용되도록 조정했다. 라운드 2 마이그레이션은 아직 미적용 상태였기 때문에 번호 변경에 의한 부작용은 없었다.

### 변경 9 — V17: `GET /projects` 정렬·CTE 인덱스

Round 2까지 손대지 못한 `GET /projects` 자체의 풀스캔(Stress p99 90초)을 분석했다.

`ProjectRepositoryImpl.searchCards`의 네이티브 쿼리에 세 가지 풀스캔이 있었다.

**1. `latest_statement` CTE의 DISTINCT ON**

```sql
SELECT DISTINCT ON (us.project_id)
    us.project_id, us.id, us.cumulative_progress_rate, us.status_code
FROM service.usage_statements us
ORDER BY us.project_id, us.report_month DESC, us.revision_no DESC
```

DISTINCT ON skip-scan을 활용하려면 `(project_id, report_month DESC, revision_no DESC)` 복합 인덱스가 필요했다.

**2. 정렬 컬럼 인덱스 부재**

`ProjectSort`의 START_DATE_ASC/DESC, END_DATE_ASC/DESC, PROJECT_NAME_ASC/DESC가 매핑된 컬럼에 인덱스가 없어 ORDER BY + LIMIT 10이 sort step으로 떨어졌다.

**3. 기간 필터 인덱스 부재**

`construction_start_date <= :periodTo`, `construction_end_date >= :periodFrom` 도 풀스캔.

```sql
-- V17__add_project_listing_indexes.sql
CREATE INDEX IF NOT EXISTS idx_projects_construction_start_date
    ON service.projects (construction_start_date);
CREATE INDEX IF NOT EXISTS idx_projects_construction_end_date
    ON service.projects (construction_end_date);
CREATE INDEX IF NOT EXISTS idx_projects_project_name_id
    ON service.projects (project_name, id DESC);
CREATE INDEX IF NOT EXISTS idx_usage_statements_project_month_revision
    ON service.usage_statements (project_id, report_month DESC, revision_no DESC);
```

### Round 3에서 일부러 안 한 것들

| 후보 | 안 한 이유 |
|---|---|
| bcrypt strength 10 → 8 | 시드 SQL의 `password_hash`가 cost=10으로 생성돼 있어 시드까지 손대야 효과. 별도 라운드로 분리하는 게 측정 깔끔 |
| `/auth/refresh` 동시성 개선 | login 환경 한계 해결 시 자연 완화될 가능성 큼 (401 → refresh 폭증 사슬). 회귀 위험 vs 효과 분리 어려움 |
| `@Cacheable` 도입 | EnableCaching + Caffeine 의존성 추가 vs 효과 미미 (코드 테이블 9 rows). 회귀 표면만 늘어남 |
| OSIV 비활성화 | 점검해보니 **이미 false**. 조치 불필요 |

"고칠 수 있는 곳"과 "지금 고칠 가치가 있는 곳"은 다르다는 걸 의식적으로 적용한 부분이다.

---

## 7. 결과 — Before vs Round 3 직접 비교

Round 1·2·3의 코드 변경이 모두 반영된 상태에서 Stress 1,000u를 다시 돌려 Before와 직접 비교했다 (둘 다 동일 시드, 1000u/8m).

| 가설 | endpoint | Before p99 | Round 3 p99 | 변화 |
|---|---|---|---|---|
| **A. 인증 병목** | 실패율 / 5xx / 401 | 18.3% / 678 / 4,358 | **0% / 0 / 0** | 완전 해소 |
| **A. 인증 병목** | `POST /auth/refresh` | 121,000 ms | **15,000 ms** | **-88%** |
| **C. pg_trgm 인덱스** | `GET /projects?keyword=` | 93,000 ms | **19,000 ms** | **-80%** |
| **D. dashboard partial** | `GET /dashboard` | 91,000 ms | **22,000 ms** | **-76%** |
| **E. projects 정렬·CTE** | `GET /projects` | 90,000 ms | **18,000 ms** | **-80%** |
| **E. projects 정렬·CTE** | `GET /projects?scope=all` | 50,000 ms | **18,000 ms** | **-64%** |
| **정합성** | orphan / UNIQUE / page_no | 0 | **0** | 한계 부하에서도 유지 |

### 처리량(RPS) 비교

| Phase | Before 성공 RPS | Round 3 성공 RPS | 변화 |
|---|---|---|---|
| Baseline 200u | 16.8 | **28.9** | **+72%** |
| Peak 500u | 28.0 | **36.6** | **+31%** |
| Stress 1000u | 46.5 | 26.5 | login CPU 한계로 throughput cap |

Baseline·Peak에서는 처리량 자체가 분명히 증가했다. Stress 1000u의 raw RPS 감소는 다음 항목에서 다루는 "허위 빠름"과 같은 메커니즘이다.

**인덱스가 직접 작용하는 4개 endpoint 모두 64~80% p99 단축** — Round 2/3 효과 실측 확정.

**`POST /auth/refresh` -88%** — login 성공 → 401 → refresh 폭증의 사슬이 끊긴 결정적 증거. Round 1의 가장 강한 신호.

### 절대값이 일부 악화처럼 보이는 이유

`p50 (전체)` 같은 일부 지표는 Round 3가 더 나빠 보였다(48ms → 3,900ms). **Stress 1000u의 raw RPS(56.9 → 26.5)도 같은 맥락**이다. 처음엔 회귀로 의심했는데, 데이터를 다시 보고 해석을 바꿨다.

- **Before**는 18.3%가 일찍 5xx/401로 죽어 시스템 부담이 줄어든 상태 — 실패 응답이 즉시 반환되어 raw RPS를 부풀린 "허위 빠름"
- **Round 3**는 0% 실패로 모든 요청을 살려내느라 큐가 길어진 상태 — throughput은 login bcrypt가 묶인 pod CPU 천장으로 수렴

운영 관점에서 "빠르지만 1/5가 실패"보다 "느리지만 모두 성공"이 압도적 우위. 측정 수치를 그대로 받아들이지 말고 그 의미를 해석하는 능력이 필요하다는 걸 배웠다.

### 가설 검증 매트릭스

| 가설 | 상태 | 근거 |
|---|---|---|
| A. 인증 병목 (Round 1) | **확정** | 전 단계 실패율 0%, `/auth/refresh` p99 Stress -88% |
| B. N+1 정리 (Round 2) | 간접 확인 | `GET /usage-statements` 절대값은 login 큐 영향에 묻힘. 쿼리 카운트는 검증됨 |
| C. pg_trgm (V16) | **확정** | `GET /projects?keyword=` -80% |
| D. dashboard partial (V16) | **확정** | `GET /dashboard` -76% |
| E. projects 정렬·CTE (V17) | **확정** | `GET /projects` -80% |
| F. latest statement (V17) | 부분 확인 | EXPLAIN으로 풀스캔 회피 확정. before 보고서 미수집 endpoint |
| 데이터 정합성 | **확정** | Baseline/Peak/Stress 모두 위반 0 |
| login 환경 한계 | **확정** | smoke 5.8s → baseline 59s → peak 123s → stress 210s. 백엔드 코드로 더 줄일 수 없음 |

---

## 8. 백엔드 개선 — 변경별 상세 정리

> 본 섹션은 §4~§6 라운드별 흐름을 **백엔드 코드/스키마 영역 변경 단위로 재정렬**한 것이다. 각 항목을 "문제 → 의심 지점 → 근거 → 해결 → 결과(수치)"로 표준화해, 어떤 가설이 어떤 측정값으로 검증됐는지 1:1 추적 가능하게 만든다.
>
> **인프라 한계의 명시** — Stress 1000u의 login 응답시간은 k8s pod `requests.cpu: 200m` 한계가 지배적이며, 이는 백엔드 코드 영역에서 더 줄일 수 없다. 매니페스트 영역(`requests.cpu` 상향 + replica 증설)으로 인계됐다. 이하 모든 항목은 **백엔드 코드/스키마 영역에서 가능한 조치**에 한정한다.

### ① `AuthService.login()` — `@Transactional` 제거 (임팩트 최대)

| 단계 | 내용 |
|---|---|
| **문제** | Baseline 200u부터 실패율 11.01% / Stress 1000u에서 18.3%. `[setup] login` median Smoke 7.5s → Baseline 30s → Peak 46s → Stress 76s |
| **의심 지점** | `login()` 메서드의 `@Transactional`이 bcrypt CPU 작업 동안 DB 커넥션을 점유 |
| **근거** | login 구조 = SELECT(user) + bcrypt(50~100ms) + INSERT(refreshToken). bcrypt는 순수 CPU work인데 트랜잭션은 계속 열려있음 → 커넥션 점유 시간이 처리량 상한을 좌우한다고 판단. 풀 20 × 점유 100ms 기준 **이론적 처리량 추정 200 RPS** (커넥션 점유만 고려한 보수적 상한) — 200u 동시 도착 시 즉시 포화 가능 |
| **해결** | `@Transactional` 제거. 안전성 검증 — User entity에 lazy 필드/관계 없음, `ProfileResponse.from(user)` 접근 필드 전부 eager, `createRefreshToken(user)` 내부에 자체 `@Transactional` 존재 → INSERT 원자성 보장. 묶을 무결성 없음 |
| **결과 (직접 귀속)** | • 커넥션 점유 시간 **~100ms → ~5ms** (login당)<br>• 이론적 처리량 추정 **200 RPS → 4,000 RPS** (커넥션 점유만 고려한 상한 — 실제 처리량은 CPU·네트워크 등 다른 요소에 의해 제약)<br>• 시스템 수준 실패율/5xx/401/refresh p99 감소는 **②와의 결합 효과** → "①+② 결합 효과" 표 참조 |

### ② `LegalDataSourceConfig` — Hikari binding 버그 수정

| 단계 | 내용 |
|---|---|
| **문제** | `application.yaml`에 `spring.datasource.hikari.maximum-pool-size: 20` 명시했는데 적용되지 않음 |
| **의심 지점** | application.yaml 설정이 실제로 DataSource bean에 binding되지 않을 가능성 |
| **근거** | 측정 직전 PG `pg_stat_activity`에서 idle 커넥션 수 확인 → **정확히 10개** (Spring Boot 디폴트값과 일치). 추적 결과 `LegalDataSourceConfig`가 DataSource bean을 직접 생성하면서 auto-config의 hikari 설정 자동 binding이 비활성화돼 있었음 |
| **해결** | bean 생성 메서드에 `@ConfigurationProperties(prefix = "spring.datasource.hikari")` + `.type(HikariDataSource.class)` 명시. `legal-datasource`는 사용량 거의 0이라 10 → 5로 축소 |
| **결과 (직접 귀속)** | • 풀 크기 **실제 10 → 20 적용 확인** (`pg_stat_activity` 재검증)<br>• PG 커넥션 5개 회수 (legal 풀 축소)<br>• 이 fix 없이는 ①의 효과도 무력화됐을 — **①의 선결 조건**<br>• 시스템 수준 실패율/5xx/401/refresh p99 감소는 **①과의 결합 효과** → 아래 표 참조 |

### ①+② 결합 효과 — 인증 계층 종합 (귀속 분리 불가)

| 지표 (Stress 1,000u) | Before | Round 3 | 변화 |
|---|---|---|---|
| 실패율 | 18.3% | **0.00%** | 완전 해소 |
| 5xx 건수 | 678 | **0** | 완전 해소 |
| 401 건수 | 4,358 | **0** | 완전 해소 |
| `POST /auth/refresh` p99 | 121,000ms | **15,000ms** | **-88%** |

> 위 지표는 ① 또는 ② 단독으로 귀속할 수 없는 **결합 효과**다. ②(Hikari binding 수정)가 풀 크기를 실제로 적용 가능하게 하고, ①(`@Transactional` 제거)이 그 풀의 회전율을 끌어올리는 구조 — 어느 한쪽만 했다면 효과가 무력화됐을 것이다. 또한 검색/대시보드 인덱스(③④⑤)가 백엔드 처리 큐를 비워준 효과도 일부 섞여있다. 단일 변수 분리 측정은 본 사이클에서 수행하지 않았으며, 다음 사이클의 개선 과제로 남겼다.

### ③ `GET /projects?keyword=` — pg_trgm GIN 인덱스 (V16)

| 단계 | 내용 |
|---|---|
| **문제** | Stress 1000u에서 p99 93,000ms |
| **의심 지점** | `LOWER(p.project_name) LIKE '%키워드%'` 풀스캔 |
| **근거** | 일반 B-tree 인덱스는 **좌측 와일드카드(`%`)에서 작동 불가** — 옵티마이저가 인덱스를 선택할 수 없는 쿼리 형태. 2,000 프로젝트 풀스캔이 직접 원인 |
| **해결** | `CREATE EXTENSION pg_trgm` + GIN 인덱스 3종 (project_name, contract_no, users.real_name) `USING gin (LOWER(컬럼) gin_trgm_ops)`. trigram은 3글자 단위 분할 인덱싱이라 `%키워드%`에서도 인덱스 스캔 가능. `LOWER(...)` expression 인덱스로 쿼리 형태와 정확히 일치 |
| **결과** | • `GET /projects?keyword=` **p99 93,000ms → 19,000ms (-80%)** |

### ④ `GET /dashboard` — partial index (V16)

| 단계 | 내용 |
|---|---|
| **문제** | Stress 1000u에서 p99 91,000ms |
| **의심 지점** | `AdminDashboardService.querySummary()`의 reviewSql이 12,000 statement에서 status 필터 EXISTS 조회 시 풀스캔 |
| **근거** | `WHERE us.project_id = p.id AND us.status_code IN ('upload_completed', 'supplement_required')` 패턴에 매칭되는 인덱스 부재. EXPLAIN으로 Seq Scan 확인 |
| **해결** | partial index `(project_id) WHERE status_code IN ('upload_completed', 'supplement_required')`. review-needed 상태 행만 인덱스에 포함해 크기 최소화, Index Only Scan 가능 |
| **결과** | • `GET /dashboard` **p99 91,000ms → 22,000ms (-76%)** |

### ⑤ `GET /projects` — 정렬·CTE 복합 인덱스 (V17)

| 단계 | 내용 |
|---|---|
| **문제** | Stress 1000u에서 `GET /projects` p99 90,000ms, `?scope=all` p99 50,000ms |
| **의심 지점** | `ProjectRepositoryImpl.searchCards` 네이티브 쿼리에 세 가지 풀스캔 — (a) `latest_statement` CTE의 DISTINCT ON, (b) ORDER BY 컬럼 인덱스 부재, (c) 기간 필터 인덱스 부재 |
| **근거** | EXPLAIN에 sort step + Seq Scan. DISTINCT ON skip-scan은 `(project_id, report_month DESC, revision_no DESC)` 순서가 정확히 일치해야 작동 |
| **해결** | 4개 인덱스 추가 — `idx_projects_construction_start_date`, `idx_projects_construction_end_date`, `idx_projects_project_name_id (project_name, id DESC)`, `idx_usage_statements_project_month_revision (project_id, report_month DESC, revision_no DESC)` |
| **결과** | • `GET /projects` **p99 90,000ms → 18,000ms (-80%)**<br>• `GET /projects?scope=all` **p99 50,000ms → 18,000ms (-64%)** |

### ⑥ `GET /usage-statements` — N+1 제거

| 단계 | 내용 |
|---|---|
| **문제** | statement 목록 조회 시 statement별 count 쿼리 2개씩 호출 |
| **의심 지점** | `UsageStatementService.list()`가 statement마다 summaryCount, itemCount를 개별 호출 |
| **근거** | 같은 서비스의 `linkedFileCountsByStatement`, `unsatisfiedRequirementCountsByStatement`는 이미 IN 절 배치 패턴인데 두 카운트만 N+1로 남음. 프로젝트당 6 statement × 2 = 12회 추가 쿼리 |
| **해결** | `CodeLookupService`에 `summaryCountsByStatement(List<Long>)`, `itemCountsByStatement(List<Long>)` 추가 — `SELECT usage_statement_id, COUNT(*) ... GROUP BY` 배치 |
| **결과** | • 쿼리 수 **`2N + 2` → 상수 4회** (N = statement 수)<br>• **검증 한계**: Stress 1000u에서 login 큐의 latency 영향으로 endpoint p99 분리 측정 불가. 쿼리 카운트 감소로만 검증됨 — ①~⑤ 대비 정량적 endpoint-level 근거 약함 (개선 영역) |

### ⑦ Flyway V15 description 충돌 발견 (방법론적)

| 단계 | 내용 |
|---|---|
| **문제** | Round 1+2 배포 후 smoke 재측정에서 ③④의 p99가 떨어지지 않음 |
| **의심 지점** | 코드는 배포됐지만 마이그레이션이 실제로 DB에 적용되지 않았을 가능성 |
| **근거** | `db/migrations/` 디렉터리에서 같은 V15 두 파일 발견 — `V15__law_log_change_type_none.sql`(기존 legal_rag)과 `V15__add_search_indexes.sql`(Round 2 신규). Flyway는 **같은 버전 번호의 description 두 개면 거부**한다 |
| **해결** | `legal_rag` 영역은 CLAUDE.md상 "절대 건드리지 않음"이라 기존 V15를 정본으로 인정. Round 2 마이그레이션을 V16으로 rename. 운영 DB `flyway_schema_history`에 V15(법령) → V16(검색 인덱스) 순서로 정상 적용 |
| **결과** | Round 3 측정에서 가설 C/D/E **모두 -76~-80% 검증 확정** (이전에는 코드만 배포되고 DB는 미반영 상태였음) |

### 변경별 임팩트 요약

| # | 변경 | 영역 | 핵심 수치 | 귀속 |
|---|---|---|---|---|
| ① | `login()` `@Transactional` 제거 | Spring | 커넥션 점유 **~100ms → ~5ms** (직접) | 단독 직접 |
| ② | Hikari binding 버그 수정 | Spring Config | 풀 **10 → 20 실제 적용**, ①의 선결 조건 | 단독 직접 |
| ①+② | 인증 계층 종합 | — | 실패율 18.3% → **0%**, 5xx **678→0**, 401 **4,358→0**, refresh p99 **-88%** | 결합 효과 |
| ③ | pg_trgm GIN 인덱스 | PostgreSQL | `?keyword=` p99 93s → 19s **(-80%)** | 단독 직접 |
| ④ | dashboard partial index | PostgreSQL | dashboard p99 91s → 22s **(-76%)** | 단독 직접 |
| ⑤ | projects 정렬·CTE 인덱스 | PostgreSQL | projects p99 90s → 18s **(-80%)**, `scope=all` 50s → 18s **(-64%)** | 단독 직접 |
| ⑥ | statement 목록 N+1 제거 | Spring JPA | 쿼리 `2N+2` → **상수 4** (endpoint p99 분리 측정 불가) | 검증 한계 |
| ⑦ | Flyway V15 충돌 발견 | Migration | Round 2 효과가 측정에 반영되도록 복원 | 방법론적 |

### 정상 운영 처리량(Baseline 200u) — 백엔드 변경의 직접 효과

| 지표 | Before | Round 3 | 변화 |
|---|---|---|---|
| 성공 RPS (Baseline 200u) | 16.8 | **28.9** | **+72%** |
| 성공 RPS (Peak 500u) | 28.0 | **36.6** | **+31%** |
| 실패율 (Baseline 200u) | 11.01% | **0.00%** | 완전 해소 |
| 실패율 (Peak 500u) | 17.0% | **0.00%** | 완전 해소 |

> Stress 1000u의 raw RPS(46.5 → 26.5)는 §7의 "허위 빠름" 메커니즘 — Before가 18% 즉시 실패로 RPS를 부풀린 결과 — 으로 회귀가 아니다. **백엔드 변경 자체의 throughput 효과는 정상~피크 부하 구간에서 명확히 측정됨**. Stress 1000u의 잔여 latency는 §8 서두에 명시한 pod CPU 한계가 지배적이다.

---

## 9. 회고 — 한계와 배운 점

### 한계로 인정한 것

**login의 절대값은 코드로 더 못 줄인다**. Round 3 진단에서 확인한 pod `requests: cpu 200m` 한계가 지배적이라, 매니페스트 변경(CPU 상향 + replica 증설)이 진짜 해법이다. 백엔드 코드 안에서 더 손대지 않기로 결정한 부분이다.

**`port-forward` overhead 포함**. `kubectl port-forward`는 동시 1,000 connection에서 `RemoteDisconnected` 신호를 보였다. 실제 사용자 트래픽은 LoadBalancer/Ingress 경유라 운영 환경 수치는 더 좋을 가능성이 크다. 측정값은 보수적 수치로 해석한다.

**Locust와 서버 동일 머신 금지 — 그러나 round1 단독 측정은 시간상 생략**. 이상적으로는 Round별 단독 측정으로 가설별 효과를 분리해야 했지만, Flyway V15 충돌 발견 시점에 Round 2 마이그레이션이 미적용 상태였음이 드러나, **Before vs Round3 한 번**의 비교로 효과를 종합 검증했다. 다음에 비슷한 사이클을 돈다면 round별 단독 측정을 빼먹지 않을 것이다.

### 배운 것 — 기술적

- **`@Transactional`은 무겁다**. CPU 작업이 들어가는 메서드에 트랜잭션을 거는 건 DB 커넥션을 인질로 잡는 행위다. 무결성이 필요 없다면 떼야 한다
- **설정 적용 여부는 별도 검증한다**. application.yaml에 적었다고 끝이 아니다. PG의 `pg_stat_activity`로 idle 커넥션 수를 확인하면 binding이 진짜로 일어났는지 보인다
- **`%키워드%` LIKE에는 일반 인덱스가 안 먹는다**. `pg_trgm` GIN이 정답. 그리고 인덱스는 쿼리의 정확한 형태(`LOWER(...)` 같은 expression)와 일치해야 옵티마이저가 선택한다
- **Partial index는 status enum 필터링에 강력**. 전체 인덱스보다 크기가 작고 적중률이 높다
- **Flyway 버전 충돌은 조용히 일어난다**. 같은 버전 번호 description 두 개면 마이그레이션이 거부되는데, 배포 로그를 안 보면 알 수 없다. 이번 사례에서 Round 2의 효과가 측정에 안 잡힌 이유였다
- **DISTINCT ON skip-scan은 정확한 복합 인덱스가 필요**. `(project_id, report_month DESC, revision_no DESC)` 순서가 정확히 맞아야 한다

### 배운 것 — 방법론

- **단계별 부하(20 → 200 → 500 → 1,000)는 신호 분리에 필수**. Smoke만 돌렸으면 인증 병목을 놓쳤다. Stress만 돌렸으면 노이즈에 묻혀 어떤 가설도 검증 못 했다
- **시드는 격리 prefix(`LOAD-*`)로**. 운영 데이터와 섞이지 않으면 teardown이 한 줄로 끝나고 정합성 검증도 가능하다
- **`catch_response`로 정상 4xx 흡수**. 부하 테스트의 동시성 충돌은 운영의 정상 시나리오 — 실패로 잡으면 의미 있는 신호가 묻힌다
- **수치를 의심하기**. `p50` 절대값이 악화로 보일 때 그게 진짜 회귀인지, 아니면 다른 메커니즘의 부작용인지 분리해야 한다. "허위 빠름" 같은 패턴은 단순 숫자 비교로는 안 보인다
- **고치지 않을 것을 명시한다**. Round 3에서 의도적으로 손대지 않은 5가지(bcrypt, refresh, archive 쿼리, @Cacheable, OSIV)를 따로 적어둔 게 나중에 이 결정을 설명할 때 큰 도움이 됐다

---

## 부록 — 재현 방법

전체 시퀀스는 다음 한 줄로 재현된다 (k8s 환경, port-forward 실행 중).

```bash
# 시드 → Before 측정 → 코드 변경 → 재측정
bash backend/load-test/locust-k8s.sh seed

# Before (Round 0)
LOCUST_OUT_DIR=before/stress_1000u \
LOCUST_USERS=1000 LOCUST_RATE=25 LOCUST_TIME=8m \
bash backend/load-test/locust-k8s.sh save

# 정합성 검증
LOCUST_OUT_DIR=before/stress_1000u \
bash backend/load-test/locust-k8s.sh verify

# Round 3 (코드 변경 + Flyway 적용 후)
LOCUST_OUT_DIR=round3/stress_1000u \
LOCUST_USERS=1000 LOCUST_RATE=25 LOCUST_TIME=8m \
bash backend/load-test/locust-k8s.sh save
```

상세 가이드: [`README.md`](./README.md) / [`K8S_RUNBOOK.md`](./K8S_RUNBOOK.md)
원본 진단 보고서: [`results/reports/00_initial_diagnosis_20260614.md`](./results/reports/00_initial_diagnosis_20260614.md)
변경 작업 로그: [`results/reports/improvement_log.md`](./results/reports/improvement_log.md)
