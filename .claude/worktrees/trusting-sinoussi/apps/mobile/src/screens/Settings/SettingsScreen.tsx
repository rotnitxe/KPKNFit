import React, { useEffect } from 'react';
import {
  NativeModules,
  Text,
  View,
  ScrollView,
  Pressable,
  StyleSheet,
} from 'react-native';
import { useNavigation } from '@react-navigation/native';
import type { BottomTabNavigationProp } from '@react-navigation/bottom-tabs';
import { ScreenShell } from '@/components/ScreenShell';
import { Button, ToggleSwitch } from '@/components/ui';
import { useColors } from '@/theme';
import { useSettingsStore } from '@/stores/settingsStore';
import { useWorkoutStore } from '@/stores/workoutStore';
import { useMealTemplateStore } from '@/stores/mealTemplateStore';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useCutoverStore } from '@/stores/cutoverStore';
import { useLocalAiDiagnosticsStore } from '@/stores/localAiDiagnosticsStore';
import type { RootTabParamList } from '@/navigation/types';
import type { Settings } from '@/types/settings';

interface SettingRowProps {
  label: string;
  value: string;
}

interface ChoiceChipProps {
  label: string;
  selected: boolean;
  onPress: () => void;
}

interface ControlRowProps {
  label: string;
  description?: string;
  children: React.ReactNode;
}

function SettingRow({ label, value }: SettingRowProps) {
  const colors = useColors();

  return (
    <View style={styles.settingRow}>
      <Text style={[styles.settingLabel, { color: colors.onSurface }]}>
        {label}
      </Text>
      <Text
        numberOfLines={1}
        style={[styles.settingValue, { color: colors.onSurfaceVariant }]}
      >
        {value}
      </Text>
    </View>
  );
}

function ChoiceChip({ label, selected, onPress }: ChoiceChipProps) {
  const colors = useColors();

  return (
    <Pressable
      accessibilityRole="button"
      onPress={onPress}
      style={[
        styles.choiceChip,
        {
          borderColor: selected ? colors.primary : colors.outlineVariant,
          backgroundColor: selected ? `${colors.primary}1A` : 'transparent',
        },
      ]}
    >
      <Text
        style={[
          styles.choiceChipText,
          { color: selected ? colors.primary : colors.onSurfaceVariant },
        ]}
      >
        {label}
      </Text>
    </Pressable>
  );
}

