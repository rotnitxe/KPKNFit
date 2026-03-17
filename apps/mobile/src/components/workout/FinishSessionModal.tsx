import React from 'react';
import { View, Text, Modal, StyleSheet } from 'react-native';
import { Button } from '../ui';
import { useColors } from '../../theme';

interface FinishSessionModalProps {
  visible: boolean;
  onClose: () => void;
  onContinue: () => void;
  summary: {
    completedSets: number;
    totalSets: number;
    durationMinutes: number;
    exerciseCount: number;
  };
}

/**
 * FinishSessionModal
 * Muestra un resumen del entrenamiento realizado antes de proceder al cuestionario.
 * Sigue el patrón visual de Bottom Sheet del proyecto.
 */
export const FinishSessionModal: React.FC<FinishSessionModalProps> = ({
  visible,
  onClose,
  onContinue,
  summary,
}) => {
  const colors = useColors();
  const { completedSets, totalSets, durationMinutes, exerciseCount } = summary;
  
  // Cálculo defensivo del porcentaje de progreso
  const progressPercent = totalSets > 0 
    ? Math.round((completedSets / totalSets) * 100) 
    : 0;

  const SummaryRow = ({ label, value }: { label: string; value: string | number }) => (
    <View style={[styles.row, { borderBottomColor: `${colors.onSurface}0D` }]}>
      <Text style={[styles.rowLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <Text style={[styles.rowValue, { color: colors.onSurface }]}>{value}</Text>
    </View>
  );

  return (
    <Modal
      visible={visible}
      transparent
      animationType="slide"
      onRequestClose={onClose}
    >
      <View style={[styles.overlay, { backgroundColor: `${colors.background}CC` }]}>
        <View style={[styles.modalContent, { backgroundColor: colors.surface, borderTopColor: `${colors.onSurface}1A` }]}>
          {/* Indicador visual de Bottom Sheet */}
          <View style={[styles.handle, { backgroundColor: `${colors.onSurface}33` }]} />

          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>
              Finalizar sesión
            </Text>
            <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
              Revisa tu resumen antes de cerrar.
            </Text>
          </View>

          {/* Bloque de Resumen */}
          <View style={[styles.summaryBlock, { backgroundColor: `${colors.onSurface}0D` }]}>
            <SummaryRow 
              label="Series completadas" 
              value={`${completedSets} / ${totalSets}`} 
            />
            <SummaryRow 
              label="Ejercicios" 
              value={exerciseCount} 
            />
            <SummaryRow 
              label="Duración aprox" 
              value={`${durationMinutes} min`} 
            />
            <SummaryRow 
              label="Progreso total" 
              value={`${progressPercent}%`} 
            />
          </View>

          {/* Botonera */}
          <View style={styles.footer}>
            <View style={styles.footerButton}>
              <Button variant="secondary" onPress={onClose}>
                Cancelar
              </Button>
            </View>
            <View style={styles.footerButton}>
              <Button variant="primary" onPress={onContinue}>
                Continuar
              </Button>
            </View>
          </View>
        </View>
      </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'flex-end',
  },
  modalContent: {
    borderTopLeftRadius: 32,
    borderTopRightRadius: 32,
    padding: 24,
    paddingBottom: 40,
    borderTopWidth: 1,
  },
  handle: {
    width: 48,
    height: 4,
    borderRadius: 2,
    alignSelf: 'center',
    marginBottom: 24,
  },
  header: {
    marginBottom: 24,
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
  subtitle: {
    fontSize: 14,
    marginTop: 4,
  },
  summaryBlock: {
    borderRadius: 16,
    padding: 16,
    marginBottom: 32,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 12,
    borderBottomWidth: 1,
  },
  rowLabel: {
    fontSize: 14,
    fontWeight: '500',
  },
  rowValue: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  footer: {
    flexDirection: 'row',
  },
  footerButton: {
    flex: 1,
    marginHorizontal: 8,
  },
});
