import React, { useEffect, useMemo, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import ReactNativeHapticFeedback from '@/services/hapticsService';
import { ScreenShell } from '../../components/ScreenShell';
import {
  ActivityIcon,
  ArrowLeftIcon,
  ChevronRightIcon,
  PlusIcon,
  SearchIcon,
  UtensilsIcon,
} from '../../components/icons';
import { FOOD_DATABASE } from '../../data/foodDatabase';
import { FoodItem } from '../../types/food';
import { NutritionStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import { usePantryStore } from '../../stores/pantryStore';
import { searchFoodIndex } from '../../services/foodIndexService';

type NavigationProp = NativeStackNavigationProp<NutritionStackParamList, 'FoodDatabase'>;
type SortKey = 'name' | 'protein' | 'carbs' | 'fats';
type Micronutrient = 'Hierro' | 'Calcio' | 'Vitamina D' | 'Magnesio';
type FoodMacroBucket = 'Proteínas' | 'Carbohidratos' | 'Grasas' | 'Mixtos' | 'Otros';

const PWA_TEXT_PRIMARY = '#1D1B20';
const PWA_TEXT_SECONDARY = '#49454F';
const PWA_BORDER = 'rgba(0,0,0,0.06)';
const PWA_CARD = 'rgba(255,255,255,0.72)';
const PWA_CARD_STRONG = 'rgba(255,255,255,0.84)';

const MACRO_GRADIENTS: Record<FoodMacroBucket, { bg: string; tint: string }> = {
  Proteínas: { bg: 'rgba(59,130,246,0.16)', tint: '#174EA6' },
  Carbohidratos: { bg: 'rgba(249,115,22,0.16)', tint: '#B45309' },
  Grasas: { bg: 'rgba(245,158,11,0.18)', tint: '#92400E' },
  Mixtos: { bg: 'rgba(139,92,246,0.16)', tint: '#6D28D9' },
  Otros: { bg: 'rgba(107,114,128,0.16)', tint: '#374151' },
};

const MICRO_MATCHERS: Record<Micronutrient, string[]> = {
  Hierro: ['carne', 'huevo', 'atun', 'espinaca', 'lenteja', 'legumbre'],
  Calcio: ['yogurt', 'leche', 'queso', 'sardina', 'cottage'],
  'Vitamina D': ['salmon', 'atun', 'huevo', 'leche'],
  Magnesio: ['avena', 'nuez', 'palta', 'quinua', 'espinaca', 'mani'],
};

function normalizeText(value: string) {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}

function getMacroBucket(food: FoodItem): FoodMacroBucket {
  const totalCalories = food.calories;
  if (!totalCalories) return 'Otros';

  const proteinCalories = food.protein * 4;
  const carbCalories = food.carbs * 4;
  const fatCalories = food.fats * 9;
  const totalMacroCalories = Math.max(1, proteinCalories + carbCalories + fatCalories);

  if (proteinCalories / totalMacroCalories > 0.4) return 'Proteínas';
  if (carbCalories / totalMacroCalories > 0.4) return 'Carbohidratos';
  if (fatCalories / totalMacroCalories > 0.4) return 'Grasas';
  return 'Mixtos';
}

function searchFood(food: FoodItem, query: string) {
  const normalizedQuery = normalizeText(query);
  if (!normalizedQuery) return true;

  const haystack = [
    food.name,
    food.brand ?? '',
    food.category ?? '',
    ...(food.tags ?? []),
  ]
    .map(normalizeText)
    .join(' ');

  return haystack.includes(normalizedQuery);
}

function buildMicronutrientFoods(micro: Micronutrient) {
  const keywords = MICRO_MATCHERS[micro];

  return FOOD_DATABASE.filter((food) => {
    const haystack = [
      food.name,
      food.brand ?? '',
      food.category ?? '',
      ...(food.tags ?? []),
    ]
      .map(normalizeText)
      .join(' ');

    return keywords.some(keyword => haystack.includes(keyword));
  });
}

function formatMacroLabel(sortKey: SortKey) {
  if (sortKey === 'protein') return 'Mayor proteína';
  if (sortKey === 'carbs') return 'Más carbs';
  if (sortKey === 'fats') return 'Más grasa';
  return 'A-Z';
}

interface FoodRowCardProps {
  food: FoodItem;
  onOpen: (food: FoodItem) => void;
  onAdd: (food: FoodItem) => void;
}

const FoodRowCard: React.FC<FoodRowCardProps> = ({ food, onOpen, onAdd }) => {
  const bucket = getMacroBucket(food);
  const palette = MACRO_GRADIENTS[bucket];

  return (
    <Pressable onPress={() => onOpen(food)} style={[styles.foodCard, { backgroundColor: PWA_CARD_STRONG }]}>
      <View style={styles.foodCardLeading}>
        <View style={[styles.foodIconBox, { backgroundColor: palette.bg }]}>
          <UtensilsIcon size={16} color={palette.tint} />
        </View>
        <View style={styles.foodInfo}>
          <Text style={styles.foodName} numberOfLines={1}>
            {food.name}
          </Text>
          <View style={styles.foodMetaRow}>
            <Text style={styles.foodPortion}>{food.portionSize}</Text>
            <View style={[styles.foodBadge, { backgroundColor: palette.bg }]}>
              <Text style={[styles.foodBadgeText, { color: palette.tint }]}>{bucket}</Text>
            </View>
          </View>
          <Text style={styles.foodMacrosText}>
            {Math.round(food.calories)} kcal · P {food.protein} · C {food.carbs} · G {food.fats}
          </Text>
        </View>
      </View>

      <View style={styles.foodActions}>
        <Pressable
          onPress={(event) => {
            event.stopPropagation();
            onAdd(food);
          }}
          style={[styles.iconAction, { backgroundColor: palette.bg }]}
        >
          <PlusIcon size={14} color={palette.tint} />
        </Pressable>
        <ChevronRightIcon size={18} color="rgba(29,27,32,0.35)" />
      </View>
    </Pressable>
  );
};

export const FoodDatabaseScreen: React.FC = () => {
  const colors = useColors();
  const navigation = useNavigation<NavigationProp>();
  const [query, setQuery] = useState('');
  const [selectedCategory, setSelectedCategory] = useState<FoodMacroBucket | null>(null);
  const [selectedMicronutrient, setSelectedMicronutrient] = useState<Micronutrient | null>(null);
  const [sortKey, setSortKey] = useState<SortKey>('name');
  const [searchResults, setSearchResults] = useState<FoodItem[]>([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchMeta, setSearchMeta] = useState<{
    matchType: 'exact' | 'partial' | 'fuzzy';
    bestConfidence: 'high' | 'medium' | 'low';
    canAutoSelect: boolean;
    decisionReason?: string;
  } | null>(null);

  const {
    status: pantryStatus,
    notice,
    hydrateFromStorage,
    addFoodToPantry,
    clearNotice,
  } = usePantryStore();

  useEffect(() => {
    if (pantryStatus === 'idle') {
      void hydrateFromStorage();
    }
  }, [hydrateFromStorage, pantryStatus]);

  useEffect(() => {
    if (!notice) return undefined;
    const timer = setTimeout(() => clearNotice(), 2800);
    return () => clearTimeout(timer);
  }, [notice, clearNotice]);

  useEffect(() => {
    let cancelled = false;

    if (query.trim().length < 2) {
      setSearchResults([]);
      setSearchMeta(null);
      setSearchLoading(false);
      return undefined;
    }

    setSearchLoading(true);
    const timer = setTimeout(() => {
      void searchFoodIndex(query, { limit: 24, scope: 'extended' })
        .then(result => {
          if (cancelled) return;
          setSearchResults(result.results);
          setSearchMeta({
            matchType: result.matchType,
            bestConfidence: result.bestConfidence,
            canAutoSelect: result.canAutoSelect,
            decisionReason: result.decisionReason,
          });
          setSearchLoading(false);
        })
        .catch(() => {
          if (cancelled) return;
          setSearchResults([]);
          setSearchMeta(null);
          setSearchLoading(false);
        });
    }, 240);

    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [query]);

  const macroBuckets = useMemo(() => {
    const grouped: Record<FoodMacroBucket, FoodItem[]> = {
      Proteínas: [],
      Carbohidratos: [],
      Grasas: [],
      Mixtos: [],
      Otros: [],
    };

    FOOD_DATABASE.forEach((food) => {
      grouped[getMacroBucket(food)].push(food);
    });

    return grouped;
  }, []);

  const selectedCategoryFoods = useMemo(() => {
    if (!selectedCategory) return [];

    const foods = [...(macroBuckets[selectedCategory] ?? [])];
    foods.sort((a, b) => {
      if (sortKey === 'name') return a.name.localeCompare(b.name, 'es');
      if (sortKey === 'protein') return b.protein - a.protein;
      if (sortKey === 'carbs') return b.carbs - a.carbs;
      return b.fats - a.fats;
    });

    return foods;
  }, [macroBuckets, selectedCategory, sortKey]);

  const micronutrientCards = useMemo(() => {
    return (Object.keys(MICRO_MATCHERS) as Micronutrient[]).map((micro) => ({
      name: micro,
      foods: buildMicronutrientFoods(micro),
    }));
  }, []);

  const selectedMicronutrientFoods = useMemo(() => {
    if (!selectedMicronutrient) return [];
    return buildMicronutrientFoods(selectedMicronutrient);
  }, [selectedMicronutrient]);

  const handleOpenFood = (food: FoodItem) => {
    ReactNativeHapticFeedback.trigger('impactLight');
    navigation.navigate('FoodDetail', { foodId: food.id });
  };

  const handleAddToPantry = (food: FoodItem) => {
    ReactNativeHapticFeedback.trigger('notificationSuccess');
    addFoodToPantry(food);
  };

  const renderFoodList = (foods: FoodItem[]) => (
    <View style={styles.foodList}>
      {foods.map(food => (
        <FoodRowCard
          key={food.id}
          food={food}
          onOpen={handleOpenFood}
          onAdd={handleAddToPantry}
        />
      ))}
    </View>
  );

  return (
    <ScreenShell
      title="Alimentos"
      subtitle="Base de Datos Nutricional"
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.heroCard}>
        <Text style={styles.heroEyebrow}>MACRONUTRIENTES Y MICRONUTRIENTES</Text>
        <Text style={styles.heroTitle}>Biblioteca 1:1 de alimentos al estilo KPKN</Text>
        <Text style={styles.heroDescription}>
          Explora por macros, revisa fuentes clave y añade alimentos a tu despensa sin salir del móvil.
        </Text>
      </View>

      <View style={styles.searchCard}>
        <SearchIcon size={16} color={PWA_TEXT_SECONDARY} />
        <TextInput
          value={query}
          onChangeText={setQuery}
          placeholder="Buscar alimento, marca o alias"
          placeholderTextColor="rgba(73,69,79,0.55)"
          style={styles.searchInput}
          autoCapitalize="none"
          autoCorrect={false}
        />
      </View>

      {notice ? (
        <View style={[styles.noticeBanner, { backgroundColor: colors.primaryContainer }]}>
          <Text style={[styles.noticeText, { color: colors.onPrimaryContainer }]} numberOfLines={2}>
            {notice}
          </Text>
        </View>
      ) : null}

      {query.trim().length > 0 ? (
        <View style={styles.stack}>
          <View style={styles.sectionHeading}>
            <Text style={styles.sectionEyebrow}>RESULTADOS DE BÚSQUEDA</Text>
            <Text style={styles.sectionTitle}>{searchResults.length} alimentos encontrados</Text>
          </View>
          {searchMeta ? (
            <View style={styles.searchMetaCard}>
              <Text style={styles.searchMetaTitle}>
                {searchMeta.matchType === 'exact'
                  ? 'Coincidencia exacta'
                  : searchMeta.matchType === 'partial'
                    ? 'Coincidencia parcial'
                    : 'Búsqueda flexible'}
              </Text>
              <Text style={styles.searchMetaBody}>
                {searchMeta.bestConfidence === 'high'
                  ? 'Alta confianza'
                  : searchMeta.bestConfidence === 'medium'
                    ? 'Confianza media'
                    : 'Confianza baja'}
                {searchMeta.canAutoSelect ? ' · selección automática disponible' : ''}
              </Text>
              {searchMeta.decisionReason ? (
                <Text style={styles.searchMetaBody}>{searchMeta.decisionReason}</Text>
              ) : null}
            </View>
          ) : null}
          {query.trim().length < 2 ? (
            <View style={styles.emptyCard}>
              <UtensilsIcon size={30} color="rgba(73,69,79,0.3)" />
              <Text style={styles.emptyTitle}>Escribe al menos 2 letras</Text>
              <Text style={styles.emptyText}>Busca por nombre, marca o alias para activar el índice compartido.</Text>
            </View>
          ) : searchLoading ? (
            <View style={styles.emptyCard}>
              <UtensilsIcon size={30} color="rgba(73,69,79,0.3)" />
              <Text style={styles.emptyTitle}>Buscando en el índice</Text>
              <Text style={styles.emptyText}>Estamos comparando alias, marcas y categorías para darte la mejor coincidencia.</Text>
            </View>
          ) : searchResults.length === 0 ? (
            <View style={styles.emptyCard}>
              <UtensilsIcon size={30} color="rgba(73,69,79,0.3)" />
              <Text style={styles.emptyTitle}>No encontramos coincidencias</Text>
              <Text style={styles.emptyText}>Prueba otra marca, alias o categoría nutricional.</Text>
            </View>
          ) : renderFoodList(searchResults)}
        </View>
      ) : selectedMicronutrient ? (
        <View style={styles.stack}>
          <Pressable onPress={() => setSelectedMicronutrient(null)} style={styles.backRow}>
            <ArrowLeftIcon size={18} color={PWA_TEXT_SECONDARY} />
            <Text style={styles.backText}>Volver a micronutrientes</Text>
          </Pressable>

          <View style={styles.sectionCard}>
            <Text style={styles.sectionEyebrow}>MICRONUTRIENTE CLAVE</Text>
            <Text style={styles.sectionTitle}>Fuentes de {selectedMicronutrient}</Text>
            <Text style={styles.sectionBody}>
              Selección rápida para mantener el criterio visual y de descubrimiento de la PWA.
            </Text>
          </View>

          {selectedMicronutrientFoods.length === 0 ? (
            <View style={styles.emptyCard}>
              <Text style={styles.emptyTitle}>Sin fuentes etiquetadas</Text>
              <Text style={styles.emptyText}>Todavía no encontramos alimentos útiles para esta vista.</Text>
            </View>
          ) : renderFoodList(selectedMicronutrientFoods)}
        </View>
      ) : selectedCategory ? (
        <View style={styles.stack}>
          <Pressable onPress={() => setSelectedCategory(null)} style={styles.backRow}>
            <ArrowLeftIcon size={18} color={PWA_TEXT_SECONDARY} />
            <Text style={styles.backText}>Volver a categorías</Text>
          </Pressable>

          <View style={styles.sectionCard}>
            <Text style={styles.sectionEyebrow}>CATEGORÍA NUTRICIONAL</Text>
            <Text style={styles.sectionTitle}>{selectedCategory}</Text>
            <Text style={styles.sectionBody}>
              {selectedCategoryFoods.length} alimentos organizados según su aporte energético principal.
            </Text>
          </View>

          <View style={styles.pillRow}>
            {(['name', 'protein', 'carbs', 'fats'] as SortKey[]).map(key => (
              <Pressable
                key={key}
                onPress={() => setSortKey(key)}
                style={[
                  styles.sortPill,
                  sortKey === key && styles.sortPillActive,
                ]}
              >
                <Text style={[
                  styles.sortPillText,
                  sortKey === key && styles.sortPillTextActive,
                ]}>
                  {formatMacroLabel(key)}
                </Text>
              </Pressable>
            ))}
          </View>

          {renderFoodList(selectedCategoryFoods)}
        </View>
      ) : (
        <View style={styles.stack}>
          <View style={styles.sectionHeading}>
            <Text style={styles.sectionEyebrow}>MACRONUTRIENTES</Text>
            <Text style={styles.sectionTitle}>Explorar por perfil dominante</Text>
          </View>

          <View style={styles.tileGrid}>
            {(Object.entries(macroBuckets) as [FoodMacroBucket, FoodItem[]][])
              .filter(([, foods]) => foods.length > 0)
              .map(([bucket, foods]) => {
                const palette = MACRO_GRADIENTS[bucket];
                return (
                  <Pressable
                    key={bucket}
                    onPress={() => setSelectedCategory(bucket)}
                    style={[styles.categoryTile, { backgroundColor: palette.bg }]}
                  >
                    <Text style={[styles.categoryTitle, { color: palette.tint }]}>{bucket}</Text>
                    <Text style={styles.categoryCount}>{foods.length} alimentos</Text>
                    <ChevronRightIcon size={18} color={palette.tint} />
                  </Pressable>
                );
              })}
          </View>

          <View style={styles.sectionHeading}>
            <Text style={styles.sectionEyebrow}>MICRONUTRIENTES CLAVE</Text>
            <Text style={styles.sectionTitle}>Atajos rápidos como en la PWA</Text>
          </View>

          <View style={styles.microGrid}>
            {micronutrientCards.map((micro) => (
              <Pressable
                key={micro.name}
                onPress={() => setSelectedMicronutrient(micro.name)}
                style={styles.microTile}
              >
                <Text style={styles.microTitle}>{micro.name}</Text>
                <Text style={styles.microCount}>{micro.foods.length} fuentes</Text>
              </Pressable>
            ))}
          </View>
        </View>
      )}
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
  searchCard: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 18,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD_STRONG,
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
    marginBottom: 12,
  },
  searchInput: {
    flex: 1,
    paddingVertical: 0,
    fontSize: 15,
    color: PWA_TEXT_PRIMARY,
  },
  searchMetaCard: {
    borderRadius: 16,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD_STRONG,
    paddingHorizontal: 14,
    paddingVertical: 12,
    gap: 4,
  },
  searchMetaTitle: {
    fontSize: 11,
    fontWeight: '900',
    letterSpacing: 1.4,
    textTransform: 'uppercase',
    color: PWA_TEXT_PRIMARY,
  },
  searchMetaBody: {
    fontSize: 12,
    lineHeight: 16,
    color: PWA_TEXT_SECONDARY,
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
  stack: {
    gap: 12,
  },
  sectionHeading: {
    paddingTop: 6,
    gap: 2,
  },
  sectionCard: {
    backgroundColor: PWA_CARD,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    padding: 18,
    gap: 4,
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
  sectionBody: {
    fontSize: 13,
    lineHeight: 18,
    color: PWA_TEXT_SECONDARY,
  },
  tileGrid: {
    gap: 10,
  },
  categoryTile: {
    borderRadius: 22,
    paddingHorizontal: 18,
    paddingVertical: 16,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
  },
  categoryTitle: {
    fontSize: 18,
    fontWeight: '900',
    flex: 1,
  },
  categoryCount: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
    marginRight: 12,
  },
  microGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 10,
  },
  microTile: {
    width: '48%',
    backgroundColor: PWA_CARD_STRONG,
    borderRadius: 20,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    paddingHorizontal: 14,
    paddingVertical: 16,
    gap: 4,
  },
  microTitle: {
    fontSize: 16,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  microCount: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  backRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingVertical: 4,
  },
  backText: {
    fontSize: 13,
    fontWeight: '700',
    color: PWA_TEXT_SECONDARY,
  },
  pillRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  sortPill: {
    paddingHorizontal: 14,
    paddingVertical: 9,
    borderRadius: 999,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: 'rgba(255,255,255,0.55)',
  },
  sortPillActive: {
    backgroundColor: '#1D1B20',
    borderColor: '#1D1B20',
  },
  sortPillText: {
    fontSize: 11,
    fontWeight: '800',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: PWA_TEXT_SECONDARY,
  },
  sortPillTextActive: {
    color: '#FFFFFF',
  },
  foodList: {
    gap: 10,
  },
  foodCard: {
    borderRadius: 22,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    paddingHorizontal: 14,
    paddingVertical: 14,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  foodCardLeading: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  foodIconBox: {
    width: 44,
    height: 44,
    borderRadius: 14,
    alignItems: 'center',
    justifyContent: 'center',
  },
  foodInfo: {
    flex: 1,
    gap: 4,
  },
  foodName: {
    fontSize: 15,
    fontWeight: '800',
    color: PWA_TEXT_PRIMARY,
  },
  foodMetaRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap',
  },
  foodPortion: {
    fontSize: 11,
    color: PWA_TEXT_SECONDARY,
  },
  foodBadge: {
    borderRadius: 999,
    paddingHorizontal: 9,
    paddingVertical: 4,
  },
  foodBadgeText: {
    fontSize: 9,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
  },
  foodMacrosText: {
    fontSize: 12,
    color: PWA_TEXT_SECONDARY,
  },
  foodActions: {
    alignItems: 'center',
    gap: 8,
  },
  iconAction: {
    width: 34,
    height: 34,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyCard: {
    borderRadius: 24,
    borderWidth: 1,
    borderColor: PWA_BORDER,
    backgroundColor: PWA_CARD,
    paddingHorizontal: 20,
    paddingVertical: 28,
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
});
