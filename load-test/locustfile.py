"""
실제 서비스 흐름 기반 부하 테스트

전제조건:
    bash load-test/locust-local.sh seed   # 시드 — admin·user 각 500명, 프로젝트 2,000개

시나리오 모드 (셸 스크립트에서 명령행 클래스 인자로 선택):
    Atomic   — 기존 endpoint 부하 측정용 (smoke·baseline)
        AdminScenario  — 검토자 시점 (목록·dashboard·archive·검토완료·보완요청)
        UserScenario   — 작성자 시점 (항목 CRUD·파일·증빙 연결·제출)
    Journey  — 실제 비즈니스 여정 측정용 (peak·stress·soak)
        WriterJourney    — 1세션 = login → detail → upload×N → link×N → submit
        ReviewerJourney  — 1세션 = 목록 → detail → todos → confirm → complete-review/supplement
        BrowsingLoad     — read-only 브라우징

직접 실행 예:
    # Atomic 모드 (기본)
    locust -f locustfile.py --host=http://localhost:8000 AdminScenario UserScenario

    # Journey 모드
    locust -f locustfile.py --host=http://localhost:8000 \
        WriterJourney ReviewerJourney BrowsingLoad

    # Spike (LoadTestShape)
    locust -f shape_spike.py -f locustfile.py --host=http://localhost:8000 \
        WriterJourney ReviewerJourney BrowsingLoad

AI 호출 API 제외 (FastAPI 의존):
    POST /agents/{parse,validate,legal,report}, POST /items, /law-changes/recent
"""

from locust import HttpUser, task, between
import os
import random
import io
import uuid
import subprocess

PASSWORD = "P@ssw0rd123!"

CATEGORY_CODES = [f"CAT_0{n}" for n in range(1, 10)]
UPLOAD_EVIDENCE_TYPES = ["tax_invoice", "receipt", "site_photo", "work_log"]
LINK_EVIDENCE_TYPES = [
    "tax_invoice", "receipt", "site_photo", "work_log",
    "pay_stub", "item_photo", "wearing_photo",
]


def _psql_command():
    env = os.environ.get("LOAD_TEST_ENV", "").lower()
    k8s_ns = os.environ.get("K8S_NAMESPACE", "")
    db_container = os.environ.get("DB_CONTAINER", "safety_db")
    pg_user = os.environ.get("POSTGRES_USER", "safety_user")
    pg_db = os.environ.get("POSTGRES_DB", "safety")

    if env == "k8s" or (env == "" and k8s_ns):
        try:
            pod = subprocess.run(
                ["kubectl", "get", "pods", "-n", k8s_ns,
                 "-l", "app=team5-postgres",
                 "-o", "jsonpath={.items[0].metadata.name}"],
                capture_output=True, text=True, timeout=10
            ).stdout.strip()
        except Exception as e:
            raise RuntimeError(f"k8s postgres pod 조회 실패: {e}")
        if not pod:
            raise RuntimeError("k8s postgres pod를 찾을 수 없습니다")
        print(f"[pool] mode=k8s, ns={k8s_ns}, pod={pod}")
        return ["kubectl", "exec", "-n", k8s_ns, "-i", pod,
                "--", "psql", "-U", pg_user, "-d", pg_db]

    if env == "psql":
        print(f"[pool] mode=local psql")
        return ["psql", "-U", pg_user, "-d", pg_db]

    print(f"[pool] mode=docker, container={db_container}")
    return ["docker", "exec", db_container,
            "psql", "-U", pg_user, "-d", pg_db]


