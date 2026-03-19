import React, { useState } from 'react';
import { View, Text, StyleSheet, TextInput, TouchableOpacity, ScrollView } from 'react-native';
import { ScreenShell } from '../../components/ScreenShell';
import { Button } from '../../components/ui/Button';
import { LiquidGlassCard } from '../../components/ui/LiquidGlassCard';
import { useColors } from '../../theme';
import type { AthleteProfileScore } from '../../types/workout';

interface WizardProps {
  onComplete: (profile: AthleteProfileScore) => void;
  onCancel: () => void;
}

const INJURIES = [
  'Hombro', 'Codo', 'Muñeca', 'Cuello', 'Espalda alta', 'Espalda baja',
  'Cadera', 'Rodilla', 'Tobillo', 'Ninguna'
];

const OBJECTIVES = [
  { id: 'hypertrophy', label: 'Hipertrofia' },
  { id: 'strength', label: 'Fuerza' },
  { id: 'health', label: 'Salud' },
  { id: 'aesthetics', label: 'Estética' },
  { id: 'performance', label: 'Rendimiento' },
];

const LEVELS = [
  { id: 'beginner', label: 'Principiante', desc: 'Menos de 1 año' },
  { id: 'intermediate', label: 'Intermedio', desc: '1-3 años' },
  { id: 'advanced', label: 'Avanzado', desc: '3-5 años' },
  { id: 'elite', label: 'Élite', desc: 'Más de 5 años' },
];

function StepIndicator({ current, total }: { current: number; total: number }) {
  const colors = useColors();
  return (
    <View style={stepStyles.container}>
      {Array.from({ length: total }).map((_, i) => (
        <View
          key={i}
          style={[
            stepStyles.dot,
            { backgroundColor: i < current ? colors.primary : colors.onSurfaceVariant },
          ]}
        />
      ))}
    </View>
  );
}

const stepStyles = StyleSheet.create({
  container: {
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 8,
    marginBottom: 24,
  },
  dot: {
    width: 8,
    height: 8,
    borderRadius: 4,
  },
});

interface StepProps {
  title: string;
  subtitle: string;
  children: React.ReactNode;
}

function Step({ title, subtitle, children }: StepProps) {
  const colors = useColors();
  return (
    <View style={styles.step}>
      <Text style={[styles.stepTitle, { color: colors.onSurface }]}>{title}</Text>
      <Text style={[styles.stepSubtitle, { color: colors.onSurfaceVariant }]}>{subtitle}</Text>
      <View style={styles.stepContent}>{children}</View>
    </View>
  );
}

function Step1({ data, onChange }: { data: any; onChange: (v: any) => void }) {
  const colors = useColors();
  return (
    <Step title="Datos básicos" subtitle="Cuéntanos sobre ti">
      <View style={inputStyles.row}>
        <View style={inputStyles.field}>
          <Text style={[inputStyles.label, { color: colors.onSurfaceVariant }]}>Nombre</Text>
          <TextInput
            style={[inputStyles.input, { backgroundColor: colors.surface, color: colors.onSurface, borderColor: colors.outline }]}
            value={data.name}
            onChangeText={(v) => onChange({ ...data, name: v })}
            placeholder="Tu nombre"
            placeholderTextColor={colors.onSurfaceVariant}
          />
        </View>
      </View>
      <View style={inputStyles.row}>
        <View style={inputStyles.halfField}>
          <Text style={[inputStyles.label, { color: colors.onSurfaceVariant }]}>Edad</Text>
          <TextInput
            style={[inputStyles.input, { backgroundColor: colors.surface, color: colors.onSurface, borderColor: colors.outline }]}
            value={data.age}
            onChangeText={(v) => onChange({ ...data, age: v })}
            placeholder="Años"
            keyboardType="numeric"
            placeholderTextColor={colors.onSurfaceVariant}
          />
        </View>
        <View style={inputStyles.halfField}>
          <Text style={[inputStyles.label, { color: colors.onSurfaceVariant }]}>Peso (kg)</Text>
          <TextInput
            style={[inputStyles.input, { backgroundColor: colors.surface, color: colors.onSurface, borderColor: colors.outline }]}
            value={data.weight}
            onChangeText={(v) => onChange({ ...data, weight: v })}
            placeholder="kg"
            keyboardType="numeric"
            placeholderTextColor={colors.onSurfaceVariant}
          />
        </View>
      </View>
      <View style={inputStyles.row}>
        <View style={inputStyles.halfField}>
          <Text style={[inputStyles.label, { color: colors.onSurfaceVariant }]}>Altura (cm)</Text>
          <TextInput
            style={[inputStyles.input, { backgroundColor: colors.surface, color: colors.onSurface, borderColor: colors.outline }]}
            value={data.height}
            onChangeText={(v) => onChange({ ...data, height: v })}
            placeholder="cm"
            keyboardType="numeric"
            placeholderTextColor={colors.onSurfaceVariant}
          />
        </View>
        <View style={inputStyles.halfField}>
          <Text style={[inputStyles.label, { color: colors.onSurfaceVariant }]}>Sexo</Text>
          <View style={inputStyles.toggle}>
            <TouchableOpacity
              style={[inputStyles.toggleBtn, data.gender === 'male' && { backgroundColor: colors.primary }]}
              onPress={() => onChange({ ...data, gender: 'male' })}
            >
              <Text style={[inputStyles.toggleText, { color: data.gender === 'male' ? '#fff' : colors.onSurface }]}>Masculino</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[inputStyles.toggleBtn, data.gender === 'female' && { backgroundColor: colors.primary }]}
              onPress={() => onChange({ ...data, gender: 'female' })}
            >
              <Text style={[inputStyles.toggleText, { color: data.gender === 'female' ? '#fff' : colors.onSurface }]}>Femenino</Text>
            </TouchableOpacity>
          </View>
        </View>
      </View>
    </Step>
  );
}

