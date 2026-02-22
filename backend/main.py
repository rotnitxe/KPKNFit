"""KPKN Fit – Backend API (FastAPI)
Ports the volume, fatigue, recovery and analysis engines from TypeScript to Python.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import volume, fatigue, recovery, analysis, ai

app = FastAPI(
    title="KPKN Engine API",
    version="0.1.0",
    description="Motores de volumen, fatiga, recuperación y análisis para KPKN Fit.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(volume.router, prefix="/api")
app.include_router(fatigue.router, prefix="/api")
app.include_router(recovery.router, prefix="/api")
app.include_router(analysis.router, prefix="/api")
app.include_router(ai.router, prefix="/api")


@app.get("/health")
def health():
    return {"status": "ok", "engine": "KPKN v0.1.0"}
