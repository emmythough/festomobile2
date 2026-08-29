package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.CostTier
import com.example.data.ModelOption
import com.example.ui.theme.FestoTheme

@Composable
fun ModelBadgeChip(
    model: ModelOption,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = FestoTheme.colors
    val tierColor = when (model.tier) {
        CostTier.FAST -> extendedColors.accentGreen
        CostTier.BALANCED -> extendedColors.accentAmber
        CostTier.DEEP -> extendedColors.accentPurple
    }

    Row(
        modifier = modifier
            .testTag("model_picker_button")
            .clip(RoundedCornerShape(20.dp))
            .background(extendedColors.surfaceSubtle)
            .border(1.dp, extendedColors.borderHairline, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Tier indicator dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(tierColor, RoundedCornerShape(3.dp))
        )

        Text(
            text = model.name,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.5.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Text(
            text = model.tier.symbols,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = extendedColors.inkTertiary
        )

        Icon(
            imageVector = Icons.Rounded.ExpandMore,
            contentDescription = "Switch Model",
            modifier = Modifier.size(16.dp),
            tint = extendedColors.inkTertiary
        )
    }
}
