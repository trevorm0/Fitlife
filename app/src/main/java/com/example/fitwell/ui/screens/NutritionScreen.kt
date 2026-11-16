package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.MealCategory
import com.example.fitwell.data.local.MealEntity
import com.example.fitwell.ui.components.*
import com.example.fitwell.ui.theme.*
import com.example.fitwell.util.*
import com.example.fitwell.viewmodel.NutritionViewModel

@Composable
fun NutritionScreen(vm: NutritionViewModel = viewModel()) {
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val meals by vm.todayMeals.collectAsStateWithLifecycle()
    val totals by vm.totals.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MealEntity?>(null) }
    var adding by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MealEntity?>(null) }
    var editTarget by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Text("Nutrition", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 8.dp))
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { CategoryFilterChip("Meals", tab == 0, { tab = 0 }) }
            item { CategoryFilterChip("History", tab == 1, { tab = 1 }) }
        }
        Spacer(Modifier.height(8.dp))

        if (tab == 0) {
            val remaining = prefs.calorieTarget - totals.calories
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    FitLifeCard(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                CircularProgressIndicatorCard(totals.calories / prefs.calorieTarget.coerceAtLeast(1).toFloat(), if (remaining < 0) HeartCoral else FitGreen) {
                                    Icon(Icons.Default.Restaurant, contentDescription = null, tint = CaloriesOrange, modifier = Modifier.size(36.dp))
                                }
                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("Daily Calories", style = MaterialTheme.typography.bodyMedium, color = FitGreen)
                                        IconButton(onClick = { editTarget = true }, Modifier.size(32.dp)) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit calorie target", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                    Text("%,d".format(totals.calories), style = MaterialTheme.typography.displaySmall)
                                    Text("of %,d kcal target".format(prefs.calorieTarget), style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                                    Text(
                                        if (remaining >= 0) "%,d kcal remaining".format(remaining) else "%,d kcal over target".format(-remaining),
                                        style = MaterialTheme.typography.bodyMedium, color = if (remaining >= 0) FitGreen else HeartCoral
                                    )
                                }
                            }
                            NutritionProgressCard("Protein", totals.protein, prefs.proteinTarget, CaloriesOrange)
                            NutritionProgressCard("Carbs", totals.carbs, prefs.carbTarget, FitCyan)
                            NutritionProgressCard("Fats", totals.fat, prefs.fatTarget, CaloriesOrange)
                            Text("Totals are added up from the meals you record. Nutrition values are estimates entered by you or from sample entries.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                    }
                }
                item { SectionHeader("Today's Meals") }
                if (meals.isEmpty()) item { EmptyState("No meals logged today", "Add what you eat to see your totals.", icon = Icons.Default.Restaurant) }
                items(meals, key = { it.mealId }) { m -> MealRow(m, onEdit = { editing = m }, onDelete = { deleteTarget = m }) }
                item { FitLifePrimaryButton("Add Meal", { adding = true }, Modifier.fillMaxWidth(), icon = Icons.Default.Add) }
            }
        } else {
            val grouped = remember(history) { history.groupBy { it.timestamp.toLocalDate() } }
            LazyColumn(contentPadding = PaddingValues(16.dp, 4.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (history.isEmpty()) item { EmptyState("No meal history", "Meals you add will appear here by day.", icon = Icons.Default.History) }
                grouped.forEach { (day, list) ->
                    item(key = "day_$day") {
                        SectionHeader("${relativeDay(day.startOfDayMs())} • ${list.sumOf { it.calories }} kcal")
                    }
                    items(list, key = { it.mealId }) { m -> MealRow(m, onEdit = { editing = m }, onDelete = { deleteTarget = m }) }
                }
            }
        }
    }

    if (adding || editing != null) {
        MealDialog(editing, onDismiss = { adding = false; editing = null }) { vm.saveMeal(it) }
    }
    deleteTarget?.let { m -> ConfirmDialog("Delete meal?", "“${m.name}” will be removed from your totals.", "Delete", { vm.deleteMeal(m) }, { deleteTarget = null }, destructive = true) }
    if (editTarget) TargetDialog(prefs.calorieTarget, { editTarget = false }) { vm.setCalorieTarget(it) }
}