def _build_pools():
    cmd = _psql_command() + [
        "-t", "-A", "-F", "\t",
        "-c", "SELECT employee_no, role_code FROM service.users "
              "WHERE employee_no LIKE 'LOAD-%' "
              "AND password_hash = ("
              "  SELECT password_hash FROM service.users "
              "  WHERE employee_no LIKE 'LOAD-ADMIN-%' LIMIT 1"
              ") ORDER BY employee_no",
    ]
    try:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=15)
        if result.returncode != 0:
            raise RuntimeError(f"psql exit={result.returncode}: {result.stderr.strip()}")
        admins, users = [], []
        for line in result.stdout.strip().splitlines():
            parts = line.split("\t")
            if len(parts) != 2:
                continue
            emp_no, role = parts[0].strip(), parts[1].strip()
            entry = {"employeeNo": emp_no, "password": PASSWORD}
            if role == "admin":
                admins.append(entry)
            elif role == "user":
                users.append(entry)
        if admins and users:
            print(f"[pool] admin {len(admins)}명 / user {len(users)}명 로드됨")
            return admins, users
        raise RuntimeError("DB 조회 결과가 비어있음 — seed 미실행 의심")
    except Exception as e:
        if os.environ.get("LOAD_POOL_FALLBACK") == "1":
            print(f"[pool] DB 조회 실패, fallback 사용 (LOAD_POOL_FALLBACK=1): {e}")
            return (
                [{"employeeNo": f"LOAD-ADMIN-{n:03d}", "password": PASSWORD} for n in range(1, 31)],
                [{"employeeNo": f"LOAD-USER-{n:03d}",  "password": PASSWORD} for n in range(1, 31)],
            )
        raise RuntimeError(
            f"[pool] 계정 풀 로드 실패: {e}\n"
            "확인: (1) seed 실행 여부 (2) 환경변수 K8S_NAMESPACE/DB_CONTAINER 설정\n"
            "강제 fallback이 필요하면 LOAD_POOL_FALLBACK=1 환경변수 설정"
        )


ADMIN_POOL, USER_POOL = _build_pools()


# ── Helpers ───────────────────────────────────────────────────────

def dummy_file():
    return io.BytesIO(b"load test file " + uuid.uuid4().bytes)


def apply_auth_cookies(client, resp):
    for cookie in resp.cookies:
        client.cookies[cookie.name] = cookie.value


def login(client, pool, name="[setup] login"):
    """공통 로그인. 성공 시 True 반환."""
    resp = client.post("/auth/login", json=random.choice(pool), name=name)
    if resp.status_code != 200:
        return False
    apply_auth_cookies(client, resp)
    return True


def load_assigned_project_id(client, prefix_filter=True):
    """배정된 LOAD-CN-* 프로젝트 중 하나 선택. 없으면 None."""
    resp = client.get("/projects", name="[setup] projects")
    if resp.status_code != 200:
        return None
    items = resp.json().get("data", {}).get("items", [])
    if prefix_filter:
        items = [p for p in items if p.get("contractNo", "").startswith("LOAD-CN-")]
    if not items:
        return None
    return random.choice(items)["id"]


def load_first_statement_id(client, project_id):
    resp = client.get(
        f"/projects/{project_id}/usage-statements",
        name="[setup] statements"
    )
    if resp.status_code != 200:
        return None
    items = resp.json().get("data", {}).get("items", [])
    if not items:
        return None
    return random.choice(items)["id"]


def load_item_ids(client, project_id, statement_id):
    resp = client.get(
        f"/projects/{project_id}/usage-statements/{statement_id}",
        name="[setup] statement detail"
    )
    if resp.status_code != 200:
        return []
    seed_items = resp.json().get("data", {}).get("statement", {}).get("items", [])
    return [i["itemId"] for i in seed_items]


# ============================================================
# Atomic 시나리오 — 기존 endpoint 부하 측정용 (smoke·baseline)
# read 70% / write 30% 분포 + 현실적 wait_time
# ============================================================

