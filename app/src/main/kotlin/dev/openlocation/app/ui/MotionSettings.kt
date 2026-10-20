@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package dev.openlocation.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.openlocation.app.R
import dev.openlocation.runtime.SpeedInput
import dev.openlocation.runtime.SpeedUnit
import dev.openlocation.scenario.*

/** Complete per-mode draft. Saving is atomic and preserves other modes' profiles. */
@Composable
fun MotionSettings(
    scene: SceneConfig,
    speedUnit: SpeedUnit,
    onDismiss: () -> Unit,
    onSave: (SceneConfig) -> Unit,
) {
    var draft by remember(scene.locomotion) { mutableStateOf(scene) }
    val locale = LocalConfiguration.current.locales[0]
    val initialSpeedInput =
        remember(scene.locomotion, speedUnit, locale) {
            SpeedInput.format(speedUnit.fromMetersPerSecond(scene.speedMetersPerSecond), locale)
        }
    var speedInput by
        rememberSaveable(scene.locomotion, speedUnit, locale.toLanguageTag()) {
            mutableStateOf(initialSpeedInput)
        }
    // Keeping an untouched field preserves the exact SI value, including imported precision.
    val targetSpeed =
        if (speedInput == initialSpeedInput) scene.speedMetersPerSecond
        else SpeedInput.metersPerSecond(speedInput, speedUnit, locale)
    val focusManager = LocalFocusManager.current
    val gait = draft.locomotion == Locomotion.WALK || draft.locomotion == Locomotion.RUN
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.9f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                stringResource(draft.locomotion.label()),
                style = MaterialTheme.typography.headlineMedium,
            )
            OutlinedTextField(
                value = speedInput,
                onValueChange = { speedInput = it },
                label = { Text(stringResource(R.string.target_speed)) },
                suffix = { Text(stringResource(speedUnit.label())) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = targetSpeed == null,
                supportingText =
                    if (targetSpeed == null) {
                        {
                            Text(
                                stringResource(
                                    R.string.speed_input_range,
                                    SpeedInput.format(
                                        speedUnit.fromMetersPerSecond(
                                            SceneConfig.MAX_SPEED_METERS_PER_SECOND
                                        ),
                                        locale,
                                    ),
                                    stringResource(speedUnit.label()),
                                )
                            )
                        }
                    } else null,
                keyboardOptions =
                    KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done,
                    ),
                keyboardActions =
                    KeyboardActions(
                        onDone = { if (targetSpeed != null) focusManager.clearFocus() }
                    ),
            )
            ParameterSlider(
                R.string.speed_variation,
                speedUnit.fromMetersPerSecond(draft.speedVariationMetersPerSecond),
                0.0..speedUnit.fromMetersPerSecond(
                        when (draft.locomotion) {
                            Locomotion.CAR -> 8.0
                            Locomotion.BICYCLE -> 3.0
                            else -> 1.5
                        }
                    ),
                speedText(draft.speedVariationMetersPerSecond, speedUnit),
            ) {
                draft = draft.copy(speedVariationMetersPerSecond = speedUnit.toMetersPerSecond(it))
            }
            ParameterSlider(
                R.string.variation_period,
                draft.variationPeriodSeconds,
                2.0..60.0,
                stringResource(R.string.seconds_value, draft.variationPeriodSeconds),
            ) {
                draft = draft.copy(variationPeriodSeconds = it)
            }
            if (gait) {
                HorizontalDivider()
                ParameterSlider(
                    R.string.stride_length,
                    draft.strideMeters,
                    0.3..1.8,
                    stringResource(R.string.meters_value, draft.strideMeters),
                ) {
                    draft =
                        draft.copy(
                            strideMeters = it,
                            strideVariationMeters =
                                draft.strideVariationMeters.coerceAtMost(it * 0.75),
                        )
                }
                ParameterSlider(
                    R.string.stride_variation,
                    draft.strideVariationMeters,
                    0.0..draft.strideMeters * 0.5,
                    stringResource(R.string.meters_value, draft.strideVariationMeters),
                ) {
                    draft = draft.copy(strideVariationMeters = it)
                }
                ParameterSlider(
                    R.string.arm_swing,
                    Math.toDegrees(draft.armSwingRadians),
                    0.0..60.0,
                    stringResource(R.string.degrees_value, Math.toDegrees(draft.armSwingRadians)),
                ) {
                    draft = draft.copy(armSwingRadians = Math.toRadians(it))
                }
                ParameterSlider(
                    R.string.arm_swing_variation,
                    Math.toDegrees(draft.armSwingVariationRadians),
                    0.0..30.0,
                    stringResource(
                        R.string.degrees_value,
                        Math.toDegrees(draft.armSwingVariationRadians),
                    ),
                ) {
                    draft = draft.copy(armSwingVariationRadians = Math.toRadians(it))
                }
            }
            ParameterSlider(
                R.string.heading_variation,
                Math.toDegrees(draft.headingVariationRadians),
                0.0..15.0,
                stringResource(
                    R.string.degrees_value,
                    Math.toDegrees(draft.headingVariationRadians),
                ),
            ) {
                draft = draft.copy(headingVariationRadians = Math.toRadians(it))
            }
            HorizontalDivider()
            SectionTitle(R.string.routes)
            Row(
                Modifier.fillMaxWidth()
                    .toggleable(
                        value = draft.curveSpeedLimitEnabled,
                        role = Role.Switch,
                        onValueChange = { draft = draft.copy(curveSpeedLimitEnabled = it) },
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.corner_slowdown), Modifier.weight(1f))
                Switch(
                    checked = draft.curveSpeedLimitEnabled,
                    onCheckedChange = null,
                )
            }
            if (draft.curveSpeedLimitEnabled)
                Text(
                    stringResource(R.string.corner_slowdown_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            ParameterSlider(
                R.string.route_variation,
                draft.routeDeviationMeters,
                0.0..30.0,
                stringResource(R.string.meters_value, draft.routeDeviationMeters),
            ) {
                draft = draft.copy(routeDeviationMeters = it)
            }
            if (draft.routeDeviationMeters > 0)
                ParameterSlider(
                    R.string.route_variation_period,
                    draft.routeDeviationWavelengthMeters,
                    10.0..200.0,
                    stringResource(R.string.meters_value, draft.routeDeviationWavelengthMeters),
                ) {
                    draft = draft.copy(routeDeviationWavelengthMeters = it)
                }
            HorizontalDivider()
            SectionTitle(R.string.placement)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PhonePlacement.entries.forEach { placement ->
                    FilterChip(
                        selected = draft.phonePlacement == placement,
                        onClick = { draft = draft.copy(phonePlacement = placement) },
                        label = { Text(stringResource(placement.label())) },
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.environment_test), Modifier.weight(1f))
                Switch(
                    checked = draft.environmentSegments.isNotEmpty(),
                    onCheckedChange = { value ->
                        draft =
                            draft.copy(
                                environmentSegments =
                                    if (value) DemoScenes.create().environmentSegments
                                    else emptyList()
                            )
                    },
                )
            }
            if (draft.environmentSegments.isNotEmpty())
                Text(
                    stringResource(R.string.environment_timing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            HorizontalDivider()
            SectionTitle(R.string.gnss_signal)
            ParameterSlider(
                R.string.signal_strength,
                draft.gnss.cn0DbHz,
                0.0..55.0,
                stringResource(R.string.cn0_value, draft.gnss.cn0DbHz),
            ) {
                draft = draft.copy(gnss = draft.gnss.copy(cn0DbHz = it))
            }
            ParameterSlider(
                R.string.signal_variation,
                draft.gnss.variationDb,
                0.0..20.0,
                stringResource(R.string.db_value, draft.gnss.variationDb),
            ) {
                draft = draft.copy(gnss = draft.gnss.copy(variationDb = it))
            }
            ParameterSlider(
                R.string.elevation_mask,
                draft.gnss.elevationMaskDegrees,
                0.0..45.0,
                stringResource(R.string.degrees_value, draft.gnss.elevationMaskDegrees),
            ) {
                draft = draft.copy(gnss = draft.gnss.copy(elevationMaskDegrees = it))
            }
            Button(
                onClick = {
                    targetSpeed?.let { onSave(draft.copy(speedMetersPerSecond = it)) }
                },
                enabled = targetSpeed != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) {
                Text(stringResource(R.string.save))
            }
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
}

@Composable
internal fun ParameterSlider(
    label: Int,
    value: Double,
    range: ClosedFloatingPointRange<Double>,
    formatted: String,
    onChange: (Double) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(label),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(formatted, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat()),
            onValueChange = { onChange(it.toDouble()) },
            valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        )
    }
}
