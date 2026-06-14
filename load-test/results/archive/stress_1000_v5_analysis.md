# Locust 부하 테스트 분석 - stress_1000_v5

## 실행 조건

```bash
make locust-save LOCUST_USERS=1000 LOCUST_RATE=25 LOCUST_TIME=8m LOCUST_RESULT=stress_1000_v5
```

- 대상: `http://localhost:8000`
- 사용자 수: `1000`
- spawn rate: `25 users/s`
- 실행 시간: `8m`
- 결과 파일:
  - `load-test/results/stress_1000_v5_stats.csv`
  - `load-test/results/stress_1000_v5_stats_history.csv`
  - `load-test/results/stress_1000_v5_failures.csv`
  - `load-test/results/stress_1000_v5_exceptions.csv`

## 전체 결과

| 항목 | 값 |
| --- | ---: |
| 총 요청 수 | 194,658 |
| 실패 수 | 1,470 |
| 실패율 | 0.75% |
| 평균 RPS | 405.8 |
| 안정 구간 RPS | 약 425-432 |
| 평균 응답 시간 | 20.4ms |
| p95 | 25ms |
| p99 | 530ms |
| max | 2028ms |
| Python 예외 | 없음 |

`stress_1000_v5_exceptions.csv`는 비어 있으므로 Locust 실행 중 Python 예외나 클라이언트 내부 예외는 관측되지 않았다.

## 실패 요약

| API | 상태 | 발생 수 | 해석 |
| --- | ---: | ---: | --- |
| `POST /items/:id/evidence-files` | 409 | 399 | 이미 연결된 파일을 다시 연결하려는 충돌 |
| `PATCH /evidence-file-links/:id` | 404 | 563 | Locust가 들고 있던 link id가 서버에서 이미 삭제되었거나 현재 project 범위에서 찾을 수 없음 |
| `DELETE /evidence-file-links/:id` | 404 | 372 | 이미 삭제된 link를 다시 삭제하려는 시나리오 경합 |
| `PATCH /evidence-file-links/:id` | 409 | 136 | 이동 대상 item에 같은 file이 이미 연결된 충돌 |

이번 실패는 모두 evidence link 계열이다. `GET` 계열, item CRUD, file CRUD, statement submit/review 계열에서는 실패가 발생하지 않았다.

중요한 결론은 evidence link가 현재 병목이라기보다, Locust 시나리오가 같은 link/file/item 상태를 로컬 리스트로 들고 있다가 서버 상태와 어긋나면서 정상적인 `404/409`를 실패로 집계했다는 점이다. evidence link API 자체의 응답 시간은 낮다.

| API | 요청 수 | 실패 수 | avg | p95 | p99 | max |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `POST /items/:id/evidence-files` | 8,212 | 399 | 6.1ms | 7ms | 31ms | 953ms |
| `PATCH /evidence-file-links/:id` | 3,411 | 699 | 3.6ms | 6ms | 16ms | 218ms |
| `DELETE /evidence-file-links/:id` | 3,529 | 372 | 3.6ms | 6ms | 21ms | 489ms |

따라서 evidence link 쪽은 성능 병목이라기보다 테스트 시나리오/충돌 처리 기준을 정리해야 하는 영역이다.

## 주요 API별 관찰

| API | 요청 수 | 실패 수 | avg | p95 | p99 | max | 비고 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | --- |
| `[setup] login` | 1,000 | 0 | 618.8ms | 1100ms | 1300ms | 2028ms | ramp-up 병목 후보 |
| `GET /usage-statements/:id` | 36,412 | 0 | 21.6ms | 27ms | 450ms | 1597ms | 요청 수 최다, 응답 크기 큼 |
| `GET /usage-statements/latest` | 6,842 | 0 | 22.8ms | 28ms | 500ms | 1400ms | 상세 조회와 유사한 tail latency |
| `GET /projects` | 13,495 | 0 | 21.8ms | 25ms | 470ms | 1239ms | 목록 조회 |
| `POST /files` | 13,795 | 0 | 16.4ms | 16ms | 480ms | 1547ms | 파일 업로드 |
| `POST /items` | 18,273 | 0 | 15.7ms | 13ms | 470ms | 1548ms | 항목 생성 |
| `PATCH /items/:id` | 13,123 | 0 | 9.2ms | 8ms | 280ms | 1110ms | 항목 수정 |

