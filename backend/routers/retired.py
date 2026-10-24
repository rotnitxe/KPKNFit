"""Isolated compatibility routes. No retired engine is imported or evaluated."""
from fastapi import APIRouter, HTTPException
router = APIRouter()
RETIRED_ROUTES = [('/api/fatigue/auge-metrics', 'POST'), ('/api/fatigue/battery-tanks', 'POST'), ('/api/fatigue/set-drain', 'POST'), ('/api/fatigue/session-drain', 'POST'), ('/api/fatigue/completed-stress', 'POST'), ('/api/fatigue/completed-muscular-impact-v2', 'POST'), ('/api/recovery/muscle-battery', 'POST'), ('/api/recovery/systemic-fatigue', 'POST'), ('/api/recovery/daily-readiness', 'POST'), ('/api/recovery/global-batteries', 'POST'), ('/api/recovery/learn-rate', 'POST'), ('/api/adaptive/recovery/update', 'POST'), ('/api/adaptive/recovery/estimate', 'POST'), ('/api/adaptive/fatigue/predict', 'POST'), ('/api/adaptive/banister/solve', 'POST'), ('/api/adaptive/banister/auge', 'POST'), ('/api/adaptive/self-improve', 'POST'), ('/api/adaptive/self-improve/corrections', 'POST'), ('/api/adaptive/status', 'GET'), ('/api/analysis/acwr', 'POST'), ('/api/volume/adjustment', 'POST'), ('/api/volume/adaptive-recalibrate', 'POST')]

def retired():
    raise HTTPException(status_code=410, detail="Esta función fue retirada. Consulta los registros y resultados de entrenamiento.")

for path, method in RETIRED_ROUTES:
    router.add_api_route(path, retired, methods=[method], include_in_schema=False)
