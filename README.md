# Backend

Spring Boot 백엔드입니다. 코드는 환경에 독립적으로 두고, 실행 환경에서 DB/API/쿠키 설정을 주입합니다.

## 환경 설정 원칙

- 로컬 개발은 `.env` 또는 쉘 환경변수를 사용합니다. 실제 `.env`는 커밋하지 않습니다.
- Kubernetes 배포 manifest는 `SKALA-TEAM5/deploy` 레포의 `k8s/backend`에서 관리하고, 민감 값은 `team5-backend-secret`으로 주입합니다.
- `main`에 머지되면 운영 배포가 진행되므로, 통합 확인은 `develop`에서 먼저 진행합니다.

## 로컬 실행 예시

공용 Kubernetes DB를 로컬에서 사용하려면 먼저 port-forward를 엽니다.

```bash
kubectl port-forward svc/team5-postgres 5433:5432 -n skala3-finalproj-class2-team5
```

MinIO가 필요하면 별도 터미널에서 엽니다.

```bash
kubectl port-forward svc/team5-minio 9000:9000 -n skala3-finalproj-class2-team5
```

환경변수 예시는 `.env.example`을 참고합니다.

```bash
cp .env.example .env
```

Spring Boot는 `.env`를 자동으로 읽지 않으므로, 터미널에서 환경변수로 내보낸 뒤 실행합니다.

```bash
set -a
source .env
set +a
./gradlew bootRun
```

## 배포 설정

운영 Pod에서는 아래처럼 내부 Service 이름을 사용합니다.

```text
POSTGRES_HOST=team5-postgres
APP_MINIO_ENDPOINT=http://team5-minio:9000
APP_FASTAPI_BASE_URL=http://team5-fastapi:8001
```

비밀번호, JWT secret, MinIO key는 Kubernetes Secret으로 관리합니다.

Kubernetes manifest는 이 레포가 아니라 `SKALA-TEAM5/deploy` 레포에서 관리합니다.
backend workflow는 이미지를 빌드/푸시한 뒤 기존 `team5-backend` Deployment를 재시작합니다.

## Prometheus 지표

Spring Actuator의 `/actuator/prometheus`에서 HTTP, JVM, DB connection pool 지표와 함께
FastAPI Agent 비동기 dispatch 지표를 제공합니다.

- `backend_agent_dispatch_total{operation,result}`
- `backend_agent_dispatch_duration_seconds{operation}`
- `backend_agent_dispatch_in_progress{operation}`
- `backend_agent_todo_refresh_total{result="fail"}`

`operation`은 `validate`, `legal`, `report` 중 하나입니다. 프로젝트 ID, 사용자 ID,
사용내역서 ID 같은 고카디널리티 값은 Prometheus 라벨에 포함하지 않습니다.
