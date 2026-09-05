package com.example.kpkn.screens.competitions

import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.kpkn.data.competitions.PowerliftingFederationCatalog
import com.example.kpkn.data.models.CompetitionAttemptResult
import com.example.kpkn.data.models.CompetitionMediaKind
import com.example.kpkn.data.models.CompetitionMovementType
import com.example.kpkn.data.models.CompetitionPhoto
import com.example.kpkn.data.models.CompetitionRecord
import com.example.kpkn.data.models.CompetitionRecordStatus
import com.example.kpkn.data.repository.CompetitionRepository
import com.example.kpkn.domain.competitions.CompetitionCompare
import com.example.kpkn.domain.competitions.CompetitionComparePoint
import com.example.kpkn.domain.competitions.CompetitionPlaceHonors
import com.example.kpkn.domain.competitions.CompetitionScoring
import com.example.kpkn.screens.competitions.wizard.BronzeMetal
import com.example.kpkn.screens.competitions.wizard.FederationMark
import com.example.kpkn.screens.competitions.wizard.GoldMetal
import com.example.kpkn.screens.competitions.wizard.SilverMetal
import com.example.kpkn.screens.competitions.wizard.WizardCardShape
import com.example.kpkn.screens.competitions.wizard.WizardFieldShape
import com.example.kpkn.screens.competitions.wizard.WizardInk
import com.example.kpkn.screens.competitions.wizard.WizardMuted
import com.example.kpkn.ui.components.KpknSheetTokens
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ProfileCompetitionsArchive(
    onOpenCompetition: (String) -> Unit,
    onCreateCompetition: () -> Unit,
) {
    val records by runCatching { CompetitionRepository.getInstance().records }
        .getOrElse { kotlinx.coroutines.flow.MutableStateFlow(emptyList()) }
        .collectAsState()
    val visible = records.filterNot { it.status == CompetitionRecordStatus.ARCHIVED }
    val series = remember(visible) { CompetitionCompare.series(visible) }
    var selectedA by remember(series) { mutableStateOf(series.getOrNull(series.lastIndex - 1)?.recordId) }
    var selectedB by remember(series) { mutableStateOf(series.lastOrNull()?.recordId) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Archivo de competiciones", color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
        if (visible.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(WizardFieldShape)
                    .background(KpknSheetTokens.ControlFill)
                    .clickable(onClick = onCreateCompetition)
                    .padding(18.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text("Registrar powerlifting", color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Black)
            }
            return@Column
        }

        visible.sortedByDescending { it.eventDate.orEmpty() }.forEach { record ->
            MeetFicha(
                record = record,
                selected = record.id == selectedA || record.id == selectedB,
                onOpen = { onOpenCompetition(record.id) },
                onSelect = {
                    when {
                        selectedA == record.id -> selectedA = null
                        selectedB == record.id -> selectedB = null
                        selectedA == null -> selectedA = record.id
                        selectedB == null -> selectedB = record.id
                        else -> {
                            selectedA = selectedB
                            selectedB = record.id
                        }
                    }
                },
            )
        }

        val album = visible.flatMap { record -> record.photos.map { record.id to it } }
        if (album.isNotEmpty()) {
            Text("Álbum", color = Color.White, fontWeight = FontWeight.Bold)
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                album.forEach { (recordId, photo) ->
                    AlbumThumb(photo = photo, onClick = { onOpenCompetition(recordId) })
                }
            }
        }

        if (series.size >= 2) {
            Text("Comparativa", color = Color.White, fontWeight = FontWeight.Bold)
            TotalSparkline(series)
            val pair = selectedA?.let { a -> selectedB?.let { b -> CompetitionCompare.pair(visible, a, b) } }
            if (pair != null) {
                ComparePair(pair.first, pair.second)
            } else {
                Text("Elige dos meets para comparar.", color = WizardMuted, fontSize = 13.sp)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(WizardFieldShape)
                .background(KpknSheetTokens.ControlFill)
                .clickable(onClick = onCreateCompetition)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("Registrar otra", color = KpknSheetTokens.ControlLabel, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun MeetFicha(
    record: CompetitionRecord,
    selected: Boolean,
    onOpen: () -> Unit,
    onSelect: () -> Unit,
) {
    val point = remember(record) { CompetitionCompare.toPoint(record) }
    val fed = PowerliftingFederationCatalog.byId(record.federationId)
    val honor = CompetitionPlaceHonors.fromPlacement(point.place, record.trophyId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(WizardCardShape)
            .background(if (selected) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onOpen)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            HonorMark(medal = honor?.medal?.id ?: record.medal, trophy = honor?.trophy?.label)
            FederationMark(
                federation = fed,
                selected = false,
                size = 44.dp,
                customLabel = record.federation?.take(2),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(record.title.ifBlank { fed?.shortName ?: "Meet" }, color = Color.White, fontWeight = FontWeight.Black)
                Text(
                    listOfNotNull(
                        point.date?.format(DateTimeFormatter.ofPattern("d MMM yyyy", Locale("es"))),
                        record.location,
                    ).joinToString(" · "),
                    color = WizardMuted,
                    fontSize = 12.sp,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f))
                    .clickable(onClick = onSelect)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Text(if (selected) "Elegido" else "Comparar", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            LiftMini("S", point.squatKg, record, CompetitionMovementType.SQUAT)
            LiftMini("B", point.benchKg, record, CompetitionMovementType.BENCH)
            LiftMini("D", point.deadliftKg, record, CompetitionMovementType.DEADLIFT)
        }
        val points = CompetitionScoring.displayedPoints(record)
        if (points != null) {
            Text("${points.label} ${CompetitionScoring.formatPoints(points.value)}", color = WizardInk, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}

@Composable
private fun HonorMark(medal: String?, trophy: String?) {
    val metal = when (medal?.lowercase()) {
        "gold" -> GoldMetal
        "silver" -> SilverMetal
        "bronze" -> BronzeMetal
        else -> Color.White.copy(alpha = 0.18f)
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(metal),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            when {
                medal.equals("gold", true) -> "1"
                medal.equals("silver", true) -> "2"
                medal.equals("bronze", true) -> "3"
                else -> trophy?.firstOrNull()?.uppercaseChar()?.toString() ?: "T"
            },
            color = Color(0xFF1A1A1A),
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun LiftMini(
    label: String,
    best: Double?,
    record: CompetitionRecord,
    type: CompetitionMovementType,
) {
    val attempts = record.technicalBlocks.firstOrNull { it.movementType == type }?.attempts.orEmpty()
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = WizardMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Text(best?.let { CompetitionScoring.formatKg(it) } ?: "—", color = Color.White, fontWeight = FontWeight.Black)
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            attempts.sortedBy { it.attemptNumber }.take(3).forEach { attempt ->
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(
                            when (attempt.resultType) {
                                CompetitionAttemptResult.GOOD_LIFT -> Color(0xFF2F6B4F)
                                CompetitionAttemptResult.NO_LIFT -> Color(0xFF7A3232)
                                CompetitionAttemptResult.SKIPPED -> Color.White.copy(alpha = 0.12f)
                                CompetitionAttemptResult.PENDING -> Color.White.copy(alpha = 0.28f)
                            },
                        ),
                )
            }
        }
    }
}

@Composable
private fun AlbumThumb(photo: CompetitionPhoto, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = Uri.parse(photo.uri),
            contentDescription = null,
            modifier = Modifier.matchParentSize(),
            contentScale = ContentScale.Crop,
        )
        if (photo.kind == CompetitionMediaKind.VIDEO) {
            Text(
                "▶",
                color = Color.White,
                modifier = Modifier.align(Alignment.Center),
                fontWeight = FontWeight.Black,
            )
        }
    }
}

@Composable
private fun TotalSparkline(series: List<CompetitionComparePoint>) {
    val values = series.mapNotNull { it.totalKg }
    if (values.size < 2) return
    val max = values.max()
    val min = values.min()
    val span = (max - min).takeIf { it > 0 } ?: 1.0
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(WizardFieldShape)
            .background(Color.White.copy(alpha = 0.04f))
            .padding(8.dp),
    ) {
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / (values.size - 1).coerceAtLeast(1)
            val y = size.height - (((value - min) / span).toFloat() * size.height)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            drawCircle(Color.White.copy(alpha = 0.85f), radius = 4.dp.toPx(), center = Offset(x, y))
        }
        drawPath(path, Color.White.copy(alpha = 0.7f), style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round))
    }
}

@Composable
private fun ComparePair(a: CompetitionComparePoint, b: CompetitionComparePoint) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(a.federationLabel ?: a.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(b.federationLabel ?: b.title, color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        }
        CompareBar("Total", a.totalKg, b.totalKg)
        CompareBar("Sentadilla", a.squatKg, b.squatKg)
        CompareBar("Banca", a.benchKg, b.benchKg)
        CompareBar("Peso muerto", a.deadliftKg, b.deadliftKg)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PointsChip(a)
            PointsChip(b)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceChip(a.place)
            PlaceChip(b.place)
        }
    }
}

@Composable
private fun CompareBar(label: String, left: Double?, right: Double?) {
    val max = listOfNotNull(left, right).maxOrNull() ?: return
    Column {
        Text(label, color = WizardMuted, fontSize = 12.sp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(((left ?: 0.0) / max).toFloat().coerceAtLeast(0.08f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.55f)),
            )
            Text(left?.let { CompetitionScoring.formatKg(it) } ?: "—", color = Color.White, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .weight(((right ?: 0.0) / max).toFloat().coerceAtLeast(0.08f))
                    .height(10.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.28f)),
            )
            Text(right?.let { CompetitionScoring.formatKg(it) } ?: "—", color = Color.White, fontSize = 12.sp)
        }
    }
}

@Composable
private fun PointsChip(point: CompetitionComparePoint) {
    val text = if (point.points != null) {
        "${point.pointsLabel} ${CompetitionScoring.formatPoints(point.points)}"
    } else {
        point.pointsLabel
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(text, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun PlaceChip(place: Int?) {
    if (place == null) return
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text("${place}º", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
    }
}
