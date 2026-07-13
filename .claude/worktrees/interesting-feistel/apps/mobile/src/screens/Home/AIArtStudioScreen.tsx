import React, { useMemo } from 'react';
import { Pressable, StyleSheet, Text, View } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { ActivityIcon } from '@/components/icons';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import type { RootTabParamList } from '@/navigation/types';
import { useColors } from '@/theme';

export function AIArtStudioScreen() {
  const colors = useColors();
  const navigation = useNavigation<BottomTabNavigationProp<RootTabParamList>>();
  const settings = useMemo(() => readStoredSettingsRaw() as any, []);
  const provider = settings?.apiProvider ?? 'gemini';
  const hasGemini = Boolean(settings?.apiKeys?.gemini);

  return (
    <ScreenShell
      title="AI Art Studio"
      subtitle="Genera y edita imágenes con IA"
    >
      <View style={styles.container}>
        {/* Blocker Banner */}
        <LiquidGlassCard style={styles.blockerBanner} padding={20}>
          <View style={styles.blockerIconRow}>
            <View style={[styles.blockerIcon, { backgroundColor: `${colors.error}15` }]}>
              <ActivityIcon size={20} color={colors.error} />
            </View>
            <Text style={[styles.blockerBadge, { color: colors.error }]}>Bloqueado</Text>
          </View>
          <Text style={[styles.blockerTitle, { color: colors.onSurface }]}>
            Generación de imagen no disponible en móvil
          </Text>
          <Text style={[styles.blockerCopy, { color: colors.onSurfaceVariant }]}>
            La PWA usa @google/generative-ai directamente para generateImage, generateImages y editImageWithText.
            En móvil, las llamadas IA pasan por el backend proxy, que no expone endpoints de imagen.
          </Text>
          <Text style={[styles.blockerCopy, { color: colors.onSurfaceVariant }]}>
            Para habilitar: agregar /api/ai/image/generate y /api/ai/image/edit al backend FastAPI.
          </Text>
        </LiquidGlassCard>

        {/* PWA Reference */}
        <LiquidGlassCard style={styles.referenceCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Funciones PWA equivalentes</Text>
          <View style={styles.functionRow}>
            <Text style={[styles.functionName, { color: colors.onSurface }]}>generateImage</Text>
            <Text style={[styles.functionStatus, { color: colors.error }]}>Backend sin endpoint</Text>
          </View>
          <View style={styles.functionRow}>
            <Text style={[styles.functionName, { color: colors.onSurface }]}>generateImages</Text>
            <Text style={[styles.functionStatus, { color: colors.error }]}>Backend sin endpoint</Text>
          </View>
          <View style={styles.functionRow}>
            <Text style={[styles.functionName, { color: colors.onSurface }]}>editImageWithText</Text>
            <Text style={[styles.functionStatus, { color: colors.error }]}>Backend sin endpoint</Text>
          </View>
          <View style={styles.functionRow}>
            <Text style={[styles.functionName, { color: colors.onSurface }]}>analyzePosturePhoto</Text>
            <Text style={[styles.functionStatus, { color: colors.error }]}>Backend sin endpoint</Text>
          </View>
          <View style={styles.functionRow}>
            <Text style={[styles.functionName, { color: colors.onSurface }]}>analyzeMealPhoto</Text>
            <Text style={[styles.functionStatus, { color: colors.error }]}>Backend sin endpoint</Text>
          </View>
        </LiquidGlassCard>

        {/* Provider Status */}
        <LiquidGlassCard style={styles.statusCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Proveedor IA</Text>
          <View style={styles.statusRow}>
            <Text style={[styles.statusLabel, { color: colors.onSurface }]}>Proveedor activo</Text>
            <Text style={[styles.statusValue, { color: colors.primary }]}>{String(provider).toUpperCase()}</Text>
          </View>
          <View style={styles.statusRow}>
            <Text style={[styles.statusLabel, { color: colors.onSurface }]}>Gemini configurado</Text>
            <Text style={[styles.statusValue, { color: hasGemini ? colors.primary : colors.onSurfaceVariant }]}>
              {hasGemini ? 'Sí' : 'No'}
            </Text>
          </View>
        </LiquidGlassCard>

        {/* Settings link */}
        <Pressable
          style={[styles.settingsButton, { borderColor: `${colors.primary}55` }]}
          onPress={() => navigation.navigate('Settings')}
        >
          <Text style={[styles.settingsButtonText, { color: colors.primary }]}>Abrir ajustes de IA</Text>
        </Pressable>
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  blockerBanner: {
    borderRadius: 28,
    gap: 10,
  },
  blockerIconRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
  },
  blockerIcon: {
    width: 36,
    height: 36,
    borderRadius: 18,
    alignItems: 'center',
    justifyContent: 'center',
  },
  blockerBadge: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
  },
  blockerTitle: {
    fontSize: 18,
    fontWeight: '800',
  },
  blockerCopy: {
    fontSize: 13,
    lineHeight: 20,
  },
  referenceCard: {
    borderRadius: 28,
  },
  eyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    marginBottom: 12,
  },
  functionRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  functionName: {
    fontSize: 13,
    fontWeight: '700',
    fontFamily: 'monospace',
  },
  functionStatus: {
    fontSize: 11,
    fontWeight: '700',
  },
  statusCard: {
    borderRadius: 28,
  },
  statusRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingVertical: 8,
  },
  statusLabel: {
    fontSize: 14,
    fontWeight: '600',
  },
  statusValue: {
    fontSize: 14,
    fontWeight: '900',
  },
  settingsButton: {
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderRadius: 999,
    paddingHorizontal: 18,
    paddingVertical: 12,
  },
  settingsButtonText: {
    fontSize: 13,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
});