@Composable
private fun MealRow(m: MealEntity, onEdit: () -> Unit, onDelete: () -> Unit) {
    FitLifeCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${m.mealCategory.label} • ${formatTime(m.timestamp)}", style = MaterialTheme.typography.labelSmall, color = FitGreen)
                Text(m.name, style = MaterialTheme.typography.titleSmall)
                Text("P ${m.proteinGrams}g • C ${m.carbohydrateGrams}g • F ${m.fatGrams}g (est.)", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
            Text("${m.calories} kcal", style = MaterialTheme.typography.titleSmall)
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit ${m.name}", tint = TextSecondary) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete ${m.name}", tint = HeartCoral) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MealDialog(initial: MealEntity?, onDismiss: () -> Unit, onSave: (MealEntity) -> Unit) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var category by remember { mutableStateOf(initial?.mealCategory ?: MealCategory.BREAKFAST) }
    var kcal by remember { mutableStateOf(initial?.calories?.toString().orEmpty()) }
    var protein by remember { mutableStateOf(initial?.proteinGrams?.toString().orEmpty()) }
    var carbs by remember { mutableStateOf(initial?.carbohydrateGrams?.toString().orEmpty()) }
    var fat by remember { mutableStateOf(initial?.fatGrams?.toString().orEmpty()) }
    var attempted by remember { mutableStateOf(false) }
    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val kcalValue = kcal.toIntOrNull()

    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated,
        title = { Text(if (initial == null) "Add meal" else "Edit meal") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                FitLifeTextField(name, { name = it }, "Food name", error = "Enter a name".takeIf { attempted && name.isBlank() })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealCategory.entries.forEach { c -> CategoryFilterChip(c.label, category == c, { category = c }) }
                }
                FitLifeTextField(kcal, { kcal = it.filter(Char::isDigit) }, "Calories (estimate)", keyboardOptions = num,
                    error = "Enter calories".takeIf { attempted && kcalValue == null })
                FitLifeTextField(protein, { protein = it.filter(Char::isDigit) }, "Protein (g, optional)", keyboardOptions = num)
                FitLifeTextField(carbs, { carbs = it.filter(Char::isDigit) }, "Carbs (g, optional)", keyboardOptions = num)
                FitLifeTextField(fat, { fat = it.filter(Char::isDigit) }, "Fat (g, optional)", keyboardOptions = num)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                attempted = true
                if (name.isNotBlank() && kcalValue != null) {
                    onSave(
                        MealEntity(
                            mealId = initial?.mealId ?: 0, name = name.trim(), mealCategory = category, calories = kcalValue,
                            proteinGrams = protein.toIntOrNull() ?: 0, carbohydrateGrams = carbs.toIntOrNull() ?: 0, fatGrams = fat.toIntOrNull() ?: 0,
                            timestamp = initial?.timestamp ?: System.currentTimeMillis(), description = initial?.description.orEmpty()
                        )
                    )
                    onDismiss()
                }
            }) { Text("Save", color = FitGreen) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}

@Composable
private fun TargetDialog(current: Int, onDismiss: () -> Unit, onSave: (Int) -> Unit) {
    var text by remember { mutableStateOf(current.toString()) }
    val v = text.toIntOrNull()
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = CardElevated, title = { Text("Daily calorie target") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FitLifeTextField(text, { text = it.filter(Char::isDigit) }, "Calories per day", keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    error = "Enter a value between 500 and 10,000".takeIf { v == null || v !in 500..10_000 })
                Text("Pick a target that suits you. FitLife does not recommend calorie targets.", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
            }
        },
        confirmButton = { TextButton(enabled = v != null && v in 500..10_000, onClick = { onSave(v!!); onDismiss() }) { Text("Save", color = FitGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextSecondary) } }
    )
}
