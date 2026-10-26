"""Small security primitives for the local-only JARVIS service."""
from __future__ import annotations

import base64
import hmac
import os
from functools import lru_cache

from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

_bearer = HTTPBearer(auto_error=False)


def require_api_token(
    credentials: HTTPAuthorizationCredentials | None = Depends(_bearer),
) -> None:
    expected = os.environ.get("JARVIS_API_TOKEN", "")
    supplied = credentials.credentials if credentials else ""
    if len(expected) < 32 or not hmac.compare_digest(supplied, expected):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Valid local API token required.",
            headers={"WWW-Authenticate": "Bearer"},
        )


@lru_cache(maxsize=1)
def _aead() -> AESGCM:
    raw = os.environ.get("JARVIS_DATA_KEY", "")
    try:
        key = base64.urlsafe_b64decode(raw.encode("ascii"))
    except Exception as exc:
        raise RuntimeError(
            "JARVIS_DATA_KEY must be a URL-safe Base64-encoded 32-byte key. "
            "Generate one locally; never store it in Git."
        ) from exc
    if len(key) != 32:
        raise RuntimeError(
            "JARVIS_DATA_KEY must decode to exactly 32 bytes. "
            "Generate one locally; never store it in Git."
        )
    return AESGCM(key)


def encrypt_text(plaintext: str) -> str:
    """AES-256-GCM authenticated encryption. Stored value is nonce || ciphertext."""
    import secrets

    nonce = secrets.token_bytes(12)
    ciphertext = _aead().encrypt(nonce, plaintext.encode("utf-8"), b"jarvis-local-v1")
    return base64.urlsafe_b64encode(nonce + ciphertext).decode("ascii")


def decrypt_text(value: str) -> str:
    raw = base64.urlsafe_b64decode(value.encode("ascii"))
    nonce, ciphertext = raw[:12], raw[12:]
    return _aead().decrypt(nonce, ciphertext, b"jarvis-local-v1").decode("utf-8")
