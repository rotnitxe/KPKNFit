package com.example.kpkn.data.exercises

import com.example.kpkn.R
import org.junit.Assert.assertEquals
import org.junit.Test

class ExerciseImagesBatch20261008Test {
    @Test
    fun approved_twenty_five_assets_resolve_for_their_published_configurations() {
        val approved = listOf(
            Triple("hollow_body_hold", "hollow_body_hold__default", R.drawable.exercise_hollow_body_hold_bodyweight_batch20261008),
            Triple("bird_dog", "bird_dog__default", R.drawable.exercise_bird_dog_bodyweight_batch20261008),
            Triple("side_plank", "side_plank__default", R.drawable.exercise_side_plank_bodyweight_batch20261008),
            Triple("forward_lunge", "forward_lunge__bodyweight", R.drawable.exercise_forward_lunge_bodyweight_batch20261008),
            Triple("forward_lunge", "forward_lunge__dumbbells", R.drawable.exercise_forward_lunge_dumbbells_batch20261008),
            Triple("forward_lunge", "forward_lunge__kettlebell", R.drawable.exercise_forward_lunge_kettlebell_batch20261008),
            Triple("forward_lunge", "forward_lunge__barbell", R.drawable.exercise_forward_lunge_barbell_batch20261008),
            Triple("forward_lunge", "forward_lunge__smith_machine", R.drawable.exercise_forward_lunge_smith_machine_batch20261008),
            Triple("suitcase_carry", "suitcase_carry__dumbbells", R.drawable.exercise_suitcase_carry_dumbbells_batch20261008),
            Triple("suitcase_carry", "suitcase_carry__kettlebell", R.drawable.exercise_suitcase_carry_kettlebell_batch20261008),
            Triple("knee_push_up", "knee_push_up__default", R.drawable.exercise_knee_push_up_bodyweight_batch20261008),
            Triple("diamond_push_up", "diamond_push_up__default", R.drawable.exercise_diamond_push_up_bodyweight_batch20261008),
            Triple("archer_push_up", "archer_push_up__default", R.drawable.exercise_archer_push_up_bodyweight_batch20261008),
            Triple("pike_push_up", "pike_push_up__flat", R.drawable.exercise_pike_push_up_bodyweight_batch20261008),
            Triple("negative_pull_up", "negative_pull_up__default", R.drawable.exercise_negative_pull_up_bodyweight_batch20261008),
            Triple("power_clean", "power_clean__barbell", R.drawable.exercise_power_clean_barbell_batch20261008),
            Triple("hang_power_clean", "hang_power_clean__barbell", R.drawable.exercise_hang_power_clean_barbell_batch20261008),
            Triple("squat_clean", "squat_clean__barbell", R.drawable.exercise_squat_clean_barbell_batch20261008),
            Triple("clean_pull", "clean_pull__barbell", R.drawable.exercise_clean_pull_barbell_batch20261008),
            Triple("power_snatch", "power_snatch__barbell", R.drawable.exercise_power_snatch_barbell_batch20261008),
            Triple("hang_power_snatch", "hang_power_snatch__barbell", R.drawable.exercise_hang_power_snatch_barbell_batch20261008),
            Triple("squat_snatch", "squat_snatch__barbell", R.drawable.exercise_squat_snatch_barbell_batch20261008),
            Triple("snatch_pull", "snatch_pull__barbell", R.drawable.exercise_snatch_pull_barbell_batch20261008),
            Triple("push_jerk", "push_jerk__barbell", R.drawable.exercise_push_jerk_barbell_batch20261008),
            Triple("split_jerk", "split_jerk__barbell", R.drawable.exercise_split_jerk_barbell_batch20261008),
        )
        assertEquals(25, approved.size)
        for ((definition, configurationId, resource) in approved) {
            assertEquals("$definition / $configurationId", resource, resolve(configurationId))
        }
    }

    @Test
    fun later_reviewed_rack_chin_and_lumbar_extension_resolve() {
        assertEquals(R.drawable.exercise_rack_chin_bodyweight, resolve("rack_chin__default"))
        assertEquals(R.drawable.exercise_back_extension_lumbar_default, resolve("back_extension_lumbar__default"))
    }

    @Test
    fun flat_and_feet_elevated_pike_keep_their_distinct_selected_images() {
        assertEquals(R.drawable.exercise_pike_push_up_bodyweight_batch20261008, resolve("pike_push_up__flat"))
        assertEquals(R.drawable.exercise_pike_push_up_feet_elevated, resolve("pike_push_up__feet_elevated"))
    }

    private fun resolve(configurationId: String) =
        ExerciseTechniqueImageLookup.resolveImageResId(
            catalogDefinitionId = configurationId.substringBefore("__"),
            exerciseDbId = null,
            exerciseId = configurationId,
            catalogConfigurationId = configurationId,
        )
}
