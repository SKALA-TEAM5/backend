#!/usr/bin/env bash
# ============================================================
# load-test/locust-k8s.sh
# k8s 포트포워딩 환경 Locust 테스트 헬퍼
#
# 사전 조건:
#   - kubectl (k8s 클러스터 접속 가능)
#   - bash port-forward-all.sh 실행 중 (별도 터미널)
#   - mc 설치: brew install minio/stable/mc
#   - locust 설치: pip install locust
#
# 최초 1회 설정:
#   bash load-test/locust-k8s.sh setup
#
# 이후 사용법:
#   bash load-test/locust-k8s.sh <command>
#
# Commands:
#   setup     mc alias 및 연결 확인 (최초 1회)
#   seed      DB에 테스트 데이터 시딩
#   up        Locust 웹UI 실행 (localhost:8089)
#   save      헤드리스 실행 + CSV 저장
#   teardown  DB + MinIO 테스트 데이터 전체 삭제
#   check     현재 LOAD-* 데이터 현황 확인
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# ================================================================
#  테스트 설정 — 여기서 값을 바꾸거나 환경변수로 override 가능
# ================================================================
LOCUST_HOST="${LOCUST_HOST:-http://localhost:8000}"  # 백엔드 주소 (포트포워딩 기준)
LOCUST_USERS="${LOCUST_USERS:-20}"                   # 동시 사용자 수
LOCUST_RATE="${LOCUST_RATE:-5}"                      # 초당 사용자 증가 수 (ramp-up)
LOCUST_TIME="${LOCUST_TIME:-1m}"                     # 테스트 실행 시간 (예: 30s, 5m)
LOCUST_RESULT="${LOCUST_RESULT:-k8s_$(date +%Y%m%d_%H%M)}"  # 결과 CSV 파일명
# ================================================================

K8S_NAMESPACE="${K8S_NAMESPACE:-skala3-finalproj-class2-team5}"
POSTGRES_USER="${POSTGRES_USER:-safety_user}"
POSTGRES_DB="${POSTGRES_DB:-safety}"
MINIO_ALIAS="${MINIO_ALIAS:-k8s-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-safety-files}"

CMD="${1:-help}"

# ── 포드 조회 ───────────────────────────────────────────────

get_postgres_pod() {
  kubectl get pods -n "$K8S_NAMESPACE" -l app=team5-postgres \
    -o jsonpath='{.items[0].metadata.name}' 2>/dev/null
}

# ── 사전 조건 확인 ──────────────────────────────────────────

check_prereqs() {
  local ok=true

  if ! command -v kubectl &>/dev/null; then
    echo "[오류] kubectl이 설치되어 있지 않습니다."
    ok=false
  elif ! kubectl cluster-info &>/dev/null; then
    echo "[오류] k8s 클러스터에 연결할 수 없습니다. kube context를 확인하세요."
    ok=false
  fi

  if ! curl -sf http://localhost:8000/categories >/dev/null 2>&1; then
    echo "[오류] localhost:8000에 접근할 수 없습니다."
    echo "       별도 터미널에서 먼저 실행: bash port-forward-all.sh"
    ok=false
  fi

  if ! command -v mc &>/dev/null; then
    echo "[오류] mc(MinIO Client)가 설치되어 있지 않습니다."
    echo "       brew install minio/stable/mc"
    ok=false
  elif ! mc alias ls "$MINIO_ALIAS" &>/dev/null; then
    echo "[오류] mc alias '${MINIO_ALIAS}'가 설정되어 있지 않습니다."
    echo "       bash load-test/locust-k8s.sh setup"
    ok=false
  fi

  if ! command -v locust &>/dev/null; then
    echo "[오류] locust가 설치되어 있지 않습니다."
    echo "       pip install locust"
    ok=false
  fi

  [ "$ok" = true ]
}

# ── DB 헬퍼 ─────────────────────────────────────────────────

run_psql() {
  local pod
  pod=$(get_postgres_pod)
  if [ -z "$pod" ]; then
    echo "[오류] PostgreSQL 포드를 찾을 수 없습니다." >&2
    exit 1
  fi
  kubectl exec -n "$K8S_NAMESPACE" -i "$pod" -- psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"
}

# ── Commands ────────────────────────────────────────────────

