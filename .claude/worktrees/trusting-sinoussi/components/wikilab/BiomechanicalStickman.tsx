import React, { useMemo } from 'react';

export type LiftType = 'low-bar-squat' | 'sumo-deadlift' | 'bench-press';

export interface LimbLengthsCm {
  femur: number;
  tibia: number;
  torso: number;
  arms: number;
}

export interface PosePreset {
  hipBackRatio: number;
  hipHeightRatio: number;
  barHeightRatio?: number;
}

export interface BiomechanicalStickmanProps {
  heightCm: number;
  lengthsCm: LimbLengthsCm;
  liftType: LiftType;
  loadKg?: number;
  preset: PosePreset;
  className?: string;
}

interface Point {
  x: number;
  y: number;
}

export interface BiomechanicalSolve {
  liftType: LiftType;
  points: {
    ankle: Point;
    knee: Point;
    hip: Point;
    shoulder: Point;
    elbow: Point;
    wrist: Point;
    bar: Point;
  };
  gravityX: number;
  angles: {
    kneeDeg: number;
    hipDeg: number;
  };
  momentArms: {
    kneeCm: number;
    hipCm: number;
  };
}

const clamp = (value: number, min: number, max: number): number => Math.min(max, Math.max(min, value));

const deg = (radians: number): number => (radians * 180) / Math.PI;

const vector = (from: Point, to: Point): Point => ({ x: to.x - from.x, y: to.y - from.y });

const magnitude = (v: Point): number => Math.hypot(v.x, v.y);

const angleBetween = (a: Point, b: Point): number => {
  const denominator = Math.max(1e-6, magnitude(a) * magnitude(b));
  const cosine = clamp((a.x * b.x + a.y * b.y) / denominator, -1, 1);
  return Math.acos(cosine);
};

const solveTwoLink = (origin: Point, target: Point, firstLength: number, secondLength: number): Point => {
  const dx = target.x - origin.x;
  const dy = target.y - origin.y;
  const distance = Math.hypot(dx, dy);
  const clampedDistance = clamp(distance, Math.abs(firstLength - secondLength) + 1e-4, firstLength + secondLength - 1e-4);

  const direction = Math.atan2(dy, dx);
  const cosAlpha = clamp(
    (firstLength * firstLength + clampedDistance * clampedDistance - secondLength * secondLength) /
      (2 * firstLength * clampedDistance),
    -1,
    1
  );
  const alpha = Math.acos(cosAlpha);

  const tibiaAngle = direction - alpha;

  return {
    x: origin.x + firstLength * Math.cos(tibiaAngle),
    y: origin.y + firstLength * Math.sin(tibiaAngle),
  };
};

const buildLowBarSquat = (heightCm: number, lengthsCm: LimbLengthsCm, preset: PosePreset): BiomechanicalSolve => {
  const ankle: Point = { x: 0, y: 0 };
  const hip: Point = {
    x: -heightCm * preset.hipBackRatio,
    y: heightCm * preset.hipHeightRatio,
  };

  const knee = solveTwoLink(ankle, hip, lengthsCm.tibia, lengthsCm.femur);

  const barOffsetFromHip = lengthsCm.torso * 0.85;
  const barCos = clamp(-hip.x / Math.max(1, barOffsetFromHip), -0.95, 0.95);
  const torsoAngle = Math.acos(barCos);

  const shoulder: Point = {
    x: hip.x + lengthsCm.torso * Math.cos(torsoAngle),
    y: hip.y + lengthsCm.torso * Math.sin(torsoAngle),
  };

  const bar: Point = {
    x: hip.x + barOffsetFromHip * Math.cos(torsoAngle),
    y: hip.y + barOffsetFromHip * Math.sin(torsoAngle),
  };

  const wrist: Point = {
    x: bar.x + 3,
    y: bar.y - 6,
  };

  const elbow: Point = {
    x: (shoulder.x + wrist.x) / 2 + 2,
    y: (shoulder.y + wrist.y) / 2 - 2,
  };

  const kneeAngle = angleBetween(vector(knee, ankle), vector(knee, hip));
  const hipAngle = angleBetween(vector(hip, knee), vector(hip, shoulder));

  const gravityX = bar.x;

  return {
    liftType: 'low-bar-squat',
    points: { ankle, knee, hip, shoulder, elbow, wrist, bar },
    gravityX,
    angles: {
      kneeDeg: deg(kneeAngle),
      hipDeg: deg(hipAngle),
    },
    momentArms: {
      kneeCm: Math.abs(gravityX - knee.x),
      hipCm: Math.abs(gravityX - hip.x),
    },
  };
};

