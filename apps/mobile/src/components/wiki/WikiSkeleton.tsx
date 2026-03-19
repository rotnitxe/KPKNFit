import React, { useEffect } from 'react';
import { View, StyleSheet } from 'react-native';
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withRepeat,
  withTiming,
} from 'react-native-reanimated';
import { useColors } from '@/theme';

interface SkeletonProps {
  width?: number;
  height?: number;
  borderRadius?: number;
  style?: any;
}

export const Skeleton: React.FC<SkeletonProps> = ({
  width = 100,
  height = 20,
  borderRadius = 12,
  style,
}) => {
  const colors = useColors();
  const shimmer = useSharedValue(0);

  useEffect(() => {
    shimmer.value = withRepeat(
      withTiming(1, { duration: 1000 }),
      -1,
      true
    );
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: 0.5 + shimmer.value * 0.3,
  }));

  return (
    <View
      style={[
        styles.skeleton,
        { width, height, borderRadius, backgroundColor: colors.surfaceContainerHigh },
        animatedStyle,
        style,
      ]}
    />
  );
};

interface WikiCategoryCardSkeletonProps {
  count?: number;
}

export const WikiCategoryCardSkeleton: React.FC<WikiCategoryCardSkeletonProps> = ({ count = 4 }) => {
  return (
    <View style={styles.grid}>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={styles.card}>
          <Skeleton width={56} height={56} borderRadius={28} />
          <View style={styles.content}>
            <Skeleton width={120} height={22} style={styles.title} />
            <Skeleton width={80} height={14} style={styles.subtitle} />
            <Skeleton width={60} height={14} />
          </View>
          <Skeleton width={80} height={1} style={styles.footer} />
        </View>
      ))}
    </View>
  );
};

export const WikiArticleCardSkeleton: React.FC<{ count?: number }> = ({ count = 2 }) => {
  return (
    <View style={styles.list}>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={styles.articleCard}>
          <View style={styles.badges}>
            <Skeleton width={70} height={24} borderRadius={12} />
            <Skeleton width={50} height={20} borderRadius={10} />
          </View>
          <Skeleton width={200} height={22} style={styles.articleTitle} />
          <Skeleton width={180} height={18} />
          <Skeleton width={140} height={18} />
          <Skeleton width={100} height={1} style={styles.footer} />
        </View>
      ))}
    </View>
  );
};

export const WikiListItemSkeleton: React.FC<{ count?: number }> = ({ count = 4 }) => {
  return (
    <View style={styles.list}>
      {Array.from({ length: count }).map((_, i) => (
        <View key={i} style={styles.listItem}>
          <Skeleton width={40} height={40} borderRadius={20} />
          <View style={styles.listItemContent}>
            <Skeleton width={140} height={20} style={styles.listItemTitle} />
            <Skeleton width={100} height={14} />
          </View>
        </View>
      ))}
    </View>
  );
};

const styles = StyleSheet.create({
  skeleton: {
    marginBottom: 8,
  },
  grid: {
    gap: 12,
  },
  card: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 20,
    marginBottom: 12,
  },
  content: {
    flex: 1,
    marginBottom: 16,
    gap: 8,
  },
  title: {
    marginBottom: 4,
  },
  subtitle: {
    marginBottom: 4,
  },
  footer: {
    marginTop: 8,
  },
  list: {
    gap: 10,
  },
  articleCard: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
    marginBottom: 12,
    gap: 12,
  },
  badges: {
    flexDirection: 'row',
    justifyContent: 'space-between',
  },
  articleTitle: {
    marginBottom: 4,
  },
  listItem: {
    flexDirection: 'row',
    alignItems: 'center',
    borderRadius: 24,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 14,
    marginBottom: 10,
    gap: 12,
  },
  listItemContent: {
    flex: 1,
    gap: 6,
  },
  listItemTitle: {
    marginBottom: 2,
  },
});
