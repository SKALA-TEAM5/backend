"""
실제 서비스 흐름 기반 부하 테스트

전제조건:
    make locust-seed  # 테스트 계정·프로젝트·사용내역서 사전 생성

시나리오:
    AdminScenario (30%) — 프로젝트·사용내역서 조회·수정, 검토완료·보완요청,
                          대시보드, 아카이브, 담당자 조회
    UserScenario  (70%) — 항목 CRUD·카테고리 이동, 파일 업로드·삭제,
                          파일-항목 연결·이동·삭제, 사용내역서 제출,
                          agent 읽기(logs/warnings/todos/button-states),
                          증빙 요건 조회, 공통 코드 조회

실행:
    locust -f load-test/locustfile.py --host=http://localhost:8000
    make locust-save LOCUST_RESULT=stress_500_v2 LOCUST_USERS=500 LOCUST_RATE=10 LOCUST_TIME=8m
"""

from locust import HttpUser, task, between
import random
import io
import uuid

# ── 계정 풀 (make locust-seed로 사전 생성) ────────────────────────
PASSWORD = "P@ssw0rd123!"
ADMIN_POOL = [{"employeeNo": f"LOAD-ADMIN-{n:03d}", "password": PASSWORD} for n in range(1, 31)]
USER_POOL  = [{"employeeNo": f"LOAD-USER-{n:03d}",  "password": PASSWORD} for n in range(1, 31)]

CATEGORY_CODES = [f"CAT_0{n}" for n in range(1, 10)]

# 파일 업로드용 (4개 버킷 코드)
UPLOAD_EVIDENCE_TYPES = ["tax_invoice", "receipt", "site_photo", "work_log"]

# 파일-항목 연결용 (EvidenceRequests 허용 코드 일부)
LINK_EVIDENCE_TYPES = [
    "tax_invoice", "receipt", "site_photo", "work_log",
    "pay_stub", "item_photo", "wearing_photo",
]


def dummy_file():
    return io.BytesIO(b"load test file " + uuid.uuid4().bytes)


# ── Admin 시나리오 ────────────────────────────────────────────────
class AdminScenario(HttpUser):
    """
    ADMIN 역할 — 프로젝트·사용내역서 조회·수정, 검토완료·보완요청,
                 대시보드, 아카이브, 담당자 조회
    """
    weight = 3
    wait_time = between(1, 3)

    def on_start(self):
        self.project_id = None
        self.statement_id = None

        resp = self.client.post("/auth/login", json=random.choice(ADMIN_POOL), name="[setup] login")
        if resp.status_code != 200:
            return

        projects = self.client.get("/projects", name="[setup] projects")
        if projects.status_code != 200:
            return

        items = projects.json().get("data", {}).get("items", [])
        load_projects = [p for p in items if p.get("contractNo", "").startswith("LOAD-CN-")]
        if not load_projects:
            return

        self.project_id = random.choice(load_projects)["id"]

        stmts = self.client.get(
            f"/projects/{self.project_id}/usage-statements",
            name="[setup] statements"
        )
        if stmts.status_code == 200:
            stmt_items = stmts.json().get("data", {}).get("items", [])
            if stmt_items:
                self.statement_id = stmt_items[0]["id"]

    # ── 프로젝트 조회·수정 ──────────────────────────────────────

    @task(4)
    def list_projects(self):
        self.client.get("/projects", name="GET /projects")

    @task(2)
    def list_projects_with_filter(self):
        keyword = random.choice(["부하", "테스트", "프로젝트"])
        self.client.get(f"/projects?keyword={keyword}", name="GET /projects?keyword=")

    @task(3)
    def get_project_detail(self):
        if not self.project_id:
            return
        self.client.get(f"/projects/{self.project_id}", name="GET /projects/:id")

    @task(1)
    def update_project(self):
        if not self.project_id:
            return
        self.client.patch(
            f"/projects/{self.project_id}",
            json={"projectName": f"부하테스트 프로젝트-{uuid.uuid4().hex[:6]}"},
            name="PATCH /projects/:id"
        )

    # ── 담당자 조회 ──────────────────────────────────────────────

    @task(2)
    def get_assignees(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/assignees",
            name="GET /projects/:id/assignees"
        )

    # ── 사용내역서 조회 ──────────────────────────────────────────

    @task(4)
    def list_usage_statements(self):
        if not self.project_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements",
            name="GET /usage-statements"
        )

    @task(4)
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
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/by-month?year=2026&month=5",
            name="GET /usage-statements/by-month"
        )

    # ── 검토완료 · 보완요청 (상태 전이 실패는 정상 시나리오) ──────

    @task(1)
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

    @task(1)
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

    # ── 대시보드 ─────────────────────────────────────────────────

    @task(2)
    def get_dashboard(self):
        self.client.get("/dashboard", name="GET /dashboard")

    @task(1)
    def get_dashboard_ai_usage(self):
        self.client.get("/dashboard/ai-usage?year=2026", name="GET /dashboard/ai-usage")

    # ── 아카이브 ─────────────────────────────────────────────────

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

    # ── Agent 읽기 전용 (Admin도 조회함) ──────────────────────────

    @task(2)
    def get_agent_logs(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/logs?usageStatementId={self.statement_id}",
            name="GET /agents/logs"
        )

    @task(1)
    def get_agent_warnings(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/warnings?usageStatementId={self.statement_id}",
            name="GET /agents/warnings"
        )


