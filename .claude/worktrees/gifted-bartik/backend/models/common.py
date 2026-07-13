"""Shared Pydantic models mirroring the TypeScript types."""
from __future__ import annotations
from pydantic import BaseModel, Field
from typing import Optional, Literal
from enum import Enum


# ── Enums ───────────────────────────────────────────────

class MuscleRole(str, Enum):
    primary = "primary"
    secondary = "secondary"
    stabilizer = "stabilizer"
    neutralizer = "neutralizer"


class AthleteType(str, Enum):
    enthusiast = "enthusiast"
    powerlifter = "powerlifter"
    bodybuilder = "bodybuilder"
    powerbuilder = "powerbuilder"
    zercher_lifter = "zercher_lifter"
    hybrid = "hybrid"
    weightlifter = "weightlifter"
    parapowerlifter = "parapowerlifter"
    calisthenics = "calisthenics"


class ExerciseType(str, Enum):
    basico = "Básico"
    accesorio = "Accesorio"
    aislamiento = "Aislamiento"


class ExerciseTier(str, Enum):
    T1 = "T1"
    T2 = "T2"
    T3 = "T3"


class Equipment(str, Enum):
    barra = "Barra"
    mancuerna = "Mancuerna"
    maquina = "Máquina"
    peso_corporal = "Peso Corporal"
    banda = "Banda"
    kettlebell = "Kettlebell"
    polea = "Polea"
    otro = "Otro"


# ── Sub-models ──────────────────────────────────────────

class InvolvedMuscle(BaseModel):
    muscle: str
    role: MuscleRole
    activation: Optional[float] = None


class ExerciseMuscleInfo(BaseModel):
    id: str
    name: str
    description: str = ""
    involvedMuscles: list[InvolvedMuscle] = []
    category: str = "Fuerza"
    type: ExerciseType = ExerciseType.accesorio
    tier: Optional[ExerciseTier] = None
    equipment: Equipment = Equipment.otro
    efc: Optional[float] = None
    ssc: Optional[float] = None
    cnc: Optional[float] = None
    axialLoadFactor: Optional[float] = None
    calculated1RM: Optional[float] = None


class ExerciseSet(BaseModel):
    id: str = ""
    targetReps: Optional[int] = None
    targetDuration: Optional[float] = None
    targetRPE: Optional[float] = None
    targetRIR: Optional[float] = None
    intensityMode: Optional[str] = None
    targetPercentageRM: Optional[float] = None
    weight: Optional[float] = None
    completedReps: Optional[int] = None
    completedDuration: Optional[float] = None
    completedRPE: Optional[float] = None
    completedRIR: Optional[float] = None
    isFailure: Optional[bool] = None
    isAmrap: Optional[bool] = None
    performanceMode: Optional[str] = None
    advancedTechnique: Optional[str] = None
    dropSets: Optional[list[dict]] = None
    restPauses: Optional[list[dict]] = None
    partialReps: Optional[int] = None
    reps: Optional[int] = None  # alias in some legacy paths
    rpe: Optional[float] = None
    rir: Optional[float] = None


class Exercise(BaseModel):
    id: str
    name: str
    exerciseDbId: Optional[str] = None
    sets: list[ExerciseSet] = []
    restTime: Optional[float] = None
    targetMuscles: Optional[list[InvolvedMuscle]] = None


class SessionPart(BaseModel):
    id: str
    name: str
    exercises: list[Exercise] = []


class Session(BaseModel):
    id: str
    name: str
    exercises: list[Exercise] = []
    parts: Optional[list[SessionPart]] = None
    dayOfWeek: Optional[int] = None


class CompletedSet(ExerciseSet):
    weight: float = 0
    spinalScore: Optional[float] = None
    side: Optional[str] = None


class CompletedExercise(BaseModel):
    exerciseId: str = ""
    exerciseDbId: Optional[str] = None
    exerciseName: str = ""
    sets: list[CompletedSet] = []
    useBodyweight: Optional[bool] = None
    totalSpinalScore: Optional[float] = None