const buildSumoDeadlift = (heightCm: number, lengthsCm: LimbLengthsCm, preset: PosePreset): BiomechanicalSolve => {
  const ankle: Point = { x: 0, y: 0 };
  const hip: Point = {
    x: -heightCm * preset.hipBackRatio,
    y: heightCm * preset.hipHeightRatio,
  };

  const knee = solveTwoLink(ankle, hip, lengthsCm.tibia, lengthsCm.femur);

  const bar: Point = {
    x: 0,
    y: heightCm * (preset.barHeightRatio ?? 0.04),
  };

  const shoulderX = clamp(bar.x, hip.x - lengthsCm.torso * 0.55, hip.x + lengthsCm.torso * 0.55);
  const shoulderDx = shoulderX - hip.x;
  const shoulderDy = Math.sqrt(Math.max(1e-6, lengthsCm.torso * lengthsCm.torso - shoulderDx * shoulderDx));

  const shoulder: Point = {
    x: hip.x + shoulderDx,
    y: hip.y + shoulderDy,
  };

  const wrist: Point = { x: bar.x, y: bar.y };

  const elbow: Point = {
    x: (shoulder.x + wrist.x) / 2,
    y: (shoulder.y + wrist.y) / 2,
  };

  const kneeAngle = angleBetween(vector(knee, ankle), vector(knee, hip));
  const hipAngle = angleBetween(vector(hip, knee), vector(hip, shoulder));

  const gravityX = bar.x;

  return {
    liftType: 'sumo-deadlift',
    points: { ankle, knee, hip, shoulder, elbow, wrist, bar },
    gravityX,
    angles: {
      kneeDeg: deg(kneeAngle),
      hipDeg: deg(hipAngle),
    },
    momentArms: {
      kneeCm: Math.abs(gravityX - knee.x),
      hipCm: Math.abs(gravityX - hip.x),
    },
  };
};

const buildBenchPress = (heightCm: number, lengthsCm: LimbLengthsCm): BiomechanicalSolve => {
  const ankle: Point = { x: -heightCm * 0.22, y: 0 };
  const knee: Point = { x: -heightCm * 0.14, y: heightCm * 0.12 };
  const hip: Point = { x: -heightCm * 0.02, y: heightCm * 0.11 };
  const shoulder: Point = { x: heightCm * 0.08, y: heightCm * 0.12 };
  const wrist: Point = { x: heightCm * 0.08, y: heightCm * 0.45 };
  const elbow: Point = {
    x: (shoulder.x + wrist.x) / 2 + 4,
    y: (shoulder.y + wrist.y) / 2,
  };
  const bar: Point = { x: wrist.x, y: wrist.y };

  const kneeAngle = angleBetween(vector(knee, ankle), vector(knee, hip));
  const hipAngle = angleBetween(vector(hip, knee), vector(hip, shoulder));
  const gravityX = bar.x;

  return {
    liftType: 'bench-press',
    points: { ankle, knee, hip, shoulder, elbow, wrist, bar },
    gravityX,
    angles: {
      kneeDeg: deg(kneeAngle),
      hipDeg: deg(hipAngle),
    },
    momentArms: {
      kneeCm: Math.abs(gravityX - knee.x),
      hipCm: Math.abs(gravityX - hip.x),
    },
  };
};

export const calculateBiomechanicalPose = (
  heightCm: number,
  lengthsCm: LimbLengthsCm,
  liftType: LiftType,
  preset: PosePreset
): BiomechanicalSolve => {
  if (liftType === 'sumo-deadlift') return buildSumoDeadlift(heightCm, lengthsCm, preset);
  if (liftType === 'bench-press') return buildBenchPress(heightCm, lengthsCm);
  return buildLowBarSquat(heightCm, lengthsCm, preset);
};

const asSvgPoint = (point: Point): Point => ({ x: point.x, y: -point.y });

const Bone: React.FC<{ a: Point; b: Point; className?: string }> = ({ a, b, className = '' }) => (
  <line
    x1={a.x}
    y1={a.y}
    x2={b.x}
    y2={b.y}
    className={className}
    stroke="currentColor"
    strokeWidth={2.2}
    strokeLinecap="round"
  />
);

const Joint: React.FC<{ p: Point; label: string }> = ({ p, label }) => (
  <g>
    <rect
      x={p.x - 1.7}
      y={p.y - 1.7}
      width={3.4}
      height={3.4}
      fill="#FDFCFE"
      stroke="#0B1220"
      strokeWidth={0.7}
      rx={0.4}
    />
    <text x={p.x + 2.8} y={p.y - 2.2} fontSize={2.8} fill="#334155" fontWeight={700}>
      {label}
    </text>
  </g>
);

