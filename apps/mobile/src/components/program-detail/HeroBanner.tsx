import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { useColors } from '../../theme';
import { ChevronLeftIcon } from '../icons/IconsBatchB';

interface HeroBannerProps {
  name: string;
  mode: string;
  onBack: () => void;
}

export const HeroBanner: React.FC<HeroBannerProps> = ({ name, mode, onBack }) => {
  const insets = useSafeAreaInsets();
  const colors = useColors();

  const modeLabel = {
    powerlifting: 'Powerlifting',
    hypertrophy: 'Hipertrofia',
    powerbuilding: 'Powerbuilding',
  }[mode] || mode;

  return (
    <View style={styles.container}>
      {/* Background container with fallback color */}
      <View 
        style={[StyleSheet.absoluteFill, { backgroundColor: colors.surfaceContainer }]}
      />
      
      {/* Visual background placeholder */}
      <View 
        style={[StyleSheet.absoluteFill, { backgroundColor: colors.primary, opacity: 0.2 }]}
      />

      <View style={[styles.content, { paddingTop: insets.top + 16 }]}>
        <TouchableOpacity 
          onPress={onBack}
          style={[styles.backButton, { backgroundColor: colors.surface, borderColor: `${colors.onSurface}1A` }]}
        >
          <ChevronLeftIcon color={colors.onSurface} size={24} />
        </TouchableOpacity>

        <View>
          <View 
            style={[styles.badge, { backgroundColor: colors.primary }]}
          >
            <Text style={[styles.badgeText, { color: colors.onPrimary }]}>
              {modeLabel}
            </Text>
          </View>
          <Text style={[styles.title, { color: colors.onSurface }]}>
            {name}
          </Text>
        </View>
      </View>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    height: 256,
    width: '100%',
  },
  content: {
    paddingHorizontal: 16,
    flex: 1,
    justifyContent: 'space-between',
    paddingBottom: 24,
  },
  backButton: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
    borderWidth: 1,
  },
  badge: {
    alignSelf: 'flex-start',
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: 4,
    marginBottom: 8,
  },
  badgeText: {
    fontSize: 10,
    fontWeight: 'bold',
    textTransform: 'uppercase',
  },
  title: {
    fontSize: 24,
    fontWeight: 'bold',
  },
});