class WorkoutLog(BaseModel):
    id: str
    programId: str = ""
    programName: str = ""
    sessionId: str = ""
    sessionName: str = ""
    date: str
    duration: Optional[float] = None
    completedExercises: list[CompletedExercise] = []
    notes: Optional[str] = None
    discomforts: Optional[list[str]] = None
    fatigueLevel: float = 5
    mentalClarity: float = 5
    sessionStressScore: Optional[float] = None
    sessionVariant: Optional[str] = None


class SleepLog(BaseModel):
    id: str
    startTime: str
    endTime: str
    duration: float
    date: str


class NutritionLogFood(BaseModel):
    id: str = ""
    foodName: str = ""
    calories: float = 0
    protein: float = 0
    carbs: float = 0
    fats: float = 0


class NutritionLog(BaseModel):
    id: str
    date: str
    mealType: str = "snack"
    foods: list[NutritionLogFood] = []
    calories: Optional[float] = None
    protein: Optional[float] = None
    carbs: Optional[float] = None
    fats: Optional[float] = None


class DailyWellbeingLog(BaseModel):
    id: str
    date: str
    sleepQuality: float = 3
    stressLevel: float = 3
    doms: float = 1
    motivation: float = 5
    workIntensity: Optional[str] = None
    studyIntensity: Optional[str] = None


class PostSessionMuscle(BaseModel):
    doms: float = 1
    jointPain: bool = False
    strengthCapacity: float = 7
    notes: str = ""


class PostSessionFeedback(BaseModel):
    logId: str
    date: str
    cnsRecovery: float = 5
    feedback: dict[str, PostSessionMuscle] = {}


class WaterLog(BaseModel):
    id: str = ""
    date: str = ""
    amount: float = 0


class UserVitals(BaseModel):
    age: Optional[int] = None
    gender: Optional[str] = None
    weight: Optional[float] = None
    height: Optional[float] = None
    bodyFatPercentage: Optional[float] = None
    workIntensity: Optional[str] = None


class AlgorithmSettings(BaseModel):
    oneRMDecayRate: float = 0.05
    failureFatigueFactor: float = 1.5
    legVolumeMultiplier: float = 1.0
    torsoVolumeMultiplier: float = 1.0
    synergistFactor: float = 0.3
    augeEnableNutritionTracking: bool = True
    augeEnableSleepTracking: bool = True


class BatteryCalibration(BaseModel):
    cnsDelta: float = 0
    muscularDelta: float = 0
    spinalDelta: float = 0
    lastCalibrated: str = ""


class AthleteProfileScore(BaseModel):
    totalScore: float = 10
    profileLevel: str = "Advanced"
    trainingStyle: Optional[str] = None


class Settings(BaseModel):
    athleteType: AthleteType = AthleteType.enthusiast
    trainingProfile: Optional[str] = None
    preferredIntensity: Optional[str] = None
    athleteScore: Optional[AthleteProfileScore] = None
    calorieGoalObjective: Optional[str] = None
    dailyCalorieGoal: Optional[float] = None
    dailyProteinGoal: Optional[float] = None
    dailyCarbGoal: Optional[float] = None
    dailyFatGoal: Optional[float] = None
    sleepTargetHours: Optional[float] = None
    userVitals: UserVitals = Field(default_factory=UserVitals)
    algorithmSettings: AlgorithmSettings = Field(default_factory=AlgorithmSettings)
    batteryCalibration: Optional[BatteryCalibration] = None
    startWeekOn: Optional[int] = None
    workDays: Optional[list[int]] = None
    wakeTimeWork: Optional[str] = None
    wakeTimeOff: Optional[str] = None


class MuscleHierarchy(BaseModel):
    bodyPartHierarchy: dict = {}
    specialCategories: dict = {}
    muscleToBodyPart: dict = {}


class ProgramWeek(BaseModel):
    id: str
    name: str = ""
    sessions: list[Session] = []


class Mesocycle(BaseModel):
    goal: str = "Acumulación"
    weeks: list[ProgramWeek] = []


class Block(BaseModel):
    mesocycles: list[Mesocycle] = []


class Macrocycle(BaseModel):
    blocks: list[Block] = []


class Program(BaseModel):
    id: str
    name: str = ""
    macrocycles: list[Macrocycle] = []
