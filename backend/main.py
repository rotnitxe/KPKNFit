"""KPKN Fit – Backend API (FastAPI)
Registros y cálculos descriptivos de entrenamiento.
"""
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from routers import volume, analysis, ai, retired

app = FastAPI(
    title="KPKN Engine API",
    version="0.2.0",
    description="Registros y cálculos descriptivos de entrenamiento; servicio opcional.",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(volume.router, prefix="/api")
app.include_router(analysis.router, prefix="/api")
app.include_router(ai.router, prefix="/api")
app.include_router(retired.router)


@app.get("/health")
def health():
    return {
        "status": "ok",
        "engine": "KPKN v0.2.0",
    }
