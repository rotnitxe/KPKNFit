import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  KeyboardAvoidingView,
  Modal,
  Platform,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useColors } from '../../theme';
import { useMealTemplateStore, type MealTemplateSummary } from '../../stores/mealTemplateStore';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useSettingsStore } from '../../stores/settingsStore';
import type { FoodItem } from '../../types/food';
import type { NutritionMealType } from '../../types/nutrition';
import { searchFoodIndex, warmFoodIndex } from '../../services/foodIndexService';
import { buildNutritionAnalysisFromFood, buildNutritionAnalysisFromTemplate } from '../../services/nutritionEntryBuilders';
import { FoodSearchBar } from './FoodSearchBar';
import PortionSelector from './PortionSelector';
import { TemplatePickerModal } from './TemplatePickerModal';
import { XIcon, SearchIcon, UtensilsIcon, ChevronDownIcon, PlusIcon } from '../icons';

const MEAL_TYPES: Array<{ id: NutritionMealType; label: string }> = [
  { id: 'breakfast', label: 'Desayuno' },
  { id: 'lunch', label: 'Almuerzo' },
  { id: 'dinner', label: 'Cena' },
  { id: 'snack', label: 'Snack' },
];

type DrawerMode = 'search' | 'text' | 'favorites' | 'templates';

interface RegisterFoodDrawerProps {
  visible: boolean;
  onClose: () => void;
  selectedDate: string;
  mealType?: string;
}

function formatMealLabel(mealType: NutritionMealType) {
  return mealType === 'breakfast'
    ? 'Desayuno'
    : mealType === 'lunch'
      ? 'Almuerzo'
      : mealType === 'dinner'
        ? 'Cena'
        : 'Snack';
}