class AdminScenario(HttpUser):
    """ADMIN 역할 — 검토자 시점. read-heavy."""
    weight = 3
    wait_time = between(3, 10)

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        if not login(self.client, ADMIN_POOL):
            return
        self.project_id = load_assigned_project_id(self.client)
        if self.project_id:
            self.statement_id = load_first_statement_id(self.client, self.project_id)

    # ── 읽기 (high weight) ───────────────────────────────────────

    @task(8)
    def list_projects(self):
        self.client.get("/projects", name="GET /projects")

    @task(3)
    def list_projects_scope_all(self):
        self.client.get("/projects?scope=all", name="GET /projects?scope=all")

    @task(3)
    def list_projects_with_filter(self):
        keyword = random.choice(["부하", "테스트", "프로젝트", "안전", "관리"])
        self.client.get(f"/projects?keyword={keyword}", name="GET /projects?keyword=")

    @task(2)
    def list_assignee_candidates(self):
        keyword = random.choice(["부하", "테스트", "관리자", "사용자"])
        self.client.get(
            f"/projects/assignee-candidates?keyword={keyword}",
            name="GET /projects/assignee-candidates"
        )

    @task(2)
    def list_users(self):
        self.client.get("/users", name="GET /users")

    @task(4)
    def get_project_detail(self):
        if not self.project_id:
            return
        self.client.get(f"/projects/{self.project_id}", name="GET /projects/:id")

    @task(2)
    def get_assignees(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/assignees",
            name="GET /projects/:id/assignees"
        )

    @task(5)
    def list_usage_statements(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements",
            name="GET /usage-statements"
        )

    @task(5)
    def get_statement_detail(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="GET /usage-statements/:id"
        )

    @task(2)
    def get_latest_statement(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/latest",
            name="GET /usage-statements/latest"
        )

    @task(1)
    def get_statement_by_month(self):
        if not self.project_id:
            return
        year = 2026
        month = random.randint(1, 6)
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/by-month?year={year}&month={month}",
            name="GET /usage-statements/by-month"
        )

    @task(3)
    def get_dashboard(self):
        self.client.get("/dashboard", name="GET /dashboard")

    @task(2)
    def get_dashboard_ai_usage(self):
        self.client.get("/dashboard/ai-usage?year=2026", name="GET /dashboard/ai-usage")

    @task(2)
    def get_archive_categories(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/archive/categories?usageStatementId={self.statement_id}",
            name="GET /archive/categories"
        )

    @task(1)
    def get_archive_category_items(self):
        if not self.project_id or not self.statement_id:
            return
        cat = random.choice(CATEGORY_CODES)
        self.client.get(
            f"/projects/{self.project_id}/archive/categories/{cat}/items?usageStatementId={self.statement_id}",
            name="GET /archive/categories/:code/items"
        )

    @task(3)
    def get_agent_logs(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/logs?usageStatementId={self.statement_id}",
            name="GET /agents/logs"
        )

    @task(2)
    def get_agent_warnings(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/warnings?usageStatementId={self.statement_id}",
            name="GET /agents/warnings"
        )

    @task(2)
    def get_agent_legal_detail(self):
        if not self.project_id or not self.statement_id:
            return
        # 404: 로그 없으면 정상 — 일부 statement는 legal 시드 없음
        with self.client.get(
            f"/projects/{self.project_id}/agents/legal?usageStatementId={self.statement_id}",
            name="GET /agents/legal",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(1)
    def get_agent_report_detail(self):
        if not self.project_id or not self.statement_id:
            # 404: report 시드 없으면 정상 (시드는 report 안 만듦)
            return
        with self.client.get(
            f"/projects/{self.project_id}/agents/report?usageStatementId={self.statement_id}",
            name="GET /agents/report",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    # ── 쓰기 (low weight) ────────────────────────────────────────

    @task(1)
    def update_project(self):
        if not self.project_id:
            return
        self.client.patch(
            f"/projects/{self.project_id}",
            json={"projectName": f"부하테스트 프로젝트-{uuid.uuid4().hex[:6]}"},
            name="PATCH /projects/:id"
        )

    @task(2)
    def complete_review(self):
        if not self.project_id or not self.statement_id:
            return
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/complete-review",
            name="PATCH /usage-statements/:id/complete-review",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 400, 409, 422):
                resp.success()

    @task(2)
    def request_supplement(self):
        if not self.project_id or not self.statement_id:
            return
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/request-supplement",
            name="PATCH /usage-statements/:id/request-supplement",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 400, 409, 422):
                resp.success()

    @task(1)
    def archive_mark_checked(self):
        if not self.project_id:
            return
        self.client.post(
            f"/projects/{self.project_id}/archive/mark-checked",
            name="POST /archive/mark-checked"
        )

    @task(1)
    def refresh_token(self):
        # 404 또는 401: refresh 토큰이 만료된 정상 케이스
        with self.client.post(
            "/auth/refresh",
            name="POST /auth/refresh",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 401):
                resp.success()


