import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Keyboard, Pressable, StyleSheet, Text, TextInput, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import type {
  LocalAiNutritionAnalysisItem,
  LocalAiNutritionAnalysisResult,
} from '@kpkn/shared-types';
import { ScreenShell } from '@/components/ScreenShell';
import {
  AlertTriangleIcon,
  CalendarIcon,
  ClockIcon,
  SearchIcon,
  TrashIcon,
  UtensilsIcon,
} from '@/components/icons';
import { useColors } from '@/theme';
import { preloadFoodDatabases, searchFoods } from '@/services/foodSearchService';
import { useMealTemplateStore } from '@/stores/mealTemplateStore';
import { useMobileNutritionStore } from '@/stores/nutritionStore';
import type { NutritionStackParamList } from '@/navigation/types';
import type { NutritionMealType, SavedNutritionEntry } from '@/types/nutrition';
import type { FoodItem } from '@/types/food';

type Nav = NativeStackNavigationProp<NutritionStackParamList, 'NutritionLog'>;
type TabId = 'description' | 'search' | 'templates';

const MEALS: Array<{ id: NutritionMealType; label: string }> = [
  { id: 'breakfast', label: 'Desayuno' },
  { id: 'lunch', label: 'Almuerzo' },
  { id: 'dinner', label: 'Cena' },
  { id: 'snack', label: 'Snack' },
];

const TABS: Array<{ id: TabId; label: string }> = [
  { id: 'description', label: 'Descripcion' },
  { id: 'search', label: 'Buscar' },
  { id: 'templates', label: 'Plantillas' },
];

const EXAMPLES = [
  '2 completos italianos',
  'cazuela de vacuno con papa y zapallo',
  '150g arroz, 180g pollo y ensalada',
];

const today = () => new Date().toISOString().slice(0, 10);
const mealLabel = (value?: NutritionMealType) =>
  value === 'breakfast' ? 'Desayuno' : value === 'dinner' ? 'Cena' : value === 'snack' ? 'Snack' : 'Almuerzo';

const fromFood = (food: FoodItem, raw?: string): LocalAiNutritionAnalysisItem => ({
  rawText: raw ?? food.name,
  canonicalName: food.name,
  calories: food.calories,
  protein: food.protein,
  carbs: food.carbs,
  fats: food.fat,
  source: 'database',
  confidence: 0.92,
  reviewRequired: false,
});

const mergeAnalysis = (
  items: LocalAiNutritionAnalysisItem[],
  prev: LocalAiNutritionAnalysisResult | null,
): LocalAiNutritionAnalysisResult => ({
  items,
  overallConfidence: items.length ? items.reduce((sum, item) => sum + item.confidence, 0) / items.length : 0,
  containsEstimatedItems: items.some(item => item.source !== 'database' || item.reviewRequired),
  requiresReview: items.some(item => item.reviewRequired),
  elapsedMs: prev?.elapsedMs ?? 0,
  modelVersion: prev?.modelVersion ?? null,
  engine: prev?.engine ?? 'heuristics',
  runtimeError: prev?.runtimeError ?? null,
});

