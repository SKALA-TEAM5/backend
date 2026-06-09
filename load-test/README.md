# load-test

Locust 기반 백엔드 API 부하 테스트.

## 전제조건

```bash
pip install locust
```

## 테스트 데이터 시딩 (최초 1회)

```bash
make locust-seed
```

`load-test/seed.sql`을 실행해 아래 데이터를 생성한다.

| 항목 | 수량 |
|---|---|
| 테스트 admin 계정 (`LOAD-ADMIN-001` ~ `030`) | 30명 |
| 테스트 user 계정 (`LOAD-USER-001` ~ `030`) | 30명 |
| 테스트 프로젝트 (`LOAD-CN-001` ~ `030`) | 30개 |
| 사용내역서 (프로젝트당 1개, `draft`) | 30개 |

비밀번호는 모든 계정 공통: `P@ssw0rd123!`

## 시나리오

AI 호출 API(`/agents/parse`, `/agents/validate`, `/agents/legal`, `/agents/report`)는 제외한다.

| 시나리오 | 비중 | 주요 흐름 |
|---|---|---|
| `AdminScenario` | 30% | 프로젝트 조회·수정, 사용내역서 조회, 검토완료·보완요청, 대시보드, 아카이브, agent 로그·경고 조회 |
| `UserScenario` | 70% | 항목 CRUD·카테고리 이동, 파일 업로드·삭제, 증빙 연결·이동·삭제, agent 읽기(button-states/todos/logs/warnings), 사용내역서 제출 |

## 실행

### 웹 UI (http://localhost:8089)

```bash
make locust-up
```

### 헤드리스 (CSV 저장)

```bash
make locust-save LOCUST_RESULT=<이름> LOCUST_USERS=<동시접속수> LOCUST_RATE=<초당증가> LOCUST_TIME=<시간>
```

예시:

```bash
# 로컬 서버, 100명, 10명/s 증가, 5분
make locust-save LOCUST_RESULT=baseline LOCUST_USERS=100 LOCUST_RATE=10 LOCUST_TIME=5m

# 개발 서버 직접 지정
locust -f load-test/locustfile.py \
  --host=http://<개발서버주소>:8000 \
  --csv=load-test/results/dev_baseline \
  --headless -u 100 -r 10 -t 5m
```

## 결과

`results/` 에 CSV 4종이 저장된다.

| 파일 | 내용 |
|---|---|
| `*_stats.csv` | 엔드포인트별 응답시간·RPS 요약 |
| `*_stats_history.csv` | 시간 경과에 따른 RPS·응답시간 추이 |
| `*_failures.csv` | 실패 요청 목록 |
| `*_exceptions.csv` | 예외 스택트레이스 |

CSV는 `.gitignore` 처리되어 있다. 분석 메모(`.md`)는 커밋 가능.

## 정상 실패 처리

상태 전이 API는 조건 불충족 시 4xx를 반환하는데, 이는 예상된 동작이므로 성공으로 처리한다.

| API | 허용 코드 |
|---|---|
| `PATCH /usage-statements/:id/submit` | 200, 400, 409, 422 |
| `PATCH /usage-statements/:id/complete-review` | 200, 400, 409, 422 |
| `PATCH /usage-statements/:id/request-supplement` | 200, 400, 409, 422 |
