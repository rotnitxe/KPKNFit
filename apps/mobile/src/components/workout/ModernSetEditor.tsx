import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Modal, Pressable, TextInput, KeyboardAvoidingView, Platform, Dimensions, ScrollView, TouchableOpacity, LayoutAnimation, UIManager, Keyboard } from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withSpring, withTiming, runOnJS } from 'react-native-reanimated';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { Button } from '../ui';
import { OngoingSetData } from '../../types/workout';
import { TargetIcon, FlameIcon, XCircleIcon, PlusIcon, MinusIcon } from '../icons';
import { PWA_WORKOUT_PALETTE as PWA } from './pwaWorkoutPalette';
import ReactNativeHapticFeedback from 'react-native-haptic-feedback';
import { Canvas, Blur, Rect, ColorMatrix, Paint } from '@shopify/react-native-skia';

interface ModernSetEditorProps {
  visible: boolean;
  onClose: () => void;
  onSave: (data: OngoingSetData) => void;
  initialData?: OngoingSetData;
  exerciseName: string;
  setIndex: number;
  targetReps?: number;
  targetWeight?: number;
  isAmrap?: boolean;
  isCalibrator?: boolean;
}

const { height: SCREEN_HEIGHT, width: SCREEN_WIDTH } = Dimensions.get('window');

