import React from 'react';
import {
  ScrollView,
  Text,
  View,
  Pressable,
  StyleSheet,
  StyleProp,
  ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useColors, useIsDark } from '../theme';
import { useNavigation } from '@react-navigation/native';
import { Canvas, LinearGradient, Rect, vec, Circle, Blur } from '@shopify/react-native-skia';

import { CaupolicanIcon } from './CaupolicanIcon';

interface ScreenShellProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
  showBack?: boolean;
  showLogo?: boolean;
  headerContent?: React.ReactNode;
  contentContainerStyle?: StyleProp<ViewStyle>;
}

export function ScreenShell({ 
  title, 
  subtitle, 
  children, 
  showBack = true,
  showLogo = false,
  headerContent,
  contentContainerStyle,
}: ScreenShellProps) {
  const colors = useColors();
  const isDark = useIsDark();
  const navigation = useNavigation();

  const gradientColors = isDark
    ? [colors.background, '#1A1820', '#151418']
    : ['#FEF7FF', '#F8F1FB', '#FFFFFF'];
  const primaryBlob = isDark ? 'rgba(167, 146, 255, 0.15)' : 'rgba(103, 80, 164, 0.10)';
  const secondaryBlob = isDark ? 'rgba(239, 184, 200, 0.10)' : 'rgba(208, 188, 255, 0.20)';

  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      {/* Background Decor Layer */}
      <View style={StyleSheet.absoluteFill}>
        <Canvas style={{ flex: 1 }}>
          {/* Main Gradient Background */}
          <Rect x={0} y={0} width={1000} height={2000}>
            <LinearGradient
               start={vec(0, 0)}
               end={vec(0, 1000)}
               colors={gradientColors}
            />
          </Rect>
          
          {/* Subtle Glowing Blobs */}
          <Circle cx={0} cy={0} r={300} color={primaryBlob}>
             <Blur blur={isDark ? 80 : 110} />
          </Circle>
          <Circle cx={400} cy={100} r={220} color={secondaryBlob}>
             <Blur blur={isDark ? 60 : 90} />
          </Circle>
        </Canvas>
      </View>

      <SafeAreaView style={{ flex: 1 }}>
        {headerContent ? (
          headerContent
        ) : (
          <View style={styles.header}>
            {showBack && navigation.canGoBack() && (
              <Pressable
                onPress={() => navigation.goBack()}
                style={[styles.backButton, { backgroundColor: `${colors.onSurface}0D` }]}
              >
                <Text style={{ color: colors.primary, fontSize: 20 }}>←</Text>
              </Pressable>
            )}
            <View style={styles.titleContainer}>
              <Text
                numberOfLines={1}
                style={[styles.title, { color: colors.onSurface }]}
              >
                {title}
              </Text>
              {subtitle ? (
                <Text
                  numberOfLines={1}
                  style={[styles.subtitle, { color: colors.onSurfaceVariant }]}
                >
                  {subtitle}
                </Text>
              ) : null}
            </View>
            {showLogo && (
              <CaupolicanIcon size={32} color={colors.primary} style={{ opacity: 0.8 }} />
            )}
          </View>
        )}

        <ScrollView
          style={styles.scrollView}
          contentContainerStyle={[styles.scrollContent, contentContainerStyle]}
          keyboardShouldPersistTaps="handled"
        >
          {children}
        </ScrollView>
      </SafeAreaView>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 20,
    paddingVertical: 12,
  },
  backButton: {
    height: 40,
    width: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    marginRight: 16,
  },
  titleContainer: {
    flex: 1,
  },
  title: {
    fontSize: 24,
    fontWeight: '700',
  },
  subtitle: {
    fontSize: 13,
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  scrollView: {
    flex: 1,
  },
  scrollContent: {
    padding: 20,
    paddingBottom: 40,
  },
});
