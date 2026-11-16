package com.example.fitwell.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.fitwell.data.local.MealEntity
import com.example.fitwell.data.prefs.UserPrefs
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/** Totals are always calculated from the recorded meals. */
data class NutritionTotals(val calories: Int = 0, val protein: Int = 0, val carbs: Int = 0, val fat: Int = 0)

class NutritionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application.fitLife()
    private fun <T> Flow<T>.state(initial: T) = stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

    val prefs: StateFlow<UserPrefs> = app.prefs.prefs.state(UserPrefs())
    val todayMeals: StateFlow<List<MealEntity>> = app.mealRepo.observeDay(System.currentTimeMillis()).state(emptyList())
    val history: StateFlow<List<MealEntity>> = app.mealRepo.observeAll().state(emptyList())

    val totals: StateFlow<NutritionTotals> = todayMeals.map { list ->
        NutritionTotals(list.sumOf { it.calories }, list.sumOf { it.proteinGrams }, list.sumOf { it.carbohydrateGrams }, list.sumOf { it.fatGrams })
    }.state(NutritionTotals())

    fun saveMeal(meal: MealEntity) = viewModelScope.launch { app.mealRepo.save(meal) }
    fun deleteMeal(meal: MealEntity) = viewModelScope.launch { app.mealRepo.delete(meal) }
    fun setCalorieTarget(kcal: Int) = viewModelScope.launch { app.prefs.setCalorieTarget(kcal) }
}
