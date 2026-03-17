import React, { useEffect } from 'react';
import {
  NativeModules,
  Text,
  View,
  ScrollView,
  StyleSheet,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { ScreenShell } from '@/components/ScreenShell';
import { Button } from '@/components/ui';
import { useColors } from '@/theme';
import { useSettingsStore } from '@/stores/settingsStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useMealTemplateStore } from '@/stores/mealTemplateStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useCutoverStore } from '@/stores/cutoverStore';
import { useLocalAiDiagnosticsStore } from '@/stores/localAiDiagnosticsStore';
import { readStoredSettingsRaw } from '@/services/mobileDomainStateService';
import type { RootTabParamList } from '@/navigation/AppNavigator';

interface SettingRowProps {
  label: string;
  value: string;
}

function SettingRow({ label, value }: SettingRowProps) {
  const colors = useColors();

  return (
    <View style={[styles.settingRow, { borderColor: colors.outlineVariant }]}>
      <Text style={[styles.settingLabel, { color: colors.onSurfaceVariant }]}>
        {label}
      </Text>
      <Text
        numberOfLines={1}
        style={[styles.settingValue, { color: colors.onSurface }]}
      >
        {value}
      </Text>
    </View>
  );
}

export function SettingsScreen() {
  const colors = useColors();
  const navigation = useNavigation<BottomTabNavigationProp<RootTabParamList>>();
  const isInternalBuild = __DEV__ || Boolean((NativeModules as Record<string, unknown>).DevMenu);
  const status = useSettingsStore(state => state.status);
  const summary = useSettingsStore(state => state.summary);
  const notice = useSettingsStore(state => state.notice);
  const hydrateFromMigration = useSettingsStore(state => state.hydrateFromMigration);
  const toggleWorkoutReminders = useSettingsStore(state => state.toggleWorkoutReminders);
  const toggleMealReminders = useSettingsStore(state => state.toggleMealReminders);
  const toggleFallbackEnabled = useSettingsStore(state => state.toggleFallbackEnabled);
  const applyReminderPreset = useSettingsStore(state => state.applyReminderPreset);
  const clearSettingsNotice = useSettingsStore(state => state.clearNotice);
  const refreshWorkoutInfra = useWorkoutStore(state => state.refreshInfrastructure);
  const templateCount = useMealTemplateStore(state => state.templates.length);
  const discardedTemplateCount = useMealTemplateStore(state => state.discardedCount);
  const wellbeingStatus = useWellbeingStore(state => state.status);
  const wellbeingOverview = useWellbeingStore(state => state.overview);
  const wellbeingSource = useWellbeingStore(state => state.source);
  const wellbeingDroppedDailyLogs = useWellbeingStore(state => state.droppedDailyLogs);
  const localAiStatus = useLocalAiDiagnosticsStore(state => state.status);
  const cutoverStage = useCutoverStore(state => state.stage);
  const cutoverChecklist = useCutoverStore(state => state.systemChecklist);
  const cutoverSignoff = useCutoverStore(state => state.manualSignoff);
  const operationalSnapshot = useCutoverStore(state => state.operationalSnapshot);
  const cutoverCheckedAt = useCutoverStore(state => state.lastCheckedAt);
  const cutoverNotice = useCutoverStore(state => state.notice);
  const refreshCutover = useCutoverStore(state => state.refresh);
  const runOperationalSweep = useCutoverStore(state => state.runOperationalSweep);
  const toggleManualSignoff = useCutoverStore(state => state.toggleManualSignoff);
  const clearCutoverNotice = useCutoverStore(state => state.clearNotice);
  const rawSettings = readStoredSettingsRaw();

  useEffect(() => {
    if (status === 'idle') {
      void hydrateFromMigration();
    }
  }, [hydrateFromMigration, status]);

  useEffect(() => {
    void refreshCutover();
  }, [refreshCutover, summary, templateCount, wellbeingStatus]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeout = setTimeout(() => {
      clearSettingsNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearSettingsNotice, notice]);

  useEffect(() => {
    if (!cutoverNotice) return undefined;
    const timeout = setTimeout(() => {
      clearCutoverNotice();
    }, 3000);
    return () => clearTimeout(timeout);
  }, [clearCutoverNotice, cutoverNotice]);

  const renderSection = (title: string, children: React.ReactNode) => (
    <View style={[styles.section, { backgroundColor: colors.surface, borderColor: colors.outlineVariant }]}>
      <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
        {title}
      </Text>
      {children}
    </View>
  );

  const renderNotice = (message: string, type: 'success' | 'info') => {
    const borderColor = type === 'success' ? colors.batteryHigh : colors.primary;
    const bgColor = type === 'success' ? `${colors.batteryHigh}1A` : `${colors.primary}1A`;

    return (
      <View style={[styles.notice, { borderColor, backgroundColor: bgColor }]}>
        <Text style={[styles.noticeText, { color: colors.onSurface }]}>{message}</Text>
      </View>
    );
  };

  return (
    <ScreenShell
      title="Ajustes"
      subtitle="Aquí ya operamos ajustes propios de RN: recordatorios, fallback IA y señales de readiness para el relevo Android."
    >
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        <View style={styles.container}>
          {/* Profile Section */}
          {rawSettings ? (
            renderSection('Perfil', (
              <View style={styles.sectionContent}>
                <SettingRow label="Nombre" value={(rawSettings.userName as string) || 'No definido'} />
                <SettingRow label="Edad" value={rawSettings.age ? `${rawSettings.age} años` : 'No definida'} />
                <SettingRow label="Peso" value={rawSettings.weight ? `${rawSettings.weight} kg` : 'No definido'} />
                <SettingRow label="Altura" value={rawSettings.height ? `${rawSettings.height} cm` : 'No definida'} />
                <SettingRow label="Género" value={(rawSettings.gender as string) || 'No definido'} />
                <SettingRow label="Nivel actividad" value={(rawSettings.activityLevel as string) || 'No definido'} />
              </View>
            ))
          ) : null}

          {/* Status Section */}
          {renderSection('Estado', (
            <View style={styles.sectionContent}>
              <Text numberOfLines={1} style={[styles.statusTitle, { color: colors.onSurface }]}>
                {status === 'ready' ? 'Configuración lista en RN' : 'Preparando configuración'}
              </Text>
              <Text style={[styles.statusDescription, { color: colors.onSurfaceVariant }]}>
                {status === 'ready'
                  ? `Fuente ${summary?.source ?? 'sin definir'} · estos ajustes ya viven en storage propio de RN y se usan para widgets, recordatorios y fallback.`
                  : 'Cuando el bootstrap termine, aquí podremos tocar tus preferencias base directamente desde RN.'}
              </Text>
            </View>
          ))}

          {/* Notices */}
          {notice ? renderNotice(notice, 'success') : null}
          {cutoverNotice ? renderNotice(cutoverNotice, 'info') : null}

          {/* Reminders Section */}
          {summary ? (
            <>
              {renderSection('Recordatorios', (
                <View style={styles.sectionContent}>
                  <SettingRow label="Entreno diario" value={summary.remindersEnabled ? (summary.reminderTime ?? 'activo') : 'desactivado'} />
                  <SettingRow label="Desayuno" value={summary.mealRemindersEnabled ? summary.breakfastReminderTime : 'desactivado'} />
                  <SettingRow label="Almuerzo" value={summary.mealRemindersEnabled ? summary.lunchReminderTime : 'desactivado'} />
                  <SettingRow label="Cena" value={summary.mealRemindersEnabled ? summary.dinnerReminderTime : 'desactivado'} />
                  <SettingRow label="Entreno no registrado" value={summary.missedWorkoutReminderEnabled ? summary.missedWorkoutReminderTime : 'desactivado'} />
                  <SettingRow label="Batería baja" value={summary.augeBatteryReminderEnabled ? `${summary.augeBatteryReminderThreshold}% · ${summary.augeBatteryReminderTime}` : 'desactivado'} />
                </View>
              ))}

              {/* Base Preferences Section */}
              {renderSection('Preferencias base', (
                <View style={styles.sectionContent}>
                  <SettingRow label="Semana inicia" value={`día ${summary.startWeekOn}`} />
                  <SettingRow label="Proveedor IA" value={summary.apiProvider ?? 'sin definir'} />
                  <SettingRow label="Fallback IA" value={summary.fallbackEnabled ? 'activo' : 'apagado'} />
                  <SettingRow label="Modo workout" value={summary.workoutLoggerMode ?? 'sin definir'} />
                  <SettingRow label="Vista compacta" value={summary.sessionCompactView ? 'sí' : 'no'} />
                  <SettingRow label="Rest timer" value={`${rawSettings?.defaultRestSeconds ?? 90}s · ${rawSettings?.autoStartTimer ? 'auto' : 'manual'}`} />
                  <SettingRow label="Metas nutricion" value={`${rawSettings?.dailyCalorieGoal ?? '--'} kcal · ${rawSettings?.dailyProteinGoal ?? '--'}g P`} />
                  <SettingRow label="Meta sueño" value={`${rawSettings?.sleepTargetHours ?? 8}h · ${rawSettings?.wakeTimeWork ?? '--'}`} />
                  <SettingRow label="Widgets Home" value={summary.homeWidgetOrder.length > 0 ? summary.homeWidgetOrder.join(', ') : 'orden por defecto'} />
                </View>
              ))}

              {/* Migrated Modules Section */}
              {renderSection('Módulos migrados', (
                <View style={styles.sectionContent}>
                  <SettingRow label="Plantillas de comida" value={templateCount > 0 ? `${templateCount} disponibles` : 'sin plantillas aún'} />
                  <SettingRow label="Plantillas descartadas" value={discardedTemplateCount > 0 ? String(discardedTemplateCount) : 'ninguna'} />
                  <SettingRow label="Wellbeing" value={wellbeingStatus === 'ready' ? `resumen activo · ${wellbeingSource}` : 'pendiente'} />
                  <SettingRow label="Sueño promedio" value={wellbeingOverview?.averageSleepHoursLast7Days ? `${wellbeingOverview.averageSleepHoursLast7Days} h` : 'sin datos'} />
                  <SettingRow label="Agua de hoy" value={wellbeingOverview ? `${wellbeingOverview.waterTodayMl} ml` : 'sin datos'} />
                  <SettingRow label="Logs wellbeing inválidos" value={wellbeingDroppedDailyLogs > 0 ? String(wellbeingDroppedDailyLogs) : '0'} />
                  <SettingRow label="IA local" value={localAiStatus ? `${localAiStatus.engine} · ${localAiStatus.modelVersion ?? 'sin modelo'}` : 'sin revisar'} />
                </View>
              ))}

              {/* Actions Section */}
              {renderSection('Acciones', (
                <View style={styles.actionsSection}>
                  <Button
                    onPress={() => navigation.navigate('Coach')}
                    variant="secondary"
                  >
                    Coach IA
                  </Button>
                  <Button
                    onPress={() => navigation.navigate('Workout')}
                    variant="secondary"
                  >
                    Base de ejercicios (entreno)
                  </Button>
                  <Button
                    onPress={() => void toggleWorkoutReminders()}
                    variant="secondary"
                  >
                    {summary.remindersEnabled ? 'Apagar recordatorios de entreno' : 'Encender recordatorios de entreno'}
                  </Button>
                  <Button
                    onPress={() => void toggleMealReminders()}
                    variant="secondary"
                  >
                    {summary.mealRemindersEnabled ? 'Apagar recordatorios de comida' : 'Encender recordatorios de comida'}
                  </Button>
                  <Button
                    onPress={() => void toggleFallbackEnabled()}
                    variant="secondary"
                  >
                    {summary.fallbackEnabled ? 'Apagar fallback IA' : 'Encender fallback IA'}
                  </Button>
                  <Button
                    onPress={() => void applyReminderPreset('light')}
                    variant="secondary"
                  >
                    Aplicar perfil suave
                  </Button>
                  <Button
                    onPress={() => void applyReminderPreset('full')}
                    variant="secondary"
                  >
                    Aplicar perfil seguimiento
                  </Button>
                  <Button
                    onPress={() => void refreshWorkoutInfra()}
                  >
                    Volver a sincronizar widgets y recordatorios
                  </Button>
                </View>
              ))}

              {/* Cutover Section (Internal) */}
              {isInternalBuild ? (
                renderSection('Cutover Android (interno)', (
                  <View style={styles.sectionContent}>
                    <Text numberOfLines={1} style={[styles.statusTitle, { color: colors.onSurface }]}>
                      {cutoverStage === 'ready-for-cutover' ? 'Listo para relevo' : cutoverStage === 'pilot-ready' ? 'Listo para piloto' : 'Aun faltan piezas'}
                    </Text>
                    <Text style={[styles.statusDescription, { color: colors.onSurfaceVariant }]}>
                      {cutoverCheckedAt
                        ? `Última revisión ${new Date(cutoverCheckedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}`
                        : 'Todavía no revisamos el checklist de relevo.'}
                    </Text>

                    {cutoverChecklist ? (
                      <View style={styles.sectionContent}>
                        {Object.entries(cutoverChecklist).map(([key, value]) => (
                          <SettingRow
                            key={key}
                            label={key}
                            value={value ? 'ok' : 'pendiente'}
                          />
                        ))}
                      </View>
                    ) : null}

                    {operationalSnapshot ? (
                      <View style={styles.sectionContent}>
                        <SettingRow label="Permiso notificaciones" value={operationalSnapshot.notificationPermission} />
                        <SettingRow label="Bridge widgets" value={operationalSnapshot.widgetModuleAvailable ? 'nativo' : 'fallback'} />
                        <SettingRow label="Bridge background" value={operationalSnapshot.backgroundModuleAvailable ? 'nativo' : 'fallback'} />
                        <SettingRow label="Bridge migración" value={operationalSnapshot.migrationBridgeAvailable ? 'nativo' : 'fallback'} />
                        <SettingRow label="Bridge IA local" value={operationalSnapshot.localAiModuleAvailable ? 'nativo' : 'fallback'} />
                        <SettingRow label="Widget stale" value={operationalSnapshot.widgetStale ? 'sí' : 'no'} />
                        <SettingRow label="Background" value={operationalSnapshot.backgroundLastResult} />
                        <SettingRow label="Logs nutrición" value={String(operationalSnapshot.nutritionLogCount)} />
                        <SettingRow label="Plantillas" value={String(operationalSnapshot.templateCount)} />
                        <SettingRow label="Wellbeing útil" value={operationalSnapshot.wellbeingHasData ? 'sí' : 'no'} />
                      </View>
                    ) : null}

                    <View style={styles.signoffSection}>
                      <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
                        Signoff manual
                      </Text>
                      {Object.entries(cutoverSignoff).map(([key, value]) => (
                        <Button
                          key={key}
                          onPress={() => toggleManualSignoff(key as keyof typeof cutoverSignoff)}
                          variant="secondary"
                        >
                          {`${value ? 'OK' : 'Pendiente'} · ${key}`}
                        </Button>
                      ))}
                    </View>

                    <View style={styles.cutoverActions}>
                      <Button
                        onPress={() => void refreshCutover()}
                        variant="secondary"
                      >
                        Actualizar checklist de relevo
                      </Button>
                      <Button
                        onPress={() => void runOperationalSweep()}
                        variant="secondary"
                      >
                        Correr sweep operativo
                      </Button>
                    </View>
                  </View>
                ))
              ) : null}
            </>
          ) : null}
        </View>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  scrollContent: {
    paddingBottom: 40,
  },
  section: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
  },
  sectionTitle: {
    fontSize: 11,
    fontWeight: '600',
    textTransform: 'uppercase',
    letterSpacing: 2,
    marginBottom: 12,
  },
  sectionContent: {
    gap: 12,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 24,
    borderWidth: 1,
  },
  settingLabel: {
    fontSize: 13,
    flex: 1,
    marginRight: 12,
  },
  settingValue: {
    fontSize: 13,
    fontWeight: '600',
    textAlign: 'right',
    maxWidth: '45%',
  },
  statusTitle: {
    fontSize: 20,
    fontWeight: '600',
  },
  statusDescription: {
    fontSize: 14,
    lineHeight: 22,
    marginTop: 8,
  },
  notice: {
    borderRadius: 24,
    borderWidth: 1,
    padding: 16,
  },
  noticeText: {
    fontSize: 15,
    fontWeight: '500',
  },
  actionsSection: {
    gap: 12,
    marginTop: 8,
  },
  signoffSection: {
    gap: 12,
    marginTop: 16,
  },
  cutoverActions: {
    gap: 12,
    marginTop: 16,
  },
});
