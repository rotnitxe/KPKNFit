import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  View, Text, StyleSheet, Modal, TextInput, TouchableOpacity,
  ScrollView, KeyboardAvoidingView, Platform, Dimensions, Switch
} from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withSpring, withTiming, runOnJS } from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Picker } from '@react-native-picker/picker';
import { useExerciseStore } from '../../stores/exerciseStore';
import { generateId } from '../../utils/generateId';
import { ExerciseMuscleInfo, InvolvedMuscle } from '../../types/workout';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface CustomExerciseEditorModalProps {
  visible: boolean;
  onClose: () => void;
  onSave: (exercise: ExerciseMuscleInfo) => void;
  existingExercise?: ExerciseMuscleInfo;
  preFilledName?: string;
}

const MUSCLE_LIST = [
  'Cuádriceps', 'Isquiosurales', 'Glúteos', 'Pantorrillas', 'Pectorales', 'Dorsales',
  'Deltoides (anterior)', 'Deltoides (lateral)', 'Deltoides (posterior)',
  'Bíceps', 'Tríceps', 'Trapecio', 'Abdomen', 'Espalda Baja', 'Antebrazo', 'Aductores'
];

const CustomExerciseEditorModal: React.FC<CustomExerciseEditorModalProps> = ({
  visible, onClose, onSave, existingExercise, preFilledName
}) => {
  const { addOrUpdateCustomExercise } = useExerciseStore();
  
  const [exercise, setExercise] = useState<ExerciseMuscleInfo>({
    id: generateId(),
    name: '',
    description: '',
    involvedMuscles: [],
    category: 'Hipertrofia',
    type: 'Accesorio',
    equipment: 'Otro',
    force: 'Otro',
    isCustom: true
  });
  const [error, setError] = useState('');
  const [isAxialLoaded, setIsAxialLoaded] = useState(false);

  const translateY = useSharedValue(SCREEN_HEIGHT);

  useEffect(() => {
    if (visible) {
      translateY.value = withSpring(0, { damping: 20, stiffness: 90 });
      if (existingExercise) {
        setExercise({ ...existingExercise });
      } else {
        setExercise({
          id: generateId(),
          name: preFilledName || '',
          description: '',
          involvedMuscles: [],
          category: 'Hipertrofia',
          type: 'Accesorio',
          equipment: 'Otro',
          force: 'Otro',
          isCustom: true
        });
      }
      setError('');
      setIsAxialLoaded(false);
    } else {
      translateY.value = withTiming(SCREEN_HEIGHT);
    }
  }, [visible, existingExercise, preFilledName]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }]
  }));

  const closeWithAnimation = useCallback(() => {
    translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
      runOnJS(onClose)();
    });
  }, [onClose]);

  const gesture = Gesture.Pan()
    .onUpdate((event) => {
      if (event.translationY > 0) translateY.value = event.translationY;
    })
    .onEnd((event) => {
      if (event.translationY > 150 || event.velocityY > 500) {
        runOnJS(closeWithAnimation);
      } else {
        translateY.value = withSpring(0);
      }
    });

  const predictedAuge = useMemo(() => {
    let baseEfc = 2.0; let baseCnc = 2.0;
    if (exercise.type === 'Aislamiento') { baseEfc = 1.5; baseCnc = 1.5; } 
    else {
      switch (exercise.force) {
        case 'Sentadilla': baseEfc = 4.0; baseCnc = 4.0; break;
        case 'Bisagra': baseEfc = 4.5; baseCnc = 4.5; break;
        case 'Empuje': baseEfc = 3.2; baseCnc = 3.2; break; 
        case 'Tirón': baseEfc = 3.2; baseCnc = 3.2; break;
        default: baseEfc = 2.5; baseCnc = 2.5; break;
      }
    }
    let eqEfcMult = 1.0; let eqCncMult = 1.0;
    switch (exercise.equipment) {
      case 'Barra': eqEfcMult = 1.0; eqCncMult = 1.2; break;
      case 'Mancuerna': eqEfcMult = 0.9; eqCncMult = 1.1; break;
      case 'Máquina': case 'Polea': eqEfcMult = 0.8; eqCncMult = 0.6; break;
      case 'Peso Corporal': eqEfcMult = 0.8; eqCncMult = 0.8; break;
    }
    let efc = baseEfc * eqEfcMult;
    let cnc = baseCnc * eqCncMult;
    if (exercise.category === 'Fuerza' || exercise.category === 'Potencia') cnc += 0.5;
    let ssc = 0.2;
    if (isAxialLoaded) {
      if (exercise.equipment === 'Barra') ssc = 1.5;
      else if (exercise.equipment === 'Mancuerna') ssc = 1.0;
      else ssc = 0.8; 
    }
    return { 
      efc: Math.min(5.0, Math.max(0.5, efc)), 
      cnc: Math.min(5.0, Math.max(0.5, cnc)), 
      ssc: Math.min(2.0, Math.max(0.0, ssc)) 
    };
  }, [exercise.type, exercise.force, exercise.equipment, exercise.category, isAxialLoaded]);

  const handleForceChange = useCallback((newForce: string) => {
    const typedForce = newForce as 'Empuje' | 'Tirón' | 'Bisagra' | 'Sentadilla' | 'Rotación' | 'Anti-Rotación' | 'Flexión' | 'Extensión' | 'Anti-Flexión' | 'Anti-Extensión' | 'Otro';
    
    // Update the force directly
    setExercise(prev => ({ ...prev, force: typedForce }));
    
    // Only auto-assign muscles if list is empty
    if ((exercise.involvedMuscles || []).length === 0) {
      let muscles: InvolvedMuscle[] = [];
      switch (newForce) {
        case 'Sentadilla': muscles = [{ muscle: 'Cuádriceps', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }]; break;
        case 'Bisagra': muscles = [{ muscle: 'Isquiosurales', role: 'primary', activation: 1.0 }, { muscle: 'Glúteos', role: 'secondary', activation: 0.5 }, { muscle: 'Espalda Baja', role: 'stabilizer', activation: 0.3 }]; break;
        case 'Empuje': muscles = [{ muscle: 'Pectorales', role: 'primary', activation: 1.0 }, { muscle: 'Tríceps', role: 'secondary', activation: 0.5 }, { muscle: 'Deltoides', role: 'secondary', activation: 0.5 }]; break;
        case 'Tirón': muscles = [{ muscle: 'Dorsales', role: 'primary', activation: 1.0 }, { muscle: 'Bíceps', role: 'secondary', activation: 0.5 }]; break;
        case 'Anti-Extensión': case 'Flexión': muscles = [{ muscle: 'Abdomen', role: 'primary', activation: 1.0 }]; break;
      }
      if (muscles.length > 0) {
        setExercise(prev => ({ ...prev, involvedMuscles: muscles }));
      }
    }
  }, [exercise.involvedMuscles]);

  const addInvolvedMuscle = useCallback(() => {
    const newMuscle: InvolvedMuscle = { muscle: '', activation: 0.5, role: 'secondary' };
    setExercise(prev => ({ ...prev, involvedMuscles: [...(prev.involvedMuscles || []), newMuscle] }));
  }, []);

  const removeInvolvedMuscle = useCallback((index: number) => {
    setExercise(prev => ({ 
      ...prev, 
      involvedMuscles: (prev.involvedMuscles || []).filter((_, i) => i !== index) 
    }));
  }, []);

  const handleInvolvedMuscleChange = useCallback((index: number, field: 'muscle' | 'role', value: string) => {
    setExercise(prev => {
      const updatedInvolved = [...(prev.involvedMuscles || [])];
      if (field === 'muscle') {
        const normalized = value ? value : '';
        updatedInvolved[index].muscle = normalized;
      } else if (field === 'role') {
        updatedInvolved[index].role = value as any;
        updatedInvolved[index].activation = value === 'primary' ? 1.0 : (value === 'secondary' ? 0.5 : 0.0);
      }
      return { ...prev, involvedMuscles: updatedInvolved };
    });
  }, []);

  const handleSaveClick = useCallback(() => {
    if (!exercise.name.trim()) {
      setError("El nombre del ejercicio es obligatorio.");
      return;
    }
    if (!exercise.involvedMuscles || exercise.involvedMuscles.length === 0 || exercise.involvedMuscles.some(m => !m.muscle)) {
      setError("Asigna correctamente los músculos implicados.");
      return;
    }
    
    const finalExercise: ExerciseMuscleInfo = {
      ...exercise,
      name: exercise.name.trim(),
      efc: exercise.efc || Number(predictedAuge.efc.toFixed(1)),
      cnc: exercise.cnc || Number(predictedAuge.cnc.toFixed(1)),
      ssc: exercise.ssc || Number(predictedAuge.ssc.toFixed(1)),
    };
    
    addOrUpdateCustomExercise(finalExercise);
    onSave(finalExercise);
    onClose();
  }, [exercise, predictedAuge, addOrUpdateCustomExercise, onSave, onClose]);

  const renderMuscleRow = useCallback((inv: InvolvedMuscle, idx: number) => (
    <View key={idx} style={styles.muscleRow}>
      <Picker
        selectedValue={inv.muscle}
        onValueChange={(val: string) => handleInvolvedMuscleChange(idx, 'muscle', val)}
        style={styles.musclePicker}
      >
        <Picker.Item label="Músculo..." value="" />
        {MUSCLE_LIST.map(m => <Picker.Item key={m} label={m} value={m} />)}
      </Picker>
      <Picker
        selectedValue={inv.role}
        onValueChange={(val: string) => handleInvolvedMuscleChange(idx, 'role', val)}
        style={[styles.rolePicker, inv.role === 'primary' && styles.rolePickerPrimary]}
      >
        <Picker.Item label="Primario" value="primary" />
        <Picker.Item label="Secundario" value="secondary" />
        <Picker.Item label="Estabilizador" value="stabilizer" />
      </Picker>
      <TouchableOpacity onPress={() => removeInvolvedMuscle(idx)} style={styles.removeBtn}>
        <Text style={styles.removeText}>×</Text>
      </TouchableOpacity>
    </View>
  ), [handleInvolvedMuscleChange, removeInvolvedMuscle]);

  return (
    <Modal transparent visible={visible} animationType="none">
      <View style={styles.overlay}>
        <Animated.View style={[styles.sheet, animatedStyle]}>
          <View style={styles.handle} />
          <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'} style={{ flex: 1 }}>
            <ScrollView style={styles.content} keyboardShouldPersistTaps="handled">
              <View style={styles.header}>
                <Text style={styles.title}>{existingExercise ? 'Editar Ejercicio' : 'Nuevo Ejercicio'}</Text>
                <TouchableOpacity onPress={closeWithAnimation} style={styles.closeBtn}>
                  <Text style={styles.closeText}>✕</Text>
                </TouchableOpacity>
              </View>
              
              <View style={styles.fieldsGrid}>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Nombre</Text>
                  <TextInput 
                    value={exercise.name}
                    onChangeText={(val: string) => setExercise(prev => ({ ...prev, name: val }))}
                    placeholder="Ej. Press Militar Libre"
                    style={styles.textInput}
                  />
                </View>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Alias</Text>
                  <TextInput 
                    value={exercise.alias || ''}
                    onChangeText={(val: string) => setExercise(prev => ({ ...prev, alias: val }))}
                    placeholder="Ej. OHP"
                    style={[styles.textInput, { textAlign: 'center' }]}
                  />
                </View>
              </View>

              <View style={styles.classificationGrid}>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Patrón</Text>
                  <Picker
                    selectedValue={exercise.force}
                    onValueChange={handleForceChange}
                    style={styles.picker}
                  >
                    <Picker.Item label="Empuje" value="Empuje" />
                    <Picker.Item label="Tirón" value="Tirón" />
                    <Picker.Item label="Bisagra" value="Bisagra" />
                    <Picker.Item label="Sentadilla" value="Sentadilla" />
                    <Picker.Item label="Rotación" value="Rotación" />
                    <Picker.Item label="Anti-Rotación" value="Anti-Rotación" />
                    <Picker.Item label="Flexión" value="Flexión" />
                    <Picker.Item label="Extensión" value="Extensión" />
                    <Picker.Item label="Anti-Flexión" value="Anti-Flexión" />
                    <Picker.Item label="Anti-Extensión" value="Anti-Extensión" />
                    <Picker.Item label="Otro" value="Otro" />
                  </Picker>
                </View>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Equipo</Text>
                  <Picker
                    selectedValue={exercise.equipment}
                    onValueChange={(val: string) => setExercise(prev => ({ ...prev, equipment: val }))}
                    style={styles.picker}
                  >
                    <Picker.Item label="Barra" value="Barra" />
                    <Picker.Item label="Mancuerna" value="Mancuerna" />
                    <Picker.Item label="Máquina" value="Máquina" />
                    <Picker.Item label="Peso Corporal" value="Peso Corporal" />
                    <Picker.Item label="Banda" value="Banda" />
                    <Picker.Item label="Kettlebell" value="Kettlebell" />
                    <Picker.Item label="Polea" value="Polea" />
                    <Picker.Item label="Otro" value="Otro" />
                  </Picker>
                </View>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Tipo</Text>
                  <Picker
                    selectedValue={exercise.type}
                    onValueChange={(val: string) => setExercise(prev => ({ ...prev, type: val as 'Básico' | 'Accesorio' | 'Aislamiento' }))}
                    style={styles.picker}
                  >
                    <Picker.Item label="Básico" value="Básico" />
                    <Picker.Item label="Accesorio" value="Accesorio" />
                    <Picker.Item label="Aislamiento" value="Aislamiento" />
                  </Picker>
                </View>
                <View style={styles.fieldGroup}>
                  <Text style={styles.label}>Categoría</Text>
                  <Picker
                    selectedValue={exercise.category}
                    onValueChange={(val: string) => setExercise(prev => ({ ...prev, category: val }))}
                    style={styles.picker}
                  >
                    <Picker.Item label="Hipertrofia" value="Hipertrofia" />
                    <Picker.Item label="Fuerza" value="Fuerza" />
                    <Picker.Item label="Potencia" value="Potencia" />
                    <Picker.Item label="Resistencia" value="Resistencia" />
                    <Picker.Item label="Movilidad" value="Movilidad" />
                  </Picker>
                </View>
              </View>

              <View style={styles.axialContainer}>
                <View>
                  <Text style={styles.axialTitle}>Carga Axial (Espalda)</Text>
                  <Text style={styles.axialSubtitle}>¿El peso descansa o comprime tu columna?</Text>
                </View>
                <Switch value={isAxialLoaded} onValueChange={setIsAxialLoaded} />
              </View>

              <View style={styles.augeContainer}>
                <View style={styles.augeHeader}>
                  <Text style={styles.augeTitle}>Motor Drenaje AUGE</Text>
                  <Text style={styles.augeAutoLabel}>Auto-calculado</Text>
                </View>
                <View style={styles.augeMetrics}>
                  <View style={styles.augeMetric}>
                    <Text style={styles.augeLabel}>EFC</Text>
                    <TextInput
                      value={exercise.efc?.toString() || ''}
                      onChangeText={(val: string) => setExercise(prev => ({ ...prev, efc: val ? parseFloat(val) : undefined }))}
                      placeholder={predictedAuge.efc.toFixed(1)}
                      style={styles.augeInput}
                    />
                  </View>
                  <View style={styles.augeMetric}>
                    <Text style={styles.augeLabel}>CNC</Text>
                    <TextInput
                      value={exercise.cnc?.toString() || ''}
                      onChangeText={(val: string) => setExercise(prev => ({ ...prev, cnc: val ? parseFloat(val) : undefined }))}
                      placeholder={predictedAuge.cnc.toFixed(1)}
                      style={styles.augeInput}
                    />
                  </View>
                  <View style={styles.augeMetric}>
                    <Text style={styles.augeLabel}>SSC</Text>
                    <TextInput
                      value={exercise.ssc?.toString() || ''}
                      onChangeText={(val: string) => setExercise(prev => ({ ...prev, ssc: val ? parseFloat(val) : undefined }))}
                      placeholder={predictedAuge.ssc.toFixed(1)}
                      style={styles.augeInput}
                    />
                  </View>
                </View>
              </View>

              <View style={styles.musclesSection}>
                <Text style={styles.label}>Músculos Implicados</Text>
                {(exercise.involvedMuscles || []).map(renderMuscleRow)}
                <TouchableOpacity onPress={addInvolvedMuscle} style={styles.addMuscleBtn}>
                  <Text style={styles.addMuscleText}>+ Agregar Músculo</Text>
                </TouchableOpacity>
              </View>

              {error ? <Text style={styles.errorText}>{error}</Text> : null}
            </ScrollView>
            
            <View style={styles.footer}>
              <TouchableOpacity onPress={closeWithAnimation} style={styles.cancelBtn}>
                <Text style={styles.cancelText}>Cancelar</Text>
              </TouchableOpacity>
              <TouchableOpacity onPress={handleSaveClick} style={styles.saveBtn}>
                <Text style={styles.saveText}>Guardar Ejercicio</Text>
              </TouchableOpacity>
            </View>
          </KeyboardAvoidingView>
        </Animated.View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.3)',
    justifyContent: 'flex-end',
  },
  sheet: {
    height: SCREEN_HEIGHT * 0.85,
    backgroundColor: '#FEF7FF',
    borderTopLeftRadius: 36,
    borderTopRightRadius: 36,
    borderTopWidth: 4,
    borderTopColor: '#6750A4',
  },
  handle: {
    width: 44,
    height: 4,
    backgroundColor: '#E2E8F0',
    borderRadius: 2,
    alignSelf: 'center',
    marginVertical: 12,
  },
  content: {
    flex: 1,
    padding: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  title: {
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.5,
    textTransform: 'uppercase',
    color: '#6750A4',
  },
  closeBtn: {
    padding: 8,
  },
  closeText: {
    fontSize: 18,
    color: '#49454F',
  },
  fieldsGrid: {
    flexDirection: 'row',
    gap: 16,
    marginBottom: 20,
  },
  fieldGroup: {
    flex: 1,
  },
  label: {
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
    color: '#49454F',
    marginBottom: 8,
  },
  textInput: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 14,
    fontSize: 14,
    fontWeight: '700',
    color: '#1C1B1F',
  },
  classificationGrid: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 20,
    padding: 16,
    marginBottom: 20,
    gap: 16,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  picker: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 12,
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1B1F',
    height: 44,
    width: '100%',
  },
  axialContainer: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 20,
    padding: 16,
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  axialTitle: {
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1B1F',
  },
  axialSubtitle: {
    fontSize: 9,
    color: '#49454F',
  },
  augeContainer: {
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 20,
    padding: 16,
    marginBottom: 20,
  },
  augeHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 16,
  },
  augeTitle: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1,
    textTransform: 'uppercase',
    color: '#1C1B1F',
  },
  augeAutoLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
    color: '#49454F',
  },
  augeMetrics: {
    flexDirection: 'row',
    gap: 12,
  },
  augeMetric: {
    flex: 1,
    alignItems: 'center',
  },
  augeLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
    color: '#49454F',
    marginBottom: 8,
  },
  augeInput: {
    backgroundColor: '#FEF7FF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 12,
    paddingHorizontal: 12,
    paddingVertical: 10,
    fontSize: 12,
    fontWeight: '700',
    color: '#1C1B1F',
    textAlign: 'center',
    width: '100%',
  },
  musclesSection: {
    marginBottom: 20,
  },
  muscleRow: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 16,
    paddingHorizontal: 12,
    paddingVertical: 8,
    marginBottom: 8,
    gap: 8,
  },
  musclePicker: {
    flex: 1,
    backgroundColor: 'transparent',
    fontSize: 12,
    fontWeight: '600',
    color: '#1C1B1F',
    height: 36,
  },
  rolePicker: {
    width: 100,
    backgroundColor: '#FEF7FF',
    borderWidth: 1,
    borderColor: '#E7E0EC',
    borderRadius: 10,
    fontSize: 9,
    fontWeight: '800',
    textTransform: 'uppercase',
    color: '#49454F',
    height: 36,
  },
  rolePickerPrimary: {
    color: '#6750A4',
  },
  removeBtn: {
    padding: 8,
  },
  removeText: {
    fontSize: 18,
    color: '#49454F',
  },
  addMuscleBtn: {
    paddingVertical: 14,
    borderWidth: 1,
    borderStyle: 'dashed',
    borderColor: '#E7E0EC',
    borderRadius: 16,
    alignItems: 'center',
    marginTop: 8,
  },
  addMuscleText: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    color: '#6750A4',
  },
  errorText: {
    color: '#DC2626',
    fontSize: 12,
    fontWeight: '700',
    textAlign: 'center',
    marginBottom: 20,
  },
  footer: {
    flexDirection: 'row',
    paddingHorizontal: 24,
    paddingVertical: 16,
    gap: 12,
    borderTopWidth: 1,
    borderTopColor: '#E7E0EC',
    backgroundColor: '#FEF7FF',
  },
  cancelBtn: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 16,
    borderWidth: 1,
    borderColor: '#E7E0EC',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#FFFFFF',
  },
  cancelText: {
    fontSize: 11,
    fontWeight: '800',
    textTransform: 'uppercase',
    color: '#49454F',
  },
  saveBtn: {
    flex: 2,
    paddingVertical: 14,
    borderRadius: 16,
    backgroundColor: '#6750A4',
    alignItems: 'center',
    justifyContent: 'center',
  },
  saveText: {
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    color: '#FFFFFF',
  },
});

export default React.memo(CustomExerciseEditorModal);
