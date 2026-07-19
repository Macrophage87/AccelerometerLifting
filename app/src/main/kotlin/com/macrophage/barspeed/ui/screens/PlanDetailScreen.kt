package com.macrophage.barspeed.ui.screens

import android.app.Application
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.macrophage.barspeed.data.PlanEntity
import com.macrophage.barspeed.model.ExerciseDef
import com.macrophage.barspeed.model.ExerciseKind
import com.macrophage.barspeed.model.PlanExerciseDef
import com.macrophage.barspeed.model.PlanSetDef
import com.macrophage.barspeed.model.WeightUnit
import com.macrophage.barspeed.ui.BarColors
import com.macrophage.barspeed.ui.components.ChipTone
import com.macrophage.barspeed.ui.components.SectionCaption
import com.macrophage.barspeed.ui.components.VerdictChip
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanDetailScreen(navController: NavController, planId: Long) {
    val context = LocalContext.current
    val viewModel: PlanDetailViewModel =
        viewModel(
            factory =
            PlanDetailViewModel.Factory(
                context.applicationContext as Application,
                planId,
            ),
        )
    val state by viewModel.state.collectAsState()
    val weightUnit by viewModel.weightUnit.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.plan?.planName ?: "Plan") },
                navigationIcon = {
                    TextButton(onClick = { navController.popBackStack() }) { Text("Back") }
                },
                actions = {
                    if (state.entity != null && state.entity?.status != PlanEntity.STATUS_ACTIVE) {
                        TextButton(onClick = viewModel::activate) {
                            Text("Make active", color = BarColors.Volt)
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val plan = state.plan
            when {
                !state.loaded -> {}
                plan == null -> Text("This plan could not be read.", color = BarColors.Red)
                else -> {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        state.entity?.let { entity ->
                            VerdictChip(
                                entity.status,
                                if (entity.status == PlanEntity.STATUS_ACTIVE) ChipTone.OK else ChipTone.NEUTRAL,
                            )
                        }
                        val totalSets = plan.sessions.sumOf { s -> s.exercises.sumOf { it.sets.size } }
                        VerdictChip("${plan.sessions.size} sessions · $totalSets sets", ChipTone.NEUTRAL)
                    }
                    plan.notes?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall, color = BarColors.Sub)
                    }
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        plan.sessions.forEach { session ->
                            item {
                                Column {
                                    Spacer(Modifier.height(4.dp))
                                    SectionCaption(session.name, color = BarColors.Volt)
                                    session.notes?.let {
                                        Text(it, style = MaterialTheme.typography.bodySmall, color = BarColors.Sub)
                                    }
                                }
                            }
                            items(session.exercises.size) { i ->
                                ExerciseCard(session.exercises[i], weightUnit)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(exercise: PlanExerciseDef, unit: WeightUnit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(displayName(exercise.exercise), style = MaterialTheme.typography.titleMedium)
            exercise.notes?.let {
                Text("“$it”", style = MaterialTheme.typography.bodySmall, color = BarColors.Amber)
            }
            val timedWord =
                if (ExerciseDef.seedById(exercise.exercise)?.kind == ExerciseKind.CARRY) "carry" else "hold"
            exercise.sets.forEachIndexed { i, set ->
                Text(
                    "${i + 1} · ${setLine(set, unit, timedWord)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = BarColors.Sub,
                )
            }
        }
    }
}

private fun displayName(id: String): String = ExerciseDef.seedById(id)?.displayName
    ?: id.replace('_', ' ').replaceFirstChar { it.uppercase() }

private fun setLine(set: PlanSetDef, unit: WeightUnit, timedWord: String): String {
    val load = set.resolvedLoadKg?.takeIf { it > 0 }?.let { unit.format(it) } ?: "bodyweight"
    return listOfNotNull(
        set.reps?.let { "$it reps" },
        set.durationS?.let { "${it}s $timedWord" },
        load,
        set.tempo?.let { "tempo $it" },
        set.targetMeanConcentricVelocityMps?.let { String.format(Locale.US, "target %.2f m/s", it) },
        set.velocityLossStopPct?.let { "stop −${trimNum(it)}%" },
        set.restS?.let { "rest ${it / 60}:${String.format(Locale.US, "%02d", it % 60)}" },
    ).joinToString(" · ")
}

private fun trimNum(value: Double): String =
    if (value == Math.floor(value)) value.toInt().toString() else String.format(Locale.US, "%.1f", value)
