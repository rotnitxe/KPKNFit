import React, { useEffect } from 'react';
import { NativeModules, Text, View } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { PrimaryButton } from '../../components/PrimaryButton';
import { useSettingsStore } from '../../stores/settingsStore';
import { useWorkoutStore } from '../../stores/workoutStore';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';
import { useWellbeingStore } from '../../stores/wellbeingStore';
import { useCutoverStore } from '../../stores/cutoverStore';
import { useLocalAiDiagnosticsStore } from '../../stores/localAiDiagnosticsStore';

function SettingRow({ label, value }: { label: string; value: string }) {
  return (
    <View className="flex-row items-center justify-between rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
      <Text className="text-sm text-kpkn-muted">{label}</Text>
      <Text className="max-w-[45%] text-right text-sm font-semibold text-kpkn-text">{value}</Text>
    </View>
  );
}

export function SettingsScreen() {
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

  return (
      <ScreenShell
        title="Ajustes"
        subtitle="Aquí ya operamos ajustes propios de RN: recordatorios, fallback IA y señales de readiness para el relevo Android."
      >
      <View className="gap-4">
        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Estado</Text>
          <Text className="mt-3 text-2xl font-semibold text-kpkn-text">
            {status === 'ready' ? 'Configuración lista en RN' : 'Preparando configuración'}
          </Text>
          <Text className="mt-2 text-base leading-6 text-kpkn-muted">
            {status === 'ready'
              ? `Fuente ${summary?.source ?? 'sin definir'} · estos ajustes ya viven en storage propio de RN y se usan para widgets, recordatorios y fallback.`
              : 'Cuando el bootstrap termine, aquí podremos tocar tus preferencias base directamente desde RN.'}
          </Text>
        </View>

        {notice ? (
          <View className="rounded-card border border-emerald-400/25 bg-emerald-500/10 px-4 py-4">
            <Text className="text-base font-medium text-white">{notice}</Text>
          </View>
        ) : null}

        {cutoverNotice ? (
          <View className="rounded-card border border-cyan-400/25 bg-cyan-500/10 px-4 py-4">
            <Text className="text-base font-medium text-white">{cutoverNotice}</Text>
          </View>
        ) : null}

        {summary ? (
          <>
            <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Recordatorios</Text>
              <View className="mt-4 gap-3">
                <SettingRow label="Entreno diario" value={summary.remindersEnabled ? (summary.reminderTime ?? 'activo') : 'desactivado'} />
                <SettingRow label="Desayuno" value={summary.mealRemindersEnabled ? summary.breakfastReminderTime : 'desactivado'} />
                <SettingRow label="Almuerzo" value={summary.mealRemindersEnabled ? summary.lunchReminderTime : 'desactivado'} />
                <SettingRow label="Cena" value={summary.mealRemindersEnabled ? summary.dinnerReminderTime : 'desactivado'} />
                <SettingRow label="Entreno no registrado" value={summary.missedWorkoutReminderEnabled ? summary.missedWorkoutReminderTime : 'desactivado'} />
                <SettingRow label="Batería baja" value={summary.augeBatteryReminderEnabled ? `${summary.augeBatteryReminderThreshold}% · ${summary.augeBatteryReminderTime}` : 'desactivado'} />
              </View>
            </View>

            <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Preferencias base</Text>
              <View className="mt-4 gap-3">
                <SettingRow label="Semana inicia" value={`día ${summary.startWeekOn}`} />
                <SettingRow label="Proveedor IA" value={summary.apiProvider ?? 'sin definir'} />
                <SettingRow label="Fallback IA" value={summary.fallbackEnabled ? 'activo' : 'apagado'} />
                <SettingRow label="Modo workout" value={summary.workoutLoggerMode ?? 'sin definir'} />
                <SettingRow label="Vista compacta" value={summary.sessionCompactView ? 'sí' : 'no'} />
                <SettingRow label="Widgets Home" value={summary.homeWidgetOrder.length > 0 ? summary.homeWidgetOrder.join(', ') : 'orden por defecto'} />
              </View>
            </View>

            <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Módulos migrados</Text>
              <View className="mt-4 gap-3">
                <SettingRow label="Plantillas de comida" value={templateCount > 0 ? `${templateCount} disponibles` : 'sin plantillas aún'} />
                <SettingRow label="Plantillas descartadas" value={discardedTemplateCount > 0 ? String(discardedTemplateCount) : 'ninguna'} />
                <SettingRow label="Wellbeing" value={wellbeingStatus === 'ready' ? `resumen activo · ${wellbeingSource}` : 'pendiente'} />
                <SettingRow label="Sueño promedio" value={wellbeingOverview?.averageSleepHoursLast7Days ? `${wellbeingOverview.averageSleepHoursLast7Days} h` : 'sin datos'} />
                <SettingRow label="Agua de hoy" value={wellbeingOverview ? `${wellbeingOverview.waterTodayMl} ml` : 'sin datos'} />
                <SettingRow label="Logs wellbeing inválidos" value={wellbeingDroppedDailyLogs > 0 ? String(wellbeingDroppedDailyLogs) : '0'} />
                <SettingRow label="IA local" value={localAiStatus ? `${localAiStatus.engine} · ${localAiStatus.modelVersion ?? 'sin modelo'}` : 'sin revisar'} />
              </View>
            </View>

            <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Acciones</Text>
              <View className="mt-4 gap-3">
                <PrimaryButton
                  label={summary.remindersEnabled ? 'Apagar recordatorios de entreno' : 'Encender recordatorios de entreno'}
                  onPress={() => void toggleWorkoutReminders()}
                  tone="secondary"
                />
                <PrimaryButton
                  label={summary.mealRemindersEnabled ? 'Apagar recordatorios de comida' : 'Encender recordatorios de comida'}
                  onPress={() => void toggleMealReminders()}
                  tone="secondary"
                />
                <PrimaryButton
                  label={summary.fallbackEnabled ? 'Apagar fallback IA' : 'Encender fallback IA'}
                  onPress={() => void toggleFallbackEnabled()}
                  tone="secondary"
                />
                <PrimaryButton
                  label="Aplicar perfil suave"
                  onPress={() => void applyReminderPreset('light')}
                  tone="secondary"
                />
                <PrimaryButton
                  label="Aplicar perfil seguimiento"
                  onPress={() => void applyReminderPreset('full')}
                  tone="secondary"
                />
                <PrimaryButton
                  label="Volver a sincronizar widgets y recordatorios"
                  onPress={() => void refreshWorkoutInfra()}
                />
              </View>
            </View>

            {isInternalBuild ? (
              <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Cutover Android (interno)</Text>
                <Text className="mt-3 text-2xl font-semibold text-kpkn-text">
                  {cutoverStage === 'ready-for-cutover' ? 'Listo para relevo' : cutoverStage === 'pilot-ready' ? 'Listo para piloto' : 'Aun faltan piezas'}
                </Text>
                <Text className="mt-2 text-base leading-6 text-kpkn-muted">
                  {cutoverCheckedAt
                    ? `Última revisión ${new Date(cutoverCheckedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' })}`
                    : 'Todavía no revisamos el checklist de relevo.'}
                </Text>
                {cutoverChecklist ? (
                  <View className="mt-4 gap-3">
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
                  <View className="mt-4 gap-3">
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
                <View className="mt-4 gap-3">
                  <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Signoff manual</Text>
                  {Object.entries(cutoverSignoff).map(([key, value]) => (
                    <PrimaryButton
                      key={key}
                      label={`${value ? 'OK' : 'Pendiente'} · ${key}`}
                      onPress={() => toggleManualSignoff(key as keyof typeof cutoverSignoff)}
                      tone="secondary"
                    />
                  ))}
                </View>
                <View className="mt-4 gap-3">
                  <PrimaryButton
                    label="Actualizar checklist de relevo"
                    onPress={() => void refreshCutover()}
                    tone="secondary"
                  />
                  <PrimaryButton
                    label="Correr sweep operativo"
                    onPress={() => void runOperationalSweep()}
                    tone="secondary"
                  />
                </View>
              </View>
            ) : null}
          </>
        ) : null}
      </View>
    </ScreenShell>
  );
}
