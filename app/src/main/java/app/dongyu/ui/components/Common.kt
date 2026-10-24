package app.dongyu.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.dongyu.model.SportType
import app.dongyu.ui.theme.Brand
import app.dongyu.ui.theme.NumberStyle
import app.dongyu.ui.theme.extraColors

fun SportType.composeColor() = Color(color)

@Composable
fun DCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .shadow(elevation = 1.dp, shape = shape, ambientColor = Color(0x22000000), spotColor = Color(0x22000000))
            .clip(shape)
            .background(extraColors.card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

@Composable
fun IconBadge(icon: ImageVector, color: Color, size: Dp = 40.dp, iconSize: Dp = size * 0.55f, filled: Boolean = false) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(if (filled) color else color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (filled) Color.White else color, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun SportBadge(sport: SportType, size: Dp = 40.dp, filled: Boolean = false) =
    IconBadge(SportIcons.of(sport.icon), sport.composeColor(), size, filled = filled)

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}

/** A labelled metric: icon, big value + unit, caption. */
@Composable
fun MetricTile(
    icon: ImageVector,
    color: Color,
    value: String,
    unit: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconBadge(icon, color, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = NumberStyle.Small, maxLines = 1)
                if (unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(unit, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Text(label, style = MaterialTheme.typography.labelSmall, color = extraColors.subtle, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Value with small unit used in hero cards. */
@Composable
fun BigStat(value: String, unit: String, label: String, color: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = NumberStyle.Medium, color = color, maxLines = 1)
            if (unit.isNotEmpty()) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelMedium, color = color.copy(alpha = 0.75f), modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.7f))
    }
}

@Composable
fun DeltaPill(delta: Double?, modifier: Modifier = Modifier, invert: Boolean = false) {
    if (delta == null) return
    val good = if (invert) delta <= 0 else delta >= 0
    val c = if (good) Brand.Up else Brand.Down
    Surface(color = c.copy(alpha = 0.14f), shape = RoundedCornerShape(50), modifier = modifier) {
        Text(
            (if (delta >= 0) "▲ " else "▼ ") + Fmt.percent(delta).drop(1),
            color = c, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@Composable
fun ChipButton(text: String, selected: Boolean, color: Color = MaterialTheme.colorScheme.primary, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = if (selected) color else extraColors.card,
        contentColor = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
        shadowElevation = if (selected) 2.dp else 0.dp,
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, Modifier.size(16.dp), tint = if (selected) Color.White else color)
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SegmentedTabs(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEachIndexed { i, label ->
            val sel = i == selected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (sel) extraColors.card else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label, style = MaterialTheme.typography.labelLarge,
                    color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        IconBadge(icon, MaterialTheme.colorScheme.primary, 72.dp)
        Spacer(Modifier.size(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = extraColors.subtle)
    }
}
