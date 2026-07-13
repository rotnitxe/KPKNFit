import { CoachContextSnapshot, CoachChatMessage } from '../types/coach';
import type { WorkoutOverview, WellbeingOverview } from '@kpkn/shared-types';
import type { BodyProgressEntry } from '../types/workout';
import type { SavedNutritionEntry } from '../types/nutrition';

export function buildCoachContextSnapshot(
  workoutOverview: WorkoutOverview | null,
  bodyProgress: BodyProgressEntry[],
  savedNutritionLogs: SavedNutritionEntry[],
  wellbeingOverview: WellbeingOverview | null
): CoachContextSnapshot {
  const sortedBody = [...bodyProgress].sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());
  const latestLog = sortedBody[0];
  
  const today = new Date().toISOString().slice(0, 10);
  const todayLogs = savedNutritionLogs.filter(log => log.createdAt.slice(0, 10) === today);
  
  const todayCalories = todayLogs.reduce((sum, log) => sum + (log.totals?.calories || 0), 0);
  const todayProtein = todayLogs.reduce((sum, log) => sum + (log.totals?.protein || 0), 0);

  return {
    activeProgramName: workoutOverview?.activeProgramName || null,
    weeklySessionCount: workoutOverview?.weeklySessionCount || 0,
    completedSetsThisWeek: workoutOverview?.completedSetsThisWeek || 0,
    plannedSetsThisWeek: workoutOverview?.plannedSetsThisWeek || 0,
    latestWeight: typeof latestLog?.weight === 'number' ? latestLog.weight : null,
    latestBodyFat: typeof latestLog?.bodyFatPercentage === 'number' ? latestLog.bodyFatPercentage : null,
    todayCalories,
    todayProtein,
    readiness: typeof wellbeingOverview?.latestSnapshot?.readiness === 'number'
      ? wellbeingOverview.latestSnapshot.readiness
      : null,
  };
}

export function generateCoachReply(input: {
  userText: string;
  context: CoachContextSnapshot;
  recentMessages: CoachChatMessage[];
}) {
  const { userText, context } = input;
  const q = userText.toLowerCase();
  
  let reply = "";
  
  if (q.includes("nutri") || q.includes("calor") || q.includes("prote") || q.includes("comid")) {
    reply = `Sobre tu nutrición hoy: llevas ${Math.round(context.todayCalories)} kcal y ${Math.round(context.todayProtein)}g de proteína. `;
    if (context.todayProtein < 100) {
      reply += "Intenta priorizar una fuente de proteína magra en tu siguiente comida para llegar a tus objetivos. ";
    } else {
      reply += "Vas por buen camino con la proteína hoy. ";
    }
  } else if (q.includes("entren") || q.includes("seri") || q.includes("sesi") || q.includes("progr")) {
    reply = `En tu programa "${context.activeProgramName || 'Sin nombre'}", has completado ${context.completedSetsThisWeek} de ${context.plannedSetsThisWeek} series esta semana. `;
    if (context.completedSetsThisWeek < context.plannedSetsThisWeek * 0.5) {
      reply += "Aún queda trabajo por hacer, ¡mantén el ritmo! ";
    } else {
      reply += "Excelente volumen de trabajo acumulado. ";
    }
  } else if (q.includes("readi") || q.includes("sueñ") || q.includes("fati") || q.includes("recuper")) {
    reply = `Tu readiness actual es de ${context.readiness || '--'}/10. `;
    if (context.readiness && context.readiness < 6) {
      reply += "Parece que la fatiga está alta. Considera ajustar la intensidad de hoy o priorizar el descanso. ";
    } else {
      reply += "Te ves listo para una sesión intensa. ";
    }
  } else if (q.includes("motiv") || q.includes("anim") || q.includes("gana") || q.includes("constan")) {
    reply = "La constancia es lo que construye el resultado a largo plazo. No todos los días estarás al 100%, pero cumplir con lo mínimo en los días difíciles es lo que marca la diferencia. ¡Sigue adelante! ";
  } else {
    reply = "Entiendo. Analizando tu contexto actual, veo que estás en el programa " + (context.activeProgramName || "general") + ". ¿En qué área específica (nutrición, entrenamiento o recuperación) puedo ayudarte mejor hoy? ";
  }

  reply += "\n\nRecomendaciones:\n1. Mantén la hidratación alta.\n2. Registra todos tus pesos para ajustar el volumen.\n3. Prioriza el sueño de calidad esta noche.";

  return { reply };
}

export function summarizeConversationTitle(firstUserText: string): string {
  const q = firstUserText.toLowerCase();
  if (q.includes("nutri") || q.includes("calor") || q.includes("comid")) return "Consulta Nutrición";
  if (q.includes("entren") || q.includes("rutin") || q.includes("seri")) return "Plan de Entrenamiento";
  if (q.includes("peso") || q.includes("grasa") || q.includes("body")) return "Progreso Corporal";
  if (q.includes("fati") || q.includes("sueñ") || q.includes("readi")) return "Recuperación y Fatiga";
  return "Consulta Coach AI";
}

export function generateCoachBriefing(context: CoachContextSnapshot): string {
  const lines: string[] = [];
  const activeProgram = context.activeProgramName ?? 'Sin programa activo';
  const readiness = context.readiness ?? null;
  const readinessLabel = readiness === null ? 'sin dato' : `${Math.round(readiness)}/10`;
  const sessionProgress =
    context.plannedSetsThisWeek > 0
      ? `${context.completedSetsThisWeek}/${context.plannedSetsThisWeek} series`
      : `${context.completedSetsThisWeek} series registradas`;
  const calories = Math.round(context.todayCalories);
  const protein = Math.round(context.todayProtein);

  lines.push('Resumen operativo de hoy');
  lines.push(`• Programa activo: ${activeProgram}`);
  lines.push(`• Progreso semanal: ${sessionProgress}`);
  lines.push(`• Readiness: ${readinessLabel}`);

  if (typeof context.latestWeight === 'number') {
    lines.push(`• Peso reciente: ${Math.round(context.latestWeight * 10) / 10} kg`);
  }

  if (typeof context.latestBodyFat === 'number') {
    lines.push(`• Grasa corporal reciente: ${Math.round(context.latestBodyFat * 10) / 10}%`);
  }

  lines.push(`• Nutrición de hoy: ${calories} kcal y ${protein} g de proteína.`);

  lines.push('');
  lines.push('Prioridad inmediata');

  if (readiness !== null && readiness < 6) {
    lines.push('Baja un cambio en la intensidad de la sesión y prioriza descanso, movilidad y sueño.');
  } else if (context.completedSetsThisWeek < context.plannedSetsThisWeek) {
    lines.push('Vas con retraso de volumen. Recupera una sesión limpia antes de perseguir más carga.');
  } else if (protein < 100) {
    lines.push('Tu siguiente comida debería cerrar proteína para sostener recuperación y masa magra.');
  } else {
    lines.push('Mantén la constancia: hoy te conviene sostener el ritmo sin improvisar el volumen.');
  }

  lines.push('');
  lines.push('Enfoque del coach');
  lines.push('1. Prioriza técnica estable.');
  lines.push('2. Registra lo que realmente haces.');
  lines.push('3. Ajusta nutrición y descanso antes de buscar más carga.');

  return lines.join('\n');
}
