import hashlib
import json
import os
import re
import sqlite3
import uuid
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
from xml.etree import ElementTree

from fastapi import FastAPI, Header, HTTPException, Query, Request
from pydantic import BaseModel, Field


DB_PATH = os.environ.get("ICON_LIBRARY_DB", "/data/icons.sqlite3")
MAX_PAYLOAD_BYTES = 256 * 1024
MAX_DATABASE_BYTES = 650 * 1024 * 1024
DATABASE_WRITE_RESERVE = 1024 * 1024
MAX_UPLOADS_PER_HOUR = 8
MAX_ICONS = 3000
MAX_LIKES = 100000
MAX_TRACKED_DEVICES = 10000
ANDROID_NS = "{http://schemas.android.com/apk/res/android}"
CONTROL_CHARS = re.compile(r"[\x00-\x1f\x7f]")
ALLOWED_TYPES = {"ringer_silent", "ringer_vibrate", "battery"}

app = FastAPI(title="Flyme Status Bar Sizer Icon Library", docs_url=None, redoc_url=None)


class Upload(BaseModel):
    type: str
    name: str = Field(min_length=1, max_length=48)
    payload: str = Field(min_length=1)


def now():
    return datetime.now(timezone.utc)


def ensure_storage_space(additional_bytes=0):
    current_bytes = sum(os.path.getsize(path) for path in
                        (DB_PATH, DB_PATH + "-wal", DB_PATH + "-shm") if os.path.exists(path))
    if current_bytes + additional_bytes + DATABASE_WRITE_RESERVE > MAX_DATABASE_BYTES:
        raise HTTPException(507, "Online icon library storage limit reached")


@contextmanager
def database():
    connection = sqlite3.connect(DB_PATH, timeout=10)
    connection.row_factory = sqlite3.Row
    connection.execute("PRAGMA foreign_keys=ON")
    try:
        yield connection
        connection.commit()
    except Exception:
        connection.rollback()
        raise
    finally:
        connection.close()


@app.on_event("startup")
def initialize():
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    with database() as connection:
        connection.execute("PRAGMA journal_mode=WAL")
        connection.executescript("""
            CREATE TABLE IF NOT EXISTS icons (
                id TEXT PRIMARY KEY,
                type TEXT NOT NULL,
                name TEXT NOT NULL,
                payload TEXT NOT NULL,
                payload_sha256 TEXT NOT NULL,
                created_at TEXT NOT NULL,
                owner_device_id TEXT,
                download_count INTEGER NOT NULL DEFAULT 0,
                UNIQUE(type, payload_sha256)
            );
            CREATE TABLE IF NOT EXISTS likes (
                icon_id TEXT NOT NULL REFERENCES icons(id) ON DELETE CASCADE,
                device_id TEXT NOT NULL,
                created_at TEXT NOT NULL,
                PRIMARY KEY(icon_id, device_id)
            );
            CREATE TABLE IF NOT EXISTS upload_limits (
                device_id TEXT PRIMARY KEY,
                window_started TEXT NOT NULL,
                upload_count INTEGER NOT NULL
            );
        """)
        columns = {row[1] for row in connection.execute("PRAGMA table_info(icons)")}
        if "owner_device_id" not in columns:
            connection.execute("ALTER TABLE icons ADD COLUMN owner_device_id TEXT")
        if "download_count" not in columns:
            connection.execute("ALTER TABLE icons ADD COLUMN download_count INTEGER NOT NULL DEFAULT 0")


def require_device(device_id):
    if not device_id or len(device_id) > 64:
        raise HTTPException(400, "Missing device identity")
    try:
        return str(uuid.UUID(device_id))
    except ValueError as error:
        raise HTTPException(400, "Invalid device identity") from error


