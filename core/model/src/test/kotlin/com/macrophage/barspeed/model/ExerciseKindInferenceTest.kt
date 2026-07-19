package com.macrophage.barspeed.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ExerciseKindInferenceTest {
    @Test
    fun `unknown ids infer kind from name hints`() {
        assertEquals(ExerciseKind.EXPLOSIVE, ExerciseDef.inferKind("kettlebell_swing_heavy"))
        assertEquals(ExerciseKind.EXPLOSIVE, ExerciseDef.inferKind("dumbbell_snatch"))
        assertEquals(ExerciseKind.EXPLOSIVE, ExerciseDef.inferKind("med_ball_slam"))
        assertEquals(ExerciseKind.HOLD, ExerciseDef.inferKind("pallof_hold"))
        assertEquals(ExerciseKind.HOLD, ExerciseDef.inferKind("wall_sit"))
        assertEquals(ExerciseKind.CARRY, ExerciseDef.inferKind("overhead_carry"))
        assertEquals(ExerciseKind.CARRY, ExerciseDef.inferKind("sled_push"))
        assertEquals(ExerciseKind.DYNAMIC, ExerciseDef.inferKind("goblet_squat"))
        assertEquals(ExerciseKind.DYNAMIC, ExerciseDef.inferKind("incline_bench_press"))
    }

    @Test
    fun `seed kettlebell lifts are explosive`() {
        assertEquals(ExerciseKind.EXPLOSIVE, ExerciseDef.seedById("kettlebell_swing")?.kind)
        assertEquals(ExerciseKind.EXPLOSIVE, ExerciseDef.seedById("kettlebell_snatch")?.kind)
    }
}
