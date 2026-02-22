"""KPKN Fit – Backend API (FastAPI)
Motores de volumen, fatiga, recuperación, análisis y motor adaptativo AUGE.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import volume, fatigue, recovery, analysis, ai, adaptive

app = FastAPI(
    title="KPKN Engine API",
    version="0.2.0",
    description=(
        "Motores de volumen, fatiga, recuperación y análisis para KPKN Fit. "
        "Incluye motor adaptativo AUGE con inferencia bayesiana, "
        "procesos gaussianos y modelo ODE Banister."
    ),
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
app.include_router(adaptive.router, prefix="/api")


@app.get("/health")
def health():
    return {
        "status": "ok",
        "engine": "KPKN v0.2.0",
        "adaptive": "AUGE Adaptive v1.0.0",
    }
