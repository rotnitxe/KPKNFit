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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(submodule.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shadowElevation = 8.dp,
            ) {
                Button(
                    onClick = onStartQuiz,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Text("Hacer quiz del módulo", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, null)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Progress indicator
            item {
                val total = module.submodules.size
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = module.category.color.copy(alpha = 0.1f),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "Módulo ${submoduleIndex + 1} de $total",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = module.category.color,
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
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        ContentType.PARAGRAPH -> {
            Text(
                block.text,
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 22.sp,
            )
        }
        ContentType.BULLET -> {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                block.items.forEach { item ->
                    Row(verticalAlignment = Alignment.Top) {
                        Text("• ", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(
                            item,
                            style = MaterialTheme.typography.bodyMedium,
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
                bgColor = Color(0xFF43A047).copy(alpha = 0.08f),
                iconColor = Color(0xFF43A047),
                borderColor = Color(0xFF43A047).copy(alpha = 0.2f),
            )
        }
        ContentType.WARNING -> {
            CalloutBlock(
                icon = Icons.Default.Warning,
                text = block.text,
                bgColor = Color(0xFFFF8F00).copy(alpha = 0.08f),
                iconColor = Color(0xFFFF8F00),
                borderColor = Color(0xFFFF8F00).copy(alpha = 0.2f),
            )
        }
        ContentType.CALLOUT -> {
            CalloutBlock(
                icon = Icons.Default.CheckCircle,
                text = block.text,
                bgColor = (block.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.08f),
                iconColor = block.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary,
                borderColor = (block.accentColor?.let { Color(it) } ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.2f),
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
        shape = RoundedCornerShape(12.dp),
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
                style = MaterialTheme.typography.bodyMedium,
                lineHeight = 20.sp,
            )
        }
    }
}
