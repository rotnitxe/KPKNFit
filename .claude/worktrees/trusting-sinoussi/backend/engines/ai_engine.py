"""Unified AI proxy for cloud providers and local Ollama models."""
from __future__ import annotations

import json
import os
from typing import Any, AsyncIterator

import httpx

PROVIDER_CONFIGS = {
    "gemini": {
        "url": "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent",
        "stream_url": "https://generativelanguage.googleapis.com/v1beta/models/{model}:streamGenerateContent?alt=sse",
        "default_model": "gemini-2.0-flash",
        "env_key": "GEMINI_API_KEY",
    },
    "gpt": {
        "url": "https://api.openai.com/v1/chat/completions",
        "stream_url": "https://api.openai.com/v1/chat/completions",
        "default_model": "gpt-4o-mini",
        "env_key": "OPENAI_API_KEY",
    },
    "deepseek": {
        "url": "https://api.deepseek.com/chat/completions",
        "stream_url": "https://api.deepseek.com/chat/completions",
        "default_model": "deepseek-chat",
        "env_key": "DEEPSEEK_API_KEY",
    },
    "ollama": {
        "default_model": "gemma3:4b",
    },
}


def _safe_json_parse(text: str) -> dict:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.split("\n")
        lines = [line for line in lines if not line.strip().startswith("```")]
        cleaned = "\n".join(lines)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        return {"text": cleaned}


def _resolve_ollama_host(host: str | None = None) -> str:
    return (host or os.getenv("OLLAMA_HOST") or "http://localhost:11434").rstrip("/")


def _resolve_model(provider: str, model: str | None = None) -> str:
    if model:
        return model
    if provider == "ollama":
        return os.getenv("OLLAMA_MODEL") or PROVIDER_CONFIGS["ollama"]["default_model"]
    cfg = PROVIDER_CONFIGS.get(provider)
    if not cfg:
        raise ValueError(f"Unknown provider: {provider}")
    return cfg["default_model"]


def _get_api_key(provider: str) -> str:
    cfg = PROVIDER_CONFIGS.get(provider)
    if not cfg or provider == "ollama":
        return ""
    key = os.getenv(cfg["env_key"], "")
    if not key:
        raise ValueError(f"Missing env var {cfg['env_key']} for provider {provider}")
    return key


def _message_text(message: dict[str, Any]) -> str:
    parts = message.get("parts")
    if isinstance(parts, list) and parts:
        return parts[0].get("text", "")
    return str(message.get("content", ""))


def _build_chat_messages(
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
) -> list[dict[str, str]]:
    built: list[dict[str, str]] = []
    if system_instruction:
        built.append({"role": "system", "content": system_instruction})

    for message in messages or []:
        role = message.get("role", "user")
        built.append(
            {
                "role": "user" if role == "user" else "assistant",
                "content": _message_text(message),
            }
        )

    built.append({"role": "user", "content": prompt})
    return built


def _build_gemini_payload(
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    json_mode: bool = False,
    temperature: float = 0.7,
    max_tokens: int = 4096,
) -> dict:
    contents = []

    for message in messages or []:
        role = "user" if message.get("role") == "user" else "model"
        contents.append({"role": role, "parts": [{"text": _message_text(message)}]})

    contents.append({"role": "user", "parts": [{"text": prompt}]})

    payload: dict[str, Any] = {
        "contents": contents,
        "generationConfig": {
            "temperature": temperature,
            "maxOutputTokens": max_tokens,
        },
    }
    if system_instruction:
        payload["systemInstruction"] = {"parts": [{"text": system_instruction}]}
    if json_mode:
        payload["generationConfig"]["responseMimeType"] = "application/json"
    return payload


