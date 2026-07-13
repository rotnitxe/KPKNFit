import React from 'react';
import { View, Text, Pressable, StyleSheet } from 'react-native';
import { useColors, useTheme } from '@/theme';
import { MealIcon } from '@/components/icons';

export function NutritionSummaryCard({ onPress }: { onPress: () => void }) {
  const colors = useColors();
  const { isDark } = useTheme();

  const bg = isDark ? 'rgba(255,255,255,0.05)' : 'rgba(255,255,255,0.4)';
  const border = isDark ? 'rgba(255,255,255,0.1)' : 'rgba(0,0,0,0.03)';

  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.card,
        {
          backgroundColor: bg,
          borderColor: border,
          shadowColor: '#000',
          shadowOffset: { width: 0, height: 2 },
          shadowOpacity: isDark ? 0 : 0.06,
          shadowRadius: 8,
          elevation: isDark ? 0 : 2,
        },
        pressed && { opacity: 0.95 },
      ]}
    >
      <View style={styles.header}>
        <View style={[styles.iconWrap, { backgroundColor: isDark ? 'rgba(255,255,255,0.1)' : '#ECE6F0' }]}>
          <MealIcon size={24} color={isDark ? 'rgba(255,255,255,0.4)' : 'rgba(73,69,79,0.5)'} />
        </View>
        <View style={styles.headerText}>
          <Text style={[styles.title, { color: isDark ? 'white' : '#1D1B20' }]}>Nutrición</Text>
          <Text style={[styles.subtitle, { color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(73,69,79,0.5)' }]}>
            Toca para ver tu plan alimenticio y registrar comidas
          </Text>
        </View>
      </View>

      <View style={styles.macrosWrap}>
        {[
          { label: 'Cal', color: '#6750A4' },
          { label: 'Prot', color: '#B3261E' },
          { label: 'Carb', color: '#D0BCFF' },
          { label: 'Fat', color: '#006A6A' },
        ].map(m => (
          <View key={m.label} style={styles.macroRow}>
            <View style={styles.macroHeader}>
              <Text style={[styles.macroLabel, { color: isDark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)' }]}>
                {m.label}
              </Text>
            </View>
            <View style={[styles.barBg, { backgroundColor: isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.05)' }]}>
              <View style={[styles.barFill, { width: '0%', backgroundColor: m.color }]} />
            </View>
          </View>
        ))}
      </View>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  card: {
    borderRadius: 32,
    borderWidth: 1,
    padding: 24,
    gap: 16,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  iconWrap: {
    width: 48,
    height: 48,
    borderRadius: 16,
    alignItems: 'center',
    justifyContent: 'center',
  },
  headerText: {
    flex: 1,
    gap: 2,
  },
  title: {
    fontSize: 16,
    fontWeight: '900',
  },
  subtitle: {
    fontSize: 11,
    fontWeight: '500',
  },
  macrosWrap: {
    gap: 10,
  },
  macroRow: {
    gap: 6,
  },
  macroHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  macroLabel: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  barBg: {
    width: '100%',
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
  },
  barFill: {
    height: '100%',
    borderRadius: 2,
  },
});
