import React, { useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, TextInput, Alert } from 'react-native';
import { useExerciseStore } from '../../stores/exerciseStore';
import type { Exercise, ExerciseMuscleInfo, BrandEquivalency } from '../../types/workout';

interface TagAccordionProps {
  exercise: Exercise;
  exerciseInfo: ExerciseMuscleInfo | undefined;
  selectedTag: string | null;
  onTagChange: (tag: string) => void;
}

export const TagAccordion: React.FC<TagAccordionProps> = ({
  exercise,
  exerciseInfo,
  selectedTag,
  onTagChange,
}) => {
  const [selectedTab, setSelectedTab] = useState<'tags' | 'setup'>('tags');
  const [newTag, setNewTag] = useState('');
  const [isAddingTag, setIsAddingTag] = useState(false);
  const [setupDetails, setSetupDetails] = useState<{
    seatPosition: string;
    pinPosition: string;
    equipmentNotes: string;
  }>({
    seatPosition: '',
    pinPosition: '',
    equipmentNotes: '',
  });

  const exerciseStore = useExerciseStore();

  // Initialize setupDetails from exerciseInfo if available
  React.useEffect(() => {
    if (exerciseInfo?.setupDetails) {
      setSetupDetails({
        seatPosition: exerciseInfo.setupDetails.seatPosition ?? '',
        pinPosition: exerciseInfo.setupDetails.pinPosition ?? '',
        equipmentNotes: exerciseInfo.setupDetails.equipmentNotes ?? '',
      });
    }
  }, [exerciseInfo]);

  // Get unique tags from brandEquivalencies
  const getTags = (): string[] => {
    const tags = exerciseInfo?.brandEquivalencies?.map(b => b.brand) ?? [];
    // Also consider the exercise's own tags if any? The spec says brandEquivalencies
    return [...new Set(tags)]; // Remove duplicates
  };

  const handleTagPress = (tag: string) => {
    onTagChange(tag);
  };

  const handleAddTag = () => {
    setIsAddingTag(true);
    Alert.prompt(
      'Nueva etiqueta de marca',
      'Ingrese el nombre de la marca o posición (ej: Technogym, Sentado):',
      [
        {
          text: 'Cancelar',
          style: 'cancel',
        },
        {
          text: 'Guardar',
          onPress: (text: string | undefined) => {
            if (text && text.trim()) {
              const newTagText = text.trim();
              // Update the exercise with the new brand equivalency
              const updatedInfo: ExerciseMuscleInfo = {
                ...(exerciseInfo ?? {
                  id: exercise.exerciseDbId ?? exercise.id,
                  name: exercise.name,
                  description: '',
                  involvedMuscles: [],
                  subMuscleGroup: '',
                  category: '',
                  type: 'Básico',
                  equipment: '',
                  force: '',
                  isCustom: false,
                  bodyPart: 'upper' as const, // Default to upper, will be overridden if exerciseInfo exists
                  chain: 'full' as const,
                  isFavorite: false,
                  variantOf: '',
                  sfr: { score: 0, justification: '' },
                  setupTime: 0,
                  averageRestSeconds: 0,
                  coreInvolvement: 'medium',
                  bracingRecommended: false,
                  strapsRecommended: false,
                  bodybuildingScore: 0,
                  functionalTransfer: '',
                  efc: 0,
                  ssc: 0,
                  cnc: 0,
                  ttc: 0,
                  axialLoadFactor: 0,
                  postureFactor: 0,
                  technicalDifficulty: 0,
                  injuryRisk: { level: 0, details: '' },
                  transferability: 0,
                  recommendedMobility: [],
                  resistanceProfile: { curve: '', peakTensionPoint: '', description: '' },
                  commonMistakes: [],
                  progressions: [],
                  regressions: [],
                  anatomicalConsiderations: [],
                  periodizationNotes: [],
                  primeStars: { score: 0, justification: '' },
                  communityOpinion: [],
                  aiCoachAnalysis: { summary: '', pros: [], cons: [] },
                  images: [],
                  videos: [],
                  userRating: 0,
                  setupDetails: { seatPosition: '', pinPosition: '', equipmentNotes: '' },
                  brandEquivalencies: [],
                  repDebtHistory: {},
                  damageProfile: 'normal',
                  calculated1RM: 0,
                  last1RMTestDate: '',
                  setupCues: [],
                  executionCues: [],
                  isHallOfFame: false,
                  sportsRelevance: [],
                  baseIFI: 0,
                }),
                brandEquivalencies: [
                  ...(exerciseInfo?.brandEquivalencies ?? []),
                  { brand: newTagText, pr: { weight: 0, reps: 0, e1rm: 0 } } as BrandEquivalency,
                ],
              };
              // Update the exercise in the store
              exerciseStore.addOrUpdateCustomExercise(updatedInfo);
              // After updating, we want to select this new tag
              onTagChange(newTagText);
            }
            setIsAddingTag(false);
            setNewTag('');
          },
        },
      ],
      'plain-text'
    );
  };

  const handleSaveSetup = () => {
    // Update the exerciseInfo with the new setupDetails
    const updatedInfo: ExerciseMuscleInfo = {
      ...(exerciseInfo ?? {
        id: exercise.exerciseDbId ?? exercise.id,
        name: exercise.name,
        description: '',
        involvedMuscles: [],
        subMuscleGroup: '',
        category: '',
        type: 'Básico',
        equipment: '',
        force: '',
        isCustom: false,
        bodyPart: 'upper' as const, // Default to upper, will be overridden if exerciseInfo exists
        chain: 'full' as const,
        isFavorite: false,
        variantOf: '',
        sfr: { score: 0, justification: '' },
        setupTime: 0,
        averageRestSeconds: 0,
        coreInvolvement: 'medium',
        bracingRecommended: false,
        strapsRecommended: false,
        bodybuildingScore: 0,
        functionalTransfer: '',
        efc: 0,
        ssc: 0,
        cnc: 0,
        ttc: 0,
        axialLoadFactor: 0,
        postureFactor: 0,
        technicalDifficulty: 0,
        injuryRisk: { level: 0, details: '' },
        transferability: 0,
        recommendedMobility: [],
        resistanceProfile: { curve: '', peakTensionPoint: '', description: '' },
        commonMistakes: [],
        progressions: [],
        regressions: [],
        anatomicalConsiderations: [],
        periodizationNotes: [],
        primeStars: { score: 0, justification: '' },
        communityOpinion: [],
        aiCoachAnalysis: { summary: '', pros: [], cons: [] },
        images: [],
        videos: [],
        userRating: 0,
        setupDetails: { seatPosition: '', pinPosition: '', equipmentNotes: '' },
        brandEquivalencies: [],
        repDebtHistory: {},
        damageProfile: 'normal',
        calculated1RM: 0,
        last1RMTestDate: '',
        setupCues: [],
        executionCues: [],
        isHallOfFame: false,
        sportsRelevance: [],
        baseIFI: 0,
      }),
      setupDetails: {
        seatPosition: setupDetails.seatPosition.trim(),
        pinPosition: setupDetails.pinPosition.trim(),
        equipmentNotes: setupDetails.equipmentNotes.trim(),
      },
    };
    // Update the exercise in the store
    exerciseStore.addOrUpdateCustomExercise(updatedInfo);
    Alert.alert('Setup guardado', 'La configuración del equipo ha sido guardada.');
  };

  const tags = getTags();

  return (
    <View style={styles.container}>
      <View style={styles.tabsContainer}>
        <TouchableOpacity
          style={[
            styles.tabButton,
            selectedTab === 'tags' && styles.activeTab,
          ]}
          onPress={() => setSelectedTab('tags')}
        >
          <Text style={styles.tabText}>Etiquetas</Text>
        </TouchableOpacity>
        <TouchableOpacity
          style={[
            styles.tabButton,
            selectedTab === 'setup' && styles.activeTab,
          ]}
          onPress={() => setSelectedTab('setup')}
        >
          <Text style={styles.tabText}>Configuración</Text>
        </TouchableOpacity>
      </View>

      {selectedTab === 'tags' ? (
        <View style={styles.tabContent}>
          <View style={styles.tagsContainer}>
            {tags.map((tag, index) => (
              <TouchableOpacity
                key={index}
                style={[
                  styles.tag,
                  selectedTag === tag && styles.selectedTag,
                ]}
                onPress={() => handleTagPress(tag)}
              >
                <Text style={styles.tagText}>{tag}</Text>
              </TouchableOpacity>
            ))}
            {!isAddingTag && (
              <TouchableOpacity style={styles.addButton} onPress={handleAddTag}>
                <Text style={styles.addButtonText}>+</Text>
              </TouchableOpacity>
            )}
          </View>
          {isAddingTag && (
            <View style={styles.inputContainer}>
              <TextInput
                style={styles.input}
                value={newTag}
                onChangeText={setNewTag}
                placeholder="Nueva etiqueta"
                autoFocus
                returnKeyType="done"
                onSubmitEditing={handleAddTag}
              />
              <View style={styles.buttonContainer}>
                <TouchableOpacity style={styles.cancelButton} onPress={() => setIsAddingTag(false)}>
                  <Text style={styles.cancelButtonText}>Cancelar</Text>
                </TouchableOpacity>
                <TouchableOpacity style={styles.saveButton} onPress={handleAddTag}>
                  <Text style={styles.saveButtonText}>Guardar</Text>
                </TouchableOpacity>
              </View>
            </View>
          )}
        </View>
      ) : (
        <View style={styles.tabContent}>
          <View style={styles.setupForm}>
            <Text style={styles.setupLabel}>Asiento:</Text>
            <TextInput
              style={styles.setupInput}
              value={setupDetails.seatPosition}
              onChangeText={(text) => setSetupDetails(prev => ({ ...prev, seatPosition: text }))}
              placeholder="Ej: Medio, Alto, Bajo"
            />
            <Text style={styles.setupLabel}>Pines:</Text>
            <TextInput
              style={styles.setupInput}
              value={setupDetails.pinPosition}
              onChangeText={(text) => setSetupDetails(prev => ({ ...prev, pinPosition: text }))}
              placeholder="Ej: 10, 20, 30"
            />
            <Text style={styles.setupLabel}>Notas del equipo:</Text>
            <TextInput
              style={styles.setupInput}
              value={setupDetails.equipmentNotes}
              onChangeText={(text) => setSetupDetails(prev => ({ ...prev, equipmentNotes: text }))}
              placeholder="Ej: Agarrotado, necesita mantenimiento"
              multiline
            />
            <TouchableOpacity style={styles.saveSetupButton} onPress={handleSaveSetup}>
              <Text style={styles.saveSetupText}>Guardar setup</Text>
            </TouchableOpacity>
          </View>
        </View>
      )}
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    marginVertical: 8,
  },
  tabsContainer: {
    flexDirection: 'row',
    borderBottomWidth: 1,
    borderBottomColor: '#EAE1F2',
  },
  tabButton: {
    paddingVertical: 12,
    paddingHorizontal: 20,
  },
  activeTab: {
    borderBottomWidth: 2,
    borderBottomColor: '#5D3FD3',
  },
  tabText: {
    fontSize: 16,
    fontWeight: '600',
    color: '#5D3FD3',
  },
  tabContent: {
    paddingVertical: 16,
  },
  tagsContainer: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  tag: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#F3EDF7',
  },
  selectedTag: {
    backgroundColor: '#D9F0DB',
  },
  tagText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#5D3FD3',
  },
  addButton: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: '#EEE7FF',
    alignItems: 'center',
    justifyContent: 'center',
  },
  addButtonText: {
    fontSize: 18,
    fontWeight: '900',
    color: '#673AB7',
  },
  inputContainer: {
    marginTop: 8,
  },
  input: {
    height: 40,
    borderWidth: 1,
    borderColor: '#EAE1F2',
    borderRadius: 8,
    paddingHorizontal: 12,
    marginBottom: 8,
  },
  buttonContainer: {
    flexDirection: 'row',
    justifyContent: 'flex-end',
    gap: 8,
  },
  cancelButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#F3EDF7',
  },
  cancelButtonText: {
    fontSize: 12,
    color: '#5D3FD3',
  },
  saveButton: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 20,
    backgroundColor: '#D9F0DB',
  },
  saveButtonText: {
    fontSize: 12,
    fontWeight: '600',
    color: '#5D3FD3',
  },
  setupForm: {
    // No specific styles needed for now, but we can add if required
  },
  setupLabel: {
    fontSize: 14,
    fontWeight: '500',
    marginVertical: 8,
  },
  setupInput: {
    height: 40,
    borderWidth: 1,
    borderColor: '#EAE1F2',
    borderRadius: 8,
    paddingHorizontal: 12,
    marginBottom: 12,
  },
  saveSetupButton: {
    backgroundColor: '#5D3FD3',
    paddingVertical: 12,
    paddingHorizontal: 24,
    borderRadius: 20,
    marginTop: 20,
    alignItems: 'center',
  },
  saveSetupText: {
    color: '#FFFFFF',
    fontSize: 16,
    fontWeight: '600',
  },
});