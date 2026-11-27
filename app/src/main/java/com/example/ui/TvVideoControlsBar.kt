package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.R

/**
 * Custom D-pad-optimized on-screen video playback controls for Android TV.
 *
 * Layout hierarchy (top to bottom):
 * 1. Video title, transient seek badge (-10s / +10s / 0:00), and gallery index counter.
 * 2. Full-width D-pad scrub timeline with current time and duration labels.
 * 3. Centered large transport bar: Restart | Rewind 10s | Play/Pause (hero focus target) | Forward 10s | Mute.
 *
 * Explicit focus wiring guarantees predictable D-pad navigation with zero dead spots:
 * - Play/Pause receives initial bottom-bar focus via [playPauseFocusRequester].
 * - Pressing Up from any transport button moves cleanly to the seek slider; pressing Up from the
 *   seek slider returns focus to the video canvas ([mediaCanvasFocusRequester]).
 * - Pressing Left/Right traverses the transport buttons strictly in order, and pressing Right on
 *   the rightmost control (Mute or Seek Slider) moves into [sidePanelFocusRequester] when open.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvVideoControlsBar(
    title: String,
    pageIndicator: String,
    isPlaying: Boolean,
    isMuted: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    sliderValue: Float,
    isDraggingSlider: Boolean,
    seekFeedbackLabel: String?,
    playPauseFocusRequester: FocusRequester,
    mediaCanvasFocusRequester: FocusRequester,
    sidePanelFocusRequester: FocusRequester?,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onRestart: () -> Unit,
    onToggleMute: () -> Unit,
    onSliderChange: (Float) -> Unit,
    onSliderChangeFinished: () -> Unit,
    onTvSliderStep: (Float) -> Unit,
    onUserInteraction: () -> Unit,
    onFocusChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val seekBarFocusRequester = remember { FocusRequester() }
    val restartFocusRequester = remember { FocusRequester() }
    val rewindFocusRequester = remember { FocusRequester() }
    val forwardFocusRequester = remember { FocusRequester() }
    val muteFocusRequester = remember { FocusRequester() }

    val displayPositionMs = if (isDraggingSlider) sliderValue.toInt() else currentPositionMs
    val maxDurationFloat = durationMs.toFloat().coerceAtLeast(1f)
    val seekStep = (maxDurationFloat / 20f).coerceIn(5_000f, 30_000f)
    val seekSteps = ((maxDurationFloat / seekStep).toInt() - 1).coerceIn(1, 50)
    val seekBarContentDescription = stringResource(R.string.tv_video_seek_bar_cd)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.68f),
                        Color.Black.copy(alpha = 0.92f)
                    )
                )
            )
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Start)
            )
            .padding(horizontal = 24.dp, vertical = 16.dp)
            .focusGroup()
            .onFocusChanged { onFocusChanged(it.hasFocus) }
            .testTag("tv_video_controls_bar")
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF14171D).copy(alpha = 0.88f),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Row 1: Video title, transient seek feedback pill, and gallery index counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 12.dp)
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        AnimatedVisibility(
                            visible = !seekFeedbackLabel.isNullOrBlank(),
                            enter = fadeIn() + scaleIn(initialScale = 0.9f),
                            exit = fadeOut() + scaleOut(targetScale = 0.9f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                                modifier = Modifier.testTag("tv_video_seek_feedback_badge")
                            ) {
                                Text(
                                    text = seekFeedbackLabel.orEmpty(),
                                    color = Color.White,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Text(
                            text = pageIndicator,
                            color = Color.White.copy(alpha = 0.78f),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Row 2: D-pad scrub timeline with crisp timestamps
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(displayPositionMs),
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag("tv_video_current_time")
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Slider(
                        value = sliderValue.coerceIn(0f, maxDurationFloat),
                        onValueChange = { newValue ->
                            onUserInteraction()
                            onSliderChange(newValue)
                        },
                        onValueChangeFinished = {
                            onUserInteraction()
                            onSliderChangeFinished()
                        },
                        valueRange = 0f..maxDurationFloat,
                        colors = SliderDefaults.colors(
                            thumbColor = MaterialTheme.colorScheme.primary,
                            activeTrackColor = MaterialTheme.colorScheme.primary,
                            inactiveTrackColor = Color.White.copy(alpha = 0.26f)
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = seekBarContentDescription }
                            .focusRequester(seekBarFocusRequester)
                            .focusProperties {
                                up = mediaCanvasFocusRequester
                                down = playPauseFocusRequester
                                left = FocusRequester.Cancel
                                right = sidePanelFocusRequester ?: FocusRequester.Cancel
                            }
                            .tvFocus(shape = RoundedCornerShape(12.dp))
                            .padding(horizontal = 4.dp)
                            .tvSlider(
                                value = sliderValue.coerceIn(0f, maxDurationFloat),
                                onValueChange = { newValue ->
                                    onUserInteraction()
                                    onTvSliderStep(newValue)
                                },
                                valueRange = 0f..maxDurationFloat,
                                steps = seekSteps
                            )
                            .testTag("viewer_video_seek_slider")
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = formatTime(durationMs),
                        color = Color.White.copy(alpha = 0.78f),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.testTag("tv_video_duration_time")
                    )
                }

                // Row 3: Centered D-pad transport controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TvTransportActionButton(
                        icon = Icons.Default.Replay,
                        contentDescription = stringResource(R.string.tv_video_restart),
                        onClick = {
                            onUserInteraction()
                            onRestart()
                        },
                        buttonSize = 52.dp,
                        iconSize = 26.dp,
                        containerColor = Color.White.copy(alpha = 0.12f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .focusRequester(restartFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                down = FocusRequester.Cancel
                                left = FocusRequester.Cancel
                                right = rewindFocusRequester
                            }
                            .testTag("tv_video_restart_button")
                    )

                    TvTransportActionButton(
                        icon = Icons.Default.Replay10,
                        contentDescription = stringResource(R.string.tv_video_seek_back_10),
                        onClick = {
                            onUserInteraction()
                            onSeekBackward()
                        },
                        buttonSize = 54.dp,
                        iconSize = 30.dp,
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .focusRequester(rewindFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                down = FocusRequester.Cancel
                                left = restartFocusRequester
                                right = playPauseFocusRequester
                            }
                            .testTag("tv_video_rewind_button")
                    )

                    TvTransportActionButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        onClick = {
                            onUserInteraction()
                            onTogglePlayPause()
                        },
                        buttonSize = 62.dp,
                        iconSize = 36.dp,
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier
                            .focusRequester(playPauseFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                down = FocusRequester.Cancel
                                left = rewindFocusRequester
                                right = forwardFocusRequester
                            }
                            .testTag("viewer_video_play_pause_button")
                    )

                    TvTransportActionButton(
                        icon = Icons.Default.Forward10,
                        contentDescription = stringResource(R.string.tv_video_seek_forward_10),
                        onClick = {
                            onUserInteraction()
                            onSeekForward()
                        },
                        buttonSize = 54.dp,
                        iconSize = 30.dp,
                        containerColor = Color.White.copy(alpha = 0.14f),
                        contentColor = Color.White,
                        modifier = Modifier
                            .focusRequester(forwardFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                down = FocusRequester.Cancel
                                left = playPauseFocusRequester
                                right = muteFocusRequester
                            }
                            .testTag("tv_video_forward_button")
                    )

                    TvTransportActionButton(
                        icon = if (isMuted) {
                            Icons.AutoMirrored.Filled.VolumeOff
                        } else {
                            Icons.AutoMirrored.Filled.VolumeUp
                        },
                        contentDescription = if (isMuted) {
                            stringResource(R.string.tv_video_unmute)
                        } else {
                            stringResource(R.string.tv_video_mute)
                        },
                        onClick = {
                            onUserInteraction()
                            onToggleMute()
                        },
                        buttonSize = 52.dp,
                        iconSize = 26.dp,
                        containerColor = if (isMuted) {
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
                        } else {
                            Color.White.copy(alpha = 0.12f)
                        },
                        contentColor = if (isMuted) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            Color.White
                        },
                        modifier = Modifier
                            .focusRequester(muteFocusRequester)
                            .focusProperties {
                                up = seekBarFocusRequester
                                down = FocusRequester.Cancel
                                left = forwardFocusRequester
                                right = sidePanelFocusRequester ?: FocusRequester.Cancel
                            }
                            .testTag("tv_video_mute_button")
                    )
                }
            }
        }
    }
}

@Composable
private fun TvTransportActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onClick,
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = containerColor,
            contentColor = contentColor
        ),
        modifier = modifier
            .size(buttonSize)
            .tvFocus(shape = CircleShape, scaleOnFocus = true, showBorder = true)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize)
        )
    }
}
