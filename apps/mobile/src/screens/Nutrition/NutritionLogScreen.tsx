import React, { useMemo } from 'react';
import { Keyboard } from 'react-native';
import { Pressable, Text, TextInput, View } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { PrimaryButton } from '../../components/PrimaryButton';
import { useMobileNutritionStore } from '../../stores/nutritionStore';
import { useMealTemplateStore } from '../../stores/mealTemplateStore';

function MacroPill({ label, value }: { label: string; value: number }) {
  return (
    <View className="rounded-full border border-white/10 bg-white/5 px-3 py-2">
      <Text className="text-xs uppercase tracking-[2px] text-kpkn-muted">{label}</Text>
      <Text className="mt-1 text-sm font-semibold text-kpkn-text">{Math.round(value)} g</Text>
    </View>
  );
}

export function NutritionLogScreen() {
  const description = useMobileNutritionStore(state => state.description);
  const status = useMobileNutritionStore(state => state.status);
  const lastAnalysis = useMobileNutritionStore(state => state.lastAnalysis);
  const savedLogs = useMobileNutritionStore(state => state.savedLogs);
  const isDetailVisible = useMobileNutritionStore(state => state.isDetailVisible);
  const saveNotice = useMobileNutritionStore(state => state.saveNotice);
  const errorMessage = useMobileNutritionStore(state => state.errorMessage);
  const setDescription = useMobileNutritionStore(state => state.setDescription);
  const analyze = useMobileNutritionStore(state => state.analyze);
  const saveCurrent = useMobileNutritionStore(state => state.saveCurrent);
  const toggleDetail = useMobileNutritionStore(state => state.toggleDetail);
  const templates = useMealTemplateStore(state => state.templates);

  const handleAnalyze = () => {
    Keyboard.dismiss();
    void analyze();
  };

  const applyExample = (value: string) => {
    setDescription(value);
    Keyboard.dismiss();
  };

  const totals = useMemo(() => {
    if (!lastAnalysis) return null;
    return lastAnalysis.items.reduce(
      (acc, item) => ({
        calories: acc.calories + item.calories,
        protein: acc.protein + item.protein,
        carbs: acc.carbs + item.carbs,
        fats: acc.fats + item.fats,
      }),
      { calories: 0, protein: 0, carbs: 0, fats: 0 },
    );
  }, [lastAnalysis]);

  const recentDescriptions = useMemo(() => {
    const seen = new Set<string>();
    return savedLogs
      .map(log => log.description.trim())
      .filter(value => {
        if (!value || seen.has(value)) return false;
        seen.add(value);
        return true;
      })
      .slice(0, 4);
  }, [savedLogs]);

  return (
    <ScreenShell
      title="Registrar comida"
      subtitle="Escribe tu comida como la dirías normalmente. Nosotros nos encargamos de transformarla en una referencia útil."
    >
      <View className="gap-4">
        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-4">
          <View className="mb-3 flex-row flex-wrap gap-2">
            <PrimaryButton
              testID="nutrition-example-completos"
              label="2 completos italianos"
              onPress={() => applyExample('2 completos italianos')}
              tone="secondary"
            />
            <PrimaryButton
              testID="nutrition-example-cazuela"
              label="Cazuela de vacuno"
              onPress={() => applyExample('cazuela de vacuno con papa y zapallo')}
              tone="secondary"
            />
          </View>
          {templates.length > 0 ? (
            <View className="mb-4 gap-3">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Plantillas rápidas</Text>
              <View className="gap-3">
                {templates.slice(0, 3).map(template => (
                  <Pressable
                    key={template.id}
                    className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4"
                    onPress={() => applyExample(template.quickDescription)}
                  >
                    <Text className="text-base font-semibold text-kpkn-text">{template.name}</Text>
                    <Text className="mt-1 text-sm leading-5 text-kpkn-muted">{template.description}</Text>
                    <Text className="mt-2 text-sm text-kpkn-muted">
                      {`${Math.round(template.calories)} kcal · ${Math.round(template.protein)}p · ${Math.round(template.carbs)}c · ${Math.round(template.fats)}g`}
                    </Text>
                  </Pressable>
                ))}
              </View>
            </View>
          ) : null}
          <TextInput
            testID="nutrition-description-input"
            className="min-h-[144px] rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4 text-base leading-6 text-kpkn-text"
            multiline
            placeholder="Ej: 2 completos italianos y una bebida zero"
            placeholderTextColor="#6E7891"
            value={description}
            onChangeText={setDescription}
            textAlignVertical="top"
          />
          <View className="mt-4">
            <PrimaryButton
              testID="nutrition-analyze-button"
              label={status === 'analyzing' ? 'Analizando...' : 'Analizar calorías'}
              onPress={handleAnalyze}
              disabled={status === 'analyzing' || description.trim().length === 0}
            />
          </View>
          {recentDescriptions.length > 0 ? (
            <View className="mt-4 gap-2">
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Recientes</Text>
              <View className="flex-row flex-wrap gap-2">
                {recentDescriptions.map(value => (
                  <Pressable
                    key={value}
                    className="rounded-full border border-white/10 bg-white/5 px-4 py-3"
                    onPress={() => applyExample(value)}
                  >
                    <Text className="text-sm text-kpkn-text">{value}</Text>
                  </Pressable>
                ))}
              </View>
            </View>
          ) : null}
        </View>

        <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
          {status === 'analyzing' ? (
            <Text className="text-base text-kpkn-muted">Analizando...</Text>
          ) : totals ? (
            <>
              <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Resultado</Text>
              <Text className="mt-3 text-5xl font-bold text-kpkn-text">{Math.round(totals.calories)}</Text>
              <Text className="mt-2 text-base text-kpkn-muted">calorías aproximadas</Text>
              <View className="mt-4 flex-row flex-wrap gap-2">
                <MacroPill label="Proteína" value={totals.protein} />
                <MacroPill label="Carbs" value={totals.carbs} />
                <MacroPill label="Grasas" value={totals.fats} />
              </View>
              <View className="mt-5 gap-3">
                <PrimaryButton testID="nutrition-save-button" label="Guardar" onPress={saveCurrent} />
                <PrimaryButton
                  testID="nutrition-toggle-detail-button"
                  label={isDetailVisible ? 'Ocultar detalle' : 'Ver detalle'}
                  onPress={toggleDetail}
                  tone="secondary"
                />
              </View>
            </>
          ) : (
            <Text className="text-base leading-6 text-kpkn-muted">
              Aquí aparecerá el total cuando analicemos la comida. Es una referencia práctica, no una medición exacta de laboratorio.
            </Text>
          )}
        </View>

        {saveNotice ? (
          <View className="rounded-card border border-emerald-400/25 bg-emerald-500/10 px-4 py-4">
            <Text className="text-base font-medium text-white">{saveNotice}</Text>
          </View>
        ) : null}

        {errorMessage ? (
          <View className="rounded-card border border-rose-400/25 bg-rose-500/10 px-4 py-4">
            <Text className="text-base leading-6 text-white">{errorMessage}</Text>
          </View>
        ) : null}

        {isDetailVisible && lastAnalysis ? (
          <View className="rounded-card border border-white/10 bg-kpkn-surface px-4 py-5">
            <Text className="text-sm uppercase tracking-[2px] text-kpkn-muted">Tu comida</Text>
            <View className="mt-4 gap-3">
              {lastAnalysis.items.map((item, index) => (
                <View key={`${item.canonicalName}-${index}`} className="rounded-3xl border border-white/8 bg-kpkn-elevated px-4 py-4">
                  <Text className="text-lg font-semibold text-kpkn-text">{item.canonicalName}</Text>
                  <Text className="mt-1 text-sm text-kpkn-muted">
                    {Math.round(item.calories)} kcal · {Math.round(item.protein)}p · {Math.round(item.carbs)}c · {Math.round(item.fats)}g
                  </Text>
                </View>
              ))}
            </View>
          </View>
        ) : null}
      </View>
    </ScreenShell>
  );
}
