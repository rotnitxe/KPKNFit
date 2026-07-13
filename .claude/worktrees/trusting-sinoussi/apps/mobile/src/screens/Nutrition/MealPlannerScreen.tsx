import React, { useEffect, useMemo, useState } from 'react';
import {
  Alert,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '../../components/ScreenShell';
import {
  ClipboardListIcon,
  PlusCircleIcon,
  SearchIcon,
  TrashIcon,
  UtensilsIcon,
} from '../../components/icons';
import { NutritionStackParamList } from '../../navigation/types';
import { useMealPlannerStore } from '../../stores/mealPlannerStore';
import { useMealTemplateStore, MealTemplateSummary } from '../../stores/mealTemplateStore';
import { usePantryStore } from '../../stores/pantryStore';
import type { PantryItem } from '../../types/pantry';
import { useColors } from '../../theme';

const PWA_TEXT_PRIMARY = '#1D1B20';
const PWA_TEXT_SECONDARY = '#49454F';
const PWA_BORDER = 'rgba(0,0,0,0.06)';
const PWA_CARD = 'rgba(255,255,255,0.72)';
const PWA_CARD_STRONG = 'rgba(255,255,255,0.84)';

type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'MealPlanner'>;

const EMPTY_ITEM = {
  name: '',
  calories: '0',
  protein: '0',
  carbs: '0',
  fats: '0',
  currentQuantity: '100',
  unit: 'g',
};

function parseNumber(value: string) {
  const parsed = Number.parseFloat(value.replace(',', '.'));
  return Number.isFinite(parsed) ? parsed : 0;
}

interface PantryMacroFieldProps {
  label: string;
  value: string;
  onChange: (value: string) => void;
}

const PantryMacroField: React.FC<PantryMacroFieldProps> = ({ label, value, onChange }) => (
  <View style={styles.macroField}>
    <Text style={styles.macroFieldLabel}>{label}</Text>
    <TextInput
      value={value}
      onChangeText={onChange}
      style={styles.macroFieldInput}
      keyboardType="decimal-pad"
      placeholder="0"
      placeholderTextColor="rgba(73,69,79,0.45)"
    />
  </View>
);

interface PantryRowCardProps {
  item: PantryItem;
  onUpdate: (id: string, patch: Partial<Omit<PantryItem, 'id'>>) => void;
  onRemove: (id: string) => void;
}

const PantryRowCard: React.FC<PantryRowCardProps> = ({ item, onUpdate, onRemove }) => {
  return (
    <View style={styles.pantryRowCard}>
      <View style={styles.pantryRowHeader}>
        <View style={styles.pantryRowTitleBlock}>
          <Text style={styles.pantryRowTitle}>{item.name}</Text>
          <Text style={styles.pantryRowSubtitle}>Despensa operativa · {item.currentQuantity}{item.unit}</Text>
        </View>
        <Pressable onPress={() => onRemove(item.id)} style={styles.deleteButton}>
          <TrashIcon size={15} color="#8C1D18" />
        </Pressable>
      </View>

      <View style={styles.macroGrid}>
        <PantryMacroField
          label="Cant."
          value={String(item.currentQuantity)}
          onChange={(value) => onUpdate(item.id, { currentQuantity: parseNumber(value) })}
        />
        <PantryMacroField
          label="Cal/100"
          value={String(item.calories)}
          onChange={(value) => onUpdate(item.id, { calories: parseNumber(value) })}
        />
        <PantryMacroField
          label="Prot/100"
          value={String(item.protein)}
          onChange={(value) => onUpdate(item.id, { protein: parseNumber(value) })}
        />
        <PantryMacroField
          label="Carb/100"
          value={String(item.carbs)}
          onChange={(value) => onUpdate(item.id, { carbs: parseNumber(value) })}
        />
        <PantryMacroField
          label="Gras/100"
          value={String(item.fats)}
          onChange={(value) => onUpdate(item.id, { fats: parseNumber(value) })}
        />
      </View>
    </View>
  );
};

export const MealPlannerScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavigationProp>();
  const [draft, setDraft] = useState(EMPTY_ITEM);

  const {
    status: pantryStatus,
    items,
    notice: pantryNotice,
    hydrateFromStorage: hydratePantry,
    addCustomItem,
    updateItem,
    removeItem,
    clearNotice: clearPantryNotice,
  } = usePantryStore();

  const {
    status: templatesStatus,
    templates,
    hydrateFromMigration,
  } = useMealTemplateStore();

  const {
    status: plannerStatus,
    summary,
    suggestions,
    hydrateFromStorage: hydratePlanner,
    generateSuggestionsForDay,
  } = useMealPlannerStore();

  useEffect(() => {
    if (pantryStatus === 'idle') void hydratePantry();
    if (templatesStatus === 'idle') void hydrateFromMigration();
    if (plannerStatus === 'idle') void hydratePlanner();
  }, [pantryStatus, templatesStatus, plannerStatus, hydratePantry, hydrateFromMigration, hydratePlanner]);

  useEffect(() => {
    if (!pantryNotice) return undefined;
    const timer = setTimeout(() => clearPantryNotice(), 2800);
    return () => clearTimeout(timer);
  }, [clearPantryNotice, pantryNotice]);

  const pantryTotals = useMemo(() => {
    return items.reduce(
      (acc, item) => ({
        calories: acc.calories + item.calories,
        protein: acc.protein + item.protein,
        carbs: acc.carbs + item.carbs,
        fats: acc.fats + item.fats,
      }),
      { calories: 0, protein: 0, carbs: 0, fats: 0 },
    );
  }, [items]);

  const handleAddCustomItem = () => {
    const name = draft.name.trim();
    const calories = parseNumber(draft.calories);
    const quantity = parseNumber(draft.currentQuantity);

    if (!name || calories <= 0 || quantity <= 0) {
      Alert.alert(
        'Faltan datos',
        'El nombre, las calorías y la cantidad inicial son obligatorios para la despensa.',
      );
      return;
    }

    ReactNativeHapticFeedback.trigger('notificationSuccess');
    addCustomItem({
      name,
      calories,
      protein: parseNumber(draft.protein),
      carbs: parseNumber(draft.carbs),
      fats: parseNumber(draft.fats),
      currentQuantity: quantity,
      unit: draft.unit || 'g',
    });
    setDraft(EMPTY_ITEM);
  };

  const handleRemoveItem = (id: string) => {
    Alert.alert(
      'Eliminar alimento',
      '¿Seguro que quieres quitar este alimento de tu despensa?',
      [
        { text: 'Cancelar', style: 'cancel' },
        {
          text: 'Eliminar',
          style: 'destructive',
          onPress: () => removeItem(id),
        },
      ],
    );
  };

  const handleApplyTemplate = (template: MealTemplateSummary) => {
    ReactNativeHapticFeedback.trigger('impactMedium');
    addCustomItem({
      name: template.name,
      calories: template.calories,
      protein: template.protein,
      carbs: template.carbs,
      fats: template.fats,
      currentQuantity: 100,
      unit: 'g',
    });
  };

  const handleGenerateSuggestions = () => {
    const todayKey = new Date().toISOString().slice(0, 10);
    generateSuggestionsForDay(todayKey, summary?.dayCaloriesTarget || 2200);
  };

  return (
    <ScreenShell
      title="Planificador Inteligente"
      subtitle="Gestiona tu despensa"
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.heroCard}>
        <Text style={styles.heroEyebrow}>DESPENSA Y COMIDAS</Text>
        <Text style={styles.heroTitle}>La misma lógica de despensa de la PWA, ahora en móvil nativo</Text>
        <Text style={styles.heroDescription}>
          Guarda alimentos base, ajústalos rápido y usa plantillas como acelerador cuando quieras armar el día.
        </Text>
      </View>

      <View style={styles.summaryStrip}>
        <View style={styles.summaryStat}>
          <Text style={styles.summaryLabel}>ALIMENTOS</Text>
          <Text style={styles.summaryValue}>{items.length}</Text>
        </View>
        <View style={styles.summaryDivider} />
        <View style={styles.summaryStat}>
          <Text style={styles.summaryLabel}>CAL/100 ACUM</Text>
          <Text style={styles.summaryValue}>{Math.round(pantryTotals.calories)}</Text>
        </View>
        <View style={styles.summaryDivider} />
        <View style={styles.summaryStat}>
          <Text style={styles.summaryLabel}>PLAN HOY</Text>
          <Text style={styles.summaryValue}>{Math.round(summary?.dayCaloriesPlanned ?? 0)}</Text>
        </View>
      </View>

      {pantryNotice ? (
        <View style={[styles.noticeBanner, { backgroundColor: colors.primaryContainer }]}>
          <Text style={[styles.noticeText, { color: colors.onPrimaryContainer }]}>{pantryNotice}</Text>
        </View>
      ) : null}

      <View style={styles.sectionCard}>
        <View style={styles.sectionHeader}>
          <View>
            <Text style={styles.sectionEyebrow}>TU DESPENSA</Text>
            <Text style={styles.sectionTitle}>Inventario editable</Text>
          </View>
          <Pressable onPress={() => navigation.navigate('FoodDatabase')} style={styles.headerAction}>
            <SearchIcon size={14} color={PWA_TEXT_SECONDARY} />
            <Text style={styles.headerActionText}>Explorar base</Text>
          </Pressable>
        </View>

        {items.length === 0 ? (
          <View style={styles.emptyCard}>
            <ClipboardListIcon size={28} color="rgba(73,69,79,0.3)" />
            <Text style={styles.emptyTitle}>Tu despensa está vacía</Text>
            <Text style={styles.emptyText}>Añade alimentos manualmente o llévalos desde la base nutricional.</Text>
          </View>
        ) : (
          <View style={styles.pantryList}>
            {items.map(item => (
              <PantryRowCard
                key={item.id}
                item={item}
                onUpdate={updateItem}
                onRemove={handleRemoveItem}
              />
            ))}
          </View>
        )}
      </View>

      <View style={styles.sectionCard}>
        <View style={styles.sectionHeader}>
          <View>
            <Text style={styles.sectionEyebrow}>NUEVO ALIMENTO</Text>
            <Text style={styles.sectionTitle}>Añadir a despensa</Text>
          </View>
        </View>

        <TextInput
          value={draft.name}
          onChangeText={(value) => setDraft(current => ({ ...current, name: value }))}
          placeholder="Nombre del alimento"
          placeholderTextColor="rgba(73,69,79,0.45)"
          style={styles.nameInput}
        />

        <View style={styles.macroGrid}>
          <PantryMacroField
            label="Cant."
            value={draft.currentQuantity}
            onChange={(value) => setDraft(current => ({ ...current, currentQuantity: value }))}
          />
          <PantryMacroField
            label="Cal/100"
            value={draft.calories}
            onChange={(value) => setDraft(current => ({ ...current, calories: value }))}
          />
          <PantryMacroField
            label="Prot/100"
            value={draft.protein}
            onChange={(value) => setDraft(current => ({ ...current, protein: value }))}
          />
          <PantryMacroField
            label="Carb/100"
            value={draft.carbs}
            onChange={(value) => setDraft(current => ({ ...current, carbs: value }))}
          />
          <PantryMacroField
            label="Gras/100"
            value={draft.fats}
            onChange={(value) => setDraft(current => ({ ...current, fats: value }))}
          />
        </View>

        <Pressable onPress={handleAddCustomItem} style={styles.primaryCta}>
          <PlusCircleIcon size={18} color="#FFFFFF" />
          <Text style={styles.primaryCtaText}>Añadir a despensa</Text>
        </Pressable>
      </View>

      <View style={styles.sectionCard}>
        <View style={styles.sectionHeader}>
          <View>
            <Text style={styles.sectionEyebrow}>ATAJOS RÁPIDOS</Text>
            <Text style={styles.sectionTitle}>Plantillas y sugerencias</Text>
          </View>
          <Pressable onPress={handleGenerateSuggestions} style={styles.headerAction}>
            <UtensilsIcon size={14} color={PWA_TEXT_SECONDARY} />
            <Text style={styles.headerActionText}>Sugerir día</Text>
          </Pressable>
        </View>

        <View style={styles.quickTileGrid}>
          {templates.slice(0, 4).map(template => (
            <Pressable
              key={template.id}
              onPress={() => handleApplyTemplate(template)}
              style={styles.quickTile}
            >
              <Text style={styles.quickTileTitle} numberOfLines={1}>{template.name}</Text>
              <Text style={styles.quickTileMeta}>{Math.round(template.calories)} kcal</Text>
              <Text style={styles.quickTileBody} numberOfLines={2}>
                {template.quickDescription || template.description}
              </Text>
            </Pressable>
          ))}
        </View>

        {suggestions.length > 0 ? (
          <View style={styles.suggestionsStack}>
            {suggestions.slice(0, 3).map((suggestion, index) => (
              <View key={`${suggestion.slot}-${index}`} style={styles.suggestionRow}>
                <View style={styles.suggestionSlot}>
                  <Text style={styles.suggestionSlotText}>{suggestion.slot}</Text>
                </View>
                <View style={styles.suggestionCopy}>
                  <Text style={styles.suggestionTitle}>{suggestion.templateName}</Text>
                  <Text style={styles.suggestionReason}>{suggestion.reason}</Text>
                </View>
              </View>
            ))}
          </View>
        ) : (
          <Text style={styles.helperText}>
            Usa “Sugerir día” para llenar esta zona con una propuesta rápida basada en tus plantillas.
          </Text>
        )}
      </View>
    </ScreenShell>
  );
};

