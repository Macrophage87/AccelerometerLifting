package com.macrophage.barspeed.ui.screens

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.macrophage.barspeed.LiftingApp
import com.macrophage.barspeed.data.PlanEntity
import com.macrophage.barspeed.model.PlanFile
import com.macrophage.barspeed.model.WeightUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PlanDetailState(
    val entity: PlanEntity? = null,
    val plan: PlanFile? = null,
    val loaded: Boolean = false,
)

class PlanDetailViewModel(app: Application, private val planId: Long) : AndroidViewModel(app) {
    private val container = (app as LiftingApp).container
    private val repository = container.planRepository

    private val stateFlow = MutableStateFlow(PlanDetailState())
    val state: StateFlow<PlanDetailState> = stateFlow

    val weightUnit =
        container.settings.weightUnit
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeightUnit.KG)

    init {
        viewModelScope.launch {
            stateFlow.value =
                PlanDetailState(
                    entity = repository.plan(planId),
                    plan = repository.planFile(planId),
                    loaded = true,
                )
        }
    }

    fun activate() {
        viewModelScope.launch {
            repository.activate(planId)
            stateFlow.value = stateFlow.value.copy(entity = repository.plan(planId))
        }
    }

    class Factory(private val app: Application, private val planId: Long) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = PlanDetailViewModel(app, planId) as T
    }
}