def _build_openai_payload(
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    json_mode: bool = False,
    temperature: float = 0.7,
    max_tokens: int = 4096,
    stream: bool = False,
    model: str = "gpt-4o-mini",
) -> dict:
    payload: dict[str, Any] = {
        "model": model,
        "messages": _build_chat_messages(prompt, system_instruction, messages),
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": stream,
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    return payload


def _build_ollama_payload(
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    *,
    temperature: float = 0.7,
    stream: bool = False,
    model: str,
    json_mode: bool = False,
) -> dict:
    payload: dict[str, Any] = {
        "model": model,
        "messages": _build_chat_messages(prompt, system_instruction, messages),
        "stream": stream,
        "options": {"temperature": temperature},
        "keep_alive": "5m",
    }
    if json_mode:
        payload["format"] = "json"
    return payload


async def get_provider_status() -> dict:
    host = _resolve_ollama_host()
    response: dict[str, Any] = {
        "backend": True,
        "providers": {
            "ollama": {
                "available": False,
                "host": host,
                "models": [],
            }
        },
    }

    try:
        async with httpx.AsyncClient(timeout=2.5) as client:
            result = await client.get(f"{host}/api/tags")
            result.raise_for_status()
            data = result.json()
            models = []
            for model in data.get("models", []):
                name = model.get("model") or model.get("name")
                if name:
                    models.append(name)

            response["providers"]["ollama"] = {
                "available": True,
                "host": host,
                "models": models,
            }
    except Exception as error:  # pragma: no cover - network-dependent
        response["providers"]["ollama"]["error"] = str(error)

    return response


async def generate_content(
    provider: str,
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    json_mode: bool = False,
    temperature: float = 0.7,
    max_tokens: int = 4096,
    model: str | None = None,
    host: str | None = None,
) -> dict:
    resolved_model = _resolve_model(provider, model)

    async with httpx.AsyncClient(timeout=60.0) as client:
        if provider == "gemini":
            key = _get_api_key(provider)
            cfg = PROVIDER_CONFIGS[provider]
            url = cfg["url"].format(model=resolved_model) + f"?key={key}"
            payload = _build_gemini_payload(prompt, system_instruction, messages, json_mode, temperature, max_tokens)
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            text = data.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
        elif provider == "ollama":
            url = f"{_resolve_ollama_host(host)}/api/chat"
            payload = _build_ollama_payload(
                prompt,
                system_instruction,
                messages,
                temperature=temperature,
                stream=False,
                model=resolved_model,
                json_mode=json_mode,
            )
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            text = data.get("message", {}).get("content", "")
        else:
            key = _get_api_key(provider)
            cfg = PROVIDER_CONFIGS[provider]
            headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
            payload = _build_openai_payload(
                prompt,
                system_instruction,
                messages,
                json_mode,
                temperature,
                max_tokens,
                False,
                resolved_model,
            )
            resp = await client.post(cfg["url"], json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            text = data.get("choices", [{}])[0].get("message", {}).get("content", "")

    if json_mode:
        return _safe_json_parse(text)
    return {"text": text}


async def generate_content_stream(
    provider: str,
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    temperature: float = 0.7,
    max_tokens: int = 4096,
    model: str | None = None,
    host: str | None = None,
) -> AsyncIterator[str]:
    resolved_model = _resolve_model(provider, model)

    async with httpx.AsyncClient(timeout=120.0) as client:
        if provider == "gemini":
            key = _get_api_key(provider)
            cfg = PROVIDER_CONFIGS[provider]
            url = cfg["stream_url"].format(model=resolved_model) + f"&key={key}"
            payload = _build_gemini_payload(prompt, system_instruction, messages, False, temperature, max_tokens)
            async with client.stream("POST", url, json=payload) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if line.startswith("data: "):
                        try:
                            chunk = json.loads(line[6:])
                            text = chunk.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                            if text:
                                yield f"data: {json.dumps({'text': text})}\n\n"
                        except (json.JSONDecodeError, IndexError, KeyError):
                            continue
        elif provider == "ollama":
            url = f"{_resolve_ollama_host(host)}/api/chat"
            payload = _build_ollama_payload(
                prompt,
                system_instruction,
                messages,
                temperature=temperature,
                stream=True,
                model=resolved_model,
            )
            async with client.stream("POST", url, json=payload) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if not line:
                        continue
                    try:
                        chunk = json.loads(line)
                    except json.JSONDecodeError:
                        continue

                    if chunk.get("done"):
                        break

                    text = chunk.get("message", {}).get("content", "")
                    if text:
                        yield f"data: {json.dumps({'text': text})}\n\n"
        else:
            key = _get_api_key(provider)
            cfg = PROVIDER_CONFIGS[provider]
            headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
            payload = _build_openai_payload(
                prompt,
                system_instruction,
                messages,
                False,
                temperature,
                max_tokens,
                True,
                resolved_model,
            )
            async with client.stream("POST", cfg["stream_url"], json=payload, headers=headers) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if line.startswith("data: ") and line.strip() != "data: [DONE]":
                        try:
                            chunk = json.loads(line[6:])
                            text = chunk.get("choices", [{}])[0].get("delta", {}).get("content", "")
                            if text:
                                yield f"data: {json.dumps({'text': text})}\n\n"
                        except (json.JSONDecodeError, IndexError, KeyError):
                            continue

    yield "data: [DONE]\n\n"
