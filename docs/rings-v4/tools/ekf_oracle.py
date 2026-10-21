"""Oráculo independiente del actualizador EKF de 3.2 (plan 6.6 + DECISIONES DEL ORQUESTADOR del brief 3.2).

NO comparte código con el Kotlin. Cada vector da: entrada completa y salida (m', P', estado). La prueba Kotlin
`AugeLearnerOracleVectorsTest` debe reproducirlos a 1e-9 usando un modelo de observación de juguete cerrado:

    h(lnTau) = 100 * exp(-(R / C) * exp(-dt / exp(lnTau)))      (batería prevista en puntos)

con (R/C, dt) por vector. Es decreciente en τ (más lento => menos batería): H < 0 en el régimen normal.
"""
import math

P0 = 0.35 ** 2          # 0,1225
Q_PER_DAY = 0.02 ** 2   # 0,0004
H_MIN = 0.1
CLIP = 2.5
STEP = 0.02
TAU_MIN, TAU_MAX = 18.0, 144.0
SAT = 99.5
DEAD_ZONE = 3.0


def make_h(rc, dt):
    return lambda ln_tau: 100.0 * math.exp(-rc * math.exp(-dt / math.exp(ln_tau)))


def make_h_increasing(rc, dt):
    """Juguete NO monótono (crece con τ) para probar la censura por H >= 0."""
    return lambda ln_tau: 100.0 * math.exp(-rc * math.exp(-dt * math.exp(ln_tau) / 1000.0))


def sensitivity(h, m):
    tau = math.exp(m)
    lo, hi = (1.0 - STEP) * tau, (1.0 + STEP) * tau
    if lo < TAU_MIN and hi > TAU_MAX:
        return None, 'sin_sensibilidad'
    if lo < TAU_MIN:                    # diferencia unilateral hacia delante: [m, ln τ+]
        a, b = m, math.log(hi)
    elif hi > TAU_MAX:                  # diferencia unilateral hacia atrás: [ln τ-, m]
        a, b = math.log(lo), m
    else:
        a, b = math.log(lo), math.log(hi)
    ha, hb = h(a), h(b)
    if ha == hb:
        return 0.0, 'sin_sensibilidad'
    return (hb - ha) / (b - a), None


def update(m, p, y, sigma, d_dias, h):
    pred = h(m)
    hh, why = sensitivity(h, m)
    nu = y - pred
    out = dict(m=m, P=p, status='CENSORED', reason=None, nu=nu, H=hh, S=None, K=None, pred=pred)
    if why:
        out['reason'] = why
        return out
    if y >= SAT or pred >= SAT:
        out['reason'] = 'saturada'
        return out
    if abs(nu) < DEAD_ZONE:
        out['reason'] = 'zona_muerta'
        return out
    if abs(hh) < H_MIN:
        out['reason'] = 'sin_sensibilidad'
        return out
    if hh >= 0.0:
        out['reason'] = 'no_monotona'
        return out
    s = hh * hh * p + sigma * sigma
    k = p * hh / s
    nu_clip = max(-CLIP * math.sqrt(s), min(CLIP * math.sqrt(s), nu))
    m2 = m + k * nu_clip
    # Límites efectivos 18–144 h aplicados a la media posterior
    m2 = max(math.log(TAU_MIN), min(math.log(TAU_MAX), m2))
    p2 = (1.0 - k * hh) * p + Q_PER_DAY * max(0.0, d_dias)
    out.update(m=m2, P=p2, status='ADMITTED', S=s, K=k, nu_clip=nu_clip, clipped=(nu_clip != nu))
    return out


