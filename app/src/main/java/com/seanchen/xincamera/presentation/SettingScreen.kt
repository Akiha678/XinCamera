package com.seanchen.xincamera.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 设置页入口层。
 *
 * Route 负责承接导航参数和事件回调；当前设置项还未接入持久化，因此状态先保留在页面内。
 */
@Composable
fun SettingRoute(
    onBackClick: () -> Unit = {}
) {
    SettingScreen(onBackClick = onBackClick)
}

/**
 * 设置页状态层。
 *
 * Screen 负责管理 UI 状态，并把状态和事件继续下发给 ContentView。
 */
@Composable
fun SettingScreen(
    onBackClick: () -> Unit = {}
) {
    var shutterSoundEnabled by remember { mutableStateOf(false) }
    var saveSelfieMirrorEnabled by remember { mutableStateOf(true) }
    var locationEnabled by remember { mutableStateOf(true) }
    var portraitCorrectionEnabled by remember { mutableStateOf(true) }

    SettingContentView(
        shutterSoundEnabled = shutterSoundEnabled,
        saveSelfieMirrorEnabled = saveSelfieMirrorEnabled,
        locationEnabled = locationEnabled,
        portraitCorrectionEnabled = portraitCorrectionEnabled,
        onBackClick = onBackClick,
        onShutterSoundChanged = { shutterSoundEnabled = it },
        onSaveSelfieMirrorChanged = { saveSelfieMirrorEnabled = it },
        onLocationChanged = { locationEnabled = it },
        onPortraitCorrectionChanged = { portraitCorrectionEnabled = it }
    )
}

/**
 * 设置页纯 UI 层。
 *
 * ContentView 只负责布局和渲染，不直接持有业务状态。
 */
@Composable
private fun SettingContentView(
    shutterSoundEnabled: Boolean,
    saveSelfieMirrorEnabled: Boolean,
    locationEnabled: Boolean,
    portraitCorrectionEnabled: Boolean,
    onBackClick: () -> Unit,
    onShutterSoundChanged: (Boolean) -> Unit,
    onSaveSelfieMirrorChanged: (Boolean) -> Unit,
    onLocationChanged: (Boolean) -> Unit,
    onPortraitCorrectionChanged: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SettingColors.Background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(WindowInsets.statusBars.asPaddingValues())
        ) {
            SettingTopBar(onBackClick = onBackClick)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(34.dp)
            ) {
                SettingSection(
                    title = "通用",
                    rows = listOf(
                        SettingRowModel.Navigation(
                            title = "水印",
                            value = "已关闭"
                        ),
                        SettingRowModel.Navigation(title = "构图辅助"),
                        SettingRowModel.Switch(
                            title = "快门声音",
                            checked = shutterSoundEnabled,
                            onCheckedChange = onShutterSoundChanged
                        ),
                        SettingRowModel.Switch(
                            title = "保存自拍镜像",
                            checked = saveSelfieMirrorEnabled,
                            onCheckedChange = onSaveSelfieMirrorChanged
                        ),
                        SettingRowModel.Switch(
                            title = "记录地理位置",
                            checked = locationEnabled,
                            onCheckedChange = onLocationChanged
                        ),
                        SettingRowModel.Navigation(title = "格式"),
                        SettingRowModel.Navigation(title = "保留设置")
                    )
                )

                SettingSection(
                    title = "照片",
                    rows = listOf(
                        SettingRowModel.Navigation(
                            title = "长按快门",
                            value = "无影抓拍"
                        ),
                        SettingRowModel.Navigation(
                            title = "辅助快门",
                            value = "关闭"
                        ),
                        SettingRowModel.Navigation(title = "镜头焦段"),
                        SettingRowModel.Navigation(title = "文字与二维码扫描"),
                        SettingRowModel.Switch(
                            title = "人像畸变校正",
                            description = "对照片边缘的人像畸变进行校正",
                            checked = portraitCorrectionEnabled,
                            onCheckedChange = onPortraitCorrectionChanged
                        )
                    )
                )
            }
        }
    }
}

