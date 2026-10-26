"""Shared Pydantic models mirroring the TypeScript types."""
from __future__ import annotations
from pydantic import BaseModel, ConfigDict, Field
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
    calculated1RM: Optional[float] = None


class ExerciseSet(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str = ""
    targetReps: Optional[int] = None
    targetDuration: Optional[float] = None
    targetRPE: Optional[float] = None
    targetRIR: Optional[float] = None
    intensityMode: Optional[str] = None
    actualIntensityMode: Optional[str] = None
    actualIntensityValue: Optional[float] = None
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
    model_config = ConfigDict(extra="allow")

    id: str
    name: str
    exerciseDbId: Optional[str] = None
    sets: list[ExerciseSet] = []
    restTime: Optional[float] = None
    targetMuscles: Optional[list[InvolvedMuscle]] = None


class SessionPart(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    name: str
    exercises: list[Exercise] = []


class Session(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    name: str
    exercises: list[Exercise] = []
    parts: Optional[list[SessionPart]] = None
    dayOfWeek: Optional[int] = None


class CompletedSet(ExerciseSet):
    model_config = ConfigDict(extra="allow")
    weight: float = 0
    side: Optional[str] = None


class CompletedExercise(BaseModel):
    model_config = ConfigDict(extra="allow")
    exerciseId: str = ""
    exerciseDbId: Optional[str] = None
    exerciseName: str = ""
    sets: list[CompletedSet] = []
    useBodyweight: Optional[bool] = None
    totalSpinalScore: Optional[float] = None








class WorkoutLog(BaseModel):
    model_config = ConfigDict(extra="allow")
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
    fatigueLevel: Optional[float] = None
    mentalClarity: Optional[float] = None
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
    startWeekOn: Optional[int] = None
    workDays: Optional[list[int]] = None
    wakeTimeWork: Optional[str] = None
    wakeTimeOff: Optional[str] = None


class MuscleHierarchy(BaseModel):
    bodyPartHierarchy: dict = {}
    specialCategories: dict = {}
    muscleToBodyPart: dict = {}


class ProgramWeek(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    name: str = ""
    sessions: list[Session] = []
    progressionIndex: int | None = None


class Mesocycle(BaseModel):
    model_config = ConfigDict(extra="allow")

    goal: str = "Acumulación"
    weeks: list[ProgramWeek] = []


class Block(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str | None = None
    name: str | None = None
    description: str | None = None
    mesocycles: list[Mesocycle] = []
    # Campos nuevos de solo-lectura (paridad Android BlockGoal / BlockProgressionScheme).
    # Enums NUEVOS en Android; aquí se reflejan como Optional[str] para no romper payloads.
    goal: str | None = None
    progressionScheme: str | None = None


class Macrocycle(BaseModel):
    model_config = ConfigDict(extra="allow")

    blocks: list[Block] = []


class Program(BaseModel):
    model_config = ConfigDict(extra="allow")

    id: str
    name: str = ""
    macrocycles: list[Macrocycle] = []