# ── User 시나리오 ─────────────────────────────────────────────────
class UserScenario(HttpUser):
    """
    USER 역할 — 항목 CRUD·카테고리 이동, 파일 업로드·삭제,
                파일-항목 연결·이동·삭제, 사용내역서 제출,
                agent 읽기(logs/warnings/todos/button-states),
                증빙 요건 조회, 공통 코드 조회
    """
    weight = 7
    wait_time = between(1, 4)

    def on_start(self):
        self.project_id = None
        self.statement_id = None
        self.created_item_ids = []
        self.uploaded_file_ids = []  # 업로드됐지만 아직 연결 안 된 파일 ID
        self.link_ids = []           # {"linkId": int, "itemId": int}

        resp = self.client.post("/auth/login", json=random.choice(USER_POOL), name="[setup] login")
        if resp.status_code != 200:
            return

        # 공통 코드 (세션 시작 시 1회)
        self.client.get("/categories", name="[setup] categories")

        projects = self.client.get("/projects", name="[setup] projects")
        if projects.status_code != 200:
            return

        items = projects.json().get("data", {}).get("items", [])
        if not items:
            return

        self.project_id = random.choice(items)["id"]

        stmts = self.client.get(
            f"/projects/{self.project_id}/usage-statements",
            name="[setup] statements"
        )
        if stmts.status_code == 200:
            stmt_items = stmts.json().get("data", {}).get("items", [])
            if stmt_items:
                self.statement_id = stmt_items[0]["id"]

    # ── 내 프로필 ────────────────────────────────────────────────

    @task(1)
    def get_my_profile(self):
        self.client.get("/users/me", name="GET /users/me")

    # ── 사용내역서 조회 ──────────────────────────────────────────

    @task(5)
    def get_statement_detail(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}",
            name="GET /usage-statements/:id"
        )

    # ── 항목 CRUD ────────────────────────────────────────────────

    @task(4)
    def create_item(self):
        if not self.project_id or not self.statement_id:
            return
        resp = self.client.post(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items",
            json={
                "categoryCode": random.choice(CATEGORY_CODES),
                "usedOn": "2026-05-15",
                "itemName": f"부하테스트 항목-{uuid.uuid4().hex[:6]}",
                "unit": "개",
                "quantity": 1,
                "unitPrice": 10000,
                "totalAmount": 10000,
                "pageNo": random.randint(1, 50)
            },
            name="POST /items"
        )
        if resp.status_code == 201:
            self.created_item_ids.append(resp.json()["data"]["itemId"])

    @task(3)
    def update_item(self):
        if not self.created_item_ids or not self.project_id or not self.statement_id:
            return
        item_id = random.choice(self.created_item_ids)
        self.client.patch(
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
            name="PATCH /items/:id"
        )

    @task(1)
    def change_item_category(self):
        if not self.created_item_ids or not self.project_id or not self.statement_id:
            return
        item_id = random.choice(self.created_item_ids)
        self.client.patch(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items/{item_id}/category",
            json={"categoryCode": random.choice(CATEGORY_CODES)},
            name="PATCH /items/:id/category"
        )

    @task(1)
    def delete_item(self):
        if not self.created_item_ids or not self.project_id or not self.statement_id:
            return
        item_id = self.created_item_ids.pop()
        self.client.delete(
            f"/projects/{self.project_id}/usage-statements/{self.statement_id}/items/{item_id}",
            name="DELETE /items/:id"
        )

    # ── 파일 CRUD ────────────────────────────────────────────────

    @task(3)
    def list_files(self):
        if not self.project_id:
            return
        self.client.get(f"/projects/{self.project_id}/files", name="GET /files")

    @task(3)
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
            self.uploaded_file_ids.append(resp.json()["data"]["fileId"])

    @task(1)
    def delete_file(self):
        if not self.uploaded_file_ids or not self.project_id:
            return
        file_id = self.uploaded_file_ids.pop()
        self.client.delete(
            f"/projects/{self.project_id}/files/{file_id}",
            name="DELETE /files/:id"
        )

    # ── 파일-항목 연결 관계 ──────────────────────────────────────

    @task(2)
    def link_evidence_file(self):
        if not self.uploaded_file_ids or not self.created_item_ids or not self.project_id:
            return
        file_id = random.choice(self.uploaded_file_ids)
        item_id = random.choice(self.created_item_ids)
        resp = self.client.post(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files",
            json={
                "fileId": file_id,
                "evidenceTypeCode": random.choice(LINK_EVIDENCE_TYPES)
            },
            name="POST /items/:id/evidence-files"
        )
        if resp.status_code == 200:
            self.link_ids.append({
                "linkId": resp.json()["data"]["linkId"],
                "itemId": item_id
            })

    @task(2)
    def get_item_evidence_files(self):
        if not self.created_item_ids or not self.project_id:
            return
        item_id = random.choice(self.created_item_ids)
        self.client.get(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-files",
            name="GET /items/:id/evidence-files"
        )

    @task(2)
    def get_evidence_requirements(self):
        if not self.created_item_ids or not self.project_id:
            return
        item_id = random.choice(self.created_item_ids)
        self.client.get(
            f"/projects/{self.project_id}/usage-statement-items/{item_id}/evidence-requirements",
            name="GET /items/:id/evidence-requirements"
        )

    @task(1)
    def move_evidence_link(self):
        if not self.link_ids or not self.project_id:
            return
        other_items = [i for i in self.created_item_ids]
        if len(other_items) < 2:
            return
        link = random.choice(self.link_ids)
        target_item_id = random.choice([i for i in other_items if i != link["itemId"]] or other_items)
        resp = self.client.patch(
            f"/projects/{self.project_id}/evidence-file-links/{link['linkId']}",
            json={
                "targetItemId": target_item_id,
                "evidenceTypeCode": random.choice(LINK_EVIDENCE_TYPES)
            },
            name="PATCH /evidence-file-links/:id"
        )
        if resp.status_code == 200:
            link["itemId"] = target_item_id

    @task(1)
    def delete_evidence_link(self):
        if not self.link_ids or not self.project_id:
            return
        link = self.link_ids.pop()
        self.client.delete(
            f"/projects/{self.project_id}/evidence-file-links/{link['linkId']}",
            name="DELETE /evidence-file-links/:id"
        )

    # ── 사용내역서 제출 (상태 전이 실패는 정상 시나리오) ──────────

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

    # ── Agent 읽기 전용 (AI 실행 제외) ───────────────────────────

    @task(3)
    def get_agent_button_states(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/button-states?usageStatementId={self.statement_id}",
            name="GET /agents/button-states"
        )

    @task(3)
    def get_agent_todos(self):
        if not self.project_id or not self.statement_id:
            return
        self.client.get(
            f"/projects/{self.project_id}/agents/todos?usageStatementId={self.statement_id}",
            name="GET /agents/todos"
        )

    @task(2)
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
