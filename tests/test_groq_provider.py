import importlib

import pytest

import backend.llm as llm


def test_groq_builds_authenticated_openai_compatible_request(monkeypatch):
    try:
        with monkeypatch.context() as patch:
            patch.setenv("JARVIS_LLM_PROVIDER", "groq")
            patch.setenv("JARVIS_LLM_MODEL", "openai/gpt-oss-20b")
            patch.setenv("GROQ_API_KEY", "test-groq-key-that-is-not-real")
            module = importlib.reload(llm)
            url, payload, headers = module._build_request(
                [{"role": "user", "content": "Hallo"}],
                json_mode=True,
            )
            assert url == "https://api.groq.com/openai/v1/chat/completions"
            assert headers["Authorization"] == "Bearer test-groq-key-that-is-not-real"
            assert payload["model"] == "openai/gpt-oss-20b"
            assert payload["messages"] == [{"role": "user", "content": "Hallo"}]
            assert payload["response_format"] == {"type": "json_object"}
            assert module.provider_status()["mode"] == "cloud"
            assert module.provider_status()["apiKeyConfigured"] is True
    finally:
        # Restore the module configuration after monkeypatch restores the environment.
        importlib.reload(llm)


def test_groq_requires_key_and_uses_a_fixed_endpoint(monkeypatch):
    try:
        with monkeypatch.context() as patch:
            patch.setenv("JARVIS_LLM_PROVIDER", "groq")
            patch.setenv("JARVIS_LLM_MODEL", "openai/gpt-oss-20b")
            patch.delenv("GROQ_API_KEY", raising=False)
            module = importlib.reload(llm)
            with pytest.raises(module.LocalModelUnavailable, match="GROQ_API_KEY"):
                module._build_request([{"role": "user", "content": "test"}])
            assert module.GROQ_URL == "https://api.groq.com/openai/v1"
    finally:
        importlib.reload(llm)