export const RegisterFoodDrawer: React.FC<RegisterFoodDrawerProps> = ({
  visible,
  onClose,
  selectedDate,
  mealType: initialMealType,
}) => {
  const colors = useColors();
  const { templates } = useMealTemplateStore();
  const settings = useSettingsStore(state => state.summary ?? state.getSettings());

  const [mode, setMode] = useState<DrawerMode>('search');
  const [searchQuery, setSearchQuery] = useState('');
  const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [selectedFood, setSelectedFood] = useState<FoodItem | null>(null);
  const [selectedTemplate, setSelectedTemplate] = useState<MealTemplateSummary | null>(null);
  const [mealType, setMealType] = useState<NutritionMealType>((initialMealType as NutritionMealType) || 'breakfast');
  const [portionQuantity, setPortionQuantity] = useState(1);
  const [portionUnit, setPortionUnit] = useState('unit');
  const [description, setDescription] = useState('');
  const [templatePickerVisible, setTemplatePickerVisible] = useState(false);
  const [saving, setSaving] = useState(false);

  const { replaceAnalysis, saveCurrent, setDescription: setStoreDescription } = useMobileNutritionStore();

  useEffect(() => {
    void warmFoodIndex();
  }, []);

  useEffect(() => {
    if (!visible) return;
    setMode('search');
    setSearchQuery('');
    setSearchResults([]);
    setSearchLoading(false);
    setSelectedFood(null);
    setSelectedTemplate(null);
    setMealType((initialMealType as NutritionMealType) || 'breakfast');
    setPortionQuantity(1);
    setPortionUnit('unit');
    setDescription('');
    setTemplatePickerVisible(false);
    setSaving(false);
  }, [initialMealType, visible]);

  useEffect(() => {
    if (mode !== 'search') return;
    let cancelled = false;

    if (searchQuery.trim().length < 2) {
      setSearchResults([]);
      setSearchLoading(false);
      return undefined;
    }

    setSearchLoading(true);
    const timer = setTimeout(() => {
      void searchFoodIndex(searchQuery, { limit: 10, scope: 'extended' })
        .then(result => {
          if (!cancelled) {
            setSearchResults(result.results);
            setSearchLoading(false);
          }
        })
        .catch(() => {
          if (!cancelled) {
            setSearchResults([]);
            setSearchLoading(false);
          }
        });
    }, 260);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [mode, searchQuery]);

  useEffect(() => {
    if (mode === 'templates' && templates.length > 0) {
      setTemplatePickerVisible(true);
    }
  }, [mode, templates.length]);

  const selectedFoodMacros = useMemo(() => {
    if (!selectedFood) return null;
    return {
      calories: Math.round(selectedFood.calories * portionQuantity),
      protein: Math.round(selectedFood.protein * portionQuantity),
      carbs: Math.round(selectedFood.carbs * portionQuantity),
      fats: Math.round((selectedFood.fats ?? selectedFood.fat ?? 0) * portionQuantity),
    };
  }, [portionQuantity, selectedFood]);

  const selectedTemplateSummary = useMemo(() => {
    if (!selectedTemplate) return null;
    return {
      calories: Math.round(selectedTemplate.calories),
      protein: Math.round(selectedTemplate.protein),
      carbs: Math.round(selectedTemplate.carbs),
      fats: Math.round(selectedTemplate.fats),
      foods: selectedTemplate.foods.length,
    };
  }, [selectedTemplate]);

  const handleSelectFood = (food: FoodItem) => {
    setSelectedTemplate(null);
    setSelectedFood(food);
    setDescription(food.name);
    setPortionQuantity(1);
    setPortionUnit(food.servingUnit || food.unit || 'unit');
    setMode('search');
  };

  const handleSelectTemplate = (templateId: string) => {
    const template = templates.find(item => item.id === templateId) ?? null;
    setTemplatePickerVisible(false);
    if (!template) {
      setMode('search');
      return;
    }
    setSelectedFood(null);
    setSelectedTemplate(template);
    setDescription(template.name);
    setMode('templates');
  };

  const cleanupAndClose = () => {
    setTemplatePickerVisible(false);
    setSelectedFood(null);
    setSelectedTemplate(null);
    setSearchQuery('');
    setSearchResults([]);
    setDescription('');
    setSaving(false);
    onClose();
  };

  const handleSave = async () => {
    if (saving) return;
    setSaving(true);

    try {
      const store = useMobileNutritionStore.getState();

      if (mode === 'text') {
        if (!description.trim()) {
          Alert.alert('Falta texto', 'Escribe una descripción antes de guardar.');
          setSaving(false);
          return;
        }

        store.setDescription(description.trim());
        await store.analyze();
        const analyzedState = useMobileNutritionStore.getState();
        if (!analyzedState.lastAnalysis || analyzedState.lastAnalysis.items.length === 0) {
          Alert.alert('Sin resultado', 'No pudimos interpretar esta comida. Prueba con más detalle o usa búsqueda manual.');
          setSaving(false);
          return;
        }

        await store.saveCurrent({ mealType, logDate: selectedDate });
        cleanupAndClose();
        return;
      }

      if (mode === 'templates') {
        if (!selectedTemplate) {
          Alert.alert('Elige una plantilla', 'Selecciona una plantilla antes de guardar.');
          setSaving(false);
          return;
        }

        const analysis = buildNutritionAnalysisFromTemplate(selectedTemplate);
        replaceAnalysis(analysis, selectedTemplate.name);
        setStoreDescription(selectedTemplate.name);
        await saveCurrent({ mealType, logDate: selectedDate });
        cleanupAndClose();
        return;
      }

      if (!selectedFood) {
        Alert.alert('Elige un alimento', 'Selecciona un alimento de la búsqueda antes de guardar.');
        setSaving(false);
        return;
      }

      const analysis = buildNutritionAnalysisFromFood(selectedFood, portionQuantity, description.trim() || selectedFood.name);
      replaceAnalysis(analysis, description.trim() || selectedFood.name);
      setStoreDescription(description.trim() || selectedFood.name);
      await saveCurrent({ mealType, logDate: selectedDate });
      cleanupAndClose();
    } catch (error) {
      Alert.alert(
        'No se pudo guardar',
        error instanceof Error ? error.message : 'Hubo un problema registrando esta comida.',
      );
      setSaving(false);
    }
  };

  const canSave =
    (mode === 'text' && description.trim().length > 0)
    || (mode === 'templates' && selectedTemplate != null)
    || (mode === 'search' && selectedFood != null);

  return (
    <Modal visible={visible} animationType="slide" transparent onRequestClose={cleanupAndClose}>
      <KeyboardAvoidingView
        behavior={Platform.OS === 'ios' ? 'padding' : 'height'}
        style={styles.overlay}
      >
        <View style={[styles.drawer, { backgroundColor: colors.surface }]}>
          <View style={styles.header}>
            <View>
              <Text style={[styles.kicker, { color: colors.onSurfaceVariant }]}>Registro rápido</Text>
              <Text style={[styles.title, { color: colors.onSurface }]}>Registrar alimento</Text>
            </View>
            <Pressable onPress={cleanupAndClose} style={[styles.closeButton, { backgroundColor: colors.surfaceContainerHigh }]}>
              <XIcon size={18} color={colors.onSurface} />
            </Pressable>
          </View>

          <ScrollView style={styles.content} contentContainerStyle={styles.contentInner} showsVerticalScrollIndicator={false}>
            <View style={styles.chipsRow}>
              {MEAL_TYPES.map(option => (
                <Pressable
                  key={option.id}
                  onPress={() => setMealType(option.id)}
                  style={[
                    styles.mealChip,
                    mealType === option.id && { backgroundColor: colors.primary },
                  ]}
                >
                  <Text style={{ color: mealType === option.id ? colors.onPrimary : colors.onSurface }}>
                    {option.label}
                  </Text>
                </Pressable>
              ))}
            </View>

            <View style={styles.modeRow}>
              {[
                { id: 'search', label: 'Buscar', icon: SearchIcon },
                { id: 'text', label: 'Texto', icon: ChevronDownIcon },
                { id: 'templates', label: 'Plantillas', icon: UtensilsIcon },
                { id: 'favorites', label: 'Favoritos', icon: PlusIcon },
              ].map(tab => {
                const Icon = tab.icon;
                return (
                  <Pressable
                    key={tab.id}
                    onPress={() => setMode(tab.id as DrawerMode)}
                    style={[
                      styles.modeButton,
                      mode === tab.id && { backgroundColor: colors.primaryContainer, borderColor: colors.primary },
                    ]}
                  >
                    <Icon size={14} color={mode === tab.id ? colors.primary : colors.onSurfaceVariant} />
                    <Text style={{ color: mode === tab.id ? colors.primary : colors.onSurfaceVariant, fontWeight: '700' }}>
                      {tab.label}
                    </Text>
                  </Pressable>
                );
              })}
            </View>

            {mode === 'search' ? (
              <View style={styles.sectionCard}>
                <FoodSearchBar
                  query={searchQuery}
                  onChangeQuery={setSearchQuery}
                  onClear={() => setSearchQuery('')}
                />

                {searchLoading ? (
                  <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>Buscando en el índice de alimentos...</Text>
                ) : searchResults.length > 0 ? (
                  <View style={styles.resultList}>
                    {searchResults.map(food => (
                      <Pressable
                        key={food.id}
                        onPress={() => handleSelectFood(food)}
                        style={[styles.resultRow, { borderColor: colors.outlineVariant }]}
                      >
                        <View style={styles.resultMain}>
                          <Text style={[styles.resultTitle, { color: colors.onSurface }]} numberOfLines={1}>{food.name}</Text>
                          <Text style={[styles.resultMeta, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
                            {food.brand || food.category || food.portionSize || 'Base nutricional'}
                          </Text>
                        </View>
                        <Text style={[styles.resultCalories, { color: colors.primary }]}>
                          {Math.round(food.calories)} kcal
                        </Text>
                      </Pressable>
                    ))}
                  </View>
                ) : searchQuery.trim().length > 1 ? (
                  <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>No encontramos coincidencias claras.</Text>
                ) : (
                  <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>Busca por nombre, marca o alias.</Text>
                )}

                {selectedFood && (
                  <View style={styles.selectionCard}>
                    <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>Seleccionado</Text>
                    <Text style={[styles.selectionTitle, { color: colors.onSurface }]}>{selectedFood.name}</Text>
                    <PortionSelector
                      food={selectedFood}
                      quantity={portionQuantity}
                      unit={portionUnit}
                      onQuantityChange={setPortionQuantity}
                      onUnitChange={setPortionUnit}
                      macroPreview={selectedFoodMacros ?? undefined}
                    />
                  </View>
                )}
              </View>
            ) : null}

            {mode === 'text' ? (
              <View style={styles.sectionCard}>
                <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>Descripción libre</Text>
                <TextInput
                  value={description}
                  onChangeText={setDescription}
                  placeholder="Ej: 2 completos italianos y un jugo"
                  placeholderTextColor={colors.onSurfaceVariant}
                  style={[styles.textInput, { borderColor: colors.outlineVariant, color: colors.onSurface }]}
                  multiline
                  textAlignVertical="top"
                />
                <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>
                  El modelo local analizará este texto y guardará el resultado con fallback offline si el runtime falla.
                </Text>
              </View>
            ) : null}

            {mode === 'templates' ? (
              <View style={styles.sectionCard}>
                <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>Plantillas ricas</Text>
                <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>
                  Tus plantillas migradas conservan alimentos, macros y descripción rápida.
                </Text>
                <Pressable
                  onPress={() => setTemplatePickerVisible(true)}
                  style={[styles.templateTrigger, { borderColor: colors.outlineVariant, backgroundColor: colors.surfaceContainerHigh }]}
                >
                  <Text style={[styles.templateTriggerTitle, { color: colors.onSurface }]}>Elegir plantilla</Text>
                  <ChevronDownIcon size={16} color={colors.onSurfaceVariant} />
                </Pressable>

                {selectedTemplateSummary ? (
                  <View style={styles.templateSummary}>
                    <Text style={[styles.selectionTitle, { color: colors.onSurface }]} numberOfLines={1}>
                      {selectedTemplate?.name}
                    </Text>
                    <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>
                      {selectedTemplateSummary.foods} alimentos · {selectedTemplateSummary.calories} kcal · P {selectedTemplateSummary.protein} / C {selectedTemplateSummary.carbs} / G {selectedTemplateSummary.fats}
                    </Text>
                  </View>
                ) : null}
              </View>
            ) : null}

            {mode === 'favorites' ? (
              <View style={styles.sectionCard}>
                <Text style={[styles.sectionLabel, { color: colors.onSurfaceVariant }]}>Favoritos</Text>
                <Text style={[styles.helper, { color: colors.onSurfaceVariant }]}>
                  Los favoritos operativos todavía se cargan desde el registro y la despensa. Usa búsqueda o plantillas mientras tanto.
                </Text>
              </View>
            ) : null}
          </ScrollView>

          <View style={styles.footer}>
            <Pressable
              onPress={handleSave}
              disabled={!canSave || saving}
              style={[
                styles.saveButton,
                { backgroundColor: !canSave || saving ? colors.surfaceContainerHigh : colors.primary },
              ]}
            >
              <Text style={{ color: !canSave || saving ? colors.onSurfaceVariant : colors.onPrimary, fontWeight: '800' }}>
                {saving ? 'Guardando...' : formatMealLabel(mealType)}
              </Text>
            </Pressable>
          </View>
        </View>
      </KeyboardAvoidingView>

      <TemplatePickerModal
        visible={templatePickerVisible}
        onClose={() => {
          setTemplatePickerVisible(false);
          if (!selectedTemplate) setMode('search');
        }}
        templates={templates}
        onSelectTemplate={handleSelectTemplate}
        title="Elegir plantilla"
      />
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  drawer: {
    minHeight: '82%',
    borderTopLeftRadius: 28,
    borderTopRightRadius: 28,
    paddingTop: 16,
    paddingHorizontal: 16,
    paddingBottom: 18,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
    marginBottom: 16,
  },
  kicker: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  title: {
    fontSize: 22,
    fontWeight: '900',
    letterSpacing: -0.4,
  },
  closeButton: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  content: {
    flex: 1,
  },
  contentInner: {
    paddingBottom: 24,
    gap: 14,
  },
  chipsRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  mealChip: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    backgroundColor: 'rgba(0,0,0,0.04)',
    borderWidth: 1,
    borderColor: 'transparent',
  },
  modeRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  modeButton: {
    flexGrow: 1,
    minWidth: '46%',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    paddingVertical: 10,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.06)',
    backgroundColor: 'rgba(255,255,255,0.6)',
  },
  sectionCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: 'rgba(0,0,0,0.06)',
    padding: 14,
    gap: 12,
    backgroundColor: 'rgba(255,255,255,0.72)',
  },
  sectionLabel: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
  },
  helper: {
    fontSize: 12,
    lineHeight: 18,
  },
  resultList: {
    gap: 10,
  },
  resultRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
    paddingVertical: 12,
    paddingHorizontal: 12,
    borderRadius: 16,
    borderWidth: 1,
    backgroundColor: 'rgba(255,255,255,0.88)',
  },
  resultMain: {
    flex: 1,
    gap: 2,
  },
  resultTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  resultMeta: {
    fontSize: 12,
  },
  resultCalories: {
    fontSize: 13,
    fontWeight: '900',
  },
  selectionCard: {
    marginTop: 6,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.06)',
    paddingTop: 12,
    gap: 10,
  },
  selectionTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  textInput: {
    minHeight: 130,
    borderRadius: 18,
    borderWidth: 1,
    paddingHorizontal: 14,
    paddingVertical: 12,
    fontSize: 15,
    lineHeight: 21,
    backgroundColor: 'rgba(255,255,255,0.88)',
  },
  templateTrigger: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 10,
    borderWidth: 1,
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  templateTriggerTitle: {
    fontSize: 14,
    fontWeight: '800',
  },
  templateSummary: {
    gap: 4,
  },
  footer: {
    paddingTop: 8,
  },
  saveButton: {
    height: 50,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
});

export default RegisterFoodDrawer;
