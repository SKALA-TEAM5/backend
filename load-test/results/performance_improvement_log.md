# 백엔드 부하 테스트 — 병목 해결 작업 로그

> 보고서: `k8s_load_test_report_20260614.md` 기반.
> 측정 환경: k8s (`skala3-finalproj-class2-team5`), Spring Boot 3.5, PostgreSQL.

---

## 진단 요약 (Before)

Baseline(200u/5m) 측정 결과 시스템 한계 신호 감지.

| 지표 | 값 | 판정 |
|---|---|---|
| 실패율 | **11.01%** | 위험 (>3%) |
| p99 | **31,000 ms** | 위험 |
| `[setup] login` median | **30,000 ms** | 매우 심각 |
| 5xx 발생 | 108건 (대부분 `[setup] login`) | — |
| 5xx 발생 시각 | 0~26초 (ramp-up 구간) | — |

**핵심 관찰**:
- 모든 5xx의 80%+가 `[setup] login`에 집중
- 본 endpoint(CRUD·PK 조회)는 Stress(1000u)에서도 median 30~60ms로 **안정적**
- → 시스템 처리 능력은 충분. **인증 계층이 병목**.

---

## 라운드 1 — 인증 계층 병목 해소

### 변경 1: HikariCP `maximum-pool-size` 상향 (10 → 20)

**문제**: `application.yaml`에 HikariCP 설정 없음 → Spring Boot 디폴트 `maximum-pool-size=10` 적용 중.
- 200u 동시 ramp-up 시 **190명이 풀 대기** → p99 폭증의 직접 원인.

**조치**:
```yaml
# backend/src/main/resources/application.yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: ${SPRING_DATASOURCE_HIKARI_MAX_POOL_SIZE:20}
      minimum-idle: ${SPRING_DATASOURCE_HIKARI_MIN_IDLE:10}
      connection-timeout: ${SPRING_DATASOURCE_HIKARI_CONNECTION_TIMEOUT:30000}
```

**근거 (PG 측 확인)**:
- `SHOW max_connections;` → `100`
- 현재 사용량: service 10 + legal 10 + worker 4 + 운영 2 = 26
- backend pod 수에 따라 안전한 풀 크기를 20으로 결정 (pod 2개여도 100 한도 내 안전)

---

### 변경 2: HikariCP binding 실제 적용 (숨겨진 버그)

**문제**: `LegalDataSourceConfig`가 DataSource bean을 **직접 생성**하기 때문에 Spring Boot의 hikari 자동 설정이 비활성화됨.

```java
// Before
@Bean @Primary
public DataSource primaryDataSource() {
    return primaryDataSourceProperties().initializeDataSourceBuilder().build();
    // ↑ hikari.* 속성이 binding 안 됨!
}
```

→ application.yaml에 hikari 설정을 추가해도 **실제로는 적용되지 않는 상태**였음.
→ PG의 `safety_service_app | idle | 10`이 정확히 디폴트값과 일치하는 증거.

**조치**: `@ConfigurationProperties`로 hikari 속성 binding 명시 + `HikariDataSource` 타입 명시.

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

legal-datasource에도 동일 적용.

---

### 변경 3: legal-datasource pool 축소 (10 → 5)

**근거**: `legal_rag` 스키마 API는 측정 범위에서 제외됐고 실 사용량 거의 0. idle 10개로 잡아둘 필요 없음.

**조치**:
```yaml
spring:
  legal-datasource:
    hikari:
      maximum-pool-size: ${SPRING_LEGAL_DATASOURCE_HIKARI_MAX_POOL_SIZE:5}
      minimum-idle: ${SPRING_LEGAL_DATASOURCE_HIKARI_MIN_IDLE:2}
```

→ PG connection 5개 여유 확보.

---

### 변경 4: `AuthService.login()` `@Transactional` 제거 ★ (가장 임팩트 큰 변경)

**문제**: login 메서드 전체가 `@Transactional`이라 **bcrypt CPU 작업 50~100ms 동안에도 DB connection을 점유**.

```java
// Before
@Transactional
public AuthResult login(LoginRequest request) {
    User user = userRepository.findByEmployeeNo(...)         // SELECT
    passwordEncoder.matches(...)                             // CPU bcrypt 50~100ms (DB 놀고있음)
    return authResult(user);                                 // INSERT (refreshToken)
}
```

- 풀 10 × 점유시간 100ms = **이론적 처리량 100 RPS** → 200u 동시 ramp-up 시 즉시 포화

**검증한 안전성**:
- `User` entity는 **lazy 필드/관계 없음** (전부 일반 `@Column`)
- `ProfileResponse.from(user)` 접근 필드: id, employeeNo, realName, roleCode, createdAt, updatedAt — 전부 eager
- `createAccessToken(user)`: getId(), getRoleCode() — 전부 eager
- `createRefreshToken(user)`: 내부에 자체 `@Transactional` 있음 → INSERT 원자성 보장
- → **트랜잭션을 합쳐서 보호할 무결성이 없음**. 안전하게 제거 가능.