class UserScenario(HttpUser):
    """USER 역할 — 작성자 시점. write-heavy이지만 전반적 read 비중 유지."""
    weight = 7
    wait_time = between(2, 8)

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        self.item_ids = []
        self.uploaded_file_ids = []
        self.link_ids = []
        self.todo_ids = []

        if not login(self.client, USER_POOL):
            return

        self.client.get("/categories", name="[setup] categories")
        self.project_id = load_assigned_project_id(self.client, prefix_filter=False)
        if not self.project_id:
            return
        self.statement_id = load_first_statement_id(self.client, self.project_id)
        if self.statement_id:
            self.item_ids = load_item_ids(self.client, self.project_id, self.statement_id)

    # ── 읽기 (high weight) ───────────────────────────────────────

    @task(2)
    def get_my_profile(self):
        self.client.get("/users/me", name="GET /users/me")

    @task(7)
    def get_statement_detail(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="GET /usage-statements/:id"
        )

    @task(5)
    def list_files(self):
        if not self.project_id:
            return
        self.client.get(f"/projects/{self.project_id}/files", name="GET /files")

    @task(3)
    def get_item_evidence_files(self):
        if not self.item_ids or not self.project_id:
            return
        item_id = random.choice(self.item_ids)
        # 404: delete_item 또는 다른 세션이 item을 먼저 삭제 — 정상 시나리오
        with self.client.get(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files",
            name="GET /items/:id/evidence-files",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(3)
    def get_evidence_requirements(self):
        if not self.item_ids or not self.project_id:
            return
        item_id = random.choice(self.item_ids)
        with self.client.get(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-requirements",
            name="GET /items/:id/evidence-requirements",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(4)
    def get_agent_button_states(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/button-states?usageStatementId={self.statement_id}",
            name="GET /agents/button-states"
        )

    @task(4)
    def get_agent_todos(self):
        if not self.project_id or not self.statement_id:
            return
        resp = self.client.get(
            f"/projects/{self.project_id}/agents/todos?usageStatementId={self.statement_id}",
            name="GET /agents/todos"
        )
        if resp.status_code == 200:
            try:
                todos = resp.json().get("data") or []
                self.todo_ids = [t["todoId"] for t in todos if "todoId" in t][:20]
            except (ValueError, KeyError, TypeError):
                pass

    @task(3)
    def get_agent_logs(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/logs?usageStatementId={self.statement_id}",
            name="GET /agents/logs"
        )

    @task(2)
    def get_agent_warnings(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/warnings?usageStatementId={self.statement_id}",
            name="GET /agents/warnings"
        )

    @task(1)
    def download_file(self):
        if not self.uploaded_file_ids or not self.project_id:
            return
        file_id = random.choice(self.uploaded_file_ids)
        # 404: 다른 세션이 먼저 삭제 — 정상
        with self.client.get(
            f"/projects/{self.project_id}/files/{file_id}/download",
            name="GET /files/:id/download",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(1)
    def preview_file(self):
        if not self.uploaded_file_ids or not self.project_id:
            return
        file_id = random.choice(self.uploaded_file_ids)
        # 415: 텍스트 파일 미리보기 미지원 — 부하 테스트 더미 파일 특성 (정상)
        with self.client.get(
            f"/projects/{self.project_id}/files/{file_id}/preview",
            name="GET /files/:id/preview",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404, 415):
                resp.success()

    # ── 쓰기 (low weight) ────────────────────────────────────────

    @task(2)
    def update_item(self):
        if not self.item_ids or not self.project_id or not self.statement_id:
            return
        item_id = random.choice(self.item_ids)
        # 404: delete_item 또는 다른 세션이 item을 먼저 삭제 — 정상 시나리오
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items/{item_id}",
            json={
                "usedOn": "2026-05-20",
                "itemName": f"수정된 항목-{uuid.uuid4().hex[:6]}",
                "unit": "식",
                "quantity": 2,
                "unitPrice": 15000,
                "totalAmount": 30000,
                "pageNo": random.randint(1, 50)
            },
            name="PATCH /items/:id",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(1)
    def change_item_category(self):
        if not self.item_ids or not self.project_id or not self.statement_id:
            return
        item_id = random.choice(self.item_ids)
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items/{item_id}/category",
            json={"categoryCode": random.choice(CATEGORY_CODES)},
            name="PATCH /items/:id/category",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(1)
    def delete_item(self):
        if not self.item_ids or not self.project_id or not self.statement_id:
            return
        item_id = random.choice(self.item_ids)
        with self.client.delete(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items/{item_id}",
            name="DELETE /items/:id",
            catch_response=True
        ) as resp:
            # 404: 다른 세션이 먼저 삭제
            if resp.status_code in (200, 204, 404):
                resp.success()
                if resp.status_code != 200:
                    # 캐시 무효화
                    try:
                        self.item_ids.remove(item_id)
                    except ValueError:
                        pass

    @task(2)
    def upload_file(self):
        if not self.project_id:
            return
        resp = self.client.post(
            f"/projects/{self.project_id}/files",
            files={"file": (f"test-{uuid.uuid4().hex[:6]}.txt", dummy_file(), "text/plain")},
            data={"evidenceTypeCode": random.choice(UPLOAD_EVIDENCE_TYPES)},
            name="POST /files"
        )
        if resp.status_code == 200:
            try:
                self.uploaded_file_ids.append(resp.json()["data"]["fileId"])
            except (ValueError, KeyError):
                pass

    @task(1)
    def upload_and_link_evidence(self):
        """파일 업로드 + 항목 연결 원샷."""
        if not self.item_ids or not self.project_id:
            return
        item_id = random.choice(self.item_ids)
        with self.client.post(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files/upload",
            files={"file": (f"test-{uuid.uuid4().hex[:6]}.txt", dummy_file(), "text/plain")},
            data={"evidenceTypeCode": random.choice(UPLOAD_EVIDENCE_TYPES)},
            name="POST /items/:id/evidence-files/upload",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 201):
                resp.success()
                try:
                    data = resp.json()["data"]
                    self.uploaded_file_ids.append(data["fileId"])
                    self.link_ids.append({"linkId": data["linkId"], "itemId": item_id})
                except (ValueError, KeyError):
                    pass
            elif resp.status_code in (404, 409):
                # 404: item 사라짐 / 409: 동일 파일-항목 중복
                resp.success()

    @task(1)
    def delete_file(self):
        if not self.uploaded_file_ids or not self.project_id:
            return
        file_id = self.uploaded_file_ids.pop()
        with self.client.delete(
            f"/projects/{self.project_id}/files/{file_id}",
            name="DELETE /files/:id",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 204, 404):
                resp.success()

    @task(2)
    def link_evidence_file(self):
        if not self.uploaded_file_ids or not self.item_ids or not self.project_id:
            return
        file_id = random.choice(self.uploaded_file_ids)
        item_id = random.choice(self.item_ids)
        with self.client.post(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files",
            json={
                "fileId": file_id,
                "evidenceTypeCode": random.choice(LINK_EVIDENCE_TYPES)
            },
            name="POST /items/:id/evidence-files",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 201):
                resp.success()
                try:
                    self.link_ids.append({
                        "linkId": resp.json()["data"]["linkId"],
                        "itemId": item_id
                    })
                except (ValueError, KeyError):
                    pass
            elif resp.status_code in (404, 409):
                # 404: item 사라짐 / 409: 동일 파일-항목 중복
                resp.success()

    @task(1)
    def move_evidence_link(self):
        if not self.link_ids or not self.project_id:
            return
        other_items = list(self.item_ids)
        if len(other_items) < 2:
            return
        link = random.choice(self.link_ids)
        target_item_id = random.choice([i for i in other_items if i != link["itemId"]] or other_items)
        with self.client.patch(
            f"/projects/{self.project_id}/evidence-file-links/{link['linkId']}",
            json={
                "targetItemId": target_item_id,
                "evidenceTypeCode": random.choice(LINK_EVIDENCE_TYPES)
            },
            name="PATCH /evidence-file-links/:id",
            catch_response=True
        ) as resp:
            if resp.status_code == 200:
                resp.success()
                link["itemId"] = target_item_id
            elif resp.status_code in (404, 409):
                resp.success()

    @task(1)
    def delete_evidence_link(self):
        if not self.link_ids or not self.project_id:
            return
        link = self.link_ids.pop()
        with self.client.delete(
            f"/projects/{self.project_id}/evidence-file-links/{link['linkId']}",
            name="DELETE /evidence-file-links/:id",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 204, 404):
                resp.success()

    @task(1)
    def confirm_todo(self):
        if not self.todo_ids or not self.project_id:
            return
        todo_id = random.choice(self.todo_ids)
        confirmed = random.choice([True, False])
        with self.client.patch(
            f"/projects/{self.project_id}/agents/todos/{todo_id}/confirm",
            json={"confirmed": confirmed},
            name="PATCH /agents/todos/:id/confirm",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 404):
                resp.success()

    @task(1)
    def submit_statement(self):
        if not self.project_id or not self.statement_id:
            return
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/submit",
            name="PATCH /usage-statements/:id/submit",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 400, 409, 422):
                resp.success()


# ============================================================
# Journey 시나리오 — 실제 비즈니스 여정 측정
# 한 세션 내에서 정해진 시퀀스를 순차 실행
# ============================================================

class WriterJourney(HttpUser):
    """작성자 여정: login → detail → upload×3 → link×3 → submit → wait → 반복"""
    weight = 4
    wait_time = between(5, 15)  # 검토·작성 사이 휴식

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        self.item_ids = []
        if not login(self.client, USER_POOL, name="[journey] login"):
            return
        self.project_id = load_assigned_project_id(self.client, prefix_filter=False)
        if not self.project_id:
            return
        self.statement_id = load_first_statement_id(self.client, self.project_id)
        if self.statement_id:
            self.item_ids = load_item_ids(self.client, self.project_id, self.statement_id)

    @task
    def writer_full_flow(self):
        if not self.project_id or not self.statement_id or not self.item_ids:
            return

        # 1. 상세 조회
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="[journey] writer / GET statement"
        )

        # 2. 증빙 충족 현황 확인
        sample_items = random.sample(self.item_ids, min(3, len(self.item_ids)))
        for item_id in sample_items:
            with self.client.get(
                f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-requirements",
                name="[journey] writer / GET requirements",
                catch_response=True
            ) as r:
                if r.status_code in (200, 404):
                    r.success()

        # 3. 파일 업로드 + 즉시 연결 (원샷) × 3
        uploaded = []
        for item_id in sample_items:
            with self.client.post(
                f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files/upload",
                files={"file": (f"j-{uuid.uuid4().hex[:6]}.txt", dummy_file(), "text/plain")},
                data={"evidenceTypeCode": random.choice(UPLOAD_EVIDENCE_TYPES)},
                name="[journey] writer / POST upload+link",
                catch_response=True
            ) as resp:
                if resp.status_code in (200, 201):
                    resp.success()
                    try:
                        uploaded.append(resp.json()["data"]["fileId"])
                    except (ValueError, KeyError):
                        pass
                elif resp.status_code in (404, 409):
                    resp.success()

        # 4. 제출
        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/submit",
            name="[journey] writer / PATCH submit",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 400, 409, 422):
                resp.success()


