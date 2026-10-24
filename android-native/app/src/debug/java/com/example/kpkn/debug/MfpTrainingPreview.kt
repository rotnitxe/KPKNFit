package com.example.kpkn.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.kpkn.data.models.*
import com.example.kpkn.domain.exercises.catalogv2.*
import com.example.kpkn.domain.workout.MyoRepsPreset
import com.example.kpkn.screens.sessioneditor.components.*
import com.example.kpkn.screens.settings.TrainingSettingsBlock
import com.example.kpkn.screens.workout.*
import com.example.kpkn.screens.workout.components.*
import com.example.kpkn.screens.onboarding.design.WizardColors
import com.example.kpkn.ui.design.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sólo fixtures de depuración: no repositorios de usuario ni escrituras. */
internal object MfpTrainingPreview {
    @Composable fun Swap(state: String, onSelected: (ExerciseMuscleInfo) -> Unit = {}) {
        val context=LocalContext.current
        val catalog by produceState<ExerciseCatalogV2?>(null) {
            value=withContext(Dispatchers.IO) { ExerciseCatalogV2Loader.decodeApproved(context.assets.open("exercise_catalog_v2.json").bufferedReader().use { it.readText() }) }
        }
        val data=catalog
        if(data==null) { Text("Cargando catálogo",color=WizardColors.text);return }
        val repository=remember(data) { InMemoryExerciseCatalogRepositoryV2(data) }
        LaunchedEffect(repository) { repository.load() }
        val definition=data.families.flatMap { it.definitions }.first { it.id=="bench_press" }
        val config=definition.configurations.first { it.profile.equipmentId=="barbell" }
        val exercise=Exercise("fixture-bench",definition.canonicalName,catalogDefinitionId=definition.id,catalogConfigurationId=config.id)
        var query by remember { mutableStateOf(if(state=="searching") "remo" else "") }
        ExercisePickerV2Catalog(repository,query,{query=it},false,emptySet(),onSelected, {emptyList()}, {}, {},onDismiss={},
            initialCatalogDefinitionId=if(state=="no_suggestions") null else definition.id,
            initialCatalogConfigurationId=if(state=="no_suggestions") null else config.id,opaqueSurface=true,
            replacement=CatalogReplacementContext(exerciseName=exercise.name,planSummary="3 × 8 · RIR 2",exercise=if(state=="no_suggestions") exercise.copy(catalogConfigurationId=null) else exercise))
    }
    @Composable fun Settings(state: String) {
        var settings by remember { mutableStateOf(if(state=="custom") Settings(restTimerDefaultSeconds=180,autoStartRestAfterRecording=false,algorithmSettings=Settings().algorithmSettings.copy(augeAutoDeload=true)) else Settings()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            TrainingSettingsBlock(settings,{transform -> settings=transform(settings)})
        }
    }
    @Composable fun Myo(state: String) {
        val myo=MyoRepsPreset.applyTo(ExerciseSet("fixture-set",targetReps=12,targetRIR=1,weight=100.0))
        val set=if(state=="myo_back" || state=="editor") myo else ExerciseSet("fixture-set",targetReps=8,targetRIR=2,weight=if(state=="plates_unreachable") 59.0 else 100.0)
        val inventory=if(state=="no_inventory") null else if(state=="plates_unreachable") EquipmentInventory(20.0,listOf(PlateStock(20.0,1),PlateStock(1.25,1))) else
            EquipmentInventory(20.0,listOf(PlateStock(25.0,1),PlateStock(10.0,1),PlateStock(5.0,1)))
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if(state=="editor") {
                var draft by remember { mutableStateOf(myo) }
                InlineSetRowTechniqueChips(draft,{transform -> draft=transform(draft)})
            } else if(state.endsWith("_back")) {
                val backSet=when(state) { "drop_back" -> set.copy(isDropSet=true); "rest_back" -> set.copy(isRestPause=true);else -> set }
                Box(Modifier.fillMaxWidth().height(620.dp).testTag("training-card")) {
                    SetCardTechniqueBack(backSet,"100 kg","12 reps","RIR 1",100.0,12,WizardColors.text,false,{}, {}, {_,_->},
                        initialRestPauseEnabled=state=="myo_back" || state=="rest_back",initialDropEnabled=state=="drop_back")
                }
            } else {
                val context=LocalContext.current
                val catalog by produceState<ExerciseCatalogV2?>(null) { value=withContext(Dispatchers.IO) { ExerciseCatalogV2Loader.decodeApproved(context.assets.open("exercise_catalog_v2.json").bufferedReader().use { it.readText() }) } }
                val config=catalog?.families?.flatMap { it.definitions }?.firstOrNull { it.id=="bench_press" }?.configurations?.firstOrNull { it.profile.equipmentId=="barbell" }
                if(config!=null) {
                    val exercise=Exercise("fixture-bench","Press de banca",sets=listOf(set),catalogDefinitionId="bench_press",catalogConfigurationId=config.id,loadQuantityConvention=LoadQuantityConvention.TOTAL_EXTERNAL)
                    Box(Modifier.fillMaxWidth().height(620.dp).testTag("training-card")) {
                        SetInputCardV2(exercise=exercise,setIndex=0,currentSet=set,ghostSet=null,onShowHistory={},onSetBodyWeight={},initialBodyWeight=80.0,
                            recordActionHolder=remember { RecordActionHolder() },initialDraft=WorkoutSetDraft(weightText=set.weight.toString(),valueText="8",intensityText="2"),
                            onRecordV2={_,_,_,_,_,_,_,_,_,_->},equipmentInventory=inventory,effortFireSecondsOverride=1.7f)
                    }
                }
            }
        }
    }
}
