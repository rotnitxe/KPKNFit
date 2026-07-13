import React, { useState } from 'react';
import { 
  View, 
  Text, 
  Pressable, 
  ScrollView, 
  TextInput,
  StyleSheet
} from 'react-native';
import { Button } from '../ui';
import { useColors } from '../../theme';
import { LiquidGlassModal } from '../ui/LiquidGlassModal';
import ReactNativeHapticFeedback from '@/services/hapticsService';

interface PostSessionQuestionnaireModalProps {
  visible: boolean;
  onClose: () => void;
  onSubmit: (feedback: {
    sessionRpe: number;
    energyAfter: number;
    sorenessAfter: number;
    hadPain: boolean;
    notes: string;
  }) => void;
  isSaving?: boolean;
}

const RatingRow = ({ 
  label, 
  max, 
  value, 
  onChange 
}: { 
  label: string; 
  max: number; 
  value: number; 
  onChange: (v: number) => void 
}) => {
  const colors = useColors();
  const options = Array.from({ length: max }, (_, i) => i + 1);
  
  return (
    <View style={styles.ratingRow}>
      <Text style={[styles.ratingLabel, { color: colors.onSurfaceVariant }]}>
        {label}
      </Text>
      <ScrollView 
        horizontal 
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.ratingContent}
      >
        {options.map((num) => {
          const isActive = value === num;
          return (
            <Pressable
              key={num}
              onPress={() => {
                ReactNativeHapticFeedback.trigger('selection', { enableVibrateFallback: true });
                onChange(num);
              }}
              style={[
                styles.ratingPill,
                { 
                  backgroundColor: isActive ? colors.primary : `${colors.onSurface}0D`,
                  borderColor: isActive ? colors.primary : `${colors.onSurface}1A`
                }
              ]}
            >
              <Text style={[styles.ratingNumber, { color: isActive ? colors.onPrimary : colors.onSurface }]}>
                {num}
              </Text>
            </Pressable>
          );
        })}
      </ScrollView>
    </View>
  );
};

export const PostSessionQuestionnaireModal: React.FC<PostSessionQuestionnaireModalProps> = ({
  visible,
  onClose,
  onSubmit,
  isSaving = false,
}) => {
  const colors = useColors();
  const [sessionRpe, setSessionRpe] = useState(7);
  const [energyAfter, setEnergyAfter] = useState(3);
  const [sorenessAfter, setSorenessAfter] = useState(2);
  const [hadPain, setHadPain] = useState(false);
  const [notes, setNotes] = useState('');

  const handleFinish = () => {
    ReactNativeHapticFeedback.trigger('notificationSuccess', { enableVibrateFallback: true });
    onSubmit({
      sessionRpe,
      energyAfter,
      sorenessAfter,
      hadPain,
      notes,
    });
  };

  return (
    <LiquidGlassModal
      visible={visible}
      onClose={onClose}
      title="Feedback de Sesión"
      subtitle="Cuéntanos cómo te fue hoy"
      height={650}
    >
      <ScrollView showsVerticalScrollIndicator={false} style={styles.scrollArea}>
        <RatingRow 
          label="RPE de la sesión (1-10)" 
          max={10} 
          value={sessionRpe} 
          onChange={setSessionRpe} 
        />

        <RatingRow 
          label="Energía al terminar (1-5)" 
          max={5} 
          value={energyAfter} 
          onChange={setEnergyAfter} 
        />

        <RatingRow 
          label="Fatiga/Dolor al terminar (1-5)" 
          max={5} 
          value={sorenessAfter} 
          onChange={setSorenessAfter} 
        />

        <View style={styles.toggleSection}>
          <Text style={[styles.ratingLabel, { color: colors.onSurfaceVariant }]}>
            ¿Molestia articular o dolor inusual?
          </Text>
          <View style={styles.toggleGrid}>
            <Pressable
              onPress={() => setHadPain(false)}
              style={[
                styles.toggleButton,
                !hadPain && { backgroundColor: `${colors.onSurface}1A`, borderColor: `${colors.onSurface}4D` }
              ]}
            >
              <Text style={[styles.toggleText, { color: !hadPain ? colors.onSurface : colors.onSurfaceVariant }]}>
                No
              </Text>
            </Pressable>
            <Pressable
              onPress={() => setHadPain(true)}
              style={[
                styles.toggleButton,
                hadPain && { backgroundColor: `${colors.error}22`, borderColor: colors.error }
              ]}
            >
              <Text style={[styles.toggleText, { color: hadPain ? colors.error : colors.onSurfaceVariant }]}>
                Sí
              </Text>
            </Pressable>
          </View>
        </View>

        <View style={styles.notesSection}>
          <Text style={[styles.ratingLabel, { color: colors.onSurfaceVariant }]}>
            Notas (opcional)
          </Text>
          <TextInput
            multiline
            numberOfLines={3}
            maxLength={280}
            placeholder="Observaciones sobre la sesión..."
            placeholderTextColor={`${colors.onSurfaceVariant}66`}
            value={notes}
            onChangeText={setNotes}
            style={[
              styles.textInput,
              { 
                backgroundColor: `${colors.onSurface}08`,
                borderColor: `${colors.onSurface}1A`,
                color: colors.onSurface
              }
            ]}
          />
        </View>
      </ScrollView>

      <View style={styles.footer}>
        <View style={styles.footerButton}>
          <Button variant="secondary" onPress={onClose} disabled={isSaving}>
            Atrás
          </Button>
        </View>
        <View style={styles.footerButton}>
          <Button variant="primary" onPress={handleFinish} isLoading={isSaving} disabled={isSaving}>
            Guardar
          </Button>
        </View>
      </View>
    </LiquidGlassModal>
  );
};

const styles = StyleSheet.create({
  scrollArea: {
    marginVertical: 12,
  },
  ratingRow: {
    marginBottom: 20,
  },
  ratingLabel: {
    fontSize: 10,
    fontWeight: '900',
    marginBottom: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  ratingContent: {
    gap: 10,
    paddingRight: 20,
    paddingVertical: 5,
  },
  ratingPill: {
    width: 44,
    height: 44,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1.5,
  },
  ratingNumber: {
    fontSize: 16,
    fontWeight: '800',
  },
  toggleSection: {
    marginBottom: 20,
  },
  toggleGrid: {
    flexDirection: 'row',
    gap: 12,
  },
  toggleButton: {
    flex: 1,
    paddingVertical: 14,
    borderRadius: 16,
    borderWidth: 1.5,
    borderColor: 'rgba(0,0,0,0.05)',
    alignItems: 'center',
    backgroundColor: 'rgba(0,0,0,0.02)',
  },
  toggleText: {
    fontWeight: '800',
    fontSize: 14,
  },
  notesSection: {
    marginBottom: 20,
  },
  textInput: {
    borderRadius: 20,
    borderWidth: 1.5,
    padding: 16,
    fontSize: 15,
    minHeight: 100,
    textAlignVertical: 'top',
    fontWeight: '500',
  },
  footer: {
    flexDirection: 'row',
    gap: 12,
    paddingTop: 12,
  },
  footerButton: {
    flex: 1,
  },
});
