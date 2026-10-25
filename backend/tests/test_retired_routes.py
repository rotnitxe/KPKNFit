import sys
from fastapi.testclient import TestClient
from main import app
from routers.retired import RETIRED_ROUTES

client = TestClient(app)

def test_retired_routes_return_gone_without_evaluating_inputs():
    for path, method in RETIRED_ROUTES:
        response = client.request(method, path, content=b'not even JSON')
        assert response.status_code == 410, (path, response.text)
    assert not any('engines.' + name in sys.modules for name in
                   ('fatigue_engine', 'recovery_engine', 'adaptive_engine', 'banister_model'))

def test_health_and_openapi_do_not_advertise_retired_features():
    assert client.get('/health').json() == {'status': 'ok', 'engine': 'KPKN v0.2.0'}
    paths = client.get('/openapi.json').json()['paths']
    assert all(path not in paths for path, _ in RETIRED_ROUTES)

def test_independent_volume_and_history_remain_available():
    result = client.post('/api/volume/fractional', json={'exercises': [
        {'muscleRole': 'primary', 'sets': 4}, {'muscleRole': 'secondary', 'sets': 2}]})
    assert result.status_code == 200
    assert result.json() == {'fractionalSets': 5.0}
    assert client.post('/api/analysis/tonnage-comparison', json={
        'history': [], 'settings': {}}).status_code == 200