function ControlRow({ label, description, children }: ControlRowProps) {
  const colors = useColors();

  return (
    <View style={styles.controlRow}>
      <View style={styles.controlText}>
        <Text style={[styles.controlLabel, { color: colors.onSurface }]}>{label}</Text>
        {description ? (
          <Text style={[styles.controlDescription, { color: colors.onSurfaceVariant }]}>
            {description}
          </Text>
        ) : null}
      </View>
      {children}
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
  const updateSettings = useSettingsStore(state => state.updateSettings);
  const getSettings = useSettingsStore(state => state.getSettings);
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
  const settings = (summary ?? getSettings()) as Settings;
  const userVitals = settings.userVitals ?? {};
  const remindersEnabled = Boolean(settings.remindersEnabled);
  const mealRemindersEnabled = Boolean(settings.mealRemindersEnabled);
  const fallbackEnabled = Boolean(settings.fallbackEnabled);
  const selectedTheme = (settings.appTheme ?? 'default') as Settings['appTheme'];
  const selectedTabBarStyle = settings.tabBarStyle ?? 'default';
  const selectedWeightUnit = settings.weightUnit ?? 'kg';
  const selectedRestTimerSeconds = settings.restTimerDefaultSeconds ?? 90;
  const selectedRestTimerAutoStart = settings.restTimerAutoStart ?? true;

  useEffect(() => {
    if (status === 'idle') {
      void hydrateFromMigration();
    }
  }, [hydrateFromMigration, status]);

  useEffect(() => {
    if (status === 'ready') {
      void refreshCutover();
    }
  }, [refreshCutover, status, templateCount, wellbeingStatus]);

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
      {title ? (
        <View style={styles.sectionHeader}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>
            {title}
          </Text>
        </View>
      ) : null}
      <View style={styles.sectionBody}>
        {children}
      </View>
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

  const isLoading = status !== 'ready';

  const renderHydratingState = () => (
    <View style={styles.hydratingContainer}>
      <Text style={[styles.hydratingTitle, { color: colors.onSurface }]}>
        Cargando preferencias...
      </Text>
      <Text style={[styles.hydratingDescription, { color: colors.onSurfaceVariant }]}>
        Estamos preparando tus ajustes. Esto toma un momento.
      </Text>
    </View>
  );

  return (
    <ScreenShell
      title="Ajustes"
      subtitle="Personaliza tu experiencia"
    >
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={styles.scrollContent}
      >
        <View style={styles.container}>
          {isLoading ? renderHydratingState() : null}

          {/* Notices */}
          {notice ? renderNotice(notice, 'success') : null}
          {cutoverNotice ? renderNotice(cutoverNotice, 'info') : null}

          {/* Profile Section — only visible once the store is hydrated */}
          {settings && status === 'ready' ? (
            renderSection('Perfil', (
              <View style={styles.sectionContent}>
                <SettingRow label="Nombre" value={(settings.username ?? 'Atleta') as string} />
                <SettingRow label="Edad" value={userVitals.age ? `${userVitals.age} años` : 'Sin definir'} />
                <SettingRow label="Peso" value={userVitals.weight ? `${userVitals.weight} kg` : 'Sin definir'} />
                <SettingRow label="Altura" value={userVitals.height ? `${userVitals.height} cm` : 'Sin definir'} />
                <SettingRow label="Género" value={(userVitals.gender as string) || 'Sin definir'} />
                <SettingRow label="Nivel de actividad" value={(userVitals.activityLevel as string) || 'Sin definir'} />
              </View>
            ))
          ) : null}

          {renderSection('Apariencia', (
            <View style={styles.sectionContent}>
              <ControlRow
                label="Tema"
                description="Apariencia visual de la app"
              >
                <View style={styles.choiceGrid}>
                  {([
                    ['default', 'Predeterminado'],
                    ['dark', 'Oscuro'],
                    ['light', 'Claro'],
                    ['deep-black', 'Deep Black'],
                    ['volt', 'Volt'],
                  ] as const).map(([value, label]) => (
                    <ChoiceChip
                      key={value}
                      label={label}
                      selected={selectedTheme === value}
                      onPress={() => void updateSettings({ appTheme: value })}
                    />
                  ))}
                </View>
              </ControlRow>
            </View>
          ))}

          {renderSection('Navegación', (
            <View style={styles.sectionContent}>
              <ControlRow
                label="Estilo barra inferior"
                description="Máximo 4 pestañas visibles"
              >
                <View style={styles.choiceGrid}>
                  {([
                    ['default', 'Predeterminado'],
                    ['compact', 'Compacto'],
                    ['icons-only', 'Solo íconos'],
                  ] as const).map(([value, label]) => (
                    <ChoiceChip
                      key={value}
                      label={label}
                      selected={selectedTabBarStyle === value}
                      onPress={() => void updateSettings({ tabBarStyle: value })}
                    />
                  ))}
                </View>
              </ControlRow>
            </View>
          ))}

          {/* Reminders Section */}
          {renderSection('Recordatorios', (
                <View style={styles.sectionContent}>
                  <ControlRow
                    label="Sesión del día"
                    description={settings.remindersEnabled ? `Diario a las ${settings.reminderTime ?? '--'}` : 'Desactivado'}
                  >
                    <ToggleSwitch
                      checked={remindersEnabled}
                      onChange={() => void toggleWorkoutReminders()}
                      testID="toggle-workout-reminders"
                    />
                  </ControlRow>
                  <ControlRow
                    label="Comidas"
                    description={settings.mealRemindersEnabled ? `Desayuno ${settings.breakfastReminderTime ?? '--'} · Almuerzo ${settings.lunchReminderTime ?? '--'} · Cena ${settings.dinnerReminderTime ?? '--'}` : 'Desactivado'}
                  >
                    <ToggleSwitch
                      checked={mealRemindersEnabled}
                      onChange={() => void toggleMealReminders()}
                      testID="toggle-meal-reminders"
                    />
                  </ControlRow>
                  <ControlRow
                    label="IA sin conexión"
                    description={settings.fallbackEnabled ? 'Activo' : 'Apagado'}
                  >
                    <ToggleSwitch
                      checked={fallbackEnabled}
                      onChange={() => void toggleFallbackEnabled()}
                      testID="toggle-fallback-ai"
                    />
                  </ControlRow>
                  <SettingRow label="Entrenamiento no registrado" value={settings.missedWorkoutReminderEnabled ? `Aviso a las ${settings.missedWorkoutReminderTime}` : 'Desactivado'} />
                  <SettingRow label="Batería AUGE baja" value={settings.augeBatteryReminderEnabled ? `Aviso al ${settings.augeBatteryReminderThreshold}% · ${settings.augeBatteryReminderTime}` : 'Desactivado'} />
                </View>
          ))}

          {renderSection('Entreno', (
                <View style={styles.sectionContent}>
                  <ControlRow
                    label="Unidad de peso"
                    description="Afecta cálculos y placas sugeridas"
                  >
                    <View style={styles.choiceGrid}>
                       {([
                        ['kg', 'KG'],
                        ['lbs', 'LBS'],
                      ] as const).map(([value, label]) => (
                        <ChoiceChip
                          key={value}
                          label={label}
                          selected={selectedWeightUnit === value}
                          onPress={() => void updateSettings({ weightUnit: value })}
                        />
                      ))}
                    </View>
                  </ControlRow>
                  <SettingRow label="Descanso por defecto" value={`${selectedRestTimerSeconds}s · ${selectedRestTimerAutoStart ? 'auto' : 'manual'}`} />
                    <SettingRow label="Vista compacta" value={settings.sessionCompactView ? 'Sí' : 'No'} />
                    <SettingRow label="PRs en sesión" value={settings.showPRsInWorkout ? 'Sí' : 'No'} />
                </View>
          ))}

          {/* Preferences Section */}
          {renderSection('Preferencias', (
                <View style={styles.sectionContent}>
                  <SettingRow label="Inicio de semana" value={`Día ${settings.startWeekOn}`} />
                  <SettingRow label="Modelo IA" value={settings.apiProvider ?? 'Sin definir'} />
                  <SettingRow label="Modo de entreno" value={settings.workoutLoggerMode === 'pro' ? 'Pro' : settings.workoutLoggerMode === 'simple' ? 'Simple' : 'Sin definir'} />
                  <SettingRow label="Meta de sueño" value={`${settings.sleepTargetHours ?? 8}h · ${settings.wakeTimeWork ?? '--'}`} />
                  <SettingRow label="Widgets de inicio" value={(settings.homeWidgetOrder?.length ?? 0) > 0 ? (settings.homeWidgetOrder ?? []).join(', ') : 'Orden por defecto'} />
                </View>
          ))}

          {/* Estado interno — visible solo en builds de desarrollo */}
          {isInternalBuild ? (
            renderSection('Estado interno', (
                  <View style={styles.sectionContent}>
                    <Text numberOfLines={1} style={[styles.statusTitle, { color: colors.onSurface }]}>
                      {status === 'ready' ? 'Preferencias cargadas' : 'Preparando preferencias'}
                    </Text>
                    <Text style={[styles.statusDescription, { color: colors.onSurfaceVariant }]}>
                      {status === 'ready'
                        ? `Fuente: ${summary?.source ?? 'desconocida'} · plantillas: ${templateCount} · wellbeing: ${wellbeingStatus === 'ready' ? 'activo' : 'pendiente'}`
                        : 'Cargando estado interno...'}
                    </Text>
                    <SettingRow label="Plantillas comida" value={templateCount > 0 ? `${templateCount} disponibles` : 'Sin plantillas'} />
                    <SettingRow label="Plantillas descartadas" value={discardedTemplateCount > 0 ? String(discardedTemplateCount) : 'Ninguna'} />
                    <SettingRow label="Wellbeing" value={wellbeingStatus === 'ready' ? `Resumen activo · ${wellbeingSource}` : 'Pendiente'} />
                    <SettingRow label="Sueño promedio" value={wellbeingOverview?.averageSleepHoursLast7Days ? `${wellbeingOverview.averageSleepHoursLast7Days} h` : 'Sin datos'} />
                    <SettingRow label="Agua de hoy" value={wellbeingOverview ? `${wellbeingOverview.waterTodayMl} ml` : 'Sin datos'} />
                    <SettingRow label="Logs inválidos" value={wellbeingDroppedDailyLogs > 0 ? String(wellbeingDroppedDailyLogs) : '0'} />
                    <SettingRow label="IA local" value={localAiStatus ? `${localAiStatus.engine} · ${localAiStatus.modelVersion ?? 'sin modelo'}` : 'Sin revisar'} />
                  </View>
            ))
          ) : null}

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
                    Base de ejercicios
                  </Button>
                  <Button
                    onPress={() => void toggleWorkoutReminders()}
                    variant="secondary"
                  >
                    {remindersEnabled ? 'Apagar recordatorios de entreno' : 'Encender recordatorios de entreno'}
                  </Button>
                  <Button
                    onPress={() => void toggleMealReminders()}
                    variant="secondary"
                  >
                    {mealRemindersEnabled ? 'Apagar recordatorios de comida' : 'Encender recordatorios de comida'}
                  </Button>
                  <Button
                    onPress={() => void toggleFallbackEnabled()}
                    variant="secondary"
                  >
                    {fallbackEnabled ? 'Apagar IA sin conexión' : 'Encender IA sin conexión'}
                  </Button>
                  <Button
                    onPress={() => void applyReminderPreset('light')}
                    variant="secondary"
                  >
                    Perfil de recordatorios suave
                  </Button>
                  <Button
                    onPress={() => void applyReminderPreset('full')}
                    variant="secondary"
                  >
                    Perfil de seguimiento completo
                  </Button>
                  <Button
                    onPress={() => void refreshWorkoutInfra()}
                  >
                    Sincronizar widgets y recordatorios
                  </Button>
                </View>
          ))}

          {/* Cutover Section (Internal) */}
          {isInternalBuild ? (
            renderSection('Cutover Android (interno)', (
              <View style={styles.sectionContent}>
                <Text numberOfLines={1} style={[styles.statusTitle, { color: colors.onSurface }]}>
                  {cutoverStage === 'ready-for-cutover' ? 'Listo para relevo' : cutoverStage === 'pilot-ready' ? 'Listo para piloto' : 'Todavía faltan piezas'}
                </Text>
                <Text style={[styles.statusDescription, { color: colors.onSurfaceVariant }]}>
                  {cutoverCheckedAt
                    ? `Última revisión ${new Date(cutoverCheckedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}`
                    : 'Sin revisiones recientes del checklist de relevo.'}
                </Text>

                {cutoverChecklist ? (
                  <View style={styles.sectionContent}>
                    {Object.entries(cutoverChecklist).map(([key, value]) => (
                      <SettingRow
                        key={key}
                        label={key}
                        value={value ? 'OK' : 'Pendiente'}
                      />
                    ))}
                  </View>
                ) : (
                  <Text style={[styles.controlDescription, { color: colors.onSurfaceVariant }]}>
                    Checklist no disponible todavía.
                  </Text>
                )}

                {operationalSnapshot ? (
                  <View style={styles.sectionContent}>
                    <SettingRow label="Permiso notificaciones" value={operationalSnapshot.notificationPermission} />
                    <SettingRow label="Bridge widgets" value={operationalSnapshot.widgetModuleAvailable ? 'Nativo' : 'Fallback'} />
                    <SettingRow label="Bridge background" value={operationalSnapshot.backgroundModuleAvailable ? 'Nativo' : 'Fallback'} />
                    <SettingRow label="Bridge migración" value={operationalSnapshot.migrationBridgeAvailable ? 'Nativo' : 'Fallback'} />
                    <SettingRow label="Bridge IA local" value={operationalSnapshot.localAiModuleAvailable ? 'Nativo' : 'Fallback'} />
                    <SettingRow label="Widget stale" value={operationalSnapshot.widgetStale ? 'Sí' : 'No'} />
                    <SettingRow label="Background" value={operationalSnapshot.backgroundLastResult} />
                    <SettingRow label="Logs nutrición" value={String(operationalSnapshot.nutritionLogCount)} />
                    <SettingRow label="Plantillas" value={String(operationalSnapshot.templateCount)} />
                    <SettingRow label="Wellbeing útil" value={operationalSnapshot.wellbeingHasData ? 'Sí' : 'No'} />
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
                    Actualizar checklist
                  </Button>
                  <Button
                    onPress={() => void runOperationalSweep()}
                    variant="secondary"
                  >
                    Ejecutar sweep operativo
                  </Button>
                </View>
              </View>
            ))
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
    overflow: 'hidden',
  },
  sectionHeader: {
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.03)',
  },
  sectionTitle: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 3,
    opacity: 0.5,
  },
  sectionBody: {
    padding: 8,
  },
  sectionContent: {
    gap: 12,
  },
  choiceGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  choiceChip: {
    paddingHorizontal: 14,
    paddingVertical: 8,
    borderRadius: 999,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  choiceChipText: {
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    textAlign: 'center',
  },
  controlRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 56,
    paddingVertical: 12,
    paddingHorizontal: 16,
    gap: 16,
  },
  controlText: {
    flex: 1,
    gap: 4,
  },
  controlLabel: {
    fontSize: 14,
    fontWeight: '700',
  },
  controlDescription: {
    fontSize: 10,
    fontWeight: '500',
    lineHeight: 16,
    opacity: 0.5,
  },
  settingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    minHeight: 56,
    paddingVertical: 12,
    paddingHorizontal: 16,
  },
  settingLabel: {
    fontSize: 14,
    fontWeight: '700',
    flex: 1,
    marginRight: 12,
  },
  settingValue: {
    fontSize: 10,
    fontWeight: '700',
    textTransform: 'uppercase',
    letterSpacing: 1.5,
    opacity: 0.5,
    textAlign: 'right',
    maxWidth: '45%',
  },
  statusTitle: {
    fontSize: 16,
    fontWeight: '700',
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
    fontSize: 14,
    fontWeight: '500',
  },
  actionsSection: {
    gap: 8,
  },
  signoffSection: {
    gap: 12,
    marginTop: 16,
  },
  cutoverActions: {
    gap: 12,
    marginTop: 16,
  },
  hydratingContainer: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingVertical: 48,
    paddingHorizontal: 24,
    gap: 8,
  },
  hydratingTitle: {
    fontSize: 16,
    fontWeight: '700',
  },
  hydratingDescription: {
    fontSize: 14,
    lineHeight: 20,
    textAlign: 'center',
  },
});