전체적으로 p95는 낮지만 p99와 max에서 tail latency가 커진다. 즉 평균 성능보다는 일부 요청이 0.5-1.6초까지 튀는 원인을 확인해야 한다.

## 병목 후보

### 1. 로그인

`[setup] login`은 평균 `618.8ms`, p95 `1100ms`, p99 `1300ms`로 가장 무겁다. 테스트 시작 시 1000명이 순차적으로 로그인하므로 ramp-up 구간에서 인증 API가 가장 큰 부담이 된다.

가능한 원인:

- bcrypt 검증 비용
- 인증 후 세션/쿠키 처리 비용
- 사용자 조회 쿼리 또는 인덱스
- 테스트 계정 수 30개에 1000명이 몰리는 구조

개선 후보:

- `users.employee_no` 인덱스 확인
- 로그인 API 쿼리 로그 확인
- 운영 부하 테스트에서는 로그인 단계를 사전 토큰 발급 또는 낮은 비중으로 분리
- 인증 자체를 테스트하려는 경우 별도 로그인 전용 시나리오로 분리

### 2. 사용내역서 상세 조회

`GET /usage-statements/:id`는 요청 수가 가장 많고 응답 크기도 크다. 실패는 없지만 p99 `450ms`, max `1597ms`까지 튄다.

가능한 원인:

- 사용내역서 상세 응답에 items, evidence files, requirements 등 여러 묶음 데이터가 포함됨
- item 수가 늘어날수록 조인 또는 후속 조회 비용 증가
- 응답 JSON 크기 증가
- 파일/evidence link 조회가 상세 응답에 함께 묶일 경우 N+1 또는 큰 IN 조건 가능성

개선 후보:

- 상세 조회 서비스의 SQL/쿼리 수 확인
- `usage_statement_items.usage_statement_id` 인덱스 사용 여부 확인
- `evidence_file_links.usage_statement_item_id`, `files.id`, `files.project_id` 경로 확인
- 응답에서 항상 필요하지 않은 무거운 필드 분리 검토
- 상세 조회 결과 캐싱 또는 read model 고려

### 3. 최신 사용내역서 조회

`GET /usage-statements/latest`는 p99 `500ms`, max `1400ms`로 상세 조회와 비슷한 tail latency를 보인다. 내부적으로 latest statement를 찾은 뒤 상세 데이터를 구성한다면 상세 조회와 같은 병목을 공유할 가능성이 높다.

개선 후보:

- latest statement 조회 인덱스 확인: `(project_id, report_month desc, revision_no desc)` 또는 실제 정렬 조건 기준
- latest 조회 후 상세 구성 로직이 `GET /usage-statements/:id`와 같은지 확인
- 최신 ID 조회와 상세 데이터 조립을 분리해서 각각의 비용 측정

### 4. 파일 업로드와 항목 생성

`POST /files`, `POST /items`는 실패는 없지만 max가 1.5초대다.

가능한 원인:

- 파일 저장 I/O
- 트랜잭션 중 DB insert 및 후속 처리
- 동시 insert로 인한 커넥션 풀 대기
- 테스트 중 생성된 데이터 증가에 따른 인덱스/테이블 크기 증가

개선 후보:

- backend connection pool 사용률 확인
- DB slow query 로그 확인
- 파일 저장 경로 I/O 확인
- 테스트 후 누적 데이터가 커질수록 성능이 변하는지 별도 확인

## Evidence Link 관련 정리

현재 evidence link 실패는 비즈니스 충돌에 가깝다.