@Composable
private fun SettingTopBar(
    onBackClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(78.dp)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .clickable(onClick = onBackClick),
            contentAlignment = Alignment.Center
        ) {
            BackGlyph()
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = "设置",
            color = SettingColors.PrimaryText,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SettingSection(
    title: String,
    rows: List<SettingRowModel>
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 16.dp),
            color = SettingColors.SectionText,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = SettingColors.Card
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                rows.forEachIndexed { index, row ->
                    SettingRow(
                        model = row,
                        showDivider = index != rows.lastIndex
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingRow(
    model: SettingRowModel,
    showDivider: Boolean
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(if (model.description == null) 74.dp else 92.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = model.title,
                    color = SettingColors.PrimaryText,
                    fontSize = 25.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                model.description?.let { description ->
                    Text(
                        text = description,
                        modifier = Modifier.padding(top = 5.dp),
                        color = SettingColors.SecondaryText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            when (model) {
                is SettingRowModel.Navigation -> {
                    model.value?.let { value ->
                        Text(
                            text = value,
                            color = SettingColors.SecondaryText,
                            fontSize = 21.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                    }
                    ChevronGlyph()
                }

                is SettingRowModel.Switch -> {
                    SettingSwitch(
                        checked = model.checked,
                        onCheckedChange = model.onCheckedChange
                    )
                }
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(SettingColors.Divider)
            )
        }
    }
}

@Composable
private fun SettingSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    val trackColor = if (checked) SettingColors.Accent else SettingColors.SwitchOffTrack
    Box(
        modifier = Modifier
            .size(width = 62.dp, height = 38.dp)
            .clip(RoundedCornerShape(50))
            .background(trackColor)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}

@Composable
private fun BackGlyph() {
    Canvas(modifier = Modifier.size(width = 30.dp, height = 30.dp)) {
        val stroke = Stroke(width = 2.8.dp.toPx(), cap = StrokeCap.Round)
        drawLine(
            color = SettingColors.PrimaryText,
            start = Offset(size.width * 0.75f, size.height * 0.12f),
            end = Offset(size.width * 0.25f, size.height * 0.50f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
        drawLine(
            color = SettingColors.PrimaryText,
            start = Offset(size.width * 0.25f, size.height * 0.50f),
            end = Offset(size.width * 0.75f, size.height * 0.88f),
            strokeWidth = stroke.width,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun ChevronGlyph() {
    Canvas(modifier = Modifier.size(width = 20.dp, height = 28.dp)) {
        drawLine(
            color = SettingColors.Chevron,
            start = Offset(size.width * 0.22f, size.height * 0.10f),
            end = Offset(size.width * 0.78f, size.height * 0.50f),
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = SettingColors.Chevron,
            start = Offset(size.width * 0.78f, size.height * 0.50f),
            end = Offset(size.width * 0.22f, size.height * 0.90f),
            strokeWidth = 3.2.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

private sealed class SettingRowModel(
    open val title: String,
    open val description: String?
) {
    data class Navigation(
        override val title: String,
        val value: String? = null,
        override val description: String? = null
    ) : SettingRowModel(title, description)

    data class Switch(
        override val title: String,
        override val description: String? = null,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit
    ) : SettingRowModel(title, description)
}

private object SettingColors {
    val Background = Color(0xFF000000)
    val Card = Color(0xFF1A1A1A)
    val PrimaryText = Color(0xFFEDEDED)
    val SecondaryText = Color(0xFF8E8E93)
    val SectionText = Color(0xFF8E8E93)
    val Divider = Color(0xFF303030)
    val Chevron = Color(0xFF5F5F64)
    val Accent = Color(0xFFFFB000)
    val SwitchOffTrack = Color(0xFF56565A)
}

