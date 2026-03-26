package com.example.kpkn.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

// ─── WikiLab Entities ──────────────────────────────────────────────────────
// All static WikiLab data stored in Room with prepopulate from JSON assets.

@Entity(tableName = "muscle_groups")
data class MuscleGroupEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val bodyPart: String?, // "upper", "lower", "core", "spine", null
    val coverImage: String?,
    val origin: String?,
    val insertion: String?,
    val mechanicalFunctions: String?, // JSON array of strings
    val mev: String?, // Volume recommendations as strings (e.g. "8")
    val mav: String?, // e.g. "12-20"
    val mrv: String?, // e.g. "25"
    val recommendedExercises: String?, // JSON array of exercise IDs
    val relatedJoints: String?, // JSON array of joint IDs
    val relatedTendons: String?, // JSON array of tendon IDs
    val importanceMovement: String?,
    val importanceHealth: String?,
    val aestheticImportance: String?,
)

@Entity(tableName = "joints")
data class JointEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String, // "hinge", "ball-socket", "pivot", "gliding", "saddle", "condyloid"
    val description: String,
    val bodyPart: String?, // "upper", "lower", "spine", null
    val musclesCrossing: String?, // JSON array of muscle IDs
    val tendonsRelated: String?, // JSON array of tendon IDs
    val movementPatterns: String?, // JSON array of pattern IDs
    val commonInjuries: String?, // JSON array of InjuryData objects
    val protectiveExercises: String?, // JSON array of exercise IDs
)

@Entity(tableName = "tendons")
data class TendonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val muscleId: String?, // related muscle
    val jointId: String?, // related joint
    val commonInjuries: String?, // JSON array of InjuryData objects
    val protectiveExercises: String?, // JSON array of exercise IDs
)

@Entity(tableName = "movement_patterns")
data class MovementPatternEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val forceTypes: String?, // JSON array of strings
    val chainTypes: String?, // JSON array of strings
    val primaryMuscles: String?, // JSON array of muscle IDs
    val primaryJoints: String?, // JSON array of joint IDs
    val exampleExercises: String?, // JSON array of exercise IDs
)

@Entity(tableName = "kinetic_chains")
data class KineticChainEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val importance: String,
    val muscles: String?, // JSON array of muscle names
)
