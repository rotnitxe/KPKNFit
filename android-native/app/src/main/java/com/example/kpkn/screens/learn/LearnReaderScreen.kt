package com.example.kpkn.screens.learn

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpkn.data.learn.ContentBlock
import com.example.kpkn.data.learn.ContentType
import com.example.kpkn.data.learn.LearnModule
import com.example.kpkn.data.repository.LearnRepository

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LearnReaderScreen(
    courseId: String,
    submoduleIndex: Int,
    onBack: () -> Unit,
    onStartQuiz: () -> Unit,
) {
    val module = LearnRepository.getModule(courseId) ?: return
    val submodule = module.submodules.getOrNull(submoduleIndex) ?: return

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        submodule.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Black,
                )
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.Black,
            ) {
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1E1E1E),
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "Hacer quiz del módulo",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Progress indicator
            item {
                val total = module.submodules.size
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFF121212),
                    border = androidx.compose.foundation.BorderStroke(1.dp, module.category.color.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Módulo ${submoduleIndex + 1} de $total",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Serif,
                            fontWeight = FontWeight.Bold,
                            color = module.category.color
                        ),
                    )
                }
            }

            // Content blocks
            submodule.content.forEach { block ->
                item { ContentBlockRenderer(block) }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ContentBlockRenderer(block: ContentBlock) {
    when (block.type) {
        ContentType.HEADING -> {
            Text(
                block.text,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                ),
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
            )
        }
        ContentType.PARAGRAPH -> {
            Text(
                block.text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.8f)
                ),
                lineHeight = 22.sp,
            )
        }
        ContentType.BULLET -> {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                block.items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium.copy(color = Color.White.copy(alpha = 0.8f)), fontWeight = FontWeight.Bold)
                        Text(
                            item,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = Color.White.copy(alpha = 0.8f)
                            ),
                            lineHeight = 22.sp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        ContentType.TIP -> {
            CalloutBlock(
                icon = Icons.Default.Lightbulb,
                text = block.text,
                bgColor = Color(0xFF121212),
                iconColor = Color(0xFF43A047),
                borderColor = Color(0xFF43A047).copy(alpha = 0.25f),
            )
        }
        ContentType.WARNING -> {
            CalloutBlock(
                icon = Icons.Default.Warning,
                text = block.text,
                bgColor = Color(0xFF121212),
                iconColor = Color(0xFFFF8F00),
                borderColor = Color(0xFFFF8F00).copy(alpha = 0.25f),
            )
        }
        ContentType.CALLOUT -> {
            val accentColorVal = block.accentColor?.let { Color(it) } ?: Color(0xFF448AFF)
            CalloutBlock(
                icon = Icons.Default.CheckCircle,
                text = block.text,
                bgColor = Color(0xFF121212),
                iconColor = accentColorVal,
                borderColor = accentColorVal.copy(alpha = 0.25f),
            )
        }
    }
}

@Composable
private fun CalloutBlock(
    icon: ImageVector,
    text: String,
    bgColor: Color,
    iconColor: Color,
    borderColor: Color,
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                icon,
                null,
                tint = iconColor,
                modifier = Modifier.size(20.dp).padding(top = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium.copy(
                    color = Color.White.copy(alpha = 0.8f)
                ),
                lineHeight = 20.sp,
            )
        }
    }
}
