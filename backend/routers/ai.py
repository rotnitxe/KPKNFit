"""AI proxy endpoints with SSE streaming support."""
from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from engines.ai_engine import generate_content, generate_content_stream

router = APIRouter(prefix="/ai", tags=["ai"])


class GenerateRequest(BaseModel):
    provider: str = "gemini"
    prompt: str
    systemInstruction: str | None = None
    messages: list[dict] | None = None
    jsonMode: bool = False
    temperature: float = 0.7
    maxTokens: int = 4096


class StreamRequest(BaseModel):
    provider: str = "gemini"
    prompt: str
    systemInstruction: str | None = None
    messages: list[dict] | None = None
    temperature: float = 0.7
    maxTokens: int = 4096


@router.post("/generate")
async def ai_generate(req: GenerateRequest):
    return await generate_content(
        provider=req.provider,
        prompt=req.prompt,
        system_instruction=req.systemInstruction,
        messages=req.messages,
        json_mode=req.jsonMode,
        temperature=req.temperature,
        max_tokens=req.maxTokens,
    )


@router.post("/stream")
async def ai_stream(req: StreamRequest):
    return StreamingResponse(
        generate_content_stream(
            provider=req.provider,
            prompt=req.prompt,
            system_instruction=req.systemInstruction,
            messages=req.messages,
            temperature=req.temperature,
            max_tokens=req.maxTokens,
        ),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
