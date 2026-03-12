import React from 'react';
import { Text, View } from 'react-native';
import { PrimaryButton } from '../components/PrimaryButton';
import { ScreenShell } from '../components/ScreenShell';
import { useLocalAiDiagnosticsStore } from '../stores/localAiDiagnosticsStore';
import { useNutritionFlowDiagnosticsStore } from '../stores/nutritionFlowDiagnosticsStore';
import { useBootstrapStore } from '../stores/bootstrapStore';

export function HomeScreen() {
  const status = useBootstrapStore(state => state.status);
  const summary = useBootstrapStore(state => state.summary);
  const hydrationResult = useBootstrapStore(state => state.hydrationResult);
  const error = useBootstrapStore(state => state.error);
  const aiStatus = useLocalAiDiagnosticsStore(state => state.status);
  const nativeDiagnostics = useLocalAiDiagnosticsStore(state => state.nativeDiagnostics);
  const aiRefreshState = useLocalAiDiagnosticsStore(state => state.refreshState);
  const smokeTestState = useLocalAiDiagnosticsStore(state => state.smokeTestState);
  const smokeTestError = useLocalAiDiagnosticsStore(state => state.smokeTestError);
  const lastCheckedAt = useLocalAiDiagnosticsStore(state => state.lastCheckedAt);
  const recentRuns = useLocalAiDiagnosticsStore(state => state.recentRuns);
  const refreshAiStatus = useLocalAiDiagnosticsStore(state => state.refreshStatus);
  const runSmokeTest = useLocalAiDiagnosticsStore(state => state.runSmokeTest);
  const nutritionSmokeState = useNutritionFlowDiagnosticsStore(state => state.smokeState);
  const nutritionSmokeError = useNutritionFlowDiagnosticsStore(state => state.smokeError);
  const nutritionSmokeResult = useNutritionFlowDiagnosticsStore(state => state.lastResult);
  const runNutritionSmokeFlow = useNutritionFlowDiagnosticsStore(state => state.runSmokeFlow);

  const aiHeadline = aiStatus?.engine === 'runtime'
    ? 'Runtime listo'
    : aiStatus?.modelVersion
      ? 'Modelo detectado, pero aun en heuristicas'
      : 'Sin modelo confirmado todavia';

  const nativeCooldownSeconds = nativeDiagnostics ? Math.ceil(nativeDiagnostics.cooldownRemainingMs / 1000) : 0;

  return (
    <ScreenShell
      title="KPKN móvil"
      subtitle="Base React Native Android-first. Nutrición es el primer vertical y la importación local corre en el primer arranque."
    >
      <View className="gap-4">
        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Estado</Text>
          <Text className="mt-3 text-2xl font-semibold text-kpkn-text">
            {status === 'booting' ? 'Preparando app...' : status === 'ready' ? 'Lista para migrar' : 'Necesita atención'}
          </Text>
          <Text className="mt-2 text-base leading-6 text-kpkn-muted">
            {status === 'ready'
              ? 'La shell nativa ya está viva y puede leer el snapshot puente cuando exista.'
              : error ?? 'Estamos levantando la app base y revisando si hay snapshot local.'}
          </Text>
        </View>

        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Migración local</Text>
          <Text className="mt-3 text-xl font-semibold text-kpkn-text">
            {summary?.validationError
              ? 'Snapshot inválido'
              : summary?.source === 'snapshot'
                ? 'Snapshot encontrado'
                : 'Sin snapshot todavía'}
          </Text>
          <Text className="mt-2 text-base leading-6 text-kpkn-muted">
            {summary?.validationError
              ? summary.validationError
              : summary?.recordCounts
              ? `Programas: ${summary.recordCounts.programs} · Historial: ${summary.recordCounts.workoutHistory} · Nutrición: ${summary.recordCounts.nutritionLogs}`
              : 'Cuando instalemos la release puente de Capacitor, este build importará settings, historial y nutrición sin pedirle nada al usuario.'}
          </Text>
          {hydrationResult ? (
            <Text className="mt-3 text-sm leading-6 text-kpkn-muted">
              {`Nutrición importada: ${hydrationResult.nutritionLogsImported} · descartada: ${hydrationResult.nutritionLogsDiscarded} · settings: ${hydrationResult.settingsImported ? 'staged' : 'pendiente'} · wellbeing: ${hydrationResult.wellbeingImported ? 'staged' : 'pendiente'} · workout: ${hydrationResult.workoutStatus}`}
            </Text>
          ) : null}
          {hydrationResult?.errors.length ? (
            <Text className="mt-2 text-sm leading-6 text-amber-200">
              {hydrationResult.errors.join(' | ')}
            </Text>
          ) : null}
        </View>

        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Lo siguiente</Text>
          <Text className="mt-3 text-base leading-6 text-kpkn-muted">
            {summary?.validationError || hydrationResult?.errors.length
              ? 'Primero necesitamos corregir la migración local para no perder datos. Cuando eso quede limpio, abrimos widgets, background y notificaciones.'
              : 'Nutrición ya tiene su flujo nativo de Fricción 0. El siguiente bloque es endurecer IA local, widgets y background sobre esta base RN.'}
          </Text>
        </View>

        {__DEV__ ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Motor local (interno)</Text>
            <Text className="mt-3 text-xl font-semibold text-kpkn-text">{aiHeadline}</Text>
            <Text className="mt-2 text-base leading-6 text-kpkn-muted">
              {aiStatus
                ? `Engine: ${aiStatus.engine} · Modelo: ${aiStatus.modelVersion ?? 'sin confirmar'} · Última revisión: ${lastCheckedAt ? new Date(lastCheckedAt).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit' }) : 'pendiente'}`
                : 'Todavía no consultamos el estado del motor local en esta sesión.'}
            </Text>
            {nativeDiagnostics ? (
              <Text className="mt-2 text-sm leading-6 text-kpkn-muted">
                {`Ruta: ${nativeDiagnostics.modelPath ?? 'sin resolver'} · Fallos runtime: ${nativeDiagnostics.runtimeFailureCount} · Cooldown: ${nativeCooldownSeconds > 0 ? `${nativeCooldownSeconds}s` : 'sin bloqueo'}`}
              </Text>
            ) : null}
            {aiStatus?.lastError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{aiStatus.lastError}</Text>
            ) : null}
            {smokeTestError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{smokeTestError}</Text>
            ) : null}
            <View className="mt-4 gap-3">
              <PrimaryButton
                testID="local-ai-refresh-status"
                label={aiRefreshState === 'refreshing' ? 'Revisando motor...' : 'Actualizar estado'}
                onPress={() => void refreshAiStatus('status')}
                tone="secondary"
                disabled={aiRefreshState === 'refreshing' || smokeTestState === 'running'}
              />
              <PrimaryButton
                testID="local-ai-warmup"
                label={aiRefreshState === 'refreshing' ? 'Preparando...' : 'Intentar warmup'}
                onPress={() => void refreshAiStatus('warmup')}
                tone="secondary"
                disabled={aiRefreshState === 'refreshing' || smokeTestState === 'running'}
              />
              <PrimaryButton
                testID="local-ai-smoke-test"
                label={smokeTestState === 'running' ? 'Probando motor...' : 'Probar análisis local'}
                onPress={() => void runSmokeTest()}
                tone="secondary"
                disabled={smokeTestState === 'running' || aiRefreshState === 'refreshing'}
              />
            </View>
            {recentRuns.length > 0 ? (
              <View className="mt-5 gap-3">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Últimos análisis</Text>
                {recentRuns.slice(0, 3).map(run => (
                  <View
                    key={`${run.analyzedAt}-${run.descriptionPreview}`}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                  >
                    <Text className="text-base font-semibold text-kpkn-text">{run.descriptionPreview}</Text>
                    <Text className="mt-1 text-sm text-kpkn-muted">
                      {run.engine} · {run.itemCount} items · {Math.round(run.elapsedMs)} ms · confianza {Math.round(run.overallConfidence * 100)}%
                    </Text>
                    {run.runtimeError ? (
                      <Text className="mt-2 text-sm leading-5 text-amber-200">{run.runtimeError}</Text>
                    ) : null}
                  </View>
                ))}
              </View>
            ) : null}
            {nativeDiagnostics?.recentEvents?.length ? (
              <View className="mt-5 gap-3">
                <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Eventos nativos</Text>
                {nativeDiagnostics.recentEvents.slice().reverse().slice(0, 4).map((event, index) => (
                  <View
                    key={`${event.timestampMs}-${event.scope}-${index}`}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                  >
                    <Text className="text-sm font-semibold text-kpkn-text">
                      {event.scope} · {event.level}
                    </Text>
                    <Text className="mt-1 text-sm leading-5 text-kpkn-muted">
                      {new Date(event.timestampMs).toLocaleTimeString('es-CL', { hour: '2-digit', minute: '2-digit', second: '2-digit' })} · {event.message}
                    </Text>
                  </View>
                ))}
              </View>
            ) : null}
          </View>
        ) : null}

        {__DEV__ ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Flujo nutrición (interno)</Text>
            <Text className="mt-3 text-base leading-6 text-kpkn-muted">
              Esta prueba ejecuta análisis + guardado + verificación de persistencia con una comida de ejemplo.
            </Text>
            <View className="mt-4 gap-3">
              <PrimaryButton
                testID="nutrition-flow-smoke-test"
                label={nutritionSmokeState === 'running' ? 'Probando flujo...' : 'Probar flujo nutrición'}
                onPress={() => void runNutritionSmokeFlow()}
                tone="secondary"
                disabled={nutritionSmokeState === 'running'}
              />
            </View>
            {nutritionSmokeError ? (
              <Text className="mt-3 text-sm leading-6 text-amber-200">{nutritionSmokeError}</Text>
            ) : null}
            {nutritionSmokeResult ? (
              <View className="mt-5 rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
                <Text className="text-base font-semibold text-kpkn-text">{nutritionSmokeResult.description}</Text>
                <Text className="mt-1 text-sm leading-6 text-kpkn-muted">
                  {`${nutritionSmokeResult.engine} · ${Math.round(nutritionSmokeResult.elapsedMs)} ms · ${nutritionSmokeResult.itemCount} items · ${Math.round(nutritionSmokeResult.calories)} kcal`}
                </Text>
                <Text className="mt-1 text-sm leading-6 text-kpkn-muted">
                  {nutritionSmokeResult.persisted
                    ? `Guardado validado (${nutritionSmokeResult.savedEntryId})`
                    : 'El análisis corrió, pero no encontramos el guardado en SQLite.'}
                </Text>
                {nutritionSmokeResult.runtimeError ? (
                  <Text className="mt-2 text-sm leading-6 text-amber-200">{nutritionSmokeResult.runtimeError}</Text>
                ) : null}
              </View>
            ) : null}
          </View>
        ) : null}
      </View>
    </ScreenShell>
  );
}
