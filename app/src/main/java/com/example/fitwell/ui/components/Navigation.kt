package com.example.fitwell.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.fitwell.navigation.BottomNavigationItem
import com.example.fitwell.ui.theme.*

@Composable
fun FitLifeBottomNavigation(
    items: List<BottomNavigationItem>, currentRoute: String?, onSelect: (BottomNavigationItem) -> Unit, modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier.clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp)),
        containerColor = NavyDark, contentColor = TextSecondary, tonalElevation = 0.dp
    ) {
        items.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                selected = selected, onClick = { onSelect(item) },
                icon = { Icon(item.icon, contentDescription = null) },
                label = { Text(item.label, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = FitGreen, selectedTextColor = FitGreen, indicatorColor = Color.Transparent,
                    unselectedIconColor = TextSecondary, unselectedTextColor = TextSecondary
                )
            )
        }
    }
}