class ReviewerJourney(HttpUser):
    """검토자 여정: 목록 → detail → todos → confirm × N → complete-review or supplement"""
    weight = 2
    wait_time = between(8, 20)  # 검토하는 시간

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        if not login(self.client, ADMIN_POOL, name="[journey] login"):
            return
        self.project_id = load_assigned_project_id(self.client)
        if self.project_id:
            self.statement_id = load_first_statement_id(self.client, self.project_id)

    @task
    def reviewer_full_flow(self):
        if not self.project_id or not self.statement_id:
            return

        # 1. 목록·dashboard 훑기
        self.client.get("/projects", name="[journey] reviewer / GET projects")
        self.client.get("/dashboard", name="[journey] reviewer / GET dashboard")

        # 2. 사용내역서 상세 + button-states
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="[journey] reviewer / GET statement"
        )
        self.client.get(
            f"/projects/{self.project_id}/agents/button-states?usageStatementId={self.statement_id}",
            name="[journey] reviewer / GET button-states"
        )

        # 3. TODO 조회 → confirm 일부
        resp = self.client.get(
            f"/projects/{self.project_id}/agents/todos?usageStatementId={self.statement_id}",
            name="[journey] reviewer / GET todos"
        )
        todo_ids = []
        if resp.status_code == 200:
            try:
                todos = resp.json().get("data") or []
                todo_ids = [t["todoId"] for t in todos if "todoId" in t]
            except (ValueError, KeyError, TypeError):
                pass

        for todo_id in todo_ids[:3]:
            with self.client.patch(
                f"/projects/{self.project_id}/agents/todos/{todo_id}/confirm",
                json={"confirmed": True},
                name="[journey] reviewer / PATCH confirm",
                catch_response=True
            ) as r:
                if r.status_code in (200, 404):
                    r.success()

        # 4. 검토완료 또는 보완요청
        if random.random() < 0.6:
            endpoint = "complete-review"
        else:
            endpoint = "request-supplement"

        with self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/{endpoint}",
            name=f"[journey] reviewer / PATCH {endpoint}",
            catch_response=True
        ) as resp:
            if resp.status_code in (200, 400, 409, 422):
                resp.success()