VECTORS = [
    # id, m0, P0, y, sigma, dDias, (rc, dt), nota
    ('V1_peor_que_lo_previsto', math.log(48.0), P0, 70.0, 5.0, 0.0, (0.5, 24.0), 'y<h: τ se alarga'),
    ('V2_mejor_que_lo_previsto', math.log(48.0), P0, 78.0, 5.0, 0.0, (0.5, 24.0), 'y>h: τ se acorta'),
    ('V3_recorte_2_5_sqrtS', math.log(48.0), P0, 40.0, 5.0, 0.0, (0.5, 24.0), 'innovación recortada'),
    ('V4_zona_muerta', math.log(48.0), P0, 75.0, 5.0, 0.0, (0.5, 24.0), '|nu|<3: sin cambio'),
    ('V4b_zona_muerta_borde_3', math.log(48.0), P0, 100.0 * math.exp(-0.5 * math.exp(-24.0 / 48.0)) - 3.0, 5.0, 0.0,
     (0.5, 24.0), '|nu|=3 exacto se admite'),
    ('V5_lectura_saturada', math.log(48.0), P0, 99.6, 5.0, 0.0, (0.5, 24.0), 'y>=99.5: sin cambio'),
    ('V6_q_por_dias', math.log(48.0), 0.05, 70.0, 5.0, 10.0, (0.5, 24.0), 'P crece q·10 d tras actualizar'),
    ('V7_H_no_negativa', math.log(48.0), P0, 70.0, 5.0, 0.0, 'inc', 'h creciente: censura no_monotona'),
    ('V8_sin_sensibilidad', math.log(48.0), P0, 50.0, 5.0, 0.0, (0.5, 0.01), '|H|<0.1 con |nu|>=3: censura'),
    ('V9_borde_inferior_18h', math.log(18.2), P0, 60.0, 5.0, 0.0, (0.6, 30.0), 'τ- < 18: diferencia unilateral hacia delante'),
    ('V10_borde_superior_144h', math.log(143.0), P0, 60.0, 5.0, 0.0, (0.6, 60.0), 'τ+ > 144: diferencia unilateral hacia atrás'),
    ('V11_sigma_e1rm_12', math.log(48.0), P0, 70.0, 12.0, 0.0, (0.5, 24.0), 'fuente rendimiento σ=12'),
    ('V12_varianza_chica', math.log(60.0), 0.01, 62.0, 5.0, 3.0, (0.5, 24.0), 'P pequeña: paso corto'),
]

# V13: cuatro lecturas encadenadas sobre el MISMO parámetro (σ=5; mismo modelo de juguete salvo dt por paso).
# (y, dt, dDias): la segunda lectura cae en zona muerta (no cambia nada ni la fecha); la cuarta llega 12 d después.
CHAIN = [(66.0, 24.0, 0.0), (80.5, 36.0, 4.0), (88.0, 18.0, 6.0), (91.0, 30.0, 12.0)]


def chain():
    m, p = math.log(48.0), P0
    rows = []
    for i, (y, dt, dd) in enumerate(CHAIN, 1):
        r = update(m, p, y, 5.0, dd, make_h(0.5, dt))
        rows.append((i, y, dt, dd, r))
        m, p = r['m'], r['P']
    return rows


def main():
    import sys
    sys.stdout.reconfigure(encoding='utf-8')
    print('# vector | m0 | P0 | y | sigma | dDias | modelo | -> estado | m1 | P1 | nu | H | S | K | pred | motivo')
    f = lambda v: 'null' if v is None else repr(float(v))
    for vid, m0, p0, y, sg, dd, mod, note in VECTORS:
        h = make_h_increasing(0.5, 24.0) if mod == 'inc' else make_h(*mod)
        r = update(m0, p0, y, sg, dd, h)
        print(f"{vid} | m0={m0!r} | P0={p0!r} | y={y!r} | sigma={sg} | dDias={dd} | modelo={mod} | {r['status']} | "
              f"m1={r['m']!r} | P1={r['P']!r} | nu={r['nu']!r} | H={f(r['H'])} | S={f(r['S'])} | K={f(r['K'])} | "
              f"pred={r['pred']!r} | {r['reason']} | tau1={math.exp(r['m']):.12g} | {note}")
    print('# V13 cadena (m0=ln 48, P0=0.1225, sigma=5, modelo rc=0.5 y dt por paso) | paso | y | dt | dDias | estado | m | P | tau')
    for i, y, dt, dd, r in chain():
        print(f"V13_paso{i} | y={y} | dt={dt} | dDias={dd} | {r['status']} | m={r['m']!r} | P={r['P']!r} | "
              f"tau={math.exp(r['m']):.12g} | nu={r['nu']!r} | H={f(r['H'])} | {r['reason']}")


if __name__ == '__main__':
    main()
