"""Unified AI proxy – routes requests to Gemini, GPT, or DeepSeek.
API keys are stored server-side via environment variables.
Supports SSE streaming for chat-style responses.
"""
from __future__ import annotations
import os
import json
import httpx
from typing import AsyncIterator

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
}


def _get_api_key(provider: str) -> str:
    cfg = PROVIDER_CONFIGS.get(provider)
    if not cfg:
        raise ValueError(f"Unknown provider: {provider}")
    key = os.getenv(cfg["env_key"], "")
    if not key:
        raise ValueError(f"Missing env var {cfg['env_key']} for provider {provider}")
    return key


def _safe_json_parse(text: str) -> dict:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        lines = cleaned.split("\n")
        lines = [l for l in lines if not l.strip().startswith("```")]
        cleaned = "\n".join(lines)
    try:
        return json.loads(cleaned)
    except json.JSONDecodeError:
        return {"text": cleaned}


# ── Build request payload per provider ───────────────────

def _build_gemini_payload(
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    json_mode: bool = False,
    temperature: float = 0.7,
    max_tokens: int = 4096,
) -> dict:
    contents = []

    if messages:
        for m in messages:
            role = "user" if m.get("role") == "user" else "model"
            text = m.get("parts", [{}])[0].get("text", "") if isinstance(m.get("parts"), list) else m.get("content", "")
            contents.append({"role": role, "parts": [{"text": text}]})

    contents.append({"role": "user", "parts": [{"text": prompt}]})

    payload: dict = {
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
    msgs = []
    if system_instruction:
        msgs.append({"role": "system", "content": system_instruction})
    if messages:
        for m in messages:
            role = m.get("role", "user")
            text = m.get("parts", [{}])[0].get("text", "") if isinstance(m.get("parts"), list) else m.get("content", "")
            msgs.append({"role": "user" if role == "user" else "assistant", "content": text})
    msgs.append({"role": "user", "content": prompt})

    payload: dict = {
        "model": model,
        "messages": msgs,
        "temperature": temperature,
        "max_tokens": max_tokens,
        "stream": stream,
    }
    if json_mode:
        payload["response_format"] = {"type": "json_object"}
    return payload


# ── Non-streaming generation ─────────────────────────────

async def generate_content(
    provider: str,
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    json_mode: bool = False,
    temperature: float = 0.7,
    max_tokens: int = 4096,
) -> dict:
    key = _get_api_key(provider)
    cfg = PROVIDER_CONFIGS[provider]

    async with httpx.AsyncClient(timeout=60.0) as client:
        if provider == "gemini":
            url = cfg["url"].format(model=cfg["default_model"]) + f"?key={key}"
            payload = _build_gemini_payload(prompt, system_instruction, messages, json_mode, temperature, max_tokens)
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
            data = resp.json()
            text = data.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
        else:
            url = cfg["url"]
            headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
            payload = _build_openai_payload(prompt, system_instruction, messages, json_mode, temperature, max_tokens, False, cfg["default_model"])
            resp = await client.post(url, json=payload, headers=headers)
            resp.raise_for_status()
            data = resp.json()
            text = data.get("choices", [{}])[0].get("message", {}).get("content", "")

    if json_mode:
        return _safe_json_parse(text)
    return {"text": text}


# ── SSE streaming generation ─────────────────────────────

async def generate_content_stream(
    provider: str,
    prompt: str,
    system_instruction: str | None = None,
    messages: list[dict] | None = None,
    temperature: float = 0.7,
    max_tokens: int = 4096,
) -> AsyncIterator[str]:
    """Yields SSE-formatted chunks: 'data: {"text": "chunk"}\n\n'"""
    key = _get_api_key(provider)
    cfg = PROVIDER_CONFIGS[provider]

    async with httpx.AsyncClient(timeout=120.0) as client:
        if provider == "gemini":
            url = cfg["stream_url"].format(model=cfg["default_model"]) + f"&key={key}"
            payload = _build_gemini_payload(prompt, system_instruction, messages, False, temperature, max_tokens)
            async with client.stream("POST", url, json=payload) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if line.startswith("data: "):
                        try:
                            chunk_data = json.loads(line[6:])
                            text = chunk_data.get("candidates", [{}])[0].get("content", {}).get("parts", [{}])[0].get("text", "")
                            if text:
                                yield f"data: {json.dumps({'text': text})}\n\n"
                        except (json.JSONDecodeError, IndexError):
                            pass
        else:
            url = cfg["stream_url"]
            headers = {"Authorization": f"Bearer {key}", "Content-Type": "application/json"}
            payload = _build_openai_payload(prompt, system_instruction, messages, False, temperature, max_tokens, True, cfg["default_model"])
            async with client.stream("POST", url, json=payload, headers=headers) as resp:
                resp.raise_for_status()
                async for line in resp.aiter_lines():
                    if line.startswith("data: ") and line.strip() != "data: [DONE]":
                        try:
                            chunk_data = json.loads(line[6:])
                            delta = chunk_data.get("choices", [{}])[0].get("delta", {})
                            text = delta.get("content", "")
                            if text:
                                yield f"data: {json.dumps({'text': text})}\n\n"
                        except (json.JSONDecodeError, IndexError):
                            pass

    yield "data: [DONE]\n\n"
