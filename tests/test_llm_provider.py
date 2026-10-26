"""Provider adapter tests without requiring a running local model server."""
from __future__ import annotations

from backend import llm


def test_ollama_request_is_local_and_json_mode_is_enabled(monkeypatch) -> None:
    monkeypatch.setattr(llm, "LLM_PROVIDER", "ollama")
    monkeypatch.setattr(llm, "LLM_MODEL", "qwen3.5:4b")
    monkeypatch.setattr(llm, "OLLAMA_URL", "http://127.0.0.1:11434")
    url, payload, headers = llm._build_request(
        [{"role": "system", "content": "classify"}, {"role": "user", "content": "text"}],
        json_mode=True,
    )
    assert url == "http://127.0.0.1:11434/api/chat"
    assert payload["model"] == "qwen3.5:4b"
    assert payload["stream"] is False
    assert payload["format"] == "json"
    assert len(payload["messages"]) == 2
    assert "Authorization" not in headers


def test_lm_studio_request_uses_openai_compatible_route(monkeypatch) -> None:
    monkeypatch.setattr(llm, "LLM_PROVIDER", "lmstudio")
    monkeypatch.setattr(llm, "LLM_MODEL", "my-local-model")
    monkeypatch.setattr(llm, "LMSTUDIO_URL", "http://127.0.0.1:1234/v1")
    monkeypatch.setattr(llm, "LMSTUDIO_API_KEY", "")
    url, payload, headers = llm._build_request(
        [{"role": "user", "content": "Hello"}], json_mode=True
    )
    assert url == "http://127.0.0.1:1234/v1/chat/completions"
    assert payload["model"] == "my-local-model"
    assert payload["response_format"] == {"type": "json_object"}
    assert "Authorization" not in headers


def test_lm_studio_key_is_only_sent_as_local_server_auth(monkeypatch) -> None:
    monkeypatch.setattr(llm, "LLM_PROVIDER", "lmstudio")
    monkeypatch.setattr(llm, "LLM_MODEL", "my-local-model")
    monkeypatch.setattr(llm, "LMSTUDIO_URL", "http://127.0.0.1:1234/v1")
    monkeypatch.setattr(llm, "LMSTUDIO_API_KEY", "local-secret")
    url, _, headers = llm._build_request([{"role": "user", "content": "Hello"}])
    assert url.startswith("http://127.0.0.1:")
    assert headers["Authorization"] == "Bearer local-secret"


def test_content_parser_handles_both_provider_shapes(monkeypatch) -> None:
    monkeypatch.setattr(llm, "LLM_PROVIDER", "ollama")
    assert llm._extract_content({"message": {"content": "Ollama response"}}) == "Ollama response"
    monkeypatch.setattr(llm, "LLM_PROVIDER", "lmstudio")
    assert llm._extract_content({"choices": [{"message": {"content": "LM Studio response"}}]}) == "LM Studio response"
