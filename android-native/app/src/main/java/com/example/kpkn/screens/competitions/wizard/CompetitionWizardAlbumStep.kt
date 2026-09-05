package com.example.kpkn.screens.competitions.wizard

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.ui.components.KpknSheetTokens

@Composable
fun CompetitionWizardAlbumStep(
    record: CompetitionRecord,
    viewModel: CompetitionWizardViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(8),
    ) { uris ->
        uris.forEach { uri -> viewModel.addMedia(context, uri) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(WizardCardShape)
                .border(1.dp, Color.White.copy(alpha = 0.14f), WizardCardShape)
                .background(Color.White.copy(alpha = 0.05f))
                .clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(KpknSheetTokens.ControlFill)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text("Añadir foto o video", color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Black)
                }
                Text("Se copia al archivo de la app", color = WizardMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
            }
        }
        if (record.photos.isNotEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(record.photos, key = { it.id }) { photo ->
                    Box {
                        AsyncImage(
                            model = Uri.parse(photo.uri),
                            contentDescription = null,
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        if (photo.kind == CompetitionMediaKind.VIDEO) {
                            Text(
                                "VIDEO",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text(
                            "×",
                            color = Color.White,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .clickable { viewModel.removeMedia(photo.id) }
                                .padding(6.dp),
                        )
                    }
                }
            }
        }
    }
}