export const BiomechanicalStickman: React.FC<BiomechanicalStickmanProps> = ({
  heightCm,
  lengthsCm,
  liftType,
  loadKg,
  preset,
  className = '',
}) => {
  const solve = useMemo(
    () => calculateBiomechanicalPose(heightCm, lengthsCm, liftType, preset),
    [heightCm, lengthsCm, liftType, preset]
  );

  const ankle = asSvgPoint(solve.points.ankle);
  const knee = asSvgPoint(solve.points.knee);
  const hip = asSvgPoint(solve.points.hip);
  const shoulder = asSvgPoint(solve.points.shoulder);
  const elbow = asSvgPoint(solve.points.elbow);
  const wrist = asSvgPoint(solve.points.wrist);
  const bar = asSvgPoint(solve.points.bar);

  const gravityTop: Point = { x: bar.x, y: -Math.max(2, solve.points.bar.y + 10) };
  const gravityBottom: Point = { x: bar.x, y: 0 };

  const kneeMomentStart: Point = { x: knee.x, y: knee.y };
  const kneeMomentEnd: Point = { x: solve.gravityX, y: knee.y };
  const hipMomentStart: Point = { x: hip.x, y: hip.y };
  const hipMomentEnd: Point = { x: solve.gravityX, y: hip.y };

  const loadLabel = loadKg ? `${loadKg}kg` : 'Load';

  return (
    <div className={`w-full rounded-[28px] border border-black/[0.08] bg-[#FAFBFF] shadow-[0_18px_40px_-24px_rgba(15,23,42,0.55)] ${className}`}>
      <svg viewBox="-95 -135 190 150" className="w-full h-full rounded-[28px]" role="img" aria-label="Palitos biomecánicos">
        <defs>
          <marker id="arrowTip" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto" markerUnits="strokeWidth">
            <path d="M0,0 L6,3 L0,6 Z" fill="#0EA5E9" />
          </marker>
        </defs>

        <rect x={-95} y={-135} width={190} height={150} fill="#FAFBFF" />

        <line x1={-88} y1={0} x2={88} y2={0} stroke="#CBD5E1" strokeWidth={1} />

        <line
          x1={gravityTop.x}
          y1={gravityTop.y}
          x2={gravityBottom.x}
          y2={gravityBottom.y}
          stroke="#EF4444"
          strokeWidth={1.2}
          strokeDasharray="2.5 2"
        />

        <Bone a={ankle} b={knee} className="text-slate-900" />
        <Bone a={knee} b={hip} className="text-slate-900" />
        <Bone a={hip} b={shoulder} className="text-slate-900" />
        <Bone a={shoulder} b={elbow} className="text-slate-700" />
        <Bone a={elbow} b={wrist} className="text-slate-700" />

        <line
          x1={kneeMomentStart.x}
          y1={kneeMomentStart.y}
          x2={kneeMomentEnd.x}
          y2={kneeMomentEnd.y}
          stroke="#0EA5E9"
          strokeWidth={1}
          markerEnd="url(#arrowTip)"
        />
        <line
          x1={hipMomentStart.x}
          y1={hipMomentStart.y}
          x2={hipMomentEnd.x}
          y2={hipMomentEnd.y}
          stroke="#6366F1"
          strokeWidth={1}
          markerEnd="url(#arrowTip)"
        />

        <Joint p={ankle} label="A" />
        <Joint p={knee} label="K" />
        <Joint p={hip} label="H" />
        <Joint p={shoulder} label="S" />
        <Joint p={elbow} label="E" />
        <Joint p={wrist} label="W" />

        <circle cx={bar.x} cy={bar.y} r={2.6} fill="#111827" />
        <circle cx={bar.x} cy={bar.y} r={4.1} fill="none" stroke="#111827" strokeWidth={0.8} opacity={0.4} />

        <text x={bar.x + 3.5} y={bar.y - 3.5} fontSize={3.1} fill="#111827" fontWeight={800}>
          {loadLabel}
        </text>

        <text x={-90} y={-127} fontSize={3.4} fill="#0B1220" fontWeight={900}>
          {solve.liftType === 'low-bar-squat' ? 'LOW BAR SQUAT' : solve.liftType === 'sumo-deadlift' ? 'SUMO DEADLIFT' : 'BENCH PRESS'}
        </text>

        <text x={-90} y={-120} fontSize={2.8} fill="#334155" fontWeight={700}>
          KNEE {solve.angles.kneeDeg.toFixed(1)}°
        </text>
        <text x={-58} y={-120} fontSize={2.8} fill="#334155" fontWeight={700}>
          HIP {solve.angles.hipDeg.toFixed(1)}°
        </text>

        <text x={-90} y={11} fontSize={2.6} fill="#0EA5E9" fontWeight={800}>
          Moment arm rodilla: {solve.momentArms.kneeCm.toFixed(1)} cm
        </text>
        <text x={-20} y={11} fontSize={2.6} fill="#6366F1" fontWeight={800}>
          Moment arm cadera: {solve.momentArms.hipCm.toFixed(1)} cm
        </text>
      </svg>
    </div>
  );
};

export default BiomechanicalStickman;
