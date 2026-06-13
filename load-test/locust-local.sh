#!/usr/bin/env bash
# ============================================================
# load-test/locust-local.sh
# 로컬 Docker 환경 Locust 테스트 헬퍼
#
# 사전 조건:
#   - Docker (safety_db 컨테이너 실행 중)
#   - mc 설치 + minio alias 설정
#     brew install minio/stable/mc
#     mc alias set minio http://localhost:9000 minioadmin minioadmin
#   - locust 설치: pip install locust
#
# 사용법:
#   bash load-test/locust-local.sh <command>
#
# Commands:
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
LOCUST_HOST="${LOCUST_HOST:-http://localhost:8000}"  # 백엔드 주소
LOCUST_USERS="${LOCUST_USERS:-20}"                   # 동시 사용자 수
LOCUST_RATE="${LOCUST_RATE:-5}"                      # 초당 사용자 증가 수 (ramp-up)
LOCUST_TIME="${LOCUST_TIME:-1m}"                     # 테스트 실행 시간 (예: 30s, 5m)
LOCUST_RESULT="${LOCUST_RESULT:-local_$(date +%Y%m%d_%H%M)}"  # 결과 CSV 파일명
LOCUST_MODE="${LOCUST_MODE:-atomic}"                 # atomic | journey | mixed
# ================================================================

DB_CONTAINER="${DB_CONTAINER:-safety_db}"
POSTGRES_USER="${POSTGRES_USER:-safety_user}"
POSTGRES_DB="${POSTGRES_DB:-safety}"
MINIO_ALIAS="${MINIO_ALIAS:-minio}"
MINIO_BUCKET="${MINIO_BUCKET:-safety-files}"

# 시나리오 모드별 클래스 결정
case "$LOCUST_MODE" in
  atomic)  LOCUST_CLASSES="AdminScenario UserScenario" ;;
  journey) LOCUST_CLASSES="WriterJourney ReviewerJourney BrowsingLoad" ;;
  mixed)   LOCUST_CLASSES="" ;;
  *)       echo "[오류] LOCUST_MODE=$LOCUST_MODE — atomic|journey|mixed 중 선택" >&2; exit 1 ;;
esac

CMD="${1:-help}"

# ── 사전 조건 확인 ──────────────────────────────────────────

check_prereqs() {
  local ok=true

  if ! docker inspect "$DB_CONTAINER" --format '{{.State.Running}}' 2>/dev/null | grep -q true; then
    echo "[오류] Docker 컨테이너 '${DB_CONTAINER}'가 실행 중이 아닙니다."
    echo "       docker compose up -d db"
    ok=false
  fi

  if ! command -v mc &>/dev/null; then
    echo "[오류] mc(MinIO Client)가 설치되어 있지 않습니다."
    echo "       brew install minio/stable/mc"
    ok=false
  elif ! mc alias ls "$MINIO_ALIAS" &>/dev/null; then
    echo "[오류] mc alias '${MINIO_ALIAS}'가 설정되어 있지 않습니다."
    echo "       mc alias set ${MINIO_ALIAS} http://localhost:9000 minioadmin minioadmin"
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
  docker exec -i "$DB_CONTAINER" psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" "$@"
}

# ── Commands ────────────────────────────────────────────────

do_seed() {
  check_prereqs
  echo "=== 테스트 데이터 시딩 ==="
  run_psql -f - < "$SCRIPT_DIR/seed.sql"
  echo "=== 시딩 완료 ==="
  do_check
}

do_up() {
  check_prereqs
  echo "=== Locust 웹UI 시작 → http://localhost:8089 (mode=$LOCUST_MODE) ==="
  LOAD_TEST_ENV=docker DB_CONTAINER="$DB_CONTAINER" \
  POSTGRES_USER="$POSTGRES_USER" POSTGRES_DB="$POSTGRES_DB" \
  locust -f "$SCRIPT_DIR/locustfile.py" --host="$LOCUST_HOST" $LOCUST_CLASSES
}

do_save() {
  check_prereqs
  mkdir -p "$SCRIPT_DIR/results"
  echo "=== 헤드리스 테스트: mode=$LOCUST_MODE, users=${LOCUST_USERS}, rate=${LOCUST_RATE}/s, time=${LOCUST_TIME} ==="
  LOAD_TEST_ENV=docker DB_CONTAINER="$DB_CONTAINER" \
  POSTGRES_USER="$POSTGRES_USER" POSTGRES_DB="$POSTGRES_DB" \
  locust -f "$SCRIPT_DIR/locustfile.py" \
    --host="$LOCUST_HOST" \
    --csv="$SCRIPT_DIR/results/${LOCUST_RESULT}" \
    --headless -u "$LOCUST_USERS" -r "$LOCUST_RATE" -t "$LOCUST_TIME" \
    $LOCUST_CLASSES
  echo "=== 결과 저장: results/${LOCUST_RESULT}_*.csv ==="
}