const styles = StyleSheet.create({
  shellContent: {
    paddingTop: 8,
    paddingHorizontal: 16,
    paddingBottom: 44,
  },
  heroCard: {
    backgroundColor: PWA_CARD,
    borderRadius: 28,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    padding: 20,
    gap: 8,
    marginBottom: 14,
  },
  heroEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.8,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  heroTitle: {
    fontSize: 28,
    lineHeight: 30,
    fontWeight: '900',
    color: PWA_TEXT_PRIMARY,
  },
  heroDescription: {
    fontSize: 14,
    lineHeight: 20,
    color: PWA_TEXT_SECONDARY,
  },
  summaryStrip: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 22,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD_STRONG,
    paddingHorizontal: 12,
    paddingVertical: 14,
    marginBottom: 12,
  },
  summaryStat: {
    flex: 1,
    alignItems: 'center',
    gap: 4,
  },
  summaryLabel: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1.3,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  summaryValue: {
    fontSize: 20,
    fontWeight: '900',
    color: PWA_TEXT_PRIMARY,
  },
  summaryDivider: {
    width: 1,
    alignSelf: 'stretch',
    backgroundColor: PWA_BORDER,
  },
  noticeBanner: {
    borderRadius: 16,
    paddingHorizontal: 14,
    paddingVertical: 12,
    marginBottom: 12,
  },
  noticeText: {
    fontSize: 13,
    lineHeight: 18,
    fontWeight: '700',
  },
  sectionCard: {
    backgroundColor: PWA_CARD,
    borderRadius: 26,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    padding: 18,
    gap: 14,
    marginBottom: 12,
  },
  sectionHeader: {
    flexDirection: 'row',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  },
  sectionEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.6,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  sectionTitle: {
    fontSize: 22,
    lineHeight: 24,
    fontWeight: '900',
    color: PWA_TEXT_PRIMARY,
  },
  headerAction: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 999,
    backgroundColor: 'rgba(255,255,255,0.78)',
    borderWidth: 1,
    borderColor: PWA_BORDER,
  },
  headerActionText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  emptyCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD_STRONG,
    paddingHorizontal: 18,
    paddingVertical: 24,
    alignItems: 'center',
    gap: 8,
  },
  emptyTitle: {
    fontSize: 18,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  emptyText: {
    fontSize: 13,
    lineHeight: 18,
    color: PWA_TEXT_SECONDARY,
    textAlign: 'center',
  },
  pantryList: {
    gap: 10,
  },
  pantryRowCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD_STRONG,
    padding: 14,
    gap: 12,
  },
  pantryRowHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  pantryRowTitleBlock: {
    flex: 1,
    gap: 2,
  },
  pantryRowTitle: {
    fontSize: 17,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  pantryRowSubtitle: {
    fontSize: 12,
    color: PWA_TEXT_SECONDARY,
  },
  deleteButton: {
    width: 36,
    height: 36,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(179,38,30,0.10)',
  },
  macroGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  macroField: {
    width: '31%',
    minWidth: 96,
    gap: 4,
  },
  macroFieldLabel: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  macroFieldInput: {
    height: 42,
    borderRadius: 14,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: 'rgba(255,255,255,0.88)',
    paddingHorizontal: 12,
    fontSize: 14,
    fontWeight: '700',
    color: PWA_TEXT_PRIMARY,
  },
  nameInput: {
    height: 48,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: 'rgba(255,255,255,0.88)',
    paddingHorizontal: 14,
    fontSize: 15,
    fontWeight: '700',
    color: PWA_TEXT_PRIMARY,
  },
  primaryCta: {
    height: 50,
    borderRadius: 18,
    backgroundColor: '#1D1B20',
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
  },
  primaryCtaText: {
    fontSize: 13,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: '#FFFFFF',
  },
  quickTileGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  quickTile: {
    width: '48%',
    backgroundColor: PWA_CARD_STRONG,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    padding: 14,
    gap: 4,
  },
  quickTileTitle: {
    fontSize: 15,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  quickTileMeta: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  quickTileBody: {
    fontSize: 12,
    lineHeight: 17,
    color: PWA_TEXT_SECONDARY,
  },
  suggestionsStack: {
    gap: 8,
  },
  suggestionRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    padding: 12,
    borderRadius: 18,
    backgroundColor: PWA_CARD_STRONG,
    borderWidth: 1,
    borderColor: PWA_BORDER,
  },
  suggestionSlot: {
    borderRadius: 999,
    backgroundColor: 'rgba(103,80,164,0.12)',
    paddingHorizontal: 10,
    paddingVertical: 6,
  },
  suggestionSlotText: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: '#6750A4',
  },
  suggestionCopy: {
    flex: 1,
    gap: 2,
  },
  suggestionTitle: {
    fontSize: 14,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  suggestionReason: {
    fontSize: 12,
    color: PWA_TEXT_SECONDARY,
  },
  helperText: {
    fontSize: 13,
    lineHeight: 18,
    color: PWA_TEXT_SECONDARY,
  },
});