**조치**: `@Transactional` 어노테이션 제거. `refresh()`, `logout()`은 진짜 원자성 필요(UPDATE+INSERT 묶음)하므로 유지.

```java
// After
public AuthResult login(LoginRequest request) {
    User user = userRepository.findByEmployeeNo(...)
    if (!passwordEncoder.matches(..., user.getPasswordHash())) {
        throw invalidCredentials();
    }
    return authResult(user);
}
```

---

## 라운드 1 — 이론적 효과 (Baseline 200u 기준)

| 지표 | Before | After (이론) |
|---|---|---|
| HikariCP pool | 10 (실제 binding 안 됨 위험) | **20 (실제 적용 보장)** |
| connection 점유시간/login | ~100ms (bcrypt 포함) | **~5ms** (SELECT+INSERT만) |
| 이론적 login 처리량 | 100 RPS | **4,000 RPS** (20 × 1/0.005) |
| legal pool idle 점유 | 10 | 2 |

**예상 결과**:
- ramp-up 5xx (login bound) 대폭 감소
- 후속 401 → /auth/refresh 폭증 연쇄 효과도 자연 완화
- 본 endpoint들의 p99 안정화 (pool wait 시간 감소)

---

## 변경 파일 요약

| 파일 | 변경 |
|---|---|
| `backend/src/main/resources/application.yaml` | HikariCP service pool 20, legal pool 5 명시 |
| `backend/src/main/java/com/skala/backend/global/config/LegalDataSourceConfig.java` | DataSource bean에 `@ConfigurationProperties("spring.datasource.hikari")` 추가 → 설정이 실제로 적용되도록 수정 |
| `backend/src/main/java/com/skala/backend/auth/service/AuthService.java` | `login()`의 `@Transactional` 제거 |

---

## 검증 측정 — 라운드 1 결과 (TBD)

> 재배포 후 Baseline(200u/5m) 재측정 예정.

| 지표 | Before | After (목표) | After (실측) |
|---|---|---|---|
| 실패율 | 11.01% | <1% | _ |
| p99 (전체) | 31,000 ms | <2,000 ms | _ |
| `[setup] login` median | 30,000 ms | <500 ms | _ |
| 5xx 총건 | 108 | 0 | _ |
| 401 총건 | 513 | <50 | _ |

---

## 라운드 2 — 데이터 조회 최적화

라운드 1과 별도 endpoint·메커니즘이라 동시 적용해도 측정으로 가설별 효과 분리 가능 (각 endpoint p99가 별개 지표).

---

### 변경 5: `GET /usage-statements` N+1 쿼리 정리

**문제**: `UsageStatementService.list()`가 statement별로 count 쿼리 2개씩 호출.
- 프로젝트당 6 statement × `summaryRepository.countByUsageStatementId()` + `itemRepository.countByUsageStatementId()` = **12회 추가 쿼리**
- 보고서 Stress p99 7,000 ms

**조치**: `CodeLookupService`에 IN 절 배치 카운트 메서드 2개 추가. 기존 `linkedFileCountsByStatement`, `unsatisfiedRequirementCountsByStatement` 패턴과 동일.

```java
// CodeLookupService.java — 추가
public Map<Long, Long> summaryCountsByStatement(List<Long> statementIds) {
    // SELECT usage_statement_id, count(*) ... GROUP BY ...
}
public Map<Long, Long> itemCountsByStatement(List<Long> statementIds) {
    // SELECT usage_statement_id, count(*) ... GROUP BY ...
}
```

```java
// UsageStatementService.list() — 호출 패턴 변경
Map<Long, Long> summaryCounts = codeLookupService.summaryCountsByStatement(statementIds);
Map<Long, Long> itemCounts = codeLookupService.itemCountsByStatement(statementIds);
// ...
.map(statement -> new UsageStatementListItemResponse(
    ...,
    summaryCounts.getOrDefault(statement.getId(), 0L),
    itemCounts.getOrDefault(statement.getId(), 0L),
    ...
))
```

**효과**: statement N개 조회 시 쿼리 수 `2N + 2` → **상수 4회**.

---

### 변경 6: `GET /projects?keyword=` `pg_trgm` GIN 인덱스 (Flyway V15)

**문제**: LIKE `'%키워드%'` 풀스캔.
- `LOWER(p.project_name) LIKE :keywordPattern`
- `LOWER(p.contract_no) LIKE :keywordPattern`
- `LOWER(u.real_name) LIKE :assigneeNamePattern`
- 일반 B-tree 인덱스는 `%키워드%` 패턴 활용 불가 → 2,000 프로젝트 풀스캔
- 보고서 Stress p99 **93,000 ms**

**조치**: `pg_trgm` 확장 + `LOWER(...)` expression 위에 GIN 인덱스 (`gin_trgm_ops`).

