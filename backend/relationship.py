"""User-managed local relationship reminders; all reminder payload text is encrypted at rest."""
from __future__ import annotations

import json
import os
import sqlite3
import time
import uuid
from pathlib import Path
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException, Query
from pydantic import BaseModel, Field

from backend.security import decrypt_text, encrypt_text, require_api_token

router = APIRouter()
ROOT = Path(__file__).resolve().parents[1]
DATA_DIR = Path(os.getenv("JARVIS_DATA_DIR", str(ROOT / "backend" / "data"))).resolve()
DB_PATH = DATA_DIR / "jarvis.sqlite3"


def init_reminders() -> None:
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            """CREATE TABLE IF NOT EXISTS reminders (
                id TEXT PRIMARY KEY,
                encrypted_payload TEXT NOT NULL,
                remind_at INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                notified_at INTEGER,
                status TEXT NOT NULL CHECK(status IN ('scheduled','completed','cancelled'))
            )"""
        )
        conn.commit()


class ReminderCreate(BaseModel):
    title: str = Field(min_length=1, max_length=160)
    notes: str = Field(default="", max_length=3000)
    remind_at: int = Field(gt=0)


def _read_item(row: tuple) -> dict:
    data = json.loads(decrypt_text(row[1]))
    return {
        "id": row[0],
        "title": data.get("title", ""),
        "notes": data.get("notes", ""),
        "remindAt": row[2],
        "createdAt": row[3],
        "notifiedAt": row[4],
        "status": row[5],
    }


@router.post("/api/reminders", dependencies=[Depends(require_api_token)])
async def create_reminder(reminder: ReminderCreate) -> dict:
    """Create a one-time user-requested reminder. No WhatsApp or other account is accessed."""
    init_reminders()
    now = int(time.time())
    if reminder.remind_at < now - 60:
        raise HTTPException(status_code=422, detail="Reminder time must be in the future.")
    item_id = uuid.uuid4().hex
    payload = encrypt_text(json.dumps(
        {"title": reminder.title.strip(), "notes": reminder.notes.strip()},
        ensure_ascii=False,
    ))
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute(
            "INSERT INTO reminders(id, encrypted_payload, remind_at, created_at, status) VALUES (?, ?, ?, ?, 'scheduled')",
            (item_id, payload, reminder.remind_at, now),
        )
        conn.commit()
    return {"id": item_id, "status": "scheduled", "remindAt": reminder.remind_at, "storedEncrypted": True}


@router.get("/api/reminders", dependencies=[Depends(require_api_token)])
async def list_reminders(status: Literal["scheduled", "completed", "cancelled"] | None = Query(default="scheduled")) -> dict:
    init_reminders()
    query = "SELECT id, encrypted_payload, remind_at, created_at, notified_at, status FROM reminders"
    args: tuple = ()
    if status:
        query += " WHERE status = ?"
        args = (status,)
    query += " ORDER BY remind_at ASC LIMIT 200"
    with sqlite3.connect(DB_PATH) as conn:
        rows = conn.execute(query, args).fetchall()
    return {"items": [_read_item(row) for row in rows], "count": len(rows)}


@router.post("/api/reminders/due", dependencies=[Depends(require_api_token)])
async def claim_due_reminders() -> dict:
    """Atomically return overdue reminders once. Dashboard may display OS/browser notification."""
    init_reminders()
    now = int(time.time())
    with sqlite3.connect(DB_PATH) as conn:
        conn.execute("BEGIN IMMEDIATE")
        rows = conn.execute(
            "SELECT id, encrypted_payload, remind_at, created_at, notified_at, status FROM reminders "
            "WHERE status = 'scheduled' AND notified_at IS NULL AND remind_at <= ? ORDER BY remind_at ASC LIMIT 50",
            (now,),
        ).fetchall()
        if rows:
            conn.executemany(
                "UPDATE reminders SET notified_at = ? WHERE id = ? AND notified_at IS NULL",
                [(now, row[0]) for row in rows],
            )
        conn.commit()
    return {"items": [_read_item(row) for row in rows], "count": len(rows), "stored": True}


@router.delete("/api/reminders/{reminder_id}", dependencies=[Depends(require_api_token)])
async def cancel_reminder(reminder_id: str) -> dict:
    init_reminders()
    with sqlite3.connect(DB_PATH) as conn:
        cursor = conn.execute(
            "UPDATE reminders SET status = 'cancelled' WHERE id = ? AND status = 'scheduled'",
            (reminder_id,),
        )
        conn.commit()
    if cursor.rowcount != 1:
        raise HTTPException(status_code=404, detail="Scheduled reminder not found.")
    return {"id": reminder_id, "status": "cancelled"}
