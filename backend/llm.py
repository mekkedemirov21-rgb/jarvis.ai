"""Local-only LLM adapter for Ollama and LM Studio.

Prompts are sent only to a loopback endpoint. No cloud provider is supported
by this module. The model has no direct access to tools or external actions.
"""
from __future__ import annotations

import os
from typing import Any
from urllib.parse import urlparse

import httpx


class LocalModelUnavailable(RuntimeError):
    """The configured local inference server could not be reached."""


class LocalModelTimeout(RuntimeError):
    """The local inference server exceeded the request timeout."""


class LocalModelError(RuntimeError):
    """The local inference server returned an invalid or unsuccessful response."""


LLM_PROVIDER = os.getenv("JARVIS_LLM_PROVIDER", "ollama").strip().lower()
LLM_MODEL = os.getenv("JARVIS_LLM_MODEL", os.getenv("OLLAMA_MODEL", "qwen3.5:4b")).strip()
OLLAMA_URL = os.getenv("OLLAMA_BASE_URL", "http://127.0.0.1:11434").strip().rstrip("/")
LMSTUDIO_URL = os.getenv("LMSTUDIO_BASE_URL", "http://127.0.0.1:1234/v1").strip().rstrip("/")
LMSTUDIO_API_KEY = os.getenv("LMSTUDIO_API_KEY", "").strip()

if LLM_PROVIDER not in {"ollama", "lmstudio"}:
    raise RuntimeError("JARVIS_LLM_PROVIDER must be either 'ollama' or 'lmstudio'.")
if not LLM_MODEL:
    raise RuntimeError("JARVIS_LLM_MODEL must name a model loaded in the local inference server.")


def _validate_loopback(base_url: str, label: str) -> None:
    parsed = urlparse(base_url)
    if (
        parsed.scheme not in {"http", "https"}
        or parsed.hostname not in {"127.0.0.1", "localhost", "::1"}
        or parsed.username is not None
        or parsed.password is not None
        or parsed.query
        or parsed.fragment
    ):
        raise RuntimeError(f"{label} must point to a loopback address without URL credentials/query/fragment.")


_validate_loopback(OLLAMA_URL, "OLLAMA_BASE_URL")
_validate_loopback(LMSTUDIO_URL, "LMSTUDIO_BASE_URL")


def provider_status() -> dict[str, Any]:
    """Expose non-sensitive local provider metadata, never model secrets or API keys."""
    return {
        "provider": "LM Studio" if LLM_PROVIDER == "lmstudio" else "Ollama",
        "model": LLM_MODEL,
        "mode": "local-only",
        "promptsStoredByApi": False,
        "toolsEnabled": False,
    }


def _build_request(messages: list[dict[str, str]], json_mode: bool = False) -> tuple[str, dict[str, Any], dict[str, str]]:
    headers = {"Accept": "application/json", "Content-Type": "application/json"}
    normalized_messages = [{"role": str(m["role"]), "content": str(m["content"])} for m in messages]
    if LLM_PROVIDER == "ollama":
        payload: dict[str, Any] = {
            "model": LLM_MODEL,
            "messages": normalized_messages,
            "stream": False,
        }
        if json_mode:
            payload["format"] = "json"
        return f"{OLLAMA_URL}/api/chat", payload, headers

    payload = {
        "model": LLM_MODEL,
        "messages": normalized_messages,
        "stream": False,
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    if LMSTUDIO_API_KEY:
        headers["Authorization"] = f"Bearer {LMSTUDIO_API_KEY}"
    return f"{LMSTUDIO_URL}/chat/completions", payload, headers


def _extract_content(data: Any) -> str:
    if not isinstance(data, dict):
        raise LocalModelError("The local model returned an invalid response.")
    if LLM_PROVIDER == "ollama":
        message = data.get("message", {})
        content = message.get("content") if isinstance(message, dict) else None
    else:
        choices = data.get("choices", [])
        first = choices[0] if isinstance(choices, list) and choices else {}
        message = first.get("message", {}) if isinstance(first, dict) else {}
        content = message.get("content") if isinstance(message, dict) else None
    if isinstance(content, list):
        # Some OpenAI-compatible servers return content blocks for multimodal responses.
        content = "".join(
            str(block.get("text", "")) for block in content
            if isinstance(block, dict) and block.get("type") in {"text", "output_text"}
        )
    if not isinstance(content, str) or not content.strip():
        raise LocalModelError("The local model returned an empty or unsupported response.")
    return content


async def chat_async(messages: list[dict[str, str]], *, json_mode: bool = False, timeout: float = 120.0) -> str:
    """Call the selected local provider asynchronously. No prompt is logged or stored."""
    url, payload, headers = _build_request(messages, json_mode)
    try:
        async with httpx.AsyncClient(timeout=httpx.Timeout(timeout, connect=3.0), follow_redirects=False) as client:
            response = await client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            return _extract_content(response.json())
    except httpx.ConnectError as exc:
        raise LocalModelUnavailable("The selected local model server is not reachable.") from exc
    except httpx.TimeoutException as exc:
        raise LocalModelTimeout("The local model request timed out.") from exc
    except httpx.HTTPStatusError as exc:
        # Never reflect provider response bodies; they may include input echoes or internal details.
        raise LocalModelError("The local model server returned an error.") from exc
    except (ValueError, KeyError, TypeError) as exc:
        raise LocalModelError("The local model returned invalid JSON.") from exc


def chat_sync(messages: list[dict[str, str]], *, json_mode: bool = False, timeout: float = 120.0) -> str:
    """Synchronous equivalent for scheduled jobs and connector callbacks."""
    url, payload, headers = _build_request(messages, json_mode)
    try:
        with httpx.Client(timeout=httpx.Timeout(timeout, connect=3.0), follow_redirects=False) as client:
            response = client.post(url, json=payload, headers=headers)
            response.raise_for_status()
            return _extract_content(response.json())
    except httpx.ConnectError as exc:
        raise LocalModelUnavailable("The selected local model server is not reachable.") from exc
    except httpx.TimeoutException as exc:
        raise LocalModelTimeout("The local model request timed out.") from exc
    except httpx.HTTPStatusError as exc:
        raise LocalModelError("The local model server returned an error.") from exc
    except (ValueError, KeyError, TypeError) as exc:
        raise LocalModelError("The local model returned invalid JSON.") from exc