const inputStyles = StyleSheet.create({
  row: {
    flexDirection: 'row',
    gap: 12,
    marginBottom: 16,
  },
  field: {
    flex: 1,
  },
  halfField: {
    flex: 1,
  },
  label: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
    marginBottom: 8,
  },
  input: {
    borderWidth: 1,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    fontSize: 16,
    fontWeight: '600',
  },
  toggle: {
    flexDirection: 'row',
    borderRadius: 12,
    overflow: 'hidden',
    borderWidth: 1,
  },
  toggleBtn: {
    flex: 1,
    paddingVertical: 12,
    alignItems: 'center',
  },
  toggleText: {
    fontSize: 12,
    fontWeight: '700',
  },
});

function Step2({ data, onChange }: { data: any; onChange: (v: any) => void }) {
  const colors = useColors();
  return (
    <Step title="Experiencia" subtitle="¿Cuánto tiempo llevas entrenando?">
      <View style={levelStyles.grid}>
        {LEVELS.map(level => (
          <TouchableOpacity
            key={level.id}
            style={[
              levelStyles.card,
              { backgroundColor: data.level === level.id ? colors.primaryContainer : colors.surface, borderColor: data.level === level.id ? colors.primary : colors.outline },
            ]}
            onPress={() => onChange({ ...data, level: level.id })}
          >
            <Text style={[levelStyles.label, { color: data.level === level.id ? colors.primary : colors.onSurface }]}>{level.label}</Text>
            <Text style={[levelStyles.desc, { color: colors.onSurfaceVariant }]}>{level.desc}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </Step>
  );
}

const levelStyles = StyleSheet.create({
  grid: {
    gap: 12,
  },
  card: {
    borderWidth: 1,
    borderRadius: 16,
    padding: 16,
  },
  label: {
    fontSize: 16,
    fontWeight: '800',
    marginBottom: 4,
  },
  desc: {
    fontSize: 12,
  },
});

function Step3({ data, onChange }: { data: any; onChange: (v: any) => void }) {
  const colors = useColors();
  return (
    <Step title="Objetivos" subtitle="¿Qué quieres lograr?">
      <View style={objStyles.grid}>
        {OBJECTIVES.map(obj => (
          <TouchableOpacity
            key={obj.id}
            style={[
              objStyles.card,
              { backgroundColor: data.objectives.includes(obj.id) ? colors.primaryContainer : colors.surface, borderColor: data.objectives.includes(obj.id) ? colors.primary : colors.outline },
            ]}
            onPress={() => {
              const newObjs = data.objectives.includes(obj.id)
                ? data.objectives.filter((o: string) => o !== obj.id)
                : [...data.objectives, obj.id];
              onChange({ ...data, objectives: newObjs });
            }}
          >
            <Text style={[objStyles.label, { color: data.objectives.includes(obj.id) ? colors.primary : colors.onSurface }]}>{obj.label}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </Step>
  );
}

const objStyles = StyleSheet.create({
  grid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 12,
  },
  card: {
    width: '47%',
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
    alignItems: 'center',
  },
  label: {
    fontSize: 14,
    fontWeight: '700',
  },
});

function Step4({ data, onChange }: { data: any; onChange: (v: any) => void }) {
  const colors = useColors();
  return (
    <Step title="Historial de lesiones" subtitle="¿Tienes alguna lesión recurrente?">
      <View style={injuryStyles.list}>
        {INJURIES.map(injury => (
          <TouchableOpacity
            key={injury}
            style={[
              injuryStyles.item,
              { backgroundColor: data.injuries.includes(injury) ? colors.errorContainer : colors.surface, borderColor: data.injuries.includes(injury) ? colors.error : colors.outline },
            ]}
            onPress={() => {
              const newInjuries = data.injuries.includes(injury)
                ? data.injuries.filter((i: string) => i !== injury)
                : [...data.injuries, injury];
              onChange({ ...data, injuries: newInjuries });
            }}
          >
            <Text style={[injuryStyles.label, { color: data.injuries.includes(injury) ? colors.error : colors.onSurface }]}>{injury}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </Step>
  );
}

const injuryStyles = StyleSheet.create({
  list: {
    gap: 8,
  },
  item: {
    borderWidth: 1,
    borderRadius: 12,
    padding: 16,
  },
  label: {
    fontSize: 14,
    fontWeight: '600',
  },
});

function Step5({ data, onChange }: { data: any; onChange: (v: any) => void }) {
  const colors = useColors();
  return (
    <Step title="Disponibilidad" subtitle="¿Cuánto tiempo tienes?">
      <View style={dispStyles.field}>
        <Text style={[dispStyles.label, { color: colors.onSurfaceVariant }]}>Días por semana</Text>
        <View style={dispStyles.numbers}>
          {[1, 2, 3, 4, 5, 6, 7].map(d => (
            <TouchableOpacity
              key={d}
              style={[dispStyles.numBtn, { backgroundColor: data.daysPerWeek === d ? colors.primary : colors.surface }]}
              onPress={() => onChange({ ...data, daysPerWeek: d })}
            >
              <Text style={[dispStyles.numText, { color: data.daysPerWeek === d ? '#fff' : colors.onSurface }]}>{d}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
      <View style={dispStyles.field}>
        <Text style={[dispStyles.label, { color: colors.onSurfaceVariant }]}>Minutos por sesión</Text>
        <View style={dispStyles.numbers}>
          {[30, 45, 60, 75, 90, 120].map(m => (
            <TouchableOpacity
              key={m}
              style={[dispStyles.numBtn, { backgroundColor: data.minutesPerSession === m ? colors.primary : colors.surface }]}
              onPress={() => onChange({ ...data, minutesPerSession: m })}
            >
              <Text style={[dispStyles.numText, { color: data.minutesPerSession === m ? '#fff' : colors.onSurface }]}>{m}</Text>
            </TouchableOpacity>
          ))}
        </View>
      </View>
    </Step>
  );
}

const dispStyles = StyleSheet.create({
  field: {
    marginBottom: 24,
  },
  label: {
    fontSize: 12,
    fontWeight: '700',
    textTransform: 'uppercase',
    marginBottom: 12,
  },
  numbers: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 8,
  },
  numBtn: {
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
  },
  numText: {
    fontSize: 14,
    fontWeight: '800',
  },
});

function ResultStep({ profile }: { profile: AthleteProfileScore }) {
  const colors = useColors();
  return (
    <View style={resultStyles.container}>
      <Text style={[resultStyles.title, { color: colors.onSurface }]}>¡Perfil Completado!</Text>
      <LiquidGlassCard style={resultStyles.card} padding={20}>
        <Text style={[resultStyles.label, { color: colors.onSurfaceVariant }]}>Estilo de entrenamiento</Text>
        <Text style={[resultStyles.value, { color: colors.primary }]}>{profile.trainingStyle}</Text>
        
        <View style={resultStyles.scores}>
          <View style={resultStyles.scoreItem}>
            <Text style={[resultStyles.scoreLabel, { color: colors.onSurfaceVariant }]}>Técnica</Text>
            <Text style={[resultStyles.scoreValue, { color: colors.onSurface }]}>{profile.technicalScore}/10</Text>
          </View>
          <View style={resultStyles.scoreItem}>
            <Text style={[resultStyles.scoreLabel, { color: colors.onSurfaceVariant }]}>Consistencia</Text>
            <Text style={[resultStyles.scoreValue, { color: colors.onSurface }]}>{profile.consistencyScore}/10</Text>
          </View>
          <View style={resultStyles.scoreItem}>
            <Text style={[resultStyles.scoreLabel, { color: colors.onSurfaceVariant }]}>Fuerza</Text>
            <Text style={[resultStyles.scoreValue, { color: colors.onSurface }]}>{profile.strengthScore}/10</Text>
          </View>
        </View>
        
        <Text style={[resultStyles.level, { color: colors.primary }]}>{profile.profileLevel}</Text>
      </LiquidGlassCard>
    </View>
  );
}

const resultStyles = StyleSheet.create({
  container: {
    alignItems: 'center',
    paddingTop: 40,
  },
  title: {
    fontSize: 24,
    fontWeight: '900',
    marginBottom: 24,
  },
  card: {
    width: '100%',
    borderRadius: 24,
  },
  label: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1,
  },
  value: {
    fontSize: 20,
    fontWeight: '900',
    marginBottom: 20,
  },
  scores: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    marginBottom: 20,
  },
  scoreItem: {
    alignItems: 'center',
  },
  scoreLabel: {
    fontSize: 9,
    fontWeight: '700',
    textTransform: 'uppercase',
  },
  scoreValue: {
    fontSize: 18,
    fontWeight: '900',
    marginTop: 4,
  },
  level: {
    fontSize: 28,
    fontWeight: '900',
    textAlign: 'center',
  },
});

export function AthleteProfilingWizard({ onComplete, onCancel }: WizardProps) {
  const [step, setStep] = useState(1);
  const [data, setData] = useState({
    name: '',
    age: '',
    weight: '',
    height: '',
    gender: 'male',
    level: '',
    objectives: [] as string[],
    injuries: [] as string[],
    daysPerWeek: 4,
    minutesPerSession: 60,
  });

  const canContinue = () => {
    switch (step) {
      case 1: return data.name && data.age && data.weight && data.height;
      case 2: return !!data.level;
      case 3: return data.objectives.length > 0;
      case 4: return true;
      case 5: return data.daysPerWeek > 0;
      default: return true;
    }
  };

  const handleNext = () => {
    if (step === 5) {
      const profile = buildProfile(data);
      onComplete(profile);
      return;
    }
    setStep(step + 1);
  };

  const buildProfile = (d: typeof data): AthleteProfileScore => {
    const levelScores: Record<string, number> = {
      beginner: 3, intermediate: 6, advanced: 8, elite: 10,
    };
    const trainingStyles: Record<string, AthleteProfileScore['trainingStyle']> = {
      hypertrophy: 'Bodybuilder', strength: 'Powerlifter', performance: 'Powerbuilder',
      aesthetics: 'Bodybuilder', health: 'Bodybuilder',
    };
    
    return {
      trainingStyle: d.objectives[0] ? trainingStyles[d.objectives[0]] || 'Bodybuilder' : 'Bodybuilder',
      technicalScore: levelScores[d.level] || 5,
      consistencyScore: Math.min(10, Math.round((d.daysPerWeek / 7) * 10)),
      strengthScore: levelScores[d.level] || 5,
      mobilityScore: 7,
      totalScore: 0,
      profileLevel: levelScores[d.level] >= 8 ? 'Advanced' : 'Beginner',
    };
  };

  return (
    <ScreenShell title="Perfil del Atleta" subtitle="Configuremos tu experiencia">
      <ScrollView style={styles.container} showsVerticalScrollIndicator={false}>
        <StepIndicator current={step} total={5} />
        
        {step === 1 && <Step1 data={data} onChange={setData} />}
        {step === 2 && <Step2 data={data} onChange={setData} />}
        {step === 3 && <Step3 data={data} onChange={setData} />}
        {step === 4 && <Step4 data={data} onChange={setData} />}
        {step === 5 && <Step5 data={data} onChange={setData} />}
        
        <View style={styles.buttons}>
          {step > 1 && (
            <Button variant="secondary" onPress={() => setStep(step - 1)}>Atrás</Button>
          )}
          {!canContinue() ? null : step < 5 ? (
            <Button onPress={handleNext}>Siguiente</Button>
          ) : (
            <Button onPress={handleNext}>Completar</Button>
          )}
        </View>
      </ScrollView>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    padding: 16,
  },
  step: {
    flex: 1,
  },
  stepTitle: {
    fontSize: 22,
    fontWeight: '900',
    marginBottom: 8,
  },
  stepSubtitle: {
    fontSize: 14,
    marginBottom: 24,
  },
  stepContent: {
    flex: 1,
  },
  buttons: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 24,
    paddingBottom: 40,
  },
});
