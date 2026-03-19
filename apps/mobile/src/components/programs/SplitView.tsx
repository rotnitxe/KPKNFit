import React from 'react';
import { View, Text, StyleSheet, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';

interface MuscleDayGrid {
  day: string;
  muscles: string[];
  freq: number;
}

export interface SplitViewProps {
  days: MuscleDayGrid[];
  onEditSplit?: () => void;
}

const dayNames = ['Lunes','Martes','Miércoles','Jueves','Viernes','Sábado','Domingo'];

const SplitView: React.FC<SplitViewProps> = ({ days, onEditSplit }) => {
  const colors = useColors();
  return (
    <View style={styles.container}>
      <View style={styles.gridRow}>
        {days.map((col, idx) => (
          <View key={col.day} style={styles.dayCell}>
            <Text style={[styles.dayName, { color: colors.primary }]}>{col.day}</Text>
            <View style={styles.muscleList}>
              {col.muscles.map((m, i) => (
                <Text key={i} style={[styles.muscle, { color: colors.onSurface }]}>{m}</Text>
              ))}
            </View>
            <Text style={[styles.freq, { color: colors.onSurfaceVariant }]}>x{col.freq}</Text>
          </View>
        ))}
      </View>
      <TouchableOpacity onPress={onEditSplit} style={[styles.editBtn, { backgroundColor: colors.secondary }] }>
        <Text style={[styles.editBtnTxt, { color: colors.onSecondary }]}>Editar Split</Text>
      </TouchableOpacity>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    padding: 15,
  },
  gridRow: {
    flexDirection: 'row',
    gap: 6,
    marginBottom: 18,
    justifyContent: 'space-between',
  },
  dayCell: {
    backgroundColor: 'rgba(160,160,255,0.07)',
    borderRadius: 10,
    alignItems: 'center',
    minWidth: 46,
    padding: 8,
    flex: 1,
    marginHorizontal: 2,
  },
  dayName: {
    fontWeight: '800',
    fontSize: 11,
    marginBottom: 4,
  },
  muscleList: {
    gap: 1,
    marginBottom: 4,
  },
  muscle: {
    fontSize: 11,
    fontWeight: '500',
  },
  freq: {
    fontSize: 10,
    fontWeight: '600',
  },
  editBtn: {
    marginTop: 12,
    alignSelf: 'center',
    borderRadius: 16,
    paddingHorizontal: 22,
    paddingVertical: 7,
  },
  editBtnTxt: {
    fontWeight: '900',
    fontSize: 13,
    letterSpacing: 1,
  },
});

export default React.memo(SplitView);