do_spike() {
  check_prereqs
  mkdir -p "$SCRIPT_DIR/results"
  # Spike는 Journey 모드 강제 (LoadTestShape이 사용자수/시간을 제어)
  echo "=== Spike 테스트: shape_spike.py LoadTestShape ==="
  echo "    baseline=${SPIKE_BASE_USERS:-100}u → peak=${SPIKE_PEAK_USERS:-800}u → drain"
  LOAD_TEST_ENV=docker DB_CONTAINER="$DB_CONTAINER" \
  POSTGRES_USER="$POSTGRES_USER" POSTGRES_DB="$POSTGRES_DB" \
  locust -f "$SCRIPT_DIR/shape_spike.py" -f "$SCRIPT_DIR/locustfile.py" \
    --host="$LOCUST_HOST" \
    --csv="$SCRIPT_DIR/results/${LOCUST_RESULT}" \
    --headless \
    WriterJourney ReviewerJourney BrowsingLoad
  echo "=== 결과 저장: results/${LOCUST_RESULT}_*.csv ==="
}

do_soak() {
  check_prereqs
  mkdir -p "$SCRIPT_DIR/results"
  # Soak는 장시간 sustained 부하 (기본 1h, 200u)
  local soak_time="${LOCUST_TIME:-1h}"
  local soak_users="${LOCUST_USERS:-200}"
  local soak_rate="${LOCUST_RATE:-5}"
  echo "=== Soak 테스트: mode=$LOCUST_MODE, users=${soak_users}, time=${soak_time} ==="
  LOAD_TEST_ENV=docker DB_CONTAINER="$DB_CONTAINER" \
  POSTGRES_USER="$POSTGRES_USER" POSTGRES_DB="$POSTGRES_DB" \
  locust -f "$SCRIPT_DIR/locustfile.py" \
    --host="$LOCUST_HOST" \
    --csv="$SCRIPT_DIR/results/${LOCUST_RESULT}" \
    --headless -u "$soak_users" -r "$soak_rate" -t "$soak_time" \
    $LOCUST_CLASSES
  echo "=== 결과 저장: results/${LOCUST_RESULT}_*.csv ==="
}

do_verify() {
  check_prereqs
  echo "=== 데이터 정합성 검증 ==="
  run_psql -f - < "$SCRIPT_DIR/verify.sql" | tee "$SCRIPT_DIR/results/${LOCUST_RESULT}_verify.txt"
  echo "=== 검증 결과: results/${LOCUST_RESULT}_verify.txt ==="
}

do_teardown() {
  check_prereqs
  DB_CONTAINER="$DB_CONTAINER" \
  PGUSER="$POSTGRES_USER" PGDATABASE="$POSTGRES_DB" \
  MINIO_ALIAS="$MINIO_ALIAS" MINIO_BUCKET="$MINIO_BUCKET" \
  bash "$SCRIPT_DIR/teardown.sh"
  do_check
}

do_run() {
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
  seed)     do_seed     ;;
  up)       do_up       ;;
  save)     do_save     ;;
  spike)    do_spike    ;;
  soak)     do_soak     ;;
  verify)   do_verify   ;;
  teardown) do_teardown ;;
  check)    check_prereqs && do_check ;;
  help|*)
    echo "사용법: bash load-test/locust-local.sh <command>"
    echo ""
    echo "Commands:"
    echo "  run       자동 파이프라인 (seed → test → teardown)"
    echo "  seed      DB에 테스트 데이터 시딩"
    echo "  up        Locust 웹UI 실행 (localhost:8089)"
    echo "  save      헤드리스 실행 + CSV 저장 (-u/-r/-t 사용)"
    echo "  spike    LoadTestShape 기반 spike 부하 (Journey 모드 강제)"
    echo "  soak      장시간 sustained 부하 (기본 1h, 200u)"
    echo "  verify    DB 정합성 검증 (verify.sql 실행)"
    echo "  teardown  DB + MinIO 테스트 데이터 전체 삭제"
    echo "  check     현재 LOAD-* 데이터 현황 확인"
    echo ""
    echo "환경변수 (선택):"
    echo "  LOCUST_HOST    백엔드 주소     (기본: http://localhost:8000)"
    echo "  LOCUST_MODE    시나리오 모드   (atomic|journey|mixed, 기본: atomic)"
    echo "  LOCUST_RESULT  결과 파일명     (기본: local_<날짜시각>)"
    echo "  LOCUST_USERS   동시 사용자 수  (save 전용, 기본: 20)"
    echo "  LOCUST_RATE    초당 증가 수    (save 전용, 기본: 5)"
    echo "  LOCUST_TIME    실행 시간       (save·soak 전용, 기본: 1m)"
    echo ""
    echo "Spike 환경변수:"
    echo "  SPIKE_BASE_USERS / SPIKE_PEAK_USERS / SPIKE_BASE_SEC / SPIKE_RAMP_SEC / SPIKE_PEAK_SEC / SPIKE_DRAIN_SEC"
    ;;
esac