```sql
-- db/migrations/V15__add_search_indexes.sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX IF NOT EXISTS idx_projects_project_name_trgm
    ON service.projects USING gin (LOWER(project_name) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_projects_contract_no_trgm
    ON service.projects USING gin (LOWER(contract_no) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_real_name_trgm
    ON service.users USING gin (LOWER(real_name) gin_trgm_ops);
```

**효과**: trigram 인덱스로 `%키워드%` 패턴이 인덱스 활용 가능 → 풀스캔 → 인덱스 스캔.

> **주의**: `CREATE EXTENSION pg_trgm`은 superuser 또는 `CREATEROLE` 권한 필요. Flyway 실행 계정 권한 사전 확인 필요. 권한 부족 시 운영자에게 한 번만 `CREATE EXTENSION pg_trgm;` 실행 요청.

---

### 변경 7: `GET /dashboard` `reviewNeededProjects` 인덱스 (Flyway V15)

**문제**: `AdminDashboardService.querySummary()`의 `reviewSql`이 전체 statement 풀스캔.
```sql
SELECT COUNT(DISTINCT p.id) FROM service.projects p
WHERE EXISTS (
    SELECT 1 FROM service.usage_statements us
    WHERE us.project_id = p.id
      AND us.status_code IN ('upload_completed', 'supplement_required')
)
```
- 12,000 statement 풀스캔 → 보고서 Stress p99 **91,000 ms**

**조치**: status 필터를 조건으로 가진 **partial index**.

```sql
-- V15 일부
CREATE INDEX IF NOT EXISTS idx_usage_statements_review_needed
    ON service.usage_statements (project_id)
    WHERE status_code IN ('upload_completed', 'supplement_required');
```

**효과**:
- review needed 상태인 행만 인덱스에 포함 → 인덱스 크기 작음
- EXISTS의 `WHERE us.project_id = p.id AND us.status_code IN (...)` 패턴에 정확히 매칭
- Index Only Scan 가능

---

## 라운드 2 — 변경 파일 요약

| 파일 | 변경 |
|---|---|
| `backend/src/main/java/com/skala/backend/project/service/CodeLookupService.java` | `summaryCountsByStatement`, `itemCountsByStatement` 배치 카운트 메서드 추가 |
| `backend/src/main/java/com/skala/backend/usage/service/UsageStatementService.java` | `list()`에서 statement별 count 호출 → 사전 배치 Map 조회로 전환 |
| `db/migrations/V15__add_search_indexes.sql` | pg_trgm 확장 + projects/users 검색 인덱스 + dashboard review needed partial index |

---

## 검증 측정 — 라운드 1+2 통합 (TBD)

### 측정 시나리오

라운드 1과 2를 한 번의 측정으로 가설별 분리 검증. 각 가설은 다른 endpoint p99를 보면 됨.

| 가설 | 검증 endpoint | Before p99 (Stress) | 기대 효과 |
|---|---|---|---|
| **A. 인증 병목 (라운드 1)** | `[setup] login`, 전체 실패율 | login 76,000 ms / 실패율 18.3% | login <500ms / 실패율 <1% |
| **B. N+1 정리 (P3)** | `GET /usage-statements` | 7,000 ms | <1,000 ms |
| **C. pg_trgm 인덱스 (P4)** | `GET /projects?keyword=` | 93,000 ms | <2,000 ms |
| **D. dashboard partial index (P5)** | `GET /dashboard` | 91,000 ms | <2,000 ms |

### 결과 표 (측정 후 채우기)

#### Baseline 200u

| 지표 | Before | After |
|---|---|---|
| 실패율 | 11.01% | _ |
| p99 (전체) | 31,000 ms | _ |
| `[setup] login` median | 30,000 ms | _ |
| `GET /usage-statements` p99 | (보고서 미명시) | _ |

#### Stress 1000u (가설 D 검증 핵심)

| 지표 | Before | After |
|---|---|---|
| 실패율 | 18.3% | _ |
| `GET /usage-statements` p99 | 7,000 ms | _ |
| `GET /projects?keyword=` p99 | 93,000 ms | _ |
| `GET /dashboard` p99 | 91,000 ms | _ |

---

## 운영자 사전 확인 사항

배포 전 PG 측에서 점검 필요:

1. **pg_trgm 확장 권한**
   ```sql
   SELECT * FROM pg_extension WHERE extname = 'pg_trgm';
   -- 결과 없으면: CREATE EXTENSION pg_trgm;  (superuser 권한 필요)
   ```

2. **인덱스 빌드 시 락**
   - GIN 인덱스 빌드는 테이블에 짧은 `SHARE` 락 발생 (운영 트래픽 미미한 시간대 권장)
   - `CONCURRENTLY` 옵션 사용 검토 (단, Flyway는 트랜잭션 안에서 실행하므로 분리 필요)

3. **Flyway 실행 계정 권한**
   - `CREATE EXTENSION` 실행 권한 확인
   - 권한 없으면 운영자가 사전 수동 실행 후 V15는 `IF NOT EXISTS`로 skip

---

*최종 업데이트: 2026-06-14 (라운드 1+2 코드 변경 완료, 측정 대기)*
