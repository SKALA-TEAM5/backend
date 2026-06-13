#!/usr/bin/env bash
# =============================================================
# load-test/teardown.sh
# 부하 테스트 데이터 전체 정리 (DB + MinIO)
#
# 실행:
#   cd <프로젝트루트>
#   bash backend/load-test/teardown.sh [옵션]
#
# 옵션 (환경변수로 override 가능):
#   DB_CONTAINER   Docker 컨테이너명  (설정 시 docker exec 사용)
#   K8S_NAMESPACE  k8s 네임스페이스  (설정 시 kubectl exec 사용)
#   PGHOST         PostgreSQL 호스트  (기본: localhost, 위 둘 미설정 시 사용)
#   PGPORT         PostgreSQL 포트   (기본: 5432,      위 둘 미설정 시 사용)
#   PGDATABASE     DB 이름           (기본: safety)
#   PGUSER         DB 사용자         (기본: safety_user)
#   PGPASSWORD     DB 비밀번호       (로컬 psql 사용 시)
#   MINIO_ALIAS    mc alias          (기본: minio)
#   MINIO_BUCKET   버킷 이름         (기본: safety-files)
#   SKIP_MINIO     MinIO 삭제 건너뜀 (기본: false)
# =============================================================
set -euo pipefail

DB_CONTAINER="${DB_CONTAINER:-}"
K8S_NAMESPACE="${K8S_NAMESPACE:-}"
PGHOST="${PGHOST:-localhost}"
PGPORT="${PGPORT:-5432}"
PGDATABASE="${PGDATABASE:-safety}"
PGUSER="${PGUSER:-safety_user}"
MINIO_ALIAS="${MINIO_ALIAS:-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-safety-files}"
SKIP_MINIO="${SKIP_MINIO:-false}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TEARDOWN_SQL="$SCRIPT_DIR/teardown.sql"

# psql 실행 래퍼 — 우선순위: docker exec > kubectl exec > 로컬 psql
run_psql() {
  if [ -n "$DB_CONTAINER" ]; then
    docker exec -i "$DB_CONTAINER" psql -U "$PGUSER" -d "$PGDATABASE" "$@"
  elif [ -n "$K8S_NAMESPACE" ]; then
    local pod
    pod=$(kubectl get pods -n "$K8S_NAMESPACE" -l app=team5-postgres \
      -o jsonpath='{.items[0].metadata.name}' 2>/dev/null)
    kubectl exec -n "$K8S_NAMESPACE" -i "$pod" -- psql -U "$PGUSER" -d "$PGDATABASE" "$@"
  else
    PGPASSWORD="${PGPASSWORD:-}" psql \
      -h "$PGHOST" -p "$PGPORT" -d "$PGDATABASE" -U "$PGUSER" "$@"
  fi
}

echo "=== [1/3] LOAD-CN-* 프로젝트 ID 수집 ==="

PROJECT_IDS=$(run_psql -t -A -c "SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%';")

if [ -z "$PROJECT_IDS" ]; then
  echo "  삭제할 LOAD 프로젝트 없음 — MinIO 단계 건너뜀"
  SKIP_MINIO=true
else
  echo "  대상 프로젝트 수: $(echo "$PROJECT_IDS" | wc -l | tr -d ' ')"
fi

echo ""
echo "=== [2/3] MinIO 오브젝트 삭제 ==="

if [ "$SKIP_MINIO" = "true" ]; then
  echo "  SKIP_MINIO=true — 건너뜀"
elif ! command -v mc &>/dev/null; then
  echo "  [경고] mc(MinIO Client) 미설치 — MinIO 오브젝트는 수동 정리 필요"
  echo "  설치: https://min.io/docs/minio/linux/reference/minio-mc.html"
  echo "  수동 삭제 명령 (mc 설치 후):"
  for pid in $PROJECT_IDS; do
    echo "    mc rm --recursive --force ${MINIO_ALIAS}/${MINIO_BUCKET}/projects/${pid}/"
  done
else
  if ! mc alias ls "$MINIO_ALIAS" &>/dev/null; then
    echo "  [경고] mc alias '${MINIO_ALIAS}' 미설정 — MinIO 오브젝트는 수동 정리 필요"
    echo "  설정 예시: mc alias set ${MINIO_ALIAS} http://localhost:9000 minioadmin minioadmin"
  else
    DELETED=0
    for pid in $PROJECT_IDS; do
      PREFIX="${MINIO_ALIAS}/${MINIO_BUCKET}/projects/${pid}/"
      COUNT=$(mc ls --recursive "$PREFIX" 2>/dev/null | wc -l | tr -d ' ')
      if [ "$COUNT" -gt 0 ]; then
        mc rm --recursive --force "$PREFIX" 2>/dev/null && DELETED=$((DELETED + COUNT))
        echo "  projects/${pid}/ — ${COUNT}개 삭제"
      fi
    done
    echo "  MinIO 오브젝트 총 ${DELETED}개 삭제 완료"
  fi
fi

echo ""
echo "=== [3/3] DB 레코드 삭제 ==="

run_psql -f - < "$TEARDOWN_SQL"

echo ""
echo "=== 정리 완료 ==="