class BrowsingLoad(HttpUser):
    """read-only 브라우징 — dashboard·목록·archive·파일 목록"""
    weight = 4
    wait_time = between(2, 5)

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        # 절반은 admin, 절반은 user
        pool = ADMIN_POOL if random.random() < 0.5 else USER_POOL
        if not login(self.client, pool, name="[journey] login"):
            return
        self.project_id = load_assigned_project_id(self.client, prefix_filter=False)
        if self.project_id:
            self.statement_id = load_first_statement_id(self.client, self.project_id)

    @task(4)
    def browse_projects(self):
        self.client.get("/projects", name="[journey] browse / GET projects")

    @task(2)
    def browse_projects_keyword(self):
        keyword = random.choice(["부하", "테스트", "안전", "관리", "프로젝트"])
        self.client.get(
            f"/projects?keyword={keyword}",
            name="[journey] browse / GET projects?keyword"
        )

    @task(3)
    def browse_dashboard(self):
        self.client.get("/dashboard", name="[journey] browse / GET dashboard")

    @task(1)
    def browse_dashboard_ai_usage(self):
        self.client.get(
            "/dashboard/ai-usage?year=2026",
            name="[journey] browse / GET dashboard/ai-usage"
        )

    @task(3)
    def browse_statement(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="[journey] browse / GET statement"
        )

    @task(2)
    def browse_files(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/files",
            name="[journey] browse / GET files"
        )

    @task(2)
    def browse_archive(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/archive/categories?usageStatementId={self.statement_id}",
            name="[journey] browse / GET archive"
        )

    @task(1)
    def browse_users_me(self):
        self.client.get("/users/me", name="[journey] browse / GET users/me")