def validate_vector(payload):
    if "<!DOCTYPE" in payload.upper() or "<!ENTITY" in payload.upper():
        raise HTTPException(422, "External XML declarations are not allowed")
    try:
        root = ElementTree.fromstring(payload)
    except ElementTree.ParseError as error:
        raise HTTPException(422, "Invalid vector XML") from error
    if root.tag != "vector":
        raise HTTPException(422, "Vector XML root must be vector")
    for node in root.iter():
        if node.tag not in {"vector", "group", "path", "clip-path"}:
            raise HTTPException(422, "Unsupported vector XML element")
        for key, value in node.attrib.items():
            if key.startswith(ANDROID_NS) and value.strip().startswith(("@", "?")):
                raise HTTPException(422, "External resource references are not allowed")
            if key.startswith("{") and not key.startswith(ANDROID_NS):
                raise HTTPException(422, "Unsupported vector XML namespace")
    try:
        return (float(root.attrib[ANDROID_NS + "viewportWidth"]),
                float(root.attrib[ANDROID_NS + "viewportHeight"]))
    except (KeyError, ValueError) as error:
        raise HTTPException(422, "Vector viewport is invalid") from error


def validate_payload(kind, payload):
    if kind not in ALLOWED_TYPES:
        raise HTTPException(422, "Unsupported icon type")
    if len(payload.encode("utf-8")) > MAX_PAYLOAD_BYTES:
        raise HTTPException(413, "Icon payload is too large")
    if kind.startswith("ringer_"):
        validate_vector(payload)
        return
    try:
        pack = json.loads(payload)
    except json.JSONDecodeError as error:
        raise HTTPException(422, "Invalid battery icon JSON") from error
    if not isinstance(pack, dict) or pack.get("version") not in (1, 2):
        raise HTTPException(422, "Unsupported battery icon package")
    viewport = None
    for key in ("shellXml", "fillXml"):
        if not isinstance(pack.get(key), str) or not pack[key].strip():
            raise HTTPException(422, f"Missing {key}")
        current_viewport = validate_vector(pack[key])
        if viewport is not None and current_viewport != viewport:
            raise HTTPException(422, "Battery icon viewports must match")
        viewport = current_viewport
    for key in ("backgroundXml", "chargingXml", "quickChargingXml"):
        if pack.get(key):
            if not isinstance(pack[key], str) or validate_vector(pack[key]) != viewport:
                raise HTTPException(422, "Battery icon viewports must match")
    if not isinstance(pack.get("name"), str) or not pack["name"].strip():
        raise HTTPException(422, "Battery package name is required")


def icon_result(connection, row, device_id):
    likes = connection.execute("SELECT COUNT(*) FROM likes WHERE icon_id = ?", (row["id"],)).fetchone()[0]
    liked = connection.execute("SELECT 1 FROM likes WHERE icon_id = ? AND device_id = ?",
                               (row["id"], device_id)).fetchone() is not None
    return {"id": row["id"], "type": row["type"], "name": row["name"],
            "payload": row["payload"], "likes": likes, "liked": liked,
            "createdAt": row["created_at"], "owned": row["owner_device_id"] == device_id,
            "downloadCount": row["download_count"]}


@app.get("/healthz")
def health():
    with database() as connection:
        connection.execute("SELECT 1")
    return {"ok": True}


@app.get("/v1/icons")
def list_icons(type: str = Query(...), sort: str = Query("popular"),
               limit: int = Query(40, ge=1, le=60), x_device_id: str = Header(None)):
    if type not in ALLOWED_TYPES or sort not in {"popular", "latest"}:
        raise HTTPException(422, "Invalid icon query")
    device_id = require_device(x_device_id)
    ordering = "like_count DESC, i.created_at DESC" if sort == "popular" else "i.created_at DESC"
    with database() as connection:
        rows = connection.execute(f"""
            SELECT i.*, COUNT(l.device_id) AS like_count FROM icons i
            LEFT JOIN likes l ON l.icon_id = i.id
            WHERE i.type = ?
            GROUP BY i.id
            ORDER BY {ordering}
            LIMIT ?
        """, (type, limit)).fetchall()
        return {"items": [icon_result(connection, row, device_id) for row in rows]}


