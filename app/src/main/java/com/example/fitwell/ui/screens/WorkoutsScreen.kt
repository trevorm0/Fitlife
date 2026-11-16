package com.example.fitwell.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.fitwell.data.local.WorkoutCategory
import com.example.fitwell.ui.components.*
import com.example.fitwell.viewmodel.WorkoutViewModel

@Composable
fun WorkoutsScreen(onOpenWorkout: (Long) -> Unit, vm: WorkoutViewModel = viewModel()) {
    val workouts by vm.workouts.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val category by vm.category.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("Workouts", style = MaterialTheme.typography.headlineMedium) }
        item { SearchBar(query, { vm.query.value = it }, placeholder = "Search workouts…") }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { CategoryFilterChip("All", category == null, { vm.category.value = null }) }
                items(WorkoutCategory.entries.toList()) { c ->
                    CategoryFilterChip(c.label, category == c, { vm.category.value = c })
                }
            }
        }
        if (workouts.isEmpty()) {
            item {
                EmptyState(
                    "No workouts found", "Try a different search or category.", icon = Icons.Default.SearchOff,
                    actionLabel = "Clear filters", onAction = { vm.query.value = ""; vm.category.value = null }
                )
            }
        } else {
            items(workouts, key = { it.workoutId }) { w -> WorkoutCard(w, onClick = { onOpenWorkout(w.workoutId) }) }
        }
    }
}
