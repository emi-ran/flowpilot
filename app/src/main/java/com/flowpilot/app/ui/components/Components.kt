package com.flowpilot.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import com.flowpilot.app.permission.CapabilityStatus
import com.flowpilot.app.ui.util.labelRes
import kotlinx.coroutines.launch

/**
 * Scrolls this field into view only after it receives focus or changes while focused.
 * It never requests focus, so it cannot summon the IME during ordinary scrolling.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.bringIntoViewOnFocusOrChange(trigger: Any? = null): Modifier = composed {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    var isFocused by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(trigger) {
        if (isFocused) {
            bringIntoViewRequester.bringIntoView()
        }
    }

    this
        .bringIntoViewRequester(bringIntoViewRequester)
        .onFocusEvent { focusState ->
            isFocused = focusState.isFocused
            if (focusState.isFocused) {
                scope.launch {
                    bringIntoViewRequester.bringIntoView()
                }
            }
        }
}

/** System-like switch, matching the design's restrained styling. */
@Composable
fun FollowSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.outline,
        label = "track",
    )
    val knobOffset by animateDpAsState(
        targetValue = if (checked) 20.dp else 2.dp,
        label = "knob",
    )
    Box(
        modifier = modifier
            .width(44.dp)
            .height(24.dp)
            .clip(CircleShape)
            .background(trackColor)
            .clickable { onCheckedChange(!checked) },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = knobOffset)
                .size(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurface),
            )
        }
    }
}

/** Small pill label for capability state (Available / Permission required / Shizuku required / Unsupported). */
/**
 * Secondary action button. Tonal fills merge into the dark card surfaces and read as disabled,
 * so secondary actions are outlined with a clearly visible border while keeping primary content.
 */
@Composable
fun SecondaryActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(10.dp),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = shape,
        contentPadding = contentPadding,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        content = content,
    )
}

/** Consistent section label used by the create/edit form (WHEN, CONDITIONS, DO). */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (hint != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                )
            }
        }
        trailing?.invoke()
    }
}

/** Muted success accent that stays readable in both themes (green instead of the blue primary). */
@Composable
fun successAccent(): Color =
    if (MaterialTheme.colorScheme.background.luminance() < 0.5f) Color(0xFF7FC59A) else Color(0xFF2E7D52)

@Composable
fun CapabilityPill(
    status: CapabilityStatus,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val bg = when (status) {
        CapabilityStatus.AVAILABLE -> successAccent().copy(alpha = 0.16f)
        CapabilityStatus.UNSUPPORTED -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when (status) {
        CapabilityStatus.AVAILABLE -> successAccent()
        CapabilityStatus.UNSUPPORTED -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        androidx.compose.material3.Text(
            text = stringResource(status.labelRes),
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}

@Composable
fun CapabilityPill(
    text: String,
    modifier: Modifier = Modifier,
    isSuccess: Boolean = false,
    isError: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val bg = when {
        isSuccess -> successAccent().copy(alpha = 0.16f)
        isError -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val fg = when {
        isSuccess -> successAccent()
        isError -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        androidx.compose.material3.Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = fg,
        )
    }
}