- `409`: 같은 item-file 조합을 중복 연결하거나, 이동 대상 item에 같은 file이 이미 연결된 경우
- `404`: Locust가 link id를 로컬에 들고 있었지만 서버에서는 이미 삭제된 경우

즉, 부하가 커져서 evidence link가 느려진 것이 아니라, 부하가 커지면서 중복/삭제/이동 경합이 더 자주 발생한 것이다. 이 상태는 실제 사용자 충돌 시나리오를 어느 정도 반영하지만, 일반 부하 테스트 성공/실패 판단에는 노이즈가 된다.

정리 방향:

- 일반 성능 측정에서는 예상 가능한 `404/409`를 `catch_response=True`로 success 처리
- 충돌률을 별도 지표로 남기고 싶으면 name을 분리
  - 예: `POST /items/:id/evidence-files [conflict]`
  - 예: `PATCH /evidence-file-links/:id [not-found]`
- 데드락/락 경합 테스트에서는 같은 link/file/item을 의도적으로 공유하는 별도 시나리오 작성

## 조회 문제와 데드락 관점

이번 테스트에서 조회 문제나 데드락으로 볼 증거는 없다.

- `GET` 계열 실패 0
- `exceptions.csv` 비어 있음
- 5xx 없음
- timeout 없음
- 실패는 전부 `404/409`

다만 이번 테스트는 일반 서비스 흐름 부하 테스트다. DB 데드락을 강하게 유도하려면 별도 경합 시나리오가 필요하다.

추가 검증 후보:

- 같은 item을 여러 사용자가 동시에 `PATCH`
- 같은 evidence link를 동시에 `PATCH`/`DELETE`
- 같은 file을 동시에 link/delete
- item 삭제와 evidence link 이동/삭제 동시 실행
- 조회 중 같은 statement에 item/link/file 변경 반복

이때는 Locust 결과뿐 아니라 PostgreSQL 로그와 lock 상태를 함께 봐야 한다.

## 다음 액션

1. 일반 부하 테스트용 locustfile 정리
   - evidence link의 예상 `404/409`를 실패에서 제외하거나 별도 이름으로 분리
   - 그래야 `make locust-save`가 성능 측정 목적에서 불필요하게 exit 1로 끝나지 않는다.

2. `GET /usage-statements/:id` 쿼리 분석
   - 요청 수 최다
   - 응답 크기 큼
   - p99와 max tail latency 존재
   - 실제 사용 흐름에서 가장 중요한 조회 API

3. `GET /usage-statements/latest` 분석
   - 상세 조회와 동일 병목을 공유하는지 확인
   - latest ID 조회와 상세 조립 비용 분리

4. 로그인 부하 분리
   - 인증 성능 테스트와 업무 API 성능 테스트를 분리
   - 업무 API 테스트에서는 사전 로그인 또는 낮은 로그인 비중 사용

5. DB 관측 추가
   - slow query log
   - `pg_stat_activity`
   - `pg_locks`
   - connection pool metrics
   - backend request log 또는 APM

6. 별도 경합/데드락 시나리오 작성
   - 일반 병목 후보를 먼저 파악한 뒤, 같은 row에 쓰기를 집중하는 테스트를 추가한다.

## 결론

`1000 users / 8m` 일반 서비스 흐름 테스트에서 서버는 약 `425 RPS` 수준을 안정적으로 처리했다. 조회 API 실패, 5xx, timeout, Locust 예외는 없었다.

현재 실패의 대부분은 evidence link의 중복 연결, 이미 삭제된 link 접근, 이동 충돌에서 발생한 정상적인 `404/409`다. 따라서 evidence link는 성능 병목이라기보다 테스트 시나리오의 충돌 처리 기준을 정리해야 하는 영역이다.

실제 병목 후보는 `GET /usage-statements/:id`, `GET /usage-statements/latest`, `[setup] login`이다. 특히 사용내역서 상세 조회는 요청 수가 가장 많고 응답 크기가 커서, 다음 최적화와 쿼리 분석의 1순위로 보는 것이 적절하다.
