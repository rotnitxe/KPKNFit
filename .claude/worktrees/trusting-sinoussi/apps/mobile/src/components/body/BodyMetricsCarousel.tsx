import React from 'react';
import { ScrollView, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface BodyMetricsCarouselProps {
  weight?: number;
  bodyFat?: number;
  ffmi?: number;
}

export function BodyMetricsCarousel({ weight, bodyFat, ffmi }: BodyMetricsCarouselProps) {
  const colors = useColors();

  const metrics = [
    {
      label: 'Peso Actual',
      value: weight !== undefined ? `${weight.toFixed(1)} kg` : '--',
      color: colors.cyberCyan,
    },
    {
      label: 'Grasa Corporal',
      value: bodyFat !== undefined ? `${bodyFat.toFixed(1)}%` : '--',
      color: colors.cyberCopper,
    },
    {
      label: 'FFMI',
      value: ffmi !== undefined ? ffmi.toFixed(1) : '--',
      color: colors.cyberSuccess,
    },
  ];

  return (
    <ScrollView
      horizontal
      showsHorizontalScrollIndicator={false}
      style={styles.container}
      contentContainerStyle={styles.scrollContent}
    >
      {metrics.map((metric) => (
        <View
          key={metric.label}
          style={[styles.card, { backgroundColor: colors.surfaceContainer }]}
        >
          <Text
            style={[styles.label, { color: colors.onSurfaceVariant }]}
          >
            {metric.label}
          </Text>
          <Text
            style={[styles.value, { color: metric.color }]}
          >
            {metric.value}
          </Text>
        </View>
      ))}
    </ScrollView>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  scrollContent: {
    gap: 12,
    paddingRight: 20,
  },
  card: {
    borderRadius: 16,
    padding: 16,
    minWidth: 140,
  },
  label: {
    fontSize: 10,
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    marginBottom: 8,
  },
  value: {
    fontSize: 24,
    fontWeight: '700',
  },
});