export function NutritionLogScreen() {
  const colors = useColors();
  const navigation = useNavigation<Nav>();
  const {
    description,
    status,
    lastAnalysis,
    savedLogs,
    isDetailVisible,
    saveNotice,
    errorMessage,
    setDescription,
    analyze,
    replaceAnalysis,
    saveCurrent,
    deleteLog,
    duplicateLog,
    toggleDetail,
  } = useMobileNutritionStore();
  const templates = useMealTemplateStore(state => state.templates);
  const [tab, setTab] = useState<TabId>('description');
  const [mealType, setMealType] = useState<NutritionMealType>('lunch');
  const [logDate, setLogDate] = useState(today());
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
  const [searching, setSearching] = useState(false);
  const [adjustingIndex, setAdjustingIndex] = useState<number | null>(null);

  useEffect(() => {
    void preloadFoodDatabases();
  }, []);

  useEffect(() => {
    if (tab !== 'search' || searchQuery.trim().length < 2) {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    let cancelled = false;
    setSearching(true);
    const timer = setTimeout(() => {
      searchFoods(searchQuery.trim()).then(result => {
        if (!cancelled) {
          setSearchResults((result.results ?? []) as FoodItem[]);
          setSearching(false);
        }
      }).catch(() => {
        if (!cancelled) {
          setSearchResults([]);
          setSearching(false);
        }
      });
    }, 280);
    return () => { cancelled = true; clearTimeout(timer); };
  }, [tab, searchQuery]);

  const totals = useMemo(() => lastAnalysis?.items.reduce((acc, item) => ({
    calories: acc.calories + item.calories,
    protein: acc.protein + item.protein,
    carbs: acc.carbs + item.carbs,
    fats: acc.fats + item.fats,
  }), { calories: 0, protein: 0, carbs: 0, fats: 0 }) ?? null, [lastAnalysis]);

  const recent = useMemo(() => {
    const seen = new Set<string>();
    return savedLogs.map(log => log.description.trim()).filter(value => value && !seen.has(value) && seen.add(value)).slice(0, 4);
  }, [savedLogs]);

  const canSave = Boolean(description.trim() && lastAnalysis?.items.length);
  const header = (
    <View style={styles.header}>
      <Pressable onPress={() => navigation.goBack()} style={[styles.roundBtn, { backgroundColor: colors.surfaceContainerHigh }]}>
        <Text style={[styles.roundBtnText, { color: colors.onSurface }]}>x</Text>
      </Pressable>
      <View style={{ flex: 1 }}>
        <Text style={[styles.kicker, { color: colors.onSurfaceVariant }]}>Registro rapido</Text>
        <Text style={[styles.title, { color: colors.onSurface }]}>Registra tu comida</Text>
      </View>
      <Pressable onPress={() => navigation.navigate('NutritionDashboard')} style={[styles.ghostBtn, { borderColor: colors.outlineVariant }]}>
        <Text style={[styles.ghostText, { color: colors.onSurface }]}>Panel</Text>
      </Pressable>
    </View>
  );

  const useSearchFood = (food: FoodItem) => {
    const nextItem = fromFood(food, searchQuery || description || food.name);
    if (lastAnalysis && adjustingIndex !== null && lastAnalysis.items[adjustingIndex]) {
      replaceAnalysis(mergeAnalysis(lastAnalysis.items.map((item, index) => index === adjustingIndex ? nextItem : item), lastAnalysis));
    } else if (lastAnalysis?.items.length) {
      replaceAnalysis(mergeAnalysis([...lastAnalysis.items, nextItem], lastAnalysis));
    } else {
      replaceAnalysis(mergeAnalysis([nextItem], lastAnalysis), food.name);
    }
    setAdjustingIndex(null);
    setSearchQuery('');
    setTab('description');
  };

  const useTemplate = (template: { name: string; description: string; calories: number; protein: number; carbs: number; fats: number; quickDescription: string }) => {
    replaceAnalysis(mergeAnalysis([{
      rawText: template.quickDescription || template.name,
      canonicalName: template.name,
      calories: template.calories,
      protein: template.protein,
      carbs: template.carbs,
      fats: template.fats,
      source: 'database',
      confidence: 0.9,
      reviewRequired: false,
    }], lastAnalysis), template.quickDescription || template.name);
    setTab('description');
  };

  return (
    <ScreenShell title="Registrar comida" subtitle="" headerContent={header} contentContainerStyle={{ paddingTop: 8, paddingBottom: 42 }}>
      <View style={{ gap: 16 }}>
        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <View style={styles.heroRow}>
            <View style={{ flex: 1 }}>
              <Text style={[styles.kicker, { color: colors.onSurfaceVariant }]}>Calorias</Text>
              <Text style={[styles.bigNumber, { color: colors.onSurface }]}>{totals ? Math.round(totals.calories) : '--'}</Text>
              <Text style={[styles.heroText, { color: colors.onSurfaceVariant }]}>{status === 'analyzing' ? 'Estamos preparando una referencia util para guardar.' : !lastAnalysis ? 'Las calorias son una referencia practica, no una medicion exacta.' : lastAnalysis.requiresReview ? 'Detectamos puntos que conviene revisar antes de guardar.' : 'Ya puedes guardar esta comida o ajustar un detalle puntual.'}</Text>
            </View>
            <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}><UtensilsIcon size={22} color={colors.primary} /></View>
          </View>
          <View style={[styles.subCard, { backgroundColor: colors.surfaceContainerHigh }]}>
            <Text style={[styles.kicker, { color: colors.onSurfaceVariant }]}>Resumen</Text>
            <Text style={[styles.meta, { color: colors.onSurface }]}>{totals ? `Prot ${Math.round(totals.protein)} g · Carb ${Math.round(totals.carbs)} g · Grasas ${Math.round(totals.fats)} g` : 'Escribe con libertad y analiza solo cuando termines.'}</Text>
          </View>
        </View>

        <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <View style={styles.row}><CalendarIcon size={16} color={colors.onSurfaceVariant} /><TextInput value={logDate} onChangeText={setLogDate} placeholder="YYYY-MM-DD" placeholderTextColor={colors.onSurfaceVariant} style={[styles.input, { color: colors.onSurface }]} /></View>
          <View style={styles.wrap}>
            {MEALS.map(option => {
              const active = option.id === mealType;
              return <Pressable key={option.id} onPress={() => setMealType(option.id)} style={[styles.pill, { backgroundColor: active ? colors.onSurface : colors.surfaceContainerHigh }]}><Text style={[styles.pillText, { color: active ? colors.surface : colors.onSurfaceVariant }]}>{option.label}</Text></Pressable>;
            })}
          </View>
        </View>

        <View style={[styles.tabBar, { backgroundColor: colors.surfaceContainerHigh, borderColor: colors.outlineVariant }]}>
          {TABS.map(option => {
            const active = option.id === tab;
            return <Pressable key={option.id} onPress={() => setTab(option.id)} style={[styles.tab, { backgroundColor: active ? colors.surface : 'transparent' }]}><Text style={[styles.tabText, { color: active ? colors.onSurface : colors.onSurfaceVariant }]}>{option.label}</Text></Pressable>;
          })}
        </View>

        {tab === 'description' && (
          <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <TextInput testID="nutrition-description-input" multiline value={description} onChangeText={setDescription} placeholder="Describe tu comida... ej: 200g arroz, 150g pollo" placeholderTextColor={colors.onSurfaceVariant} textAlignVertical="top" style={[styles.textarea, { color: colors.onSurface }]} />
            <View style={styles.wrap}>
              {EXAMPLES.map(value => <Pressable key={value} onPress={() => setDescription(value)} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>{value}</Text></Pressable>)}
            </View>
            {recent.length > 0 && <View style={styles.wrap}>{recent.map(value => <Pressable key={value} onPress={() => setDescription(value)} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><ClockIcon size={13} color={colors.onSurfaceVariant} /><Text style={[styles.smallPillText, { color: colors.onSurface }]}>{value}</Text></Pressable>)}</View>}
            <Pressable testID="nutrition-analyze-button" onPress={() => { Keyboard.dismiss(); void analyze(); }} disabled={!description.trim() || status === 'analyzing'} style={[styles.primary, { backgroundColor: !description.trim() || status === 'analyzing' ? colors.surfaceContainerHigh : colors.primary }]}><Text style={[styles.primaryText, { color: !description.trim() || status === 'analyzing' ? colors.onSurfaceVariant : colors.onPrimary }]}>{status === 'analyzing' ? 'Analizando...' : lastAnalysis ? 'Reanalizar calorias' : 'Analizar calorias'}</Text></Pressable>
            <View style={styles.wrap}>
              {!!lastAnalysis?.items.length && <Pressable testID="nutrition-toggle-detail-button" onPress={toggleDetail} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>{isDetailVisible ? 'Ocultar detalle' : 'Ver detalle'}</Text></Pressable>}
              <Pressable onPress={() => { setAdjustingIndex(null); setTab('search'); }} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>Ajustar resultado</Text></Pressable>
              <Pressable onPress={() => setTab('templates')} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>Usar plantilla</Text></Pressable>
            </View>
          </View>
        )}

        {tab === 'search' && (
          <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            {adjustingIndex !== null && lastAnalysis?.items[adjustingIndex] && <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>Ajustando: {lastAnalysis.items[adjustingIndex].canonicalName}</Text>}
            <View style={styles.row}><SearchIcon size={16} color={colors.onSurfaceVariant} /><TextInput value={searchQuery} onChangeText={setSearchQuery} placeholder="Buscar alimento..." placeholderTextColor={colors.onSurfaceVariant} style={[styles.input, { color: colors.onSurface }]} /></View>
            <View style={{ gap: 10 }}>
              {searching ? <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>Buscando...</Text> : searchQuery.trim().length < 2 ? <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>Escribe al menos 2 letras para buscar.</Text> : searchResults.length === 0 ? <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>No encontramos coincidencias utiles todavia.</Text> : searchResults.map(food => (
                <Pressable key={food.id} onPress={() => useSearchFood(food)} style={[styles.resultRow, { borderColor: colors.outlineVariant }]}>
                  <View style={[styles.iconBox, { backgroundColor: colors.primaryContainer }]}><UtensilsIcon size={18} color={colors.primary} /></View>
                  <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: colors.onSurface }]} numberOfLines={1}>{food.name}</Text><Text style={[styles.meta, { color: colors.onSurfaceVariant }]} numberOfLines={1}>{food.portionSize} · {food.calories} kcal · {food.protein}p · {food.carbs}c · {food.fat}g</Text></View>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        {tab === 'templates' && (
          <View style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
            <View style={{ gap: 10 }}>
              {templates.length === 0 ? <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>Todavia no hay plantillas migradas en esta instalacion.</Text> : templates.map(template => (
                <Pressable key={template.id} onPress={() => useTemplate(template)} style={[styles.resultRow, { borderColor: colors.outlineVariant }]}>
                  <View style={{ flex: 1 }}><Text style={[styles.resultTitle, { color: colors.onSurface }]}>{template.name}</Text><Text style={[styles.meta, { color: colors.onSurfaceVariant }]} numberOfLines={2}>{template.description}</Text><Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>{Math.round(template.calories)} kcal · {Math.round(template.protein)}p · {Math.round(template.carbs)}c · {Math.round(template.fats)}g</Text></View>
                </Pressable>
              ))}
            </View>
          </View>
        )}

        {saveNotice && <View style={[styles.notice, { borderColor: colors.primaryContainer }]}><Text style={[styles.meta, { color: colors.onSurface }]}>{saveNotice}</Text></View>}
        {errorMessage && <View style={[styles.notice, { borderColor: colors.errorContainer }]}><AlertTriangleIcon size={15} color={colors.error} /><Text style={[styles.meta, { color: colors.onSurface, flex: 1 }]}>{errorMessage}</Text></View>}

        {isDetailVisible && !!lastAnalysis?.items.length && (
          <View style={{ gap: 10 }}>
            {lastAnalysis.items.map((item, index) => (
              <View key={`${item.canonicalName}-${index}`} style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
                <Text style={[styles.resultTitle, { color: colors.onSurface }]}>{item.canonicalName}</Text>
                <Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>{Math.round(item.calories)} kcal · {Math.round(item.protein)}p · {Math.round(item.carbs)}c · {Math.round(item.fats)}g</Text>
                <View style={styles.wrap}>
                  <Pressable onPress={() => { setAdjustingIndex(index); setSearchQuery(item.canonicalName); setTab('search'); }} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>Ajustar</Text></Pressable>
                  <Pressable onPress={() => replaceAnalysis(lastAnalysis.items.filter((_, itemIndex) => itemIndex !== index).length ? mergeAnalysis(lastAnalysis.items.filter((_, itemIndex) => itemIndex !== index), lastAnalysis) : null)} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><TrashIcon size={13} color={colors.error} /><Text style={[styles.smallPillText, { color: colors.error }]}>Quitar</Text></Pressable>
                </View>
              </View>
            ))}
          </View>
        )}

        <View style={[styles.footer, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
          <Pressable onPress={() => navigation.goBack()} style={[styles.footerBtn, { backgroundColor: colors.surfaceContainerHigh }]}><Text style={[styles.footerText, { color: colors.onSurface }]}>Cancelar</Text></Pressable>
          <Pressable testID="nutrition-save-button" onPress={() => { if (canSave) { Keyboard.dismiss(); void saveCurrent({ mealType, logDate }); } }} disabled={!canSave} style={[styles.footerBtn, { flex: 1.5, backgroundColor: canSave ? colors.onSurface : colors.surfaceContainerHigh }]}><Text style={[styles.footerText, { color: canSave ? colors.surface : colors.onSurfaceVariant }]}>Guardar</Text></Pressable>
        </View>

        {savedLogs.length > 0 && <View style={{ gap: 10 }}>{savedLogs.slice(0, 5).map((log: SavedNutritionEntry) => <View key={log.id} style={[styles.card, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}><Text style={[styles.kicker, { color: colors.onSurfaceVariant }]}>{mealLabel(log.mealType)}</Text><Text style={[styles.resultTitle, { color: colors.onSurface }]}>{log.description}</Text><Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>{new Date(log.createdAt).toLocaleString('es-CL')}</Text><Text style={[styles.meta, { color: colors.onSurfaceVariant }]}>{Math.round(log.totals.calories)} kcal · {Math.round(log.totals.protein)}p · {Math.round(log.totals.carbs)}c · {Math.round(log.totals.fats)}g</Text><View style={styles.wrap}><Pressable onPress={() => setDescription(log.description)} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>Reusar</Text></Pressable><Pressable onPress={() => void duplicateLog(log.id)} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.onSurface }]}>Duplicar</Text></Pressable><Pressable onPress={() => Alert.alert('Eliminar registro', 'Esta comida saldra del historial reciente.', [{ text: 'Cancelar', style: 'cancel' }, { text: 'Eliminar', style: 'destructive', onPress: () => void deleteLog(log.id) }])} style={[styles.smallPill, { borderColor: colors.outlineVariant }]}><Text style={[styles.smallPillText, { color: colors.error }]}>Eliminar</Text></Pressable></View></View>)}</View>}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  header: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingHorizontal: 20, paddingTop: 6, paddingBottom: 8 },
  card: { borderRadius: 28, borderWidth: 1, padding: 18, gap: 12 },
  heroRow: { flexDirection: 'row', gap: 12 },
  row: { flexDirection: 'row', alignItems: 'center', gap: 10 },
  wrap: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  roundBtn: { height: 40, width: 40, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  roundBtnText: { fontSize: 18, fontWeight: '900' },
  ghostBtn: { minHeight: 36, borderRadius: 18, borderWidth: 1, paddingHorizontal: 14, alignItems: 'center', justifyContent: 'center' },
  ghostText: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 1.5 },
  kicker: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 2.2 },
  title: { fontSize: 30, fontWeight: '900', letterSpacing: -1 },
  bigNumber: { fontSize: 50, fontWeight: '900', letterSpacing: -1.5, lineHeight: 56 },
  heroText: { fontSize: 12, lineHeight: 18 },
  input: { flex: 1, fontSize: 13, fontWeight: '800', letterSpacing: 1.5, paddingVertical: 0 },
  textarea: { minHeight: 150, fontSize: 16, lineHeight: 24 },
  iconBox: { height: 44, width: 44, borderRadius: 18, alignItems: 'center', justifyContent: 'center' },
  subCard: { borderRadius: 20, paddingHorizontal: 14, paddingVertical: 12 },
  meta: { fontSize: 12, lineHeight: 18, fontWeight: '600' },
  pill: { borderRadius: 18, paddingHorizontal: 14, paddingVertical: 11 },
  pillText: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 1.8 },
  tabBar: { flexDirection: 'row', gap: 8, borderRadius: 24, borderWidth: 1, padding: 8 },
  tab: { flex: 1, borderRadius: 18, paddingVertical: 11, alignItems: 'center' },
  tabText: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 1.8 },
  primary: { minHeight: 48, borderRadius: 22, alignItems: 'center', justifyContent: 'center', paddingHorizontal: 16 },
  primaryText: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 1.8 },
  smallPill: { flexDirection: 'row', alignItems: 'center', gap: 6, borderRadius: 18, borderWidth: 1, paddingHorizontal: 12, paddingVertical: 10 },
  smallPillText: { fontSize: 10, fontWeight: '800' },
  resultRow: { flexDirection: 'row', alignItems: 'center', gap: 12, borderRadius: 22, borderWidth: 1, padding: 14 },
  resultTitle: { fontSize: 15, fontWeight: '800', lineHeight: 20 },
  notice: { flexDirection: 'row', alignItems: 'center', gap: 10, borderRadius: 20, borderWidth: 1, paddingHorizontal: 14, paddingVertical: 12 },
  footer: { flexDirection: 'row', gap: 10, borderRadius: 28, borderWidth: 1, padding: 12 },
  footerBtn: { flex: 1, minHeight: 50, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  footerText: { fontSize: 10, fontWeight: '900', textTransform: 'uppercase', letterSpacing: 1.8 },
});
