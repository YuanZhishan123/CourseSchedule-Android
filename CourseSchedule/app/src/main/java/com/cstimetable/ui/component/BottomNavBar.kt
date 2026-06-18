package com.cstimetable.ui.component

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.cstimetable.ui.theme.BottomNavSelected
import com.cstimetable.ui.theme.BottomNavUnselected

data class NavTab(
    val label: String,
    val icon: ImageVector,
)

private val TABS = listOf(
    NavTab("日程", Icons.Filled.DateRange),
    NavTab("课表", Icons.Filled.TableChart),
    NavTab("发现", Icons.Filled.Explore),
    NavTab("我的", Icons.Filled.Person),
)

@Composable
fun BottomNavBar(
    selectedIndex: Int = 1, // 默认选中"课表"
    modifier: Modifier = Modifier,
) {
    NavigationBar(
        modifier = modifier.fillMaxWidth(),
        containerColor = NavigationBarDefaults.containerColor,
    ) {
        TABS.forEachIndexed { index, tab ->
            val isSelected = index == selectedIndex
            NavigationBarItem(
                selected = isSelected,
                onClick = { /* 暂不切换页面 */ },
                icon = {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = tab.label,
                        modifier = Modifier.size(22.dp),
                    )
                },
                label = {
                    Text(text = tab.label)
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BottomNavSelected,
                    selectedTextColor = BottomNavSelected,
                    unselectedIconColor = BottomNavUnselected,
                    unselectedTextColor = BottomNavUnselected,
                ),
            )
        }
    }
}
