import React from 'react';
import { TouchableOpacity, Text, View, StyleSheet } from 'react-native';
import { useColors } from '@/theme';

interface WikiListItemProps {
  title: string;
  subtitle?: string;
  onPress?: () => void;
}

export const WikiListItem: React.FC<WikiListItemProps> = ({
  title,
  subtitle,
  onPress,
}) => {
  const colors = useColors();

  return (
    <TouchableOpacity
      onPress={onPress}
      activeOpacity={0.7}
      style={[styles.container, { borderColor: colors.outlineVariant }]}
    >
      <View style={styles.content}>
        <Text style={[styles.title, { color: colors.onSurface }]} numberOfLines={1}>
          {title}
        </Text>
        {subtitle && (
          <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>
            {subtitle}
          </Text>
        )}
      </View>
      <Text style={[styles.chevron, { color: colors.onSurfaceVariant, opacity: 0.4 }]}>〉</Text>
    </TouchableOpacity>
  );
};

const styles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    backgroundColor: '#1A1C24',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 16,
    marginBottom: 12,
  },
  content: {
    flex: 1,
    paddingRight: 16,
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 12,
    lineHeight: 16,
  },
  chevron: {
    fontSize: 20,
    paddingLeft: 4,
  },
});