do_setup() {
  echo "=== k8s 환경 설정 (최초 1회) ==="

  if ! command -v kubectl &>/dev/null; then
    echo "[오류] kubectl이 필요합니다."
    exit 1
  fi
  if ! command -v mc &>/dev/null; then
    echo "[오류] mc가 필요합니다: brew install minio/stable/mc"
    exit 1
  fi

  # MinIO 크레덴셜 — k8s secret 자동 탐색
  echo "  MinIO 크레덴셜 조회 중..."
  local secret_name minio_user minio_password
  secret_name=$(kubectl get secret -n "$K8S_NAMESPACE" \
    -o jsonpath='{.items[*].metadata.name}' 2>/dev/null \
    | tr ' ' '\n' | grep -i minio | head -1 || true)

  if [ -n "$secret_name" ]; then
    minio_user=$(kubectl get secret "$secret_name" -n "$K8S_NAMESPACE" \
      -o jsonpath='{.data.MINIO_ROOT_USER}' 2>/dev/null | base64 -d 2>/dev/null || true)
    minio_password=$(kubectl get secret "$secret_name" -n "$K8S_NAMESPACE" \
      -o jsonpath='{.data.MINIO_ROOT_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || true)
  fi

  if [ -z "${minio_user:-}" ] || [ -z "${minio_password:-}" ]; then
    echo "  secret 자동 추출 실패 — 직접 입력하세요."
    read -rp "  MinIO Access Key: " minio_user
    read -rsp "  MinIO Secret Key: " minio_password
    echo ""
  else
    echo "  secret '${secret_name}'에서 크레덴셜 추출 성공"
  fi

  mc alias set "$MINIO_ALIAS" http://localhost:9000 "$minio_user" "$minio_password"
  echo "  mc alias '${MINIO_ALIAS}' → http://localhost:9000 설정 완료"

  echo ""
  echo "=== 연결 확인 ==="
  local pod
  pod=$(get_postgres_pod)
  if [ -n "$pod" ]; then
    echo "  PostgreSQL 포드: ${pod} ✓"
  else
    echo "  [경고] PostgreSQL 포드를 찾을 수 없습니다. port-forward-all.sh 실행 여부 확인"
  fi

  if mc ls "${MINIO_ALIAS}/${MINIO_BUCKET}" &>/dev/null; then
    echo "  MinIO 버킷: ${MINIO_BUCKET} ✓"
  else
    echo "  [경고] MinIO 버킷 접근 실패. port-forward-all.sh 실행 여부 확인"
  fi

  echo ""
  echo "설정 완료 — 이제 seed부터 시작하세요:"
  echo "  bash load-test/locust-k8s.sh seed"
}

do_seed() {
  check_prereqs
  echo "=== 테스트 데이터 시딩 ==="
  run_psql -f - < "$SCRIPT_DIR/seed.sql"
  echo "=== 시딩 완료 ==="
  do_check
}

do_up() {
  check_prereqs
  echo "=== Locust 웹UI 시작 → http://localhost:8089 ==="
  locust -f "$SCRIPT_DIR/locustfile.py" --host="$LOCUST_HOST"
}

do_save() {
  check_prereqs
  mkdir -p "$SCRIPT_DIR/results"
  echo "=== 헤드리스 테스트: users=${LOCUST_USERS}, rate=${LOCUST_RATE}/s, time=${LOCUST_TIME} ==="
  locust -f "$SCRIPT_DIR/locustfile.py" \
    --host="$LOCUST_HOST" \
    --csv="$SCRIPT_DIR/results/${LOCUST_RESULT}" \
    --headless -u "$LOCUST_USERS" -r "$LOCUST_RATE" -t "$LOCUST_TIME"
  echo "=== 결과 저장: results/${LOCUST_RESULT}_*.csv ==="
}

do_teardown() {
  check_prereqs
  K8S_NAMESPACE="$K8S_NAMESPACE" \
  PGUSER="$POSTGRES_USER" PGDATABASE="$POSTGRES_DB" \
  MINIO_ALIAS="$MINIO_ALIAS" MINIO_BUCKET="$MINIO_BUCKET" \
  bash "$SCRIPT_DIR/teardown.sh"
  do_check
}

do_run() {
  # setup은 mc alias가 없을 때만 자동 실행
  if ! mc alias ls "$MINIO_ALIAS" &>/dev/null; then
    echo "=== mc alias 미설정 — setup 먼저 실행합니다 ==="
    do_setup
    echo ""
  fi
  check_prereqs
  echo "================================================================"
  echo " Locust 자동 파이프라인: seed → test → teardown"
  echo " users=${LOCUST_USERS}, rate=${LOCUST_RATE}/s, time=${LOCUST_TIME}"
  echo "================================================================"
  echo ""
  do_seed
  echo ""
  do_save
  echo ""
  do_teardown
}

do_check() {
  echo "=== LOAD-* 데이터 현황 ==="
  run_psql -t -A -c "
    SELECT 'users'     AS type, count(*) FROM service.users    WHERE employee_no LIKE 'LOAD-%'
    UNION ALL
    SELECT 'projects'  AS type, count(*) FROM service.projects WHERE contract_no  LIKE 'LOAD-CN-%'
    UNION ALL
    SELECT 'files(DB)' AS type, count(*) FROM service.files    WHERE project_id IN (
      SELECT id FROM service.projects WHERE contract_no LIKE 'LOAD-CN-%');" \
  | column -t -s $'\t'
  echo ""
  printf "    MinIO 오브젝트 수: "
  mc ls --recursive "${MINIO_ALIAS}/${MINIO_BUCKET}" 2>/dev/null | wc -l | tr -d ' '
}

# ── 라우팅 ──────────────────────────────────────────────────

case "$CMD" in
  run)      do_run      ;;
  setup)    do_setup    ;;
  seed)     do_seed     ;;
  up)       do_up       ;;
  save)     do_save     ;;
  teardown) do_teardown ;;
  check)    check_prereqs && do_check ;;
  help|*)
    echo "사용법: bash load-test/locust-k8s.sh <command>"
    echo ""
    echo "Commands:"
    echo "  run       자동 파이프라인 (setup 필요시 → seed → test → teardown)"
    echo "  setup     mc alias 및 연결 확인 (최초 1회)"
    echo "  seed      DB에 테스트 데이터 시딩"
    echo "  up        Locust 웹UI 실행 (localhost:8089)"
    echo "  save      헤드리스 실행 + CSV 저장"
    echo "  teardown  DB + MinIO 테스트 데이터 전체 삭제"
    echo "  check     현재 LOAD-* 데이터 현황 확인"
    echo ""
    echo "환경변수 (선택):"
    echo "  LOCUST_HOST    백엔드 주소     (기본: http://localhost:8000)"
    echo "  LOCUST_RESULT  결과 파일명     (save 전용, 기본: result)"
    echo "  LOCUST_USERS   동시 사용자 수  (save 전용, 기본: 20)"
    echo "  LOCUST_RATE    초당 증가 수    (save 전용, 기본: 5)"
    echo "  LOCUST_TIME    실행 시간       (save 전용, 기본: 1m)"
    ;;
esac
