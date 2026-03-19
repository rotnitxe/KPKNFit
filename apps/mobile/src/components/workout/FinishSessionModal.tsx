import React, { useState, useMemo, useCallback } from 'react';
import {
  View,
  Text,
  Modal,
  StyleSheet,
  TextInput,
  TouchableOpacity,
  Alert,
  ScrollView,
} from 'react-native';
import { Button } from '../ui';
import { useColors } from '../../theme';
import { getLocalDateString, formatDateForDisplay } from '../../utils/dateUtils';
import { DISCOMFORT_DATABASE } from '../../data/discomfortList';

// Helper component for Point Selector (1-10 scale)
const PointSelector = ({
  value,
  onChange,
  label,
  points = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10],
}: {
  value: number;
  onChange: (value: number) => void;
  label: string;
  points?: number[];
}) => {
  const colors = useColors();
  return (
    <View style={styles.pointSelectorContainer}>
      {label ? <Text style={[styles.pointSelectorLabel, { color: colors.onSurface }]}>{label}</Text> : null}
      <View style={styles.pointSelectorRow}>
        {points.map(point => (
          <TouchableOpacity
            key={point}
            style={[
              styles.pointSelectorButton,
              point === value
                ? { backgroundColor: colors.primary, borderColor: colors.primary }
                : { backgroundColor: colors.surfaceVariant, borderColor: colors.outline },
            ]}
            onPress={() => onChange(point)}
          >
            <Text style={[styles.pointSelectorText, { color: point === value ? colors.onPrimary : colors.onSurfaceVariant }]}>
              {point}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

// Helper component for Tag Selector (chips)
const TagSelector = ({
  tags,
  selectedTags,
  onToggle,
  title,
}: {
  tags: string[];
  selectedTags: string[];
  onToggle: (tag: string) => void;
  title: string;
}) => {
  const colors = useColors();
  return (
    <View style={styles.tagGroupContainer}>
      <Text style={[styles.tagGroupTitle, { color: colors.onSurface }]}>{title}</Text>
      <View style={styles.tagGroupWrapper}>
        {tags.map(tag => (
          <TouchableOpacity
            key={tag}
            style={[
              styles.tagButton,
              selectedTags.includes(tag)
                ? { backgroundColor: colors.primaryContainer, borderColor: colors.primary }
                : { backgroundColor: colors.surfaceVariant, borderColor: colors.outline },
            ]}
            onPress={() => onToggle(tag)}
          >
            <Text style={[styles.tagText, { color: selectedTags.includes(tag) ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>
              {tag}
            </Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
};

// Helper component for Muscle Batteries Accordion
const MuscleBatteryAccordion = ({
  musclesWithEffectiveSets,
  muscleBatteries,
  setMuscleBatteries,
}: {
  musclesWithEffectiveSets: string[];
  muscleBatteries: Record<string, number>;
  setMuscleBatteries: React.Dispatch<React.SetStateAction<Record<string, number>>>;
}) => {
  const colors = useColors();
  const [isOpen, setIsOpen] = useState(false);

  const toggleAccordion = useCallback(() => {
    setIsOpen(prev => !prev);
  }, []);

  if (musclesWithEffectiveSets.length === 0) return null;

  return (
    <View style={styles.muscleAccordionContainer}>
      <TouchableOpacity
        style={[styles.muscleAccordionHeader, { backgroundColor: colors.surfaceVariant }]}
        onPress={toggleAccordion}
      >
        <View style={styles.muscleAccordionHeaderRow}>
          <Text style={[styles.muscleAccordionHeaderTitle, { color: colors.onSurface }]}>
            Puntaje por Músculo
          </Text>
          <Text style={[styles.muscleAccordionHeaderIcon, { color: colors.onSurfaceVariant }]}>
            {isOpen ? '▲' : '▼'}
          </Text>
        </View>
      </TouchableOpacity>

      {isOpen && (
        <View style={[styles.muscleAccordionContent, { borderColor: colors.outlineVariant }]}>
          {musclesWithEffectiveSets.map(muscle => (
            <View key={muscle} style={[styles.muscleBatteryRow, { borderBottomColor: colors.outlineVariant }]}>
              <Text style={[styles.muscleBatteryLabel, { color: colors.onSurface }]}>{muscle}</Text>
              <PointSelector
                value={muscleBatteries[muscle] ?? 5}
                onChange={(value: number) =>
                  setMuscleBatteries(prev => ({ ...prev, [muscle]: value }))
                }
                label=""
              />
            </View>
          ))}
        </View>
      )}
    </View>
  );
};

// Helper component for Discomfort Search
const DiscomfortSearcher = ({
  selectedDiscomforts,
  setSelectedDiscomforts,
  showSearch,
  setShowSearch,
  searchQuery,
  setSearchQuery,
}: {
  selectedDiscomforts: string[];
  setSelectedDiscomforts: React.Dispatch<React.SetStateAction<string[]>>;
  showSearch: boolean;
  setShowSearch: React.Dispatch<React.SetStateAction<boolean>>;
  searchQuery: string;
  setSearchQuery: React.Dispatch<React.SetStateAction<string>>;
}) => {
  const colors = useColors();
  const filteredDiscomforts = useMemo(() => {
    const query = searchQuery.toLowerCase().trim();
    if (!query) return DISCOMFORT_DATABASE;
    return DISCOMFORT_DATABASE.filter(
      d =>
        d.name.toLowerCase().includes(query) ||
        d.description.toLowerCase().includes(query),
    );
  }, [searchQuery]);

  const toggleDiscomfort = useCallback((name: string) => {
    setSelectedDiscomforts(prev =>
      prev.includes(name) ? prev.filter(x => x !== name) : [...prev, name],
    );
  }, [setSelectedDiscomforts]);

  return (
    <View style={styles.discomfortSearchContainer}>
      {showSearch ? (
        <View style={[styles.discomfortSearchContent, { backgroundColor: colors.surface }]}>
          <View style={[styles.discomfortSearchHeader, { borderBottomColor: colors.outlineVariant }]}>
            <TextInput
              placeholder="Buscar síntoma..."
              placeholderTextColor={`${colors.onSurfaceVariant}88`}
              value={searchQuery}
              onChangeText={text => setSearchQuery(text)}
              style={[styles.discomfortSearchInput, { color: colors.onSurface }]}
            />
            <TouchableOpacity
              style={styles.discomfortSearchClose}
              onPress={() => setShowSearch(false)}
            >
              <Text style={[styles.discomfortSearchCloseText, { color: colors.onSurface }]}>×</Text>
            </TouchableOpacity>
          </View>

          <View style={styles.discomfortSearchList}>
            {filteredDiscomforts.map(discomfort => (
              <TouchableOpacity
                key={discomfort.id}
                style={[
                  styles.discomfortItem,
                  selectedDiscomforts.includes(discomfort.name)
                    ? { backgroundColor: colors.primaryContainer }
                    : { backgroundColor: colors.surfaceVariant },
                ]}
                onPress={() => toggleDiscomfort(discomfort.name)}
              >
                <View style={styles.discomfortItemInfo}>
                  <Text style={[styles.discomfortItemName, { color: colors.onSurface }]}>
                    {discomfort.name}
                  </Text>
                  <Text style={[styles.discomfortItemDescription, { color: colors.onSurfaceVariant }]}>
                    {discomfort.description}
                  </Text>
                </View>
                {selectedDiscomforts.includes(discomfort.name) && (
                  <Text style={[styles.discomfortItemCheck, { color: colors.primary }]}>✓</Text>
                )}
              </TouchableOpacity>
            ))}
          </View>
        </View>
      ) : null}

      <TouchableOpacity
        style={[styles.discomfortButton, { backgroundColor: colors.surfaceVariant }]}
        onPress={() => setShowSearch(true)}
      >
        <Text style={[styles.discomfortButtonText, { color: colors.onSurface }]}>
          {selectedDiscomforts.length > 0
            ? `${selectedDiscomforts.length} Molestias registradas`
            : '¿Alguna molestia física?'}
        </Text>
      </TouchableOpacity>
    </View>
  );
};

interface FinishSessionModalProps {
  visible: boolean;
  onClose: () => void;
  onContinue: (data: {
    notes?: string;
    discomforts?: string[];
    fatigueLevel?: number;
    mentalClarity?: number;
    durationInMinutes?: number;
    logDate?: string;
    focus?: number;
    pump?: number;
    environmentTags?: string[];
    sessionDifficulty?: number;
    planAdherenceTags?: string[];
    muscleBatteries?: Record<string, number>;
  }) => void;
  summary?: {
    completedSets: number;
    totalSets: number;
    durationMinutes: number;
    exerciseCount: number;
  };
  initialValues?: {
    notes?: string;
    discomforts?: string[];
    fatigueLevel?: number;
    mentalClarity?: number;
    durationInMinutes?: number;
    logDate?: string;
    focus?: number;
    pump?: number;
    environmentTags?: string[];
    sessionDifficulty?: number;
    planAdherenceTags?: string[];
    muscleBatteries?: Record<string, number>;
  };
  /** Muscles that logged effective sets — pass from parent for accurate per-muscle battery */
  musclesWithEffectiveSets?: string[];
}

export const FinishSessionModal: React.FC<FinishSessionModalProps> = React.memo(
  ({ visible, onClose, onContinue, summary, initialValues, musclesWithEffectiveSets = [] }) => {
    const colors = useColors();

    const [diaryNotes, setDiaryNotes] = useState<string>(initialValues?.notes ?? '');
    const [selectedDiscomforts, setSelectedDiscomforts] = useState<string[]>(
      initialValues?.discomforts ?? [],
    );
    // 1-10 direct scale (matching PWA)
    const [fatigueLevel, setFatigueLevel] = useState(initialValues?.fatigueLevel ?? 5);
    const [mentalClarity, setMentalClarity] = useState(initialValues?.mentalClarity ?? 5);
    const [durationInMinutes, setDurationInMinutes] = useState<string>(
      initialValues?.durationInMinutes !== undefined
        ? String(initialValues.durationInMinutes)
        : summary?.durationMinutes !== undefined
          ? String(summary.durationMinutes)
          : '',
    );
    const [logDate, setLogDate] = useState<string>(initialValues?.logDate ?? getLocalDateString());
    const [focus, setFocus] = useState<number>(initialValues?.focus ?? 5);
    const [pump, setPump] = useState<number>(initialValues?.pump ?? 5);
    const [environmentTags, setEnvironmentTags] = useState<string[]>(
      initialValues?.environmentTags ?? [],
    );
    const [sessionDifficulty, setSessionDifficulty] = useState<number>(
      initialValues?.sessionDifficulty ?? 5,
    );
    const [planAdherenceTags, setPlanAdherenceTags] = useState<string[]>(
      initialValues?.planAdherenceTags ?? [],
    );
    const [muscleBatteries, setMuscleBatteries] = useState<Record<string, number>>(
      initialValues?.muscleBatteries ?? {},
    );
    const [showDiscomfortSearch, setShowDiscomfortSearch] = useState<boolean>(false);
    const [searchQuery, setSearchQuery] = useState<string>('');

    // Critical fatigue warning: fatigue >= 8 AND mental clarity <= 3
    const showFatigueWarning = fatigueLevel >= 8 && mentalClarity <= 3;

    // Init muscle batteries for new muscles
    React.useEffect(() => {
      if (musclesWithEffectiveSets.length > 0) {
        setMuscleBatteries(prev => {
          const next = { ...prev };
          let changed = false;
          for (const muscle of musclesWithEffectiveSets) {
            if (!(muscle in next)) {
              next[muscle] = 5;
              changed = true;
            }
          }
          return changed ? next : prev;
        });
      }
    }, [musclesWithEffectiveSets]);

    const handleClose = () => onClose();

    const handleContinue = () => {
      const durationNum = durationInMinutes ? parseInt(durationInMinutes, 10) : undefined;
      if (durationNum !== undefined && durationNum <= 0) {
        Alert.alert('Error', 'Por favor, introduce una duración válida en minutos.');
        return;
      }
      onContinue({
        notes: diaryNotes,
        discomforts: selectedDiscomforts,
        fatigueLevel,
        mentalClarity,
        durationInMinutes: durationNum,
        logDate,
        focus,
        pump,
        environmentTags,
        sessionDifficulty,
        planAdherenceTags,
        muscleBatteries,
      });
    };

    const handleAcceptDeload = () => {
      Alert.alert(
        'Éxito',
        'Se ha programado una sesión de descanso activo para tu próximo entrenamiento.',
      );
      handleContinue();
    };

    if (!visible) return null;

    return (
      <Modal
        transparent
        visible={visible}
        animationType="slide"
        onRequestClose={handleClose}
      >
        <View style={styles.background}>
          <View style={[styles.modalContent, { backgroundColor: colors.surface }]}>
            {/* Handle */}
            <View style={[styles.handle, { backgroundColor: colors.onSurfaceVariant }]} />

            {/* Header */}
            <View style={styles.header}>
              <Text style={[styles.headerTitle, { color: colors.onSurface }]}>Finalizar sesión</Text>
              <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
                {logDate ? formatDateForDisplay(logDate) : ''}
              </Text>
            </View>

            {/* Scrollable content */}
            <ScrollView
              contentContainerStyle={styles.scrollViewContent}
              keyboardShouldPersistTaps="handled"
            >
              {/* Duration */}
              <View style={styles.inputGroup}>
                <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>
                  Duración (minutos)
                </Text>
                <TextInput
                  placeholder="Ej: 45"
                  placeholderTextColor={`${colors.onSurfaceVariant}88`}
                  value={durationInMinutes}
                  onChangeText={setDurationInMinutes}
                  keyboardType="numeric"
                  style={[
                    styles.input,
                    { color: colors.onSurface, borderColor: colors.outline, backgroundColor: colors.surfaceVariant },
                  ]}
                />
              </View>

              {/* Session Difficulty */}
              <PointSelector
                value={sessionDifficulty}
                onChange={setSessionDifficulty}
                label="Dificultad de sesión"
              />

              {/* Fatigue + Stress side-by-side */}
              <View style={styles.batteriesRow}>
                <View style={styles.batteryHalf}>
                  <PointSelector
                    value={fatigueLevel}
                    onChange={setFatigueLevel}
                    label="Fatiga General"
                  />
                </View>
                <View style={styles.batteryHalf}>
                  <PointSelector
                    value={mentalClarity}
                    onChange={setMentalClarity}
                    label="Estrés Percibido"
                  />
                </View>
              </View>

              {/* Muscle Batteries Accordion */}
              <MuscleBatteryAccordion
                musclesWithEffectiveSets={musclesWithEffectiveSets}
                muscleBatteries={muscleBatteries}
                setMuscleBatteries={setMuscleBatteries}
              />

              {/* Focus + Pump */}
              <View style={styles.batteriesRow}>
                <View style={styles.batteryHalf}>
                  <PointSelector value={focus} onChange={setFocus} label="Foco" />
                </View>
                <View style={styles.batteryHalf}>
                  <PointSelector value={pump} onChange={setPump} label="Pump" />
                </View>
              </View>

              {/* Environment Tags */}
              <TagSelector
                tags={[
                  'Gimnasio Lleno',
                  'Gimnasio Vacío',
                  'Entrenando con Amigos',
                  'Buena Música',
                  'Distraído',
                  'Con Prisa',
                ]}
                selectedTags={environmentTags}
                onToggle={tag =>
                  setEnvironmentTags(prev =>
                    prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag],
                  )
                }
                title="Entorno"
              />

              {/* Plan Adherence Tags */}
              <TagSelector
                tags={[
                  'Seguí el Plan',
                  'Más Pesado',
                  'Más Ligero',
                  'Más Reps',
                  'Menos Reps',
                  'Cambié Ejercicios',
                  'Añadí Ejercicios',
                ]}
                selectedTags={planAdherenceTags}
                onToggle={tag =>
                  setPlanAdherenceTags(prev =>
                    prev.includes(tag) ? prev.filter(t => t !== tag) : [...prev, tag],
                  )
                }
                title="Adherencia al Plan"
              />

              {/* Discomfort Searcher */}
              <DiscomfortSearcher
                selectedDiscomforts={selectedDiscomforts}
                setSelectedDiscomforts={setSelectedDiscomforts}
                showSearch={showDiscomfortSearch}
                setShowSearch={setShowDiscomfortSearch}
                searchQuery={searchQuery}
                setSearchQuery={setSearchQuery}
              />

              {/* Notes */}
              <View style={styles.inputGroup}>
                <Text style={[styles.inputLabel, { color: colors.onSurfaceVariant }]}>
                  Notas del Diario
                </Text>
                <TextInput
                  placeholder="Cuéntanos cómo te sentiste hoy..."
                  placeholderTextColor={`${colors.onSurfaceVariant}88`}
                  value={diaryNotes}
                  onChangeText={setDiaryNotes}
                  multiline
                  numberOfLines={4}
                  style={[
                    styles.notesInput,
                    { color: colors.onSurface, borderColor: colors.outline, backgroundColor: colors.surfaceVariant },
                  ]}
                />
              </View>

              {/* Fatigue Warning */}
              {showFatigueWarning && (
                <View style={[styles.fatigueWarningContainer, { backgroundColor: colors.errorContainer, borderColor: `${colors.error}44` }]}>
                  <Text style={[styles.fatigueWarningIcon]}>⚠️</Text>
                  <View style={styles.fatigueWarningTextContainer}>
                    <Text style={[styles.fatigueWarningTitle, { color: colors.errorContainer }]}>
                      Fatiga Crítica Detectada
                    </Text>
                    <Text style={[styles.fatigueWarningMessage, { color: colors.errorContainer }]}>
                      Tu sistema indica una fatiga de {fatigueLevel}/10 y un estrés de{' '}
                      {mentalClarity}/10. Considera programar un descanso activo.
                    </Text>
                  </View>
                  <View style={styles.fatigueWarningButtons}>
                    <Button variant="secondary" onPress={handleAcceptDeload}>
                      Programar Descanso
                    </Button>
                  </View>
                </View>
              )}
            </ScrollView>

            {/* Footer Buttons */}
            <View style={styles.footer}>
              <Button variant="secondary" onPress={handleClose}>
                Cancelar
              </Button>
              <Button variant="primary" onPress={handleContinue}>
                {showFatigueWarning ? 'Ignorar y Continuar' : 'Continuar al Feedback'}
              </Button>
            </View>
          </View>
        </View>
      </Modal>
    );
  },
);

const styles = StyleSheet.create({
  background: {
    flex: 1,
    backgroundColor: '#00000060',
    justifyContent: 'flex-end',
  },
  modalContent: {
    borderTopLeftRadius: 24,
    borderTopRightRadius: 24,
    padding: 24,
    maxHeight: '90%',
  },
  handle: {
    width: 40,
    height: 4,
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 12,
    opacity: 0.4,
  },
  header: {
    marginBottom: 24,
    alignItems: 'center',
  },
  headerTitle: {
    fontSize: 20,
    fontWeight: '700',
  },
  headerSubtitle: {
    fontSize: 14,
    marginTop: 4,
  },
  scrollViewContent: {
    paddingBottom: 20,
    gap: 20,
  },
  inputGroup: {
    gap: 8,
  },
  inputLabel: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    minHeight: 48,
  },
  notesInput: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 15,
    textAlignVertical: 'top',
    minHeight: 100,
  },
  pointSelectorContainer: {
    gap: 8,
  },
  pointSelectorLabel: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  pointSelectorRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    flexWrap: 'wrap',
    gap: 4,
  },
  pointSelectorButton: {
    width: 30,
    height: 30,
    borderRadius: 15,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
  },
  pointSelectorText: {
    fontSize: 12,
    fontWeight: '700',
  },
  batteriesRow: {
    flexDirection: 'row',
    gap: 16,
  },
  batteryHalf: {
    flex: 1,
  },
  muscleAccordionContainer: {},
  muscleAccordionHeader: {
    padding: 14,
    borderRadius: 12,
  },
  muscleAccordionHeaderRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  muscleAccordionHeaderTitle: {
    fontSize: 14,
    fontWeight: '700',
  },
  muscleAccordionHeaderIcon: {
    fontSize: 14,
  },
  muscleAccordionContent: {
    marginTop: 8,
    borderWidth: 1,
    borderRadius: 12,
    overflow: 'hidden',
  },
  muscleBatteryRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 10,
    paddingHorizontal: 12,
    borderBottomWidth: 1,
  },
  muscleBatteryLabel: {
    fontSize: 13,
    fontWeight: '600',
    flex: 1,
  },
  tagGroupContainer: {
    gap: 8,
  },
  tagGroupTitle: {
    fontSize: 13,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  tagGroupWrapper: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tagButton: {
    paddingHorizontal: 12,
    paddingVertical: 7,
    borderRadius: 20,
    borderWidth: 1,
  },
  tagText: {
    fontSize: 12,
    fontWeight: '600',
  },
  discomfortSearchContainer: {
    gap: 8,
  },
  discomfortButton: {
    paddingHorizontal: 16,
    paddingVertical: 14,
    borderRadius: 14,
  },
  discomfortButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  discomfortSearchContent: {
    borderRadius: 14,
    overflow: 'hidden',
  },
  discomfortSearchHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 14,
    borderBottomWidth: 1,
  },
  discomfortSearchInput: {
    flex: 1,
    fontSize: 15,
  },
  discomfortSearchClose: {
    padding: 8,
  },
  discomfortSearchCloseText: {
    fontSize: 20,
    fontWeight: '700',
  },
  discomfortSearchList: {
    padding: 12,
    gap: 6,
  },
  discomfortItem: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: 12,
    borderRadius: 10,
  },
  discomfortItemInfo: {
    flex: 1,
  },
  discomfortItemName: {
    fontSize: 14,
    fontWeight: '600',
  },
  discomfortItemDescription: {
    fontSize: 12,
    marginTop: 2,
  },
  discomfortItemCheck: {
    fontSize: 16,
    fontWeight: '700',
  },
  fatigueWarningContainer: {
    borderRadius: 16,
    borderWidth: 1,
    padding: 16,
    gap: 10,
  },
  fatigueWarningIcon: {
    fontSize: 24,
  },
  fatigueWarningTextContainer: {
    gap: 4,
  },
  fatigueWarningTitle: {
    fontSize: 15,
    fontWeight: '800',
  },
  fatigueWarningMessage: {
    fontSize: 13,
    lineHeight: 18,
  },
  fatigueWarningButtons: {
    marginTop: 6,
  },
  footer: {
    flexDirection: 'row',
    gap: 12,
    paddingTop: 16,
  },
});