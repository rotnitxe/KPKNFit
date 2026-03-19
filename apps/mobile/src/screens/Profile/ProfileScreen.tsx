import React, { useEffect, useMemo, useState } from 'react';
import { Alert, Pressable, ScrollView, StyleSheet, Text, View, TextInput, TouchableOpacity } from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { ScreenShell } from '@/components/ScreenShell';
import { CaupolicanIcon } from '@/components/CaupolicanIcon';
import { BodyMetricsCarousel } from '@/components/body/BodyMetricsCarousel';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { ProfilePictureModal } from '@/components/profile/ProfilePictureModal';
import { OnThisDayCard } from '@/components/analytics/OnThisDayCard';
import {
  ActivityIcon,
  CameraIcon,
  EditIcon,
  InfoIcon,
  MoonIcon,
  UserBadgeIcon,
  ChevronRightIcon,
  DumbbellIcon,
  TrophyIcon,
  RulerIcon,
} from '@/components/icons';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import { useBodyStore } from '@/stores/bodyStore';
import { useSettingsStore } from '@/stores/settingsStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useColors } from '@/theme';
import type { ProfileStackParamList } from '@/navigation/types';

function computeFfmi(weightKg?: number, bodyFatPct?: number, heightCm?: number) {
  if (!weightKg || !bodyFatPct || !heightCm) return null;
  const leanMassKg = weightKg * (1 - bodyFatPct / 100);
  const heightMeters = heightCm / 100;
  if (!heightMeters) return null;
  return leanMassKg / (heightMeters * heightMeters);
}

function athleteTier(ffmi: number | null) {
  if (!ffmi) return 'Perfil general';
  if (ffmi >= 24) return 'Élite';
  if (ffmi >= 22) return 'Avanzado';
  if (ffmi >= 20) return 'Intermedio';
  return 'Base';
}

function StatBox({
  label,
  value,
  unit,
}: {
  label: string;
  value: string;
  unit?: string;
}) {
  const colors = useColors();
  return (
    <LiquidGlassCard style={styles.statBox} padding={16}>
      <Text style={[styles.statLabel, { color: colors.onSurfaceVariant }]}>{label}</Text>
      <View style={styles.statValueRow}>
        <Text style={[styles.statValue, { color: colors.onSurface }]}>{value}</Text>
        {unit ? <Text style={[styles.statUnit, { color: colors.onSurfaceVariant }]}>{unit}</Text> : null}
      </View>
    </LiquidGlassCard>
  );
}

