"""Small integration tests; all data is written under a temporary local test directory."""
from __future__ import annotations

import base64
import os
import secrets
import tempfile
from pathlib import Path

os.environ["JARVIS_API_TOKEN"] = "test-token-" + secrets.token_urlsafe(40)
os.environ["JARVIS_DATA_KEY"] = base64.urlsafe_b64encode(bytes(range(32))).decode("ascii")
TEST_ROOT = Path(tempfile.mkdtemp(prefix="jarvis-tests-"))
os.environ["JARVIS_DATA_DIR"] = str(TEST_ROOT / "data")
os.environ["JARVIS_VAULT_PATH"] = str(TEST_ROOT / "vault")
os.environ["OLLAMA_BASE_URL"] = "http://127.0.0.1:11434"
os.environ.pop("ADZUNA_APP_ID", None)
os.environ.pop("ADZUNA_APP_KEY", None)

from fastapi.testclient import TestClient  # noqa: E402
from backend.main import app, DB_PATH, VAULT_DIR  # noqa: E402

client = TestClient(app)
AUTH = {"Authorization": f"Bearer {os.environ['JARVIS_API_TOKEN']}"}


def test_health_has_no_sensitive_details() -> None:
    response = client.get("/health")
    assert response.status_code == 200
    body = response.json()
    assert body["status"] == "ok"
    assert "token" not in body
    assert "email" not in body


def test_protected_endpoints_require_bearer_token() -> None:
    assert client.get("/api/integrations/status").status_code == 401


def test_jobs_endpoint_only_returns_search_links() -> None:
    response = client.get(
        "/api/jobs/search-links",
        params={"q": "Python Entwickler", "location": "Sinsheim"},
        headers=AUTH,
    )
    assert response.status_code == 200
    body = response.json()
    assert body["automation"] == "disabled"
    assert {item["name"] for item in body["sources"]} == {
        "Indeed", "Bundesagentur für Arbeit", "LinkedIn Jobs"
    }
    assert all(item["url"].startswith("https://") for item in body["sources"])


def test_approval_payload_is_encrypted_and_decision_does_not_execute() -> None:
    proposal = {
        "action_type": "email_send",
        "summary": "Confidential draft test",
        "payload": {"body": "SENSITIVE_BODY_MARKER_98a1", "to": "person@example.test"},
    }
    created = client.post("/api/approvals", json=proposal, headers=AUTH)
    assert created.status_code == 200
    item_id = created.json()["id"]
    assert created.json()["status"] == "pending"
    assert created.json()["execution"] == "not-implemented"

    raw_db = DB_PATH.read_bytes()
    assert b"SENSITIVE_BODY_MARKER_98a1" not in raw_db
    assert b"Confidential draft test" not in raw_db

    listed = client.get("/api/approvals", headers=AUTH)
    item = next(row for row in listed.json()["items"] if row["id"] == item_id)
    assert item["payload"]["body"] == "SENSITIVE_BODY_MARKER_98a1"

    decision = client.post(
        f"/api/approvals/{item_id}/decision",
        json={"decision": "approve"},
        headers=AUTH,
    )
    assert decision.status_code == 200
    assert decision.json()["status"] == "approved"
    assert decision.json()["execution"] == "not-implemented"


def test_memory_note_is_written_to_local_obsidian_markdown() -> None:
    response = client.post(
        "/api/memory",
        json={"title": "Test Note", "content": "Only local test content.", "tags": ["test", "memory"]},
        headers=AUTH,
    )
    assert response.status_code == 200
    note_path = VAULT_DIR / response.json()["name"]
    assert note_path.exists()
    assert note_path.suffix == ".md"
    assert "Only local test content." in note_path.read_text(encoding="utf-8")


def test_dashboard_is_served_from_local_api() -> None:
    response = client.get("/")
    assert response.status_code == 200
    assert "JARVIS" in response.text
    assert "localStorage" not in response.text


def test_job_search_falls_back_to_authorized_source_links_without_keys() -> None:
    response = client.get("/api/jobs/search", params={"q": "Python Entwickler", "location": "Sinsheim"}, headers=AUTH)
    assert response.status_code == 200
    body = response.json()
    assert body["configured"] is False
    assert body["results"] == []
    assert {source["name"] for source in body["searchLinks"]} == {
        "Indeed", "Bundesagentur für Arbeit", "LinkedIn Jobs"
    }


def test_relationship_reminder_payload_is_encrypted_and_cancelable() -> None:
    import time

    marker = "PRIVATE_RELATIONSHIP_NOTE_41f9"
    created = client.post(
        "/api/reminders",
        json={
            "title": "Anniversary reminder",
            "notes": marker,
            "remind_at": int(time.time()) + 3600,
        },
        headers=AUTH,
    )
    assert created.status_code == 200
    reminder_id = created.json()["id"]
    assert created.json()["storedEncrypted"] is True

    raw_db = DB_PATH.read_bytes()
    assert marker.encode() not in raw_db
    assert b"Anniversary reminder" not in raw_db

    listed = client.get("/api/reminders", headers=AUTH)
    row = next(item for item in listed.json()["items"] if item["id"] == reminder_id)
    assert row["title"] == "Anniversary reminder"
    assert row["notes"] == marker

    deleted = client.delete(f"/api/reminders/{reminder_id}", headers=AUTH)
    assert deleted.status_code == 200
    assert deleted.json()["status"] == "cancelled"