export const ModernSetEditor: React.FC<ModernSetEditorProps> = ({
  visible,
  onClose,
  onSave,
  initialData,
  exerciseName,
  setIndex,
  targetReps,
  targetWeight,
  isAmrap,
  isCalibrator,
}) => {
  const [intensityMode, setIntensityMode] = useState<'rpe' | 'rir' | 'percent'>(initialData?.rpe !== undefined ? 'rpe' : (initialData?.rir !== undefined ? 'rir' : 'rir'));
  const [weight, setWeight] = useState(initialData?.weight?.toString() || targetWeight?.toString() || '0');
  const [reps, setReps] = useState(initialData?.reps?.toString() || targetReps?.toString() || '0');
  const [rir, setRir] = useState((initialData?.rir ?? 2).toString());
  const [rpe, setRpe] = useState((initialData?.rpe ?? 8).toString());
  const [percentRM, setPercentRM] = useState('75');
  const [perfMode, setPerfMode] = useState<OngoingSetData['performanceMode']>(initialData?.performanceMode || 'target');
  const [isAmrapState, setIsAmrapState] = useState(initialData?.isAmrap ?? isAmrap ?? false);
  const [isCalibratorState, setIsCalibratorState] = useState(initialData?.isCalibrator ?? isCalibrator ?? false);

  const reference1RM = initialData?.weight ? (initialData.weight / (1.0278 - 0.0278 * (initialData.reps || 1))) : targetWeight; // Very basic fallback

  const translateY = useSharedValue(SCREEN_HEIGHT);

  useEffect(() => {
    if (visible) {
      setWeight(initialData?.weight?.toString() || targetWeight?.toString() || '0');
      setReps(initialData?.reps?.toString() || targetReps?.toString() || '0');
      setRir((initialData?.rir ?? 2).toString());
      setPerfMode(initialData?.performanceMode || 'target');
      setIsAmrapState(initialData?.isAmrap ?? isAmrap ?? false);
      setIsCalibratorState(initialData?.isCalibrator ?? isCalibrator ?? false);
      translateY.value = withSpring(0, { damping: 20, stiffness: 90 });
    } else {
      translateY.value = withTiming(SCREEN_HEIGHT);
    }
  }, [visible, initialData, targetWeight, targetReps, isAmrap, isCalibrator, translateY]);

  useEffect(() => {
    const keyboardDidShowListener = Keyboard.addListener('keyboardDidShow', () => {
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    });
    const keyboardDidHideListener = Keyboard.addListener('keyboardDidHide', () => {
      LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    });
    return () => {
      keyboardDidShowListener?.remove();
      keyboardDidHideListener?.remove();
    };
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ translateY: translateY.value }],
  }));

  const closeWithAnimation = () => {
    translateY.value = withTiming(SCREEN_HEIGHT, {}, () => {
      runOnJS(onClose)();
    });
  };

  const handleSave = () => {
    onSave({
      weight: parseFloat(weight) || 0,
      reps: parseInt(reps, 10) || 0,
      rir: intensityMode === 'rir' ? parseFloat(rir) : undefined,
      rpe: intensityMode === 'rpe' ? parseFloat(rpe) : undefined,
      performanceMode: perfMode,
      isAmrap: isAmrapState,
      isCalibrator: isCalibratorState,
    });
    
    if (isCalibratorState) {
      ReactNativeHapticFeedback.trigger('notificationSuccess', { enableVibrateFallback: true });
    }
    
    closeWithAnimation();
  };

  const cycleMode = () => {
    ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
    if (perfMode === 'target') setPerfMode('failure');
    else if (perfMode === 'failure') setPerfMode('failed');
    else setPerfMode('target');
  };

  const gesture = Gesture.Pan()
    .onUpdate((event) => {
      if (event.translationY > 0) translateY.value = event.translationY;
    })
    .onEnd((event) => {
      if (event.translationY > 150 || event.velocityY > 500) {
        runOnJS(closeWithAnimation)();
      } else {
        translateY.value = withSpring(0);
      }
    });

  useEffect(() => {
    if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
      UIManager.setLayoutAnimationEnabledExperimental(true);
    }
  }, []);

  const adjustValue = (setter: React.Dispatch<React.SetStateAction<string>>, increment: number, isFloat = false) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    setter(prev => {
      const parsed = isFloat ? parseFloat(prev) : parseInt(prev, 10);
      const safeCurrent = Number.isFinite(parsed) ? parsed : 0;
      const next = Math.max(0, safeCurrent + increment);
      return isFloat ? next.toFixed(1) : next.toString();
    });
    ReactNativeHapticFeedback.trigger('impactLight', { enableVibrateFallback: true });
  };

  if (!visible) return null;

  return (
    <Modal transparent visible={visible} animationType="none">
      <View style={styles.overlay}>
        {/* Premium Frosted Glass Background using Skia */}
        <View style={StyleSheet.absoluteFill}>
          <Canvas style={StyleSheet.absoluteFill}>
            <Rect x={0} y={0} width={SCREEN_WIDTH} height={SCREEN_HEIGHT}>
              <Blur blur={25} />
              <ColorMatrix matrix={[
                1, 0, 0, 0, 0,
                0, 1, 0, 0, 0,
                0, 0, 1, 0, 0,
                0, 0, 0, 0.45, 0,
              ]} />
            </Rect>
            <Rect x={0} y={0} width={SCREEN_WIDTH} height={SCREEN_HEIGHT}>
              <Paint color="rgba(255, 255, 255, 0.12)" />
            </Rect>
          </Canvas>
        </View>
        
        <Pressable style={StyleSheet.absoluteFill} onPress={closeWithAnimation} />

        <GestureDetector gesture={gesture}>
          <Animated.View style={[styles.sheet, animatedStyle]}>
            <View style={styles.handle} />

            <KeyboardAvoidingView behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
              <ScrollView style={styles.content} keyboardShouldPersistTaps="handled">
                <View style={styles.header}>
                  <View>
                    <Text style={styles.title}>{exerciseName}</Text>
                    <Text style={styles.subtitle}>Serie {setIndex + 1}</Text>
                  </View>

                  <TouchableOpacity onPress={cycleMode} style={styles.modeCycleButton}>
                    {perfMode === 'target' && <TargetIcon size={20} color={PWA.muted} />}
                    {perfMode === 'failure' && <FlameIcon size={20} color={PWA.warning} />}
                    {perfMode === 'failed' && <XCircleIcon size={20} color={PWA.danger} />}
                  </TouchableOpacity>
                </View>

                <View style={[styles.targetHint, isCalibratorState && styles.calibratorHint]}>
                  <View style={styles.hintHeader}>
                    <Text style={[styles.targetHintText, isCalibratorState && styles.calibratorHintText]}>
                      {isCalibratorState ? 'Serie Calibradora AMRAP' : 'Objetivo sugerido'}
                    </Text>
                    {isCalibratorState && (
                      <View style={styles.calBadge}>
                        <Text style={styles.calBadgeText}>AUTO-CAL</Text>
                      </View>
                    )}
                  </View>
                  <Text style={styles.targetHintSub}>
                    {targetWeight ?? 0} kg · {targetReps ?? 0} {isAmrapState ? 'o + reps' : 'reps'}
                  </Text>
                  
                  {(parseFloat(weight) > 0 && parseInt(reps, 10) > 0) && (
                    <View style={styles.e1rmRow}>
                      <Text style={styles.e1rmLabel}>1RM Estimado:</Text>
                      <Text style={styles.e1rmValue}>
                        {(parseFloat(weight) * (1 + parseInt(reps, 10) / 30)).toFixed(1)} kg
                      </Text>
                    </View>
                  )}
                </View>

                <View style={styles.inputGrid}>
                  <View style={styles.inputItem}>
                    <Text style={styles.inputLabel}>Peso (kg)</Text>
                    <View style={styles.numericInputGroup}>
                      <TouchableOpacity onPress={() => adjustValue(setWeight, -1.25, true)} style={styles.adjBtn}>
                        <MinusIcon size={18} color={PWA.text} />
                      </TouchableOpacity>
                      <TextInput
                        style={styles.input}
                        value={weight}
                        onChangeText={(text) => {
                          LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                          setWeight(text);
                        }}
                        keyboardType="numeric"
                      />
                      <TouchableOpacity onPress={() => adjustValue(setWeight, 1.25, true)} style={styles.adjBtn}>
                        <PlusIcon size={18} color={PWA.text} />
                      </TouchableOpacity>
                    </View>
                  </View>

                  <View style={styles.inputItem}>
                    <Text style={styles.inputLabel}>Reps</Text>
                    <View style={styles.numericInputGroup}>
                      <TouchableOpacity onPress={() => adjustValue(setReps, -1)} style={styles.adjBtn}>
                        <MinusIcon size={18} color={PWA.text} />
                      </TouchableOpacity>
                      <TextInput
                        style={styles.input}
                        value={reps}
                        onChangeText={(text) => {
                          LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
                          setReps(text);
                        }}
                        keyboardType="numeric"
                      />
                      <TouchableOpacity onPress={() => adjustValue(setReps, 1)} style={styles.adjBtn}>
                        <PlusIcon size={18} color={PWA.text} />
                      </TouchableOpacity>
                    </View>
                  </View>
                </View>

                <View style={styles.intensitySection}>
                  <View style={styles.intensityTabs}>
                    {(['rir', 'rpe', 'percent'] as const).map((m) => (
                      <TouchableOpacity 
                        key={m} 
                        onPress={() => {
                          ReactNativeHapticFeedback.trigger('selection');
                          setIntensityMode(m);
                        }}
                        style={[styles.intensityTab, intensityMode === m && styles.intensityTabActive]}
                      >
                        <Text style={[styles.intensityTabText, intensityMode === m && styles.intensityTabTextActive]}>
                          {m === 'rir' ? 'RIR' : m === 'rpe' ? 'RPE' : '%RM'}
                        </Text>
                      </TouchableOpacity>
                    ))}
                  </View>

                  {intensityMode === 'rir' && (
                    <View style={styles.rpeRow}>
                      {[0, 1, 2, 3, 4].map((val) => (
                        <Pressable
                          key={val}
                          onPress={() => {
                            ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
                            setRir(val.toString());
                          }}
                          style={[styles.rpeButton, rir === val.toString() && styles.rpeButtonActive]}
                        >
                          <Text style={[styles.rpeText, rir === val.toString() && styles.rpeTextActive]}>{val}</Text>
                        </Pressable>
                      ))}
                    </View>
                  )}

                  {intensityMode === 'rpe' && (
                    <View style={styles.rpeRow}>
                      {[6, 7, 8, 9, 10].map((val) => (
                        <Pressable
                          key={val}
                          onPress={() => {
                            ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
                            setRpe(val.toString());
                          }}
                          style={[styles.rpeButton, rpe === val.toString() && styles.rpeButtonActive]}
                        >
                          <Text style={[styles.rpeText, rpe === val.toString() && styles.rpeTextActive]}>{val}</Text>
                        </Pressable>
                      ))}
                    </View>
                  )}

                  {intensityMode === 'percent' && (
                    <View style={styles.percentRow}>
                       <TextInput
                          style={[styles.input, { flex: 0.4, height: 48, fontSize: 18 }]}
                          value={percentRM}
                          onChangeText={setPercentRM}
                          keyboardType="numeric"
                          placeholder="%"
                       />
                       <TouchableOpacity 
                        style={[styles.adjBtn, { flex: 0.6, height: 48 }]}
                        onPress={() => {
                           if (targetWeight && parseFloat(percentRM) > 0) {
                             const newWeight = (targetWeight * parseFloat(percentRM)) / 100;
                             setWeight(newWeight.toFixed(1));
                           }
                        }}
                       >
                         <Text style={{ fontSize: 10, fontWeight: '900' }}>APLICAR LOAD</Text>
                       </TouchableOpacity>
                    </View>
                  )}
                </View>

                <View style={styles.toggleRow}>
                  <TouchableOpacity 
                    onPress={() => {
                      ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
                      setIsAmrapState(!isAmrapState);
                    }} 
                    style={[styles.toggleBtn, isAmrapState && styles.toggleBtnActive]}
                  >
                    <Text style={[styles.toggleBtnText, isAmrapState && styles.toggleBtnTextActive]}>AMRAP</Text>
                  </TouchableOpacity>
                  <TouchableOpacity 
                    onPress={() => {
                      ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
                      setIsCalibratorState(!isCalibratorState);
                    }} 
                    style={[styles.toggleBtn, isCalibratorState && styles.toggleBtnActiveCal]}
                  >
                    <Text style={[styles.toggleBtnText, isCalibratorState && styles.toggleBtnTextActive]}>Calibradora</Text>
                  </TouchableOpacity>
                </View>

                <Button onPress={handleSave} style={styles.saveButton}>
                  Confirmar serie
                </Button>

                <TouchableOpacity onPress={closeWithAnimation} style={styles.cancelLink}>
                  <Text style={styles.cancelText}>Cancelar</Text>
                </TouchableOpacity>
              </ScrollView>
            </KeyboardAvoidingView>
          </Animated.View>
        </GestureDetector>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(28,27,31,0.28)',
    justifyContent: 'flex-end',
  },
  sheet: {
    borderTopLeftRadius: 36,
    borderTopRightRadius: 36,
    paddingBottom: 40,
    backgroundColor: PWA.page,
    borderTopWidth: 1,
    borderTopColor: PWA.border,
    elevation: 20,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: -10 },
    shadowOpacity: 0.1,
    shadowRadius: 10,
  },
  handle: {
    width: 44,
    height: 4,
    backgroundColor: '#D1C9DD',
    borderRadius: 2,
    alignSelf: 'center',
    marginTop: 12,
    marginBottom: 8,
  },
  content: {
    padding: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 18,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
    letterSpacing: -0.5,
    color: PWA.text,
  },
  subtitle: {
    marginTop: 4,
    fontSize: 12,
    fontWeight: '900',
    letterSpacing: 1.6,
    textTransform: 'uppercase',
    color: PWA.primary,
  },
  modeCycleButton: {
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    borderWidth: 1,
    borderColor: '#E6DCF3',
    backgroundColor: '#FFFFFF',
  },
  targetHint: {
    marginBottom: 18,
    borderRadius: 18,
    paddingHorizontal: 16,
    paddingVertical: 14,
    backgroundColor: '#F3EDF7',
    borderWidth: 1,
    borderColor: '#E9DEF8',
  },
  calibratorHint: {
    backgroundColor: '#EEF2FF',
    borderColor: '#C7D2FE',
  },
  hintHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  targetHintText: {
    color: PWA.primaryDeep,
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  calibratorHintText: {
    color: '#4338CA',
  },
  targetHintSub: {
    color: PWA.text,
    fontSize: 15,
    fontWeight: '700',
  },
  calBadge: {
    backgroundColor: '#4338CA',
    paddingHorizontal: 6,
    paddingVertical: 2,
    borderRadius: 6,
  },
  calBadgeText: {
    color: '#FFFFFF',
    fontSize: 8,
    fontWeight: '900',
  },
  e1rmRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 10,
    paddingTop: 10,
    borderTopWidth: 1,
    borderTopColor: 'rgba(0,0,0,0.05)',
    gap: 8,
  },
  e1rmLabel: {
    fontSize: 12,
    color: PWA.muted,
    fontWeight: '600',
  },
  e1rmValue: {
    fontSize: 14,
    color: PWA.success,
    fontWeight: '900',
  },
  inputGrid: {
    gap: 18,
    marginBottom: 24,
  },
  inputItem: {
    flex: 1,
  },
  inputLabel: {
    fontSize: 10,
    fontWeight: '900',
    letterSpacing: 1.5,
    marginBottom: 10,
    textTransform: 'uppercase',
    color: PWA.muted,
  },
  numericInputGroup: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  adjBtn: {
    width: 48,
    height: 48,
    borderRadius: 16,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E6DCF3',
    justifyContent: 'center',
    alignItems: 'center',
  },
  input: {
    flex: 1,
    height: 64,
    borderRadius: 20,
    fontSize: 32,
    fontWeight: '800',
    textAlign: 'center',
    color: PWA.text,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E6DCF3',
  },
  rpeSection: {
    padding: 16,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#E6DCF3',
    backgroundColor: 'rgba(255,255,255,0.72)',
    marginBottom: 24,
  },
  rpeRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginTop: 8,
  },
  rpeButton: {
    flex: 1,
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderRadius: 12,
    marginHorizontal: 4,
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E6DCF3',
  },
  rpeButtonActive: {
    backgroundColor: PWA.primary,
    borderColor: PWA.primary,
  },
  rpeText: {
    color: PWA.text,
    fontWeight: '800',
  },
  rpeTextActive: {
    color: '#FFFFFF',
  },
  intensitySection: {
    padding: 16,
    borderRadius: 24,
    borderWidth: 1,
    borderColor: '#E6DCF3',
    backgroundColor: 'rgba(255,255,255,0.72)',
    marginBottom: 24,
  },
  intensityTabs: {
    flexDirection: 'row',
    gap: 8,
    marginBottom: 12,
  },
  intensityTab: {
    flex: 1,
    paddingVertical: 6,
    borderRadius: 8,
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.03)',
  },
  intensityTabActive: {
    backgroundColor: PWA.primary,
  },
  intensityTabText: {
    fontSize: 10,
    fontWeight: '900',
    color: PWA.muted,
  },
  intensityTabTextActive: {
    color: '#FFF',
  },
  percentRow: {
    flexDirection: 'row',
    gap: 12,
    alignItems: 'center',
  },
  toggleRow: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 32,
  },
  toggleBtn: {
    flex: 1,
    height: 48,
    borderRadius: 16,
    justifyContent: 'center',
    alignItems: 'center',
    backgroundColor: '#FFFFFF',
    borderWidth: 1,
    borderColor: '#E6DCF3',
  },
  toggleBtnActive: {
    backgroundColor: '#F3EDF7',
    borderColor: PWA.primary,
  },
  toggleBtnActiveCal: {
    backgroundColor: '#EEF2FF',
    borderColor: '#4338CA',
  },
  toggleBtnText: {
    fontSize: 11,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1,
    color: PWA.muted,
  },
  toggleBtnTextActive: {
    color: PWA.primary,
  },
  saveButton: {
    height: 64,
    borderRadius: 20,
  },
  cancelLink: {
    marginTop: 20,
    alignItems: 'center',
    padding: 10,
  },
  cancelText: {
    fontSize: 12,
    fontWeight: '800',
    letterSpacing: 1,
    color: PWA.muted,
    textTransform: 'uppercase',
  },
});