export function ProfileScreen() {
  const navigation = useNavigation<NativeStackNavigationProp<ProfileStackParamList>>();
  const colors = useColors();
  const settingsSummary = useSettingsStore(state => state.summary);
  const rawSettings = settingsSummary ?? (readStoredSettingsRaw() as any);
  const updateSettings = useSettingsStore((state: any) => state.updateSettings);

  const [isEditing, setIsEditing] = useState(false);
  const [editName, setEditName] = useState<string>(String(rawSettings.username || ''));
  const [editWeight, setEditWeight] = useState<string>(String(rawSettings.userVitals?.weight ?? ''));
  const [editHeight, setEditHeight] = useState<string>(String(rawSettings.userVitals?.height ?? ''));
  const [editBodyFat, setEditBodyFat] = useState<string>(String(rawSettings.userVitals?.bodyFatPercentage ?? ''));
  const [editTargetWeight, setEditTargetWeight] = useState<string>(String(rawSettings.userVitals?.targetWeight ?? ''));
  const [showPictureModal, setShowPictureModal] = useState(false);

  const settingsStatus = useSettingsStore(state => state.status);
  const hydrateSettings = useSettingsStore(state => state.hydrateFromMigration);

  const bodyStatus = useBodyStore(state => state.status);
  const bodyProgress = useBodyStore(state => state.bodyProgress);
  const bodyLabAnalysis = useBodyStore(state => state.bodyLabAnalysis);
  const hydrateBody = useBodyStore(state => state.hydrateFromMigration);

  const wellbeingStatus = useWellbeingStore(state => state.status);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);

  useEffect(() => {
    if (settingsStatus === 'idle') void hydrateSettings();
    if (bodyStatus === 'idle') void hydrateBody();
    if (wellbeingStatus === 'idle') void hydrateWellbeing();
  }, [bodyStatus, hydrateBody, hydrateSettings, settingsStatus, hydrateWellbeing, wellbeingStatus]);

  const latestBody = bodyProgress[0];
  const ffmi = useMemo(
    () =>
      computeFfmi(
        latestBody?.weight,
        latestBody?.bodyFatPercentage,
        typeof rawSettings.userVitals?.height === 'number' ? rawSettings.userVitals.height : undefined,
      ),
    [latestBody?.bodyFatPercentage, latestBody?.weight, rawSettings.userVitals?.height],
  );

  const athleteName =
    typeof rawSettings.username === 'string' && rawSettings.username.trim() !== ''
      ? rawSettings.username.trim()
      : 'Atleta KPKN';
  const subtitle = athleteTier(ffmi);

  const handleSaveEdit = async () => {
    await updateSettings({
      username: editName.trim() || undefined,
      userVitals: {
        ...rawSettings.userVitals,
        weight: editWeight.trim() !== '' ? Number(editWeight) : undefined,
        height: editHeight.trim() !== '' ? Number(editHeight) : undefined,
        bodyFatPercentage: editBodyFat.trim() !== '' ? Number(editBodyFat) : undefined,
        targetWeight: editTargetWeight.trim() !== '' ? Number(editTargetWeight) : undefined,
      },
    });
    setIsEditing(false);
  };

  const handleOpenEdit = () => {
    setEditName(String(rawSettings.username || ''));
    setEditWeight(String(rawSettings.userVitals?.weight ?? ''));
    setEditHeight(String(rawSettings.userVitals?.height ?? ''));
    setEditBodyFat(String(rawSettings.userVitals?.bodyFatPercentage ?? ''));
    setEditTargetWeight(String(rawSettings.userVitals?.targetWeight ?? ''));
    setIsEditing(true);
  };

  const handleOpenPictureModal = () => {
    setShowPictureModal(true);
  };

  const headerContent = (
    <View style={styles.header}>
      <View style={styles.badgeRow}>
        <UserBadgeIcon size={18} color={colors.primary} />
        <Text style={[styles.headerEyebrow, { color: colors.onSurfaceVariant }]}>Perfil de Atleta</Text>
      </View>
      <Text style={[styles.headerTitle, { color: colors.onSurface }]}>{athleteName}</Text>
      <Text style={[styles.headerSubtitle, { color: colors.onSurfaceVariant }]}>
        {subtitle}
      </Text>
    </View>
  );

  return (
    <ScreenShell
      title={athleteName}
      showBack={false}
      headerContent={headerContent}
      contentContainerStyle={styles.shellContent}
    >
      <View style={styles.container}>
        <LiquidGlassCard style={styles.heroCard} padding={22}>
          <View style={styles.heroTop}>
            <Pressable onPress={handleOpenPictureModal} style={[styles.avatarCircle, { backgroundColor: `${colors.onSurface}10` }]}>
              <CaupolicanIcon size={44} color={colors.primary} />
            </Pressable>

            <View style={styles.heroActions}>
              <Pressable onPress={handleOpenPictureModal} style={[styles.heroAction, { backgroundColor: `${colors.onSurface}10` }]}>
                <CameraIcon size={18} color={colors.onSurfaceVariant} />
              </Pressable>
              <Pressable onPress={isEditing ? handleSaveEdit : handleOpenEdit} style={[styles.heroAction, { backgroundColor: `${colors.onSurface}10` }]}>
                <EditIcon size={18} color={colors.onSurfaceVariant} />
              </Pressable>
            </View>
          </View>

          {isEditing ? (
            <View style={styles.editSection}>
              <View style={styles.editGrid}>
                <TextInput
                  style={[styles.editInput, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outline }]}
                  value={editName}
                  onChangeText={(text: string) => setEditName(text)}
                  placeholder="Tu nombre"
                  placeholderTextColor={colors.onSurfaceVariant}
                />
                <TextInput
                  style={[styles.editInput, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outline }]}
                  value={editWeight}
                  onChangeText={setEditWeight}
                  placeholder="Peso kg"
                  placeholderTextColor={colors.onSurfaceVariant}
                  keyboardType="decimal-pad"
                />
                <TextInput
                  style={[styles.editInput, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outline }]}
                  value={editHeight}
                  onChangeText={setEditHeight}
                  placeholder="Altura cm"
                  placeholderTextColor={colors.onSurfaceVariant}
                  keyboardType="decimal-pad"
                />
                <TextInput
                  style={[styles.editInput, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outline }]}
                  value={editBodyFat}
                  onChangeText={setEditBodyFat}
                  placeholder="% Grasa"
                  placeholderTextColor={colors.onSurfaceVariant}
                  keyboardType="decimal-pad"
                />
                <TextInput
                  style={[styles.editInput, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outline }]}
                  value={editTargetWeight}
                  onChangeText={setEditTargetWeight}
                  placeholder="Peso objetivo"
                  placeholderTextColor={colors.onSurfaceVariant}
                  keyboardType="decimal-pad"
                />
              </View>
            </View>
          ) : (
            <Text style={[styles.heroTier, { color: colors.primary }]}>{subtitle}</Text>
          )}
          
          <Text style={[styles.heroSummary, { color: colors.onSurfaceVariant }]}>
            {bodyLabAnalysis?.profileSummary ||
              'Este panel va a concentrar la misma identidad atlética de la PWA: composición corporal, recuperación, progreso y contexto del atleta.'}
          </Text>
        </LiquidGlassCard>

        <BodyMetricsCarousel
          weight={latestBody?.weight}
          bodyFat={latestBody?.bodyFatPercentage}
          ffmi={ffmi || undefined}
        />

        <OnThisDayCard />

        <View style={styles.statsGrid}>
          <StatBox label="Peso" value={latestBody?.weight ? `${latestBody.weight}` : '--'} unit="kg" />
          <StatBox label="% Grasa" value={latestBody?.bodyFatPercentage ? `${latestBody.bodyFatPercentage}` : '--'} unit="%" />
          <StatBox label="FFMI" value={ffmi ? ffmi.toFixed(1) : '--'} />
          <StatBox
            label="Sueño"
            value={
              wellbeingOverview?.averageSleepHoursLast7Days
                ? `${wellbeingOverview.averageSleepHoursLast7Days}`
                : '--'
            }
            unit="h"
          />
        </View>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <InfoIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Resumen rápido</Text>
          </View>
          <View style={styles.detailGrid}>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.primary }]}>
                {typeof rawSettings.userVitals?.height === 'number' ? `${rawSettings.userVitals.height} cm` : '--'}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Altura</Text>
            </View>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.secondary }]}>
                {wellbeingOverview?.waterTodayMl ? `${wellbeingOverview.waterTodayMl} ml` : '--'}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Agua hoy</Text>
            </View>
            <View style={styles.detailItem}>
              <Text style={[styles.detailValue, { color: colors.tertiary }]}>
                {wellbeingOverview?.pendingTaskCount ?? 0}
              </Text>
              <Text style={[styles.detailLabel, { color: colors.onSurfaceVariant }]}>Tareas</Text>
            </View>
          </View>
        </LiquidGlassCard>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <MoonIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Secciones</Text>
          </View>

          <View style={styles.navSection}>
            <Pressable style={[styles.navButton, { backgroundColor: colors.surfaceContainer }]} onPress={() => navigation.navigate('AthleteID' as any)}>
              <View style={styles.navButtonContent}>
                <UserBadgeIcon size={20} color={colors.primary} />
                <Text style={[styles.navButtonText, { color: colors.onSurface }]}>Perfil de Atleta</Text>
              </View>
              <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
            </Pressable>
            
            <Pressable style={[styles.navButton, { backgroundColor: colors.surfaceContainer }]} onPress={() => navigation.navigate('PersonalRecords' as any)}>
              <View style={styles.navButtonContent}>
                <TrophyIcon size={20} color={colors.secondary} />
                <Text style={[styles.navButtonText, { color: colors.onSurface }]}>Records Personales</Text>
              </View>
              <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
            </Pressable>
            
            <Pressable style={[styles.navButton, { backgroundColor: colors.surfaceContainer }]} onPress={() => navigation.navigate('BodyProgress')}>
              <View style={styles.navButtonContent}>
                <RulerIcon size={20} color={colors.tertiary} />
                <Text style={[styles.navButtonText, { color: colors.onSurface }]}>Progreso Corporal</Text>
              </View>
              <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
            </Pressable>
            
            <Pressable style={[styles.navButton, { backgroundColor: colors.surfaceContainer }]} onPress={() => navigation.navigate('BodyLab' as any)}>
              <View style={styles.navButtonContent}>
                <DumbbellIcon size={20} color={colors.error} />
                <Text style={[styles.navButtonText, { color: colors.onSurface }]}>Body Lab</Text>
              </View>
              <ChevronRightIcon size={20} color={colors.onSurfaceVariant} />
            </Pressable>
          </View>
        </LiquidGlassCard>

        <LiquidGlassCard style={styles.detailCard} padding={20}>
          <View style={styles.detailHeader}>
            <ActivityIcon size={16} color={colors.onSurfaceVariant} />
            <Text style={[styles.detailEyebrow, { color: colors.onSurfaceVariant }]}>Estado actual</Text>
          </View>
          <Text style={[styles.statusCopy, { color: colors.onSurfaceVariant }]}>
            Seguimos cerrando la paridad 1:1 del Perfil de Atleta. Lo importante acá es que ya no es una maqueta: este panel consume stores reales de cuerpo y wellbeing.
          </Text>
        </LiquidGlassCard>
      </View>

      <ProfilePictureModal
        visible={showPictureModal}
        onClose={() => setShowPictureModal(false)}
        onSelectCamera={async () => {
          await updateSettings({ profilePicture: 'camera_placeholder' });
        }}
        onSelectGallery={async () => {
          await updateSettings({ profilePicture: 'gallery_placeholder' });
        }}
        currentPicture={rawSettings.profilePicture as string | null | undefined}
      />
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  shellContent: {
    paddingTop: 8,
  },
  header: {
    paddingHorizontal: 24,
    paddingTop: 12,
    paddingBottom: 8,
  },
  badgeRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    marginBottom: 10,
  },
  headerEyebrow: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 2,
  },
  headerTitle: {
    fontSize: 28,
    fontWeight: '900',
    letterSpacing: -0.9,
  },
  headerSubtitle: {
    marginTop: 6,
    fontSize: 12,
    lineHeight: 18,
  },
  container: {
    gap: 18,
  },
  heroCard: {
    borderRadius: 32,
  },
  heroTop: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  },
  avatarCircle: {
    width: 88,
    height: 88,
    borderRadius: 44,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroActions: {
    flexDirection: 'row',
    gap: 10,
  },
  heroAction: {
    width: 42,
    height: 42,
    borderRadius: 21,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroTier: {
    marginTop: 18,
    fontSize: 20,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  heroSummary: {
    marginTop: 10,
    fontSize: 14,
    lineHeight: 22,
  },
  editSection: {
    marginTop: 16,
  },
  editGrid: {
    gap: 10,
  },
  editInput: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    fontWeight: '600',
  },
  navSection: {
    marginTop: 8,
  },
  navButton: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 14,
    paddingHorizontal: 16,
    borderRadius: 16,
    marginBottom: 8,
  },
  navButtonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 12,
  },
  navButtonText: {
    fontSize: 15,
    fontWeight: '600',
  },
  statsGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  statBox: {
    width: '47%',
    borderRadius: 24,
  },
  statLabel: {
    fontSize: 9,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  statValueRow: {
    marginTop: 10,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: 4,
  },
  statValue: {
    fontSize: 26,
    fontWeight: '900',
    letterSpacing: -0.8,
  },
  statUnit: {
    fontSize: 11,
    fontWeight: '700',
    marginBottom: 4,
  },
  detailCard: {
    borderRadius: 28,
  },
  detailHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  detailEyebrow: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  detailGrid: {
    marginTop: 14,
    flexDirection: 'row',
    justifyContent: 'space-between',
    gap: 12,
  },
  detailItem: {
    flex: 1,
  },
  detailValue: {
    fontSize: 21,
    fontWeight: '900',
  },
  detailLabel: {
    marginTop: 4,
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.1,
  },
  buttonColumn: {
    marginTop: 14,
    gap: 12,
  },
  statusCopy: {
    marginTop: 12,
    fontSize: 13,
    lineHeight: 20,
  },
});