@app.get("/v1/icons/{icon_id}")
def get_icon(icon_id: str, x_device_id: str = Header(None)):
    device_id = require_device(x_device_id)
    with database() as connection:
        row = connection.execute("SELECT * FROM icons WHERE id = ?", (icon_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Icon not found")
        return {"item": icon_result(connection, row, device_id)}


@app.post("/v1/icons")
def upload_icon(body: Upload, x_device_id: str = Header(None)):
    device_id = require_device(x_device_id)
    name = body.name.strip()
    if not name or CONTROL_CHARS.search(name):
        raise HTTPException(422, "Invalid icon name")
    validate_payload(body.type, body.payload)
    timestamp = now()
    digest = hashlib.sha256(body.payload.encode("utf-8")).hexdigest()
    with database() as connection:
        connection.execute("BEGIN IMMEDIATE")
        current = connection.execute("SELECT * FROM upload_limits WHERE device_id = ?",
                                     (device_id,)).fetchone()
        if current is None or timestamp - datetime.fromisoformat(current["window_started"]) >= timedelta(hours=1):
            if current is None:
                device_count = connection.execute("SELECT COUNT(*) FROM upload_limits").fetchone()[0]
                if device_count >= MAX_TRACKED_DEVICES:
                    raise HTTPException(429, "Online icon library device limit reached")
                ensure_storage_space()
            connection.execute("INSERT OR REPLACE INTO upload_limits VALUES (?, ?, 1)",
                               (device_id, timestamp.isoformat()))
        elif current["upload_count"] >= MAX_UPLOADS_PER_HOUR:
            raise HTTPException(429, "Upload limit reached; try again later")
        else:
            connection.execute("UPDATE upload_limits SET upload_count = upload_count + 1 WHERE device_id = ?",
                               (device_id,))
        row = connection.execute("SELECT * FROM icons WHERE type = ? AND payload_sha256 = ?",
                                 (body.type, digest)).fetchone()
        if row is None:
            icon_count = connection.execute("SELECT COUNT(*) FROM icons").fetchone()[0]
            if icon_count >= MAX_ICONS:
                raise HTTPException(507, "Online icon library icon limit reached")
            ensure_storage_space(len(body.payload.encode("utf-8")))
            icon_id = str(uuid.uuid4())
            connection.execute("""INSERT INTO icons
                (id, type, name, payload, payload_sha256, created_at, owner_device_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)""",
                               (icon_id, body.type, name, body.payload, digest,
                                timestamp.isoformat(), device_id))
            row = connection.execute("SELECT * FROM icons WHERE id = ?", (icon_id,)).fetchone()
        return {"item": icon_result(connection, row, device_id)}


@app.delete("/v1/icons/{icon_id}")
def delete_icon(icon_id: str, x_device_id: str = Header(None)):
    device_id = require_device(x_device_id)
    with database() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute("SELECT owner_device_id FROM icons WHERE id = ?", (icon_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Icon not found")
        if row["owner_device_id"] != device_id:
            raise HTTPException(403, "Only the uploader can delete this icon")
        connection.execute("DELETE FROM icons WHERE id = ?", (icon_id,))
        return {"deleted": True}


@app.post("/v1/icons/{icon_id}/download")
def record_download(icon_id: str, x_device_id: str = Header(None)):
    require_device(x_device_id)
    with database() as connection:
        connection.execute("BEGIN IMMEDIATE")
        connection.execute("UPDATE icons SET download_count = download_count + 1 WHERE id = ?", (icon_id,))
        row = connection.execute("SELECT * FROM icons WHERE id = ?", (icon_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Icon not found")
        return {"downloadCount": row["download_count"]}


@app.api_route("/v1/icons/{icon_id}/like", methods=["PUT", "DELETE"])
def set_like(icon_id: str, request: Request, x_device_id: str = Header(None)):
    device_id = require_device(x_device_id)
    with database() as connection:
        connection.execute("BEGIN IMMEDIATE")
        row = connection.execute("SELECT * FROM icons WHERE id = ?", (icon_id,)).fetchone()
        if row is None:
            raise HTTPException(404, "Icon not found")
        if request.method == "DELETE":
            connection.execute("DELETE FROM likes WHERE icon_id = ? AND device_id = ?", (icon_id, device_id))
        else:
            existing = connection.execute("SELECT 1 FROM likes WHERE icon_id = ? AND device_id = ?",
                                          (icon_id, device_id)).fetchone()
            if existing is None:
                like_count = connection.execute("SELECT COUNT(*) FROM likes").fetchone()[0]
                if like_count >= MAX_LIKES:
                    raise HTTPException(507, "Online icon library like limit reached")
                ensure_storage_space()
                connection.execute("INSERT INTO likes VALUES (?, ?, ?)",
                                   (icon_id, device_id, now().isoformat()))
        return {"item": icon_result(connection, row, device_id), "type": row["type"]}
