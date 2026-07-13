export interface WikiCommonInjury {
  name: string;
  description: string;
  riskExercises?: string[];
  contraindications?: string[];
  returnProgressions?: string[];
}

export interface WikiMuscle {
  id: string;
  name: string;
  description: string;
  relatedJoints?: string[];
  relatedTendons?: string[];
  movementPatterns?: string[];
  commonInjuries?: WikiCommonInjury[];
}

export interface WikiJoint {
  id: string;
  name: string;
  description: string;
  type: 'hinge' | 'ball-socket' | 'pivot' | 'gliding' | 'saddle' | 'condyloid';
  bodyPart: 'upper' | 'lower' | 'spine';
  musclesCrossing: string[];
  tendonsRelated: string[];
  movementPatterns: string[];
  commonInjuries: WikiCommonInjury[];
  protectiveExercises?: string[];
}

export interface WikiTendon {
  id: string;
  name: string;
  description: string;
  muscleId: string;
  jointId?: string;
  commonInjuries: WikiCommonInjury[];
  protectiveExercises?: string[];
}

export interface WikiMovementPattern {
  id: string;
  name: string;
  description: string;
  forceTypes: string[];
  chainTypes: string[];
  primaryMuscles: string[];
  primaryJoints: string[];
  exampleExercises: string[];
}
