
// components/ProgramEditor.tsx
import ProgramAdherenceWidget from './ProgramAdherenceWidget'; 
import ExerciseHistoryWidget from './ExerciseHistoryWidget'; 
import { calculateWeeklyVolume, normalizeMuscleGroup, calculateUnifiedMuscleVolume } from '../services/volumeCalculator';
import FeedbackInsights from './FeedbackInsights';
import SessionAuditAlerts from './SessionAuditAlerts';
import VolumeBudgetBar from './VolumeBudgetBar';
import AthleteProfilingWizard from './AthleteProfilingWizard';
import { AthleteProfileScore } from '../types'; // Asegúrate de que esto ya esté exportado en types.ts
import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Program, Macrocycle, Mesocycle, ProgramWeek, Session, SessionBackground, Block, Exercise, ExerciseMuscleInfo } from '../types';
import Button from './ui/Button';
import { generateImage } from '../services/aiService';
import { SparklesIcon, UploadIcon, ImageIcon, PlusIcon, TrashIcon, ChevronRightIcon, Wand2Icon, PencilIcon, TargetIcon, CheckCircleIcon, RefreshCwIcon, GridIcon, LayersIcon, ClockIcon, TrophyIcon, ArrowUpIcon, ArrowDownIcon, XIcon, ChevronDownIcon, EditIcon, DumbbellIcon, BellIcon, SearchIcon,  } from './icons';
import { storageService } from '../services/storageService';
import BackgroundEditorModal from './SessionBackgroundModal';
import { useAppContext } from '../contexts/AppContext';
import PeriodizationTemplateModal from './PeriodizationTemplateModal';
import Card from './ui/Card';
import { CaupolicanIcon } from './CaupolicanIcon';
import ReactDOM from 'react-dom';
import InteractiveWeekOverlay from './InteractiveWeekOverlay'; // <-- Nuevo import
import CustomExerciseEditorModal from './CustomExerciseEditorModal';
import { 
    ArrowLeftIcon, 
    SaveIcon, 
    MoreVerticalIcon, 
    CalendarIcon,  
    PlayIcon, 
    SettingsIcon, 
    AlertTriangleIcon, 
    BarChart2Icon, 
    TrendingUpIcon, 
} from './icons';


// --- SHARED UTILS ---

const PROGRAM_DRAFT_KEY = 'program-editor-draft';

const isProgramComplex = (p: Program | null): boolean => {
    if (!p) return false;
    if (p.structure === 'complex') return true;
    if (p.structure === 'simple') return false;
    if (p.macrocycles.length > 1) return true;
    const macro = p.macrocycles[0];
    if (!macro) return false;
    if ((macro.blocks || []).length > 1) return true;
    const block = (macro.blocks || [])[0];
    if (!block) return false;
    if (block.mesocycles.length > 1) return true;
    return false;
};

const ToggleSwitch: React.FC<{ checked: boolean; onChange: (checked: boolean) => void; isBlackAndWhite?: boolean; size?: 'sm' | 'md' }> = ({ checked, onChange, isBlackAndWhite, size = 'md' }) => {
    const h = size === 'sm' ? 'h-5' : 'h-6';
    const w = size === 'sm' ? 'w-10' : 'w-11';
    const translate = size === 'sm' ? 'translate-x-5' : 'translate-x-5'; 
    const knobSize = size === 'sm' ? 'h-4 w-4' : 'h-5 w-5';

    return (
        <button type="button" onClick={() => onChange(!checked)} className={`relative inline-flex flex-shrink-0 ${h} ${w} border-2 border-transparent rounded-full cursor-pointer transition-colors ease-in-out duration-200 focus:outline-none ${checked ? (isBlackAndWhite ? 'bg-white' : 'bg-primary-color') : 'bg-white/20'}`}>
            <span className={`inline-block ${knobSize} rounded-full shadow transform ring-0 transition ease-in-out duration-200 ${isBlackAndWhite ? 'bg-black' : 'bg-white'} ${checked ? translate : 'translate-x-0'}`} />
        </button>
    );
};

// --- WIZARD SPECIFIC DATA ---
interface TemplateOption { id: string; name: string; description: string; extendedInfo: string; type: 'simple' | 'complex'; weeks: number; icon: React.ReactNode; }
type SplitTag = 'Recomendado por KPKN' | 'Alta Frecuencia' | 'Baja Frecuencia' | 'Balanceado' | 'Alto Volumen' | 'Alta Tolerancia' | 'Personalizado' | 'Powerlifting';
interface SplitTemplate { id: string; name: string; description: string; tags: SplitTag[]; pattern: string[]; difficulty: 'Principiante' | 'Intermedio' | 'Avanzado'; }

const SPLIT_TEMPLATES: SplitTemplate[] = [
    { id: 'custom', name: 'Crear desde Cero', description: 'Lienzo en blanco.', tags: ['Personalizado'], pattern: Array(7).fill('Descanso'), difficulty: 'Avanzado' },
    { id: 'ul_x4', name: 'Upper / Lower x4', description: 'El estándar de oro. Equilibrio perfecto.', tags: ['Recomendado por KPKN', 'Balanceado', 'Alta Tolerancia'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'ppl_ul', name: 'PPL + Upper/Lower', description: 'Híbrido de 5 días. Volumen y frecuencia.', tags: ['Recomendado por KPKN', 'Alto Volumen', 'Balanceado'], pattern: ['Torso', 'Pierna', 'Descanso', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'fullbody_x3', name: 'Full Body x3', description: 'Alta frecuencia, ideal para agendas ocupadas.', tags: ['Recomendado por KPKN', 'Baja Frecuencia'], pattern: ['Cuerpo Completo A', 'Descanso', 'Cuerpo Completo B', 'Descanso', 'Cuerpo Completo C', 'Descanso', 'Descanso'], difficulty: 'Principiante' },
    { id: 'ppl_x6', name: 'Push Pull Legs x6', description: 'Máximo volumen. Solo expertos.', tags: ['Alta Frecuencia', 'Alto Volumen', 'Alta Tolerancia'], pattern: ['Empuje', 'Tirón', 'Pierna', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Avanzado' },
    
    // --- NUEVOS SPLITS AGREGADOS ---
    { id: 'ul_x6', name: 'Upper / Lower x6', description: 'Frecuencia 3 por grupo muscular. Gestión de fatiga crítica.', tags: ['Alta Frecuencia', 'Alta Tolerancia', 'Alto Volumen'], pattern: ['Torso', 'Pierna', 'Torso', 'Pierna', 'Torso', 'Pierna', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'ppl_arnold', name: 'PPL + Arnold', description: 'Lo mejor de la estética: PPL + Pecho/Espalda + Hombro/Brazo.', tags: ['Alto Volumen', 'Balanceado', 'Personalizado'], pattern: ['Empuje', 'Tirón', 'Pierna', 'Pecho/Espalda', 'Hombro/Brazo', 'Pierna', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'phat_hybrid', name: 'UL x2 + Full Body', description: 'Híbrido de 5 días. Frecuencia y repaso total.', tags: ['Balanceado', 'Alta Frecuencia'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Full Body', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'ant_post_x4', name: 'Anterior / Posterior x4', description: 'Enfoque en cadenas musculares cinéticas.', tags: ['Balanceado', 'Personalizado'], pattern: ['Cadena Anterior', 'Cadena Posterior', 'Descanso', 'Cadena Anterior', 'Cadena Posterior', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'ant_post_x6', name: 'Anterior / Posterior x6', description: 'Frecuencia agresiva por plano de movimiento.', tags: ['Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Cadena Anterior', 'Cadena Posterior', 'Cadena Anterior', 'Cadena Posterior', 'Cadena Anterior', 'Cadena Posterior', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'bro_split', name: 'Bro Split Clásico', description: 'Un grupo muscular por día. Foco máximo por sesión.', tags: ['Baja Frecuencia', 'Alto Volumen'], pattern: ['Pecho', 'Espalda', 'Piernas', 'Hombros', 'Brazos', 'Descanso', 'Descanso'], difficulty: 'Principiante' },
    { id: 'hybrid_fb_ap', name: 'Híbrido FB + Ant/Post', description: '4 días: 2 Full Body + 1 Anterior + 1 Posterior.', tags: ['Balanceado', 'Personalizado'], pattern: ['Full Body', 'Descanso', 'Full Body', 'Descanso', 'Anterior', 'Posterior', 'Descanso'], difficulty: 'Intermedio' },
    
// --- NUEVAS SUGERENCIAS CREATIVAS ---
    
    // 1. Minimalismo y Agenda Ocupada
    { id: 'minimalist_x2', name: 'Minimalista x2', description: 'Dosis mínima efectiva. Ideal para padres o ejecutivos.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body A', 'Descanso', 'Descanso', 'Full Body B', 'Descanso', 'Descanso', 'Descanso'], difficulty: 'Principiante' },
    { id: 'weekend_warrior', name: 'Guerrero de Finde', description: '¿Solo puedes entrenar el fin de semana? Esto es para ti.', tags: ['Baja Frecuencia', 'Personalizado'], pattern: ['Descanso', 'Descanso', 'Descanso', 'Descanso', 'Descanso', 'Torso/Full Body', 'Pierna/Full Body'], difficulty: 'Intermedio' },

    // 2. Enfoque Estético (Specialization)
    { id: 'glute_focus', name: 'Especialización Glúteo', description: '3 días de tren inferior con énfasis en cadena posterior.', tags: ['Personalizado', 'Alto Volumen'], pattern: ['Glúteo/Isquios', 'Torso Liviano', 'Descanso', 'Cuádriceps/Glúteo', 'Hombros/Abs', 'Glúteo Pump', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'beach_body', name: 'Torso Dominante', description: 'Enfoque "Beach Body". 3 días de torso, 1 de pierna mantenimiento.', tags: ['Personalizado', 'Alto Volumen'], pattern: ['Pecho/Espalda', 'Pierna Mantenimiento', 'Descanso', 'Hombros/Brazos', 'Descanso', 'Upper Completo', 'Descanso'], difficulty: 'Intermedio' },

    // 3. Alta Frecuencia (Nuevas tendencias)
    { id: 'fullbody_x5', name: 'Full Body x5', description: 'Estilo "Noreguian" o "Squat Every Day" lite. Volumen bajo por sesión.', tags: ['Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Full Body', 'Full Body', 'Full Body', 'Full Body', 'Full Body', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    
    // 4. Clásicos y Simples
    { id: 'push_pull_x4', name: 'Push / Pull x4', description: 'Simple y brutal. Sin día exclusivo de pierna, se integra.', tags: ['Balanceado', 'Recomendado por KPKN'], pattern: ['Empuje + Cuádriceps', 'Tirón + Isquios', 'Descanso', 'Empuje + Cuádriceps', 'Tirón + Isquios', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },

    // 5. Metodologías de Fuerza (Powerlifting Avanzado)
    { id: 'texas_method', name: 'Estilo Texas', description: 'Ondulación diaria: Volumen, Recuperación e Intensidad.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Día Volumen (5x5)', 'Descanso', 'Día Recuperación', 'Descanso', 'Día Intensidad (1RM/3RM)', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'smolov_base', name: 'Alta Frecuencia Base', description: 'Inspirado en ciclos de acumulación tipo Smolov Jr. (4 días).', tags: ['Powerlifting', 'Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Sesión 1 (4x9)', 'Descanso', 'Sesión 2 (5x7)', 'Sesión 3 (7x5)', 'Descanso', 'Sesión 4 (10x3)', 'Descanso'], difficulty: 'Avanzado' },

    // --- SPLITS POWERLIFTING ---
    { id: 'pl_sbd_x3', name: 'SBD Full Body x3', description: 'Alta especificidad. Los tres básicos, tres veces por semana.', tags: ['Powerlifting', 'Alta Tolerancia'], pattern: ['SBD Día 1', 'Descanso', 'SBD Día 2', 'Descanso', 'SBD Día 3', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'pl_hf_bench', name: 'PL: Bench Freq 4', description: 'Especialización Banca. Sq x3, Bp x4, Dl x2.', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Banca', 'Descanso', 'Sentadilla/Banca', 'Variante DL/Banca', 'Sentadilla/Accesorios', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'pl_classic_4', name: 'PL: Clásico 4 Días', description: 'Base sólida: 3 Bancas, 2 Sentadillas, 2 Pesos Muertos.', tags: ['Powerlifting', 'Balanceado', 'Recomendado por KPKN'], pattern: ['Sentadilla/Banca', 'Peso Muerto', 'Descanso', 'Banca Volumen', 'Sentadilla/Peso Muerto', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    
// --- LEYENDAS SOVIÉTICAS Y DEL ESTE (SHEIKO, SMOLOV, BÚLGARO) ---
    { id: 'sheiko_3day', name: 'Sheiko Clásico (3 Días)', description: 'Estilo soviético. Alta frecuencia de competición, gestión de fatiga brutal.', tags: ['Powerlifting', 'Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Sentadilla/Banca', 'Descanso', 'Peso Muerto/Banca', 'Descanso', 'Sentadilla/Banca', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'sheiko_4day', name: 'Sheiko 4 Días', description: 'Volumen distribuido. Para atletas que necesitan más práctica técnica.', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla', 'Banca', 'Descanso', 'Peso Muerto', 'Banca', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'bulgarian_lite', name: 'Método Búlgaro (Lite)', description: 'Inspirado en Abadjiev. Alta intensidad diaria. Solo para masoquistas.', tags: ['Alta Frecuencia', 'Alta Tolerancia', 'Powerlifting'], pattern: ['SBD Max', 'SBD Max', 'SBD Max', 'SBD Max', 'SBD Max', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'russian_bear', name: 'Oso Ruso', description: 'Volumen brutal con cargas moderadas. Hipertrofia y fuerza base.', tags: ['Alto Volumen', 'Alta Tolerancia'], pattern: ['Sentadilla/Banca', 'Descanso', 'Peso Muerto/Press', 'Descanso', 'Sentadilla/Banca', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },

    // --- ESCUELA AMERICANA (WESTSIDE, COAN, STARR) ---
    { id: 'westside_conjugate', name: 'Westside (Conjugado)', description: 'Método Louie Simmons. Días de Esfuerzo Máximo (ME) y Dinámico (DE).', tags: ['Powerlifting', 'Recomendado por KPKN', 'Balanceado'], pattern: ['ME Lower', 'ME Upper', 'Descanso', 'DE Lower', 'DE Upper', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'coan_split', name: 'Split Ed Coan', description: 'La distribución del GOAT. 4 días, linealización clásica.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Sentadilla/Pierna', 'Descanso', 'Press Banca/Pecho', 'Peso Muerto/Espalda', 'Hombros/Brazos', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'bill_starr_5x5', name: 'Bill Starr 5x5', description: 'La base del atleta de fuerza. Pesado / Liviano / Medio.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body Pesado', 'Descanso', 'Full Body Liviano', 'Descanso', 'Full Body Medio', 'Descanso', 'Descanso'], difficulty: 'Principiante' },
    { id: 'cube_method', name: 'Método Cubo', description: 'Rotación de esfuerzos (Pesado, Repeticiones, Explosivo) por Brandon Lilly.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Día Pesado (Rotativo)', 'Día Explosivo', 'Descanso', 'Día Repeticiones', 'Bodybuilding', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },

    // --- BODYBUILDING OLD SCHOOL & EUROPEO (YATES, MENTZER, ARNOLD) ---
    { id: 'dorian_yates', name: 'Blood & Guts (Yates)', description: 'HIT (High Intensity Training). Bajo volumen, fallo absoluto.', tags: ['Baja Frecuencia', 'Alta Tolerancia'], pattern: ['Hombro/Tríceps', 'Espalda', 'Descanso', 'Pecho/Bíceps', 'Piernas', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'mentzer_heavy_duty', name: 'Heavy Duty (Mentzer)', description: 'Frecuencia ultra baja. Una serie al fallo y a casa. Descanso extremo.', tags: ['Baja Frecuencia', 'Personalizado'], pattern: ['Pecho/Espalda', 'Descanso', 'Descanso', 'Piernas', 'Descanso', 'Descanso', 'Hombros/Brazos'], difficulty: 'Avanzado' },
    { id: 'arnold_classic_6', name: 'Arnold Clásico 6 Días', description: 'La rutina de la "Enciclopedia". Volumen olímpico.', tags: ['Alto Volumen', 'Alta Frecuencia'], pattern: ['Pecho/Espalda', 'Hombros/Brazos', 'Piernas', 'Pecho/Espalda', 'Hombros/Brazos', 'Piernas', 'Descanso'], difficulty: 'Avanzado' },

    // --- ESCUELA ASIÁTICA (HALTEROFILIA CHINA ADAPTADA) ---
    { id: 'chinese_hybrid', name: 'Híbrido Chino', description: 'Énfasis en Squat y Pull diario. Estructura de equipo nacional adaptada.', tags: ['Alta Frecuencia', 'Powerlifting', 'Personalizado'], pattern: ['Squat/Press', 'Pull/Accesorios', 'Squat/Press', 'Pull/Accesorios', 'Squat Max', 'Bodybuilding', 'Descanso'], difficulty: 'Avanzado' },

// --- POWERLIFTING: CLÁSICOS Y FUERZA ---
    { id: '531_bbb', name: '5/3/1 Boring But Big', description: 'El clásico de Jim Wendler. Un movimiento principal + volumen básico.', tags: ['Powerlifting', 'Balanceado', 'Baja Frecuencia'], pattern: ['Press Militar/Hombro', 'Peso Muerto/Espalda', 'Descanso', 'Press Banca/Pecho', 'Sentadilla/Pierna', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'madcow_5x5', name: 'Madcow 5x5', description: 'Progresión lineal avanzada. Frecuencia media con ondulación de cargas.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Volumen (5x5)', 'Descanso', 'Recuperación (Light)', 'Descanso', 'Intensidad (1x3/1x5)', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'korte_3x3', name: 'Korte 3x3', description: 'Escuela alemana. Solo SBD (Sentadilla, Banca, Peso Muerto), 3 veces por semana.', tags: ['Powerlifting', 'Alta Frecuencia', 'Baja Frecuencia'], pattern: ['SBD (Volumen)', 'Descanso', 'SBD (Técnica)', 'Descanso', 'SBD (Intensidad)', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'gzcl_method', name: 'Método GZCL (Tiered)', description: 'Estructura piramidal: T1 (Pesado), T2 (Volumen), T3 (Accesorios).', tags: ['Powerlifting', 'Personalizado', 'Balanceado'], pattern: ['T1 Sentadilla', 'T1 Banca', 'Descanso', 'T1 Peso Muerto', 'T1 Militar/Sling', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },

    // --- POWERLIFTING: ENFOQUES MODERNOS ---
    { id: 'tsa_inter', name: 'TSA Intermedio', description: 'The Strength Athlete. Frecuencia y especificidad moderna (4 días).', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Accesorios', 'Descanso', 'Banca/Sentadilla Var.', 'Peso Muerto/Banca Var.', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'calgary_barbell', name: 'Estilo Calgary', description: 'Alta variedad de ejercicios y gestión de fatiga precisa (4 días).', tags: ['Powerlifting', 'Alta Frecuencia', 'Recomendado por KPKN'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Press', 'Descanso', 'Sentadilla/Banca (Var.)', 'Peso Muerto (Var.)', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'deathbench_spec', name: 'Deathbench (Especialización)', description: 'Solo para fanáticos del Press Banca. Volumen masivo de empuje.', tags: ['Powerlifting', 'Alto Volumen', 'Personalizado'], pattern: ['Banca Volumen', 'Descanso', 'Tríceps/Hombro', 'Descanso', 'Banca Intensidad', 'Espalda/Bíceps', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'lilliebridge_method', name: 'Método Lilliebridge', description: 'Heavy/Light rotativo. Brutal para pesos muertos pesados.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Sentadilla/PM Pesado', 'Descanso', 'Banca Pesada', 'Descanso', 'Sentadilla/PM Ligero', 'Banca Ligera/Acc.', 'Descanso'], difficulty: 'Avanzado' },
    { id: 'conjugate_3day', name: 'Conjugado 3 Días', description: 'Westside adaptado para recuperación limitada. Rota ME y DE semanalmente.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Max Effort Lower', 'Descanso', 'Max Effort Upper', 'Descanso', 'Dynamic Effort (Full)', 'Descanso', 'Descanso'], difficulty: 'Avanzado' },

    // --- EXTRAS INTERESANTES ---
    { id: 'ul_arms', name: 'Upper / Lower + Brazos', description: 'Estructura torso/pierna con día de especialización de brazos.', tags: ['Alto Volumen', 'Personalizado'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Brazos/Hombros', 'Descanso'], difficulty: 'Intermedio' },
    { id: 'heavy_light', name: 'Pesado / Liviano x4', description: 'Gestión ondulatoria diaria. Ideal recuperación.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body Pesado', 'Descanso', 'Full Body Liviano', 'Descanso', 'Full Body Moderado', 'Descanso', 'Descanso'], difficulty: 'Intermedio' },
];

const TEMPLATES = [
    { id: 'simple-1', name: 'Lineal Simple', type: 'simple', weeks: 1, icon: <TrendingUpIcon />, description: 'Progresión cíclica estándar.' },
    { id: 'simple-2', name: 'Ondulante (A/B)', type: 'simple', weeks: 2, icon: <TrendingUpIcon />, description: 'Ciclo de 2 semanas (A/B).' },
    { id: 'power-complex', name: 'Bloques: Powerlifting', type: 'complex', weeks: 16, icon: <BarChart2Icon />, description: 'Estructura profesional de Fuerza.' },
    { id: 'bodybuilding-complex', name: 'Bloques: Culturismo', type: 'complex', weeks: 12, icon: <StarIcon />, description: 'Estructura PRO Hipertrofia.' },
];

const goalOptions: (Mesocycle['goal'])[] = ['Acumulación', 'Intensificación', 'Realización', 'Descarga', 'Custom'];

// ------------------------------------------------------------------
const InlineExerciseRow: React.FC<{
    exercise: Exercise;
    index: number;
    updateExercise: (index: number, field: any, value: any) => void;
    removeExercise: (index: number) => void;
    exerciseList: ExerciseMuscleInfo[]; // <--- AGREGADO: Definición del tipo
}> = ({ exercise, index, updateExercise, removeExercise, exerciseList }) => { // <--- AGREGADO: Destructuring (aunque no se use por ahora)
    
    // Formatting Helpers
    const formatRest = (seconds: number) => {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${m}:${s.toString().padStart(2, '0')}`;
    };

    const parseRest = (val: string) => {
        if (val.includes(':')) {
            const [m, s] = val.split(':').map(Number);
            return (m * 60) + (s || 0);
        }
        return parseInt(val) || 0;
    };

    return (
        <div className="grid grid-cols-[1.5fr_0.5fr_0.5fr_0.5fr_0.6fr_24px] gap-2 items-center border-b border-gray-100 py-2.5 last:border-0 relative hover:bg-gray-50/50 transition-colors px-1 rounded-lg">
            {/* Name - AHORA ES TEXTO ESTÁTICO (Más limpio y estable) */}
            <div className="truncate pr-2">
                 <span className="text-xs font-black text-black truncate block" title={exercise.name}>
                    {exercise.name || 'Sin nombre'}
                 </span>
            </div>

            {/* Sets */}
            <input 
                type="number" 
                placeholder="3" 
                value={exercise.sets.length} 
                onChange={(e) => updateExercise(index, 'sets', e.target.value)} 
                className="w-full bg-white border border-gray-200 rounded-md px-1 py-1 text-center text-xs font-bold text-black focus:ring-1 focus:ring-black focus:border-black"
            />

            {/* Reps */}
            <input 
                type="number" 
                placeholder="10" 
                value={exercise.sets[0]?.targetReps || ''} 
                onChange={(e) => updateExercise(index, 'reps', e.target.value)} 
                className="w-full bg-white border border-gray-200 rounded-md px-1 py-1 text-center text-xs font-bold text-black focus:ring-1 focus:ring-black focus:border-black"
            />

            {/* RPE */}
            <input 
                type="number" 
                placeholder="8" 
                max="10"
                step="0.5"
                value={exercise.sets[0]?.targetRPE || ''} 
                onChange={(e) => updateExercise(index, 'rpe', e.target.value)} 
                className="w-full bg-white border border-gray-200 rounded-md px-1 py-1 text-center text-xs font-bold text-black focus:ring-1 focus:ring-black focus:border-black"
            />

            {/* Rest (MM:SS) */}
            <input 
                type="text" 
                placeholder="1:30" 
                value={formatRest(exercise.restTime || 90)} 
                onChange={(e) => updateExercise(index, 'rest', parseRest(e.target.value))} 
                className="w-full bg-white border border-gray-200 rounded-md px-1 py-1 text-center text-xs font-bold text-black focus:ring-1 focus:ring-black focus:border-black"
            />

            {/* Remove */}
            <button onClick={() => removeExercise(index)} className="text-gray-300 hover:text-red-500 transition-colors flex justify-center h-full items-center">
                <TrashIcon size={14}/>
            </button>
        </div>
    );
}
// ------------------------------------------------------------------
// 2. MODAL EDITOR (Lógica de Inmutabilidad)
// ------------------------------------------------------------------
const SessionEditorModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    dayLabel: string;
    sessionName: string;
    onRename: (val: string) => void;
    sessionData: Session | undefined;
    onUpdateSession: (session: Session) => void;
    exerciseList: ExerciseMuscleInfo[];
}> = ({ isOpen, onClose, dayLabel, sessionName, onRename, sessionData, onUpdateSession, exerciseList }) => {
    
    if (!isOpen) return null;

    // Recuperar o Inicializar Sesión
    const currentSession = sessionData || { 
        id: crypto.randomUUID(), 
        name: sessionName, 
        description: '', 
        exercises: [], 
        parts: [] 
    };

    const handleAddExercise = () => {
        const newEx: Exercise = {
            id: crypto.randomUUID(),
            name: '',
            sets: [{ id: crypto.randomUUID(), targetReps: 10, intensityMode: 'rpe', targetRPE: 8 }],
            restTime: 90,
            trainingMode: 'reps'
        };
        onUpdateSession({
            ...currentSession,
            exercises: [...(currentSession.exercises || []), newEx]
        });
    };

    const handleUpdateExercise = (index: number, field: string, value: any) => {
        const newExercises = [...(currentSession.exercises || [])];
        const targetEx = { ...newExercises[index] };

        if (field === 'sets') {
            const count = parseInt(value) || 1;
            const tpl = targetEx.sets[0] || { id: crypto.randomUUID(), targetReps: 10, intensityMode: 'rpe', targetRPE: 8 };
            targetEx.sets = Array.from({ length: count }).map((_, i) => targetEx.sets[i] || { ...tpl, id: crypto.randomUUID() });
        } else if (field === 'reps') {
            targetEx.sets = targetEx.sets.map(s => ({ ...s, targetReps: parseInt(value) || 0 }));
        } else if (field === 'rpe') {
            targetEx.sets = targetEx.sets.map(s => ({ ...s, targetRPE: parseFloat(value) || 0 }));
        } else if (field === 'rest') {
            if (typeof value === 'string' && value.includes(':')) {
               const [m, s] = value.split(':').map(Number);
               targetEx.restTime = (m * 60) + (s || 0);
            } else {
               targetEx.restTime = parseInt(value) || 90;
            }
        } else {
            (targetEx as any)[field] = value;
        }

        newExercises[index] = targetEx;
        onUpdateSession({ ...currentSession, exercises: newExercises });
    };

    const handleRemoveExercise = (index: number) => {
        const newExercises = (currentSession.exercises || []).filter((_, i) => i !== index);
        onUpdateSession({ ...currentSession, exercises: newExercises });
    };

    return ReactDOM.createPortal(
        <div className="fixed inset-0 z-[99999] flex items-center justify-center p-4 sm:p-6 font-sans">
            <div className="absolute inset-0 bg-black/80 backdrop-blur-sm animate-in fade-in duration-200" onClick={onClose}></div>
            <div className="bg-white rounded-2xl w-full max-w-2xl max-h-[85vh] flex flex-col shadow-2xl relative z-10 animate-in zoom-in-95 duration-200 overflow-hidden transform-none">
                <div className="flex items-center justify-between p-5 border-b border-gray-100 bg-white z-20 shrink-0">
                    <div className="flex items-center gap-4">
                        <div className="w-12 h-12 rounded-xl bg-black text-white flex items-center justify-center font-black text-sm uppercase shadow-lg shadow-black/20">
                            {dayLabel.substring(0, 3)}
                        </div>
                        <div>
                            <p className="text-[10px] font-bold text-gray-400 uppercase tracking-wider mb-0.5">Editando Sesión</p>
                            <input 
                                type="text" 
                                value={sessionName}
                                onChange={(e) => onRename(e.target.value)}
                                className="text-2xl font-black uppercase tracking-tight text-black border-none p-0 focus:ring-0 w-full bg-transparent placeholder-gray-300"
                                placeholder="NOMBRE SESIÓN"
                            />
                        </div>
                    </div>
                    <button onClick={onClose} className="p-2 rounded-full hover:bg-gray-100 text-gray-400 hover:text-black transition-colors"><XIcon size={24}/></button>
                </div>

                <div className="flex-1 overflow-y-auto custom-scrollbar bg-gray-50/50 p-5 pb-32">
                    {currentSession.exercises?.map((ex, i) => (
                        <InlineExerciseRow
                            key={ex.id || `temp-${i}`}
                            exercise={ex}
                            index={i}
                            updateExercise={handleUpdateExercise}
                            removeExercise={handleRemoveExercise}
                            exerciseList={exerciseList}
                        />
                    ))}
                    <button onClick={handleAddExercise} className="w-full py-4 mt-2 bg-white border-2 border-dashed border-gray-300 rounded-xl text-xs font-bold text-gray-400 hover:text-primary-color hover:border-primary-color hover:bg-blue-50 transition-all flex items-center justify-center gap-2 group">
                        <PlusIcon size={16} className="group-hover:scale-110 transition-transform"/> Añadir Ejercicio
                    </button>
                    {(!currentSession.exercises || currentSession.exercises.length === 0) && (
                        <div className="text-center py-12 opacity-50"><DumbbellIcon size={40} className="mx-auto text-gray-300 mb-2"/><p className="text-sm text-gray-400 font-bold">Sesión vacía</p></div>
                    )}
                </div>

                <div className="p-4 border-t border-gray-100 bg-white z-20 flex justify-between items-center shrink-0">
                    <span className="text-xs font-bold text-gray-400">{currentSession.exercises?.length || 0} ejercicios</span>
                    <button onClick={onClose} className="px-8 py-3 bg-black text-white rounded-xl text-xs font-bold hover:bg-gray-800 transition-colors shadow-lg shadow-black/20 uppercase tracking-widest">
                        Guardar
                    </button>
                </div>
            </div>
        </div>,
        document.body
    );
};

// --- ADVANCED EXERCISE PICKER MODAL UNIFICADO (DARK PREMIUM + FATIGA 1-10 + LIST/GRID) ---
import { ScaleIcon, ChevronLeftIcon, MaximizeIcon, StarIcon, FlameIcon, InfoIcon, ActivityIcon } from './icons';

export const AdvancedExercisePickerModal: React.FC<{
    isOpen: boolean;
    onClose: () => void;
    onSelect: (exercise: ExerciseMuscleInfo) => void;
    onCreateNew: () => void;
    exerciseList: ExerciseMuscleInfo[];
}> = ({ isOpen, onClose, onSelect, onCreateNew, exerciseList }) => {
    const [search, setSearch] = useState('');
    const [activeCategory, setActiveCategory] = useState<string | null>(null);
    const [tooltipExId, setTooltipExId] = useState<string | null>(null);
    const [viewMode, setViewMode] = useState<'grid' | 'list'>('grid');
    
    // Multi-sorting state para modo lista
    const [sortKey, setSortKey] = useState<'name' | 'muscle' | 'fatigue'>('name');
    const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');
    const inputRef = useRef<HTMLInputElement>(null);

    const categoryMap: Record<string, string[]> = {
        'Pecho': ['pectoral', 'pecho'],
        'Espalda': ['dorsal', 'trapecio', 'espalda', 'romboide'],
        'Hombros': ['deltoide', 'hombro'],
        'Piernas': ['cuádriceps', 'cuadriceps', 'isquio', 'glúteo', 'gluteo', 'pantorrilla', 'pierna', 'femoral'],
        'Brazos': ['bíceps', 'biceps', 'tríceps', 'triceps', 'antebrazo', 'brazo'],
        'Core': ['abdomen', 'core', 'lumbar', 'espalda baja']
    };

    const topTierNames = [
        'sentadilla trasera', 'peso muerto convencional', 'peso muerto rumano', 
        'sentadilla hack', 'sentadilla pendulum', 'extensión de cuádriceps', 'sissy squat',
        'curl femoral sentado', 'curl nórdico', 'hip-thrust', 'press banca',
        'press inclinado', 'cruce de poleas', 'elevaciones laterales en polea',
        'press de hombro en máquina', 'jalón al pecho', 'dominada libre', 'remo en t', 'remo pendlay'
    ];

    const isTopTier = (exName: string) => topTierNames.some(name => exName.toLowerCase().includes(name.toLowerCase()));
    
    const getParentMuscle = (muscleName: string) => {
        const lower = muscleName.toLowerCase();
        if (lower.includes('deltoide')) return muscleName; 
        if (lower.includes('pectoral') || lower.includes('pecho')) return 'Pectoral';
        if (lower.includes('cuádriceps') || lower.includes('cuadriceps') || lower.includes('vasto') || lower.includes('recto femoral')) return 'Cuádriceps';
        if (lower.includes('bíceps') || lower.includes('biceps')) return 'Bíceps';
        if (lower.includes('tríceps') || lower.includes('triceps')) return 'Tríceps';
        if (lower.includes('isquio') || lower.includes('femoral')) return 'Isquiosurales';
        if (lower.includes('glúteo') || lower.includes('gluteo')) return 'Glúteos';
        if (lower.includes('trapecio')) return 'Trapecio';
        if (lower.includes('dorsal')) return 'Dorsal';
        if (lower.includes('gemelo') || lower.includes('pantorrilla') || lower.includes('sóleo')) return 'Pantorrillas';
        if (lower.includes('abdomen') || lower.includes('core')) return 'Abdomen';
        return muscleName;
    };

    const getPrimaryMuscleName = (ex: ExerciseMuscleInfo) => {
        const primary = ex.involvedMuscles.find(m => m.role === 'primary');
        return primary ? getParentMuscle(primary.muscle) : 'Varios';
    };

    // ALGORITMO FATIGA INTRÍNSECA (SNC + LOCAL) POR SERIE EFECTIVA (1-10)
    const calculateIntrinsicFatigue = (ex: ExerciseMuscleInfo) => {
        let score = 5; // Base moderada
        const isMultiJoint = ex.involvedMuscles.filter(m => m.role === 'primary').length > 1 || ex.involvedMuscles.length > 2;
        const equip = ex.equipment?.toLowerCase() || '';
        const isMachine = equip.includes('máquina') || equip.includes('maquina') || equip.includes('polea');
        const isFreeWeight = equip.includes('barra') || equip.includes('mancuerna');

        if (isMultiJoint) score += 3;
        else score -= 1;

        if (isMachine) score += 2; // Regla del usuario: Máquinas fatigan más
        if (isFreeWeight) score -= 1;

        return Math.max(1, Math.min(10, score));
    };

    const getFatigueUI = (score: number) => {
        if (score <= 3) return { color: 'bg-green-500', text: 'text-green-500', label: 'Baja' };
        if (score <= 7) return { color: 'bg-yellow-500', text: 'text-yellow-500', label: 'Moderada' };
        return { color: 'bg-red-500', text: 'text-red-500', label: 'Alta' };
    };

    useEffect(() => {
        if (isOpen) setTimeout(() => inputRef.current?.focus(), 50);
        else { setSearch(''); setActiveCategory(null); setTooltipExId(null); }
    }, [isOpen]);

    const handleSort = (key: 'name' | 'muscle' | 'fatigue') => {
        if (sortKey === key) setSortDir(prev => prev === 'asc' ? 'desc' : 'asc');
        else { setSortKey(key); setSortDir('asc'); }
    };

    const filteredAndSorted = useMemo(() => {
        let result = exerciseList;

        if (search) {
            result = result.filter(e => e.name.toLowerCase().includes(search.toLowerCase()));
        } else if (activeCategory) {
            if (activeCategory === 'KPKN Top Tier') result = result.filter(e => isTopTier(e.name));
            else if (activeCategory === 'Baja Fatiga') result = result.filter(e => calculateIntrinsicFatigue(e) <= 4);
            else {
                const terms = categoryMap[activeCategory] || [];
                result = result.filter(e => e.involvedMuscles.some(m => m.role === 'primary' && terms.some(term => m.muscle.toLowerCase().includes(term))));
            }
        } else if (viewMode === 'grid') {
            return []; // No mostrar lista si está en grid y sin buscar
        }

        result = [...result].sort((a, b) => {
            if (sortKey === 'name') return sortDir === 'asc' ? a.name.localeCompare(b.name) : b.name.localeCompare(a.name);
            if (sortKey === 'fatigue') {
                const fA = calculateIntrinsicFatigue(a);
                const fB = calculateIntrinsicFatigue(b);
                return sortDir === 'asc' ? fA - fB : fB - fA;
            }
            if (sortKey === 'muscle') {
                const mA = getPrimaryMuscleName(a);
                const mB = getPrimaryMuscleName(b);
                const comp = sortDir === 'asc' ? mA.localeCompare(mB) : mB.localeCompare(mA);
                // Secondary sort: Si son del mismo músculo, ordenar por fatiga (asc)
                if (comp !== 0) return comp;
                return calculateIntrinsicFatigue(a) - calculateIntrinsicFatigue(b);
            }
            return 0;
        });

        return result.slice(0, 50); // Límite por rendimiento
    }, [search, activeCategory, exerciseList, viewMode, sortKey, sortDir]);

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[99999] bg-black/60 backdrop-blur-md flex items-center justify-center p-4 sm:p-6 font-sans overflow-hidden animate-in fade-in duration-200">
            <div className="absolute inset-0" onClick={onClose} />
            <div className="bg-zinc-950 border border-white/10 shadow-2xl relative z-10 flex flex-col w-full max-w-lg max-h-[85vh] rounded-3xl overflow-hidden animate-in zoom-in-95 duration-200">
                
                {/* Cabecera Dark Premium */}
                <div className="p-4 border-b border-white/5 bg-black/50 backdrop-blur-lg shrink-0 flex flex-col gap-3">
                    <div className="flex items-center justify-between">
                        <div className="flex items-center gap-2">
                            <button onClick={() => setViewMode(prev => prev === 'grid' ? 'list' : 'grid')} className="p-2 bg-white/5 rounded-lg text-zinc-400 hover:text-white hover:bg-white/10 transition-colors">
                                {viewMode === 'grid' ? <ActivityIcon size={16} /> : <GridIcon size={16} />}
                            </button>
                            <span className="text-[10px] font-black uppercase tracking-widest text-zinc-500">
                                {viewMode === 'grid' ? 'Categorías' : 'Lista Detallada'}
                            </span>
                        </div>
                        <div className="flex items-center gap-2">
                            <button onClick={onCreateNew} className="px-3 py-1.5 bg-white/5 hover:bg-white/10 border border-white/10 rounded-lg text-[10px] font-black uppercase text-white transition-colors flex items-center gap-1">
                                <PlusIcon size={12}/> Crear
                            </button>
                            <button onClick={onClose} className="p-2 bg-white/5 rounded-full text-zinc-400 hover:text-red-500 transition-colors">
                                <XIcon size={16} />
                            </button>
                        </div>
                    </div>
                    
                    <div className="flex items-center gap-3 bg-zinc-900 border border-white/10 rounded-xl px-3 focus-within:border-white/30 transition-colors">
                        {activeCategory && !search ? (
                            <button onClick={() => setActiveCategory(null)} className="p-1 text-zinc-400 hover:text-white transition-colors"><ChevronLeftIcon size={18} /></button>
                        ) : (
                            <SearchIcon size={18} className="text-zinc-500" />
                        )}
                        <input 
                            ref={inputRef}
                            className="flex-1 bg-transparent border-none outline-none text-sm font-bold text-white placeholder-zinc-600 h-10 p-0 focus:ring-0"
                            placeholder={activeCategory ? `Buscar en ${activeCategory}...` : "Nombre del ejercicio..."}
                            value={search}
                            onChange={e => { setSearch(e.target.value); if (viewMode==='grid') setViewMode('list'); }}
                        />
                    </div>
                </div>
                
                <div className="overflow-y-auto flex-1 custom-scrollbar relative bg-zinc-950 p-2">
                    {/* VISTA GRID (MASONRY) */}
                    {viewMode === 'grid' && !search && !activeCategory ? (
                        <div className="grid grid-cols-2 gap-2 p-2 auto-rows-[80px]">
                            {[
                                { id: 'KPKN Top Tier', cols: 'col-span-2 row-span-1', border: 'border-yellow-500/30', text: 'text-yellow-500', label: '★ KPKN Top Tier' },
                                { id: 'Baja Fatiga', cols: 'col-span-1 row-span-1', border: 'border-emerald-500/30', text: 'text-emerald-500', label: 'Baja Fatiga' },
                                { id: 'Piernas', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Piernas' },
                                { id: 'Pecho', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Pecho' },
                                { id: 'Espalda', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Espalda' },
                                { id: 'Hombros', cols: 'col-span-2 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Hombros' },
                                { id: 'Brazos', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Brazos' },
                                { id: 'Core', cols: 'col-span-1 row-span-1', border: 'border-white/10', text: 'text-white', label: 'Core' },
                            ].map(cat => (
                                <button key={cat.id} onClick={() => { setActiveCategory(cat.id); setViewMode('list'); }} className={`${cat.cols} bg-black border ${cat.border} hover:border-white/50 rounded-2xl p-4 text-left flex flex-col justify-center items-start transition-all group relative overflow-hidden`}>
                                    <div className="absolute inset-0 bg-white/0 group-hover:bg-white/5 transition-colors"></div>
                                    <span className={`font-black text-sm uppercase tracking-tight relative z-10 ${cat.text}`}>{cat.label}</span>
                                </button>
                            ))}
                        </div>
                    ) : (
                        /* VISTA LISTA DETALLADA */
                        <div className="flex flex-col h-full">
                            {/* Cabecera de Ordenamiento */}
                            {filteredAndSorted.length > 0 && (
                                <div className="grid grid-cols-[2fr_1fr_1fr] gap-2 px-4 py-2 border-b border-white/5 sticky top-0 bg-zinc-950 z-20">
                                    <button onClick={() => handleSort('name')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Ejercicio {sortKey === 'name' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                    <button onClick={() => handleSort('muscle')} className="text-left flex items-center gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Músculo {sortKey === 'muscle' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                    <button onClick={() => handleSort('fatigue')} className="text-right flex items-center justify-end gap-1 text-[9px] font-black uppercase text-zinc-500 hover:text-white">Fatiga {sortKey === 'fatigue' && (sortDir === 'asc' ? '↑' : '↓')}</button>
                                </div>
                            )}

                            <div className="space-y-1 p-2">
                                {filteredAndSorted.map(ex => {
                                    const topTier = isTopTier(ex.name);
                                    const fatigueScore = calculateIntrinsicFatigue(ex);
                                    const fatigueUI = getFatigueUI(fatigueScore);
                                    const primaryMuscle = getPrimaryMuscleName(ex);
                                    
                                    // Agrupación matemática correcta (Tomando el MÁXIMO por padre, no la suma)
                                    const groupedMuscles = ex.involvedMuscles.reduce((acc, m) => {
                                        const parent = getParentMuscle(m.muscle);
                                        const value = m.role === 'primary' ? 1.0 : m.role === 'secondary' ? 0.5 : 0.4;
                                        if (!acc[parent] || value > acc[parent]) acc[parent] = value;
                                        return acc;
                                    }, {} as Record<string, number>);

                                    return (
                                        <div key={ex.id} className="w-full bg-black rounded-xl border border-white/5 hover:border-white/20 transition-all flex flex-col">
                                            <div className="flex items-center justify-between px-2 py-1">
                                                <button onClick={() => onSelect(ex)} className="flex-1 text-left py-2 px-2 grid grid-cols-[2fr_1fr_1fr] gap-2 items-center group">
                                                    <div className="flex flex-col truncate pr-2">
                                                        <span className={`font-bold text-xs truncate ${topTier ? 'text-yellow-400' : 'text-white'}`}>
                                                            {topTier && '★ '}{ex.name}
                                                        </span>
                                                        <span className="text-[9px] text-zinc-500 uppercase font-bold mt-0.5 truncate">{ex.equipment}</span>
                                                    </div>
                                                    <div className="text-[10px] text-zinc-400 font-bold truncate">
                                                        {primaryMuscle}
                                                    </div>
                                                    <div className="flex items-center justify-end gap-1.5">
                                                        <div className={`w-2 h-2 rounded-full ${fatigueUI.color} shadow-[0_0_8px_currentColor]`}></div>
                                                        <span className="text-[10px] font-black text-white">{fatigueScore}<span className="text-zinc-600">/10</span></span>
                                                    </div>
                                                </button>
                                                <button onClick={(e) => { e.stopPropagation(); setTooltipExId(tooltipExId === ex.id ? null : ex.id); }} className={`p-2 transition-colors ${tooltipExId === ex.id ? 'text-blue-400' : 'text-zinc-600 hover:text-white'}`}>
                                                    <InfoIcon size={16} />
                                                </button>
                                            </div>
                                            
                                            {/* Panel de Detalles */}
                                            {tooltipExId === ex.id && (
                                                <div className="bg-zinc-900 border-t border-white/5 p-4 animate-in slide-in-from-top-2 duration-200">
                                                    <div className="grid grid-cols-2 gap-4">
                                                        <div className="space-y-2">
                                                            <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest">Aporte (1 Serie Efectiva)</span>
                                                            <div className="space-y-1">
                                                                {Object.entries(groupedMuscles).map(([muscle, maxVal], idx) => (
                                                                    <div key={idx} className="flex justify-between items-center text-[10px]">
                                                                        <span className="font-bold text-zinc-300">{muscle}</span>
                                                                        <span className="text-zinc-400 font-mono">+{(maxVal as number).toFixed(1)}</span>
                                                                    </div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                        
                                                        <div className="space-y-2 border-l border-white/10 pl-4">
                                                            <span className="text-[9px] font-black uppercase text-zinc-500 tracking-widest block">Análisis de Fatiga</span>
                                                            <div className="bg-black border border-white/5 p-2 rounded-lg">
                                                                <div className="flex items-center justify-between mb-1">
                                                                    <span className="text-[10px] font-bold text-white">Impacto en Batería</span>
                                                                    <div className={`w-2 h-2 rounded-full ${fatigueUI.color}`}></div>
                                                                </div>
                                                                <p className="text-[9px] text-zinc-400">Castigo inherente estimado (RIR 2) hacia SNC y articulaciones: <strong className={fatigueUI.text}>{fatigueScore}/10</strong>.</p>
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>
                                            )}
                                        </div>
                                    );
                                })}
                                {filteredAndSorted.length === 0 && (
                                    <div className="text-center py-12">
                                        <DumbbellIcon size={32} className="mx-auto text-zinc-800 mb-2"/>
                                        <p className="text-xs text-zinc-500 font-bold mb-4">No se encontraron ejercicios</p>
                                        <button onClick={onCreateNew} className="px-6 py-2 bg-white text-black rounded-full font-black text-[10px] uppercase tracking-widest hover:scale-105 transition-transform">Crear Ejercicio Personalizado</button>
                                    </div>
                                )}
                            </div>
                        </div>
                    )}
                </div>
            </div>
        </div>
    );
};

const InlineSessionCreator: React.FC<{
    dayLabel: string;
    sessionName: string;
    isRest: boolean;
    sessionData: Session | undefined;
    onRename: (name: string) => void;
    onUpdateSession: (session: Session) => void;
    onMoveUp: () => void;
    onMoveDown: () => void;
    isFirst: boolean;
    isLast: boolean;
    exerciseList: ExerciseMuscleInfo[];
}> = ({ dayLabel, sessionName, isRest, sessionData, onRename, onUpdateSession, onMoveUp, onMoveDown, isFirst, isLast, exerciseList }) => {
    // --- DENTRO DE InlineSessionCreator (aprox línea 470) ---

    // 1. Necesitamos acceder a la jerarquía muscular
    const { muscleHierarchy } = useAppContext();

    // 2. Calculamos el volumen en tiempo real (NUEVO ALGORITMO UNIFICADO AVANZADO)
    const volumeStats = useMemo(() => {
        if (!sessionData?.exercises?.length) return [];
        
        const tempSession = { 
            ...sessionData, 
            id: sessionData.id || 'temp',
            parts: []
        }; 
        
        return calculateUnifiedMuscleVolume([tempSession], exerciseList)
            .filter(s => s.displayVolume > 0);
    }, [sessionData, exerciseList]);

    const [isExpanded, setIsExpanded] = useState(false);
    const [isPickerOpen, setIsPickerOpen] = useState(false); // Estado para el modal
    const [isCustomExerciseModalOpen, setIsCustomExerciseModalOpen] = useState(false);
    
    const ensureSessionExists = () => {
        if (!sessionData) {
            const newSession: Session = {
                id: crypto.randomUUID(),
                name: sessionName,
                description: '',
                exercises: [],
                parts: []
            };
            onUpdateSession(newSession);
            return newSession;
        }
        return sessionData;
    };

    const handleExpand = () => {
        if (!isRest) {
            ensureSessionExists();
            setIsExpanded(!isExpanded);
        }
    };

    // Nueva función para añadir desde el Picker
    const addExerciseFromPicker = (selected: ExerciseMuscleInfo) => {
        const session = ensureSessionExists();
        const newEx: Exercise = {
            id: crypto.randomUUID(),
            name: selected.name,
            exerciseDbId: selected.id, // Guardamos referencia real
            sets: [{ id: crypto.randomUUID(), targetReps: 10, intensityMode: 'rpe', targetRPE: 8 }],
            restTime: 90, 
            trainingMode: 'reps'
        };
        const updatedSession = { ...session, exercises: [...(session.exercises || []), newEx] };
        onUpdateSession(updatedSession);
        setIsPickerOpen(false); // Cerrar picker
    };

    const updateExercise = (index: number, field: keyof Exercise | 'sets' | 'reps' | 'rest' | 'rpe' | 'exerciseDbId', value: any) => {
        const session = ensureSessionExists();
        const exercises = [...(session.exercises || [])];
        const ex = { ...exercises[index] };

        if (field === 'sets') {
            const count = parseInt(value) || 1;
            // Mantener lógica de sets existente
            const currentSets = ex.sets || [];
            if (count > currentSets.length) {
                 const toAdd = count - currentSets.length;
                 const newSets = Array.from({length: toAdd}).map(() => ({ 
                     id: crypto.randomUUID(), 
                     targetReps: currentSets[0]?.targetReps || 10, 
                     intensityMode: 'rpe' as const, 
                     targetRPE: currentSets[0]?.targetRPE || 8 
                 }));
                 ex.sets = [...currentSets, ...newSets];
            } else if (count < currentSets.length) {
                ex.sets = currentSets.slice(0, count);
            }
        } else if (field === 'reps') {
            const reps = parseInt(value) || 0;
            ex.sets = ex.sets.map(s => ({ ...s, targetReps: reps }));
        } else if (field === 'rpe') {
            const rpe = parseFloat(value) || 0;
            ex.sets = ex.sets.map(s => ({ ...s, targetRPE: rpe }));
        } else if (field === 'rest') {
            ex.restTime = value; 
        } else {
            (ex as any)[field] = value;
        }

        exercises[index] = ex;
        onUpdateSession({ ...session, exercises });
    };

    const removeExercise = (index: number) => {
        const session = ensureSessionExists();
        const exercises = session.exercises.filter((_, i) => i !== index);
        onUpdateSession({ ...session, exercises });
    };

    return (
        <>
            {/* Modal de Selección se renderiza fuera del flujo del layout para evitar z-index issues */}

            <AdvancedExercisePickerModal 
                isOpen={isPickerOpen}
                onClose={() => setIsPickerOpen(false)}
                onSelect={addExerciseFromPicker}
                exerciseList={exerciseList}
                onCreateNew={() => {
                    setIsPickerOpen(false);
                    setIsCustomExerciseModalOpen(true);
                }}
            />

            {/* AQUÍ ENCHUFAMOS EL MODAL DE CREACIÓN QUE FALTABA */}
            {isCustomExerciseModalOpen && (
                <CustomExerciseEditorModal
                    isOpen={isCustomExerciseModalOpen}
                    onClose={() => setIsCustomExerciseModalOpen(false)}
                    isOnline={true}
                    onSave={(newEx) => {
                        addExerciseFromPicker(newEx as any);
                        setIsCustomExerciseModalOpen(false);
                    }}
                />
            )}

            <div className={`transition-all duration-300 rounded-2xl overflow-hidden shadow-sm border ${isRest ? 'bg-[#0a0a0a] border-white/5 opacity-60' : 'bg-white border-white/10 shadow-lg shadow-black/20'}`}>
                {/* Header Row */}
                <div className="flex items-center justify-between p-4">
                    <div className="flex items-center gap-3 flex-1 min-w-0">
                        <div className={`w-8 h-8 rounded-lg flex-shrink-0 flex items-center justify-center font-black text-[10px] uppercase border ${isRest ? 'bg-transparent border-white/20 text-white/50' : 'bg-black text-white border-black'}`}>
                            {dayLabel.substring(0, 3)}
                        </div>
                        <input 
                            type="text" 
                            value={sessionName}
                            onChange={(e) => onRename(e.target.value)}
                            className={`bg-transparent border-none p-0 text-lg font-black uppercase tracking-tight focus:ring-0 w-full truncate ${isRest ? 'text-white/50' : 'text-black placeholder-gray-400'}`}
                            placeholder="NOMBRE SESIÓN"
                        />
                    </div>
                    
                    <div className="flex items-center gap-2 ml-2 flex-shrink-0">
                        <button onClick={onMoveUp} disabled={isFirst} className={`p-1.5 rounded-full transition-colors ${isRest ? 'hover:bg-white/10 text-white/30' : 'hover:bg-gray-100 text-gray-400 hover:text-black'} disabled:opacity-0`}>
                            <ArrowUpIcon size={14}/>
                        </button>
                        <button onClick={onMoveDown} disabled={isLast} className={`p-1.5 rounded-full transition-colors ${isRest ? 'hover:bg-white/10 text-white/30' : 'hover:bg-gray-100 text-gray-400 hover:text-black'} disabled:opacity-0`}>
                            <ArrowDownIcon size={14}/>
                        </button>
                        {!isRest && (
                            <button onClick={handleExpand} className={`p-2 rounded-full transition-colors ml-2 ${isExpanded ? 'bg-black text-white' : 'bg-gray-100 text-black hover:bg-gray-200'}`}>
                                {isExpanded ? <ChevronDownIcon size={16} className="rotate-180"/> : <PencilIcon size={14}/>}
                            </button>
                        )}
                    </div>
                </div>

                {/* Expanded Editor */}
                {isExpanded && !isRest && (
                    <div className="px-4 pb-4 animate-fade-in space-y-3 border-t border-gray-100 pt-3">
                    
                        {/* --- PEGAR ESTO AQUÍ: WIDGET DE VOLUMEN --- */}
                        {volumeStats.length > 0 && (
                            <div className="flex gap-2 overflow-x-auto pb-3 mb-1 custom-scrollbar px-1 snap-x">
                                {volumeStats.map(stat => (
                                    <div key={stat.muscleGroup} className="snap-start flex-shrink-0 flex flex-col items-center bg-gray-50 border border-gray-200 p-2 rounded-lg min-w-[60px]">
                                        <span className="text-[8px] font-black text-gray-400 uppercase truncate max-w-full tracking-wider">{stat.muscleGroup}</span>
                                        <div className="flex items-baseline gap-0.5">
                                            <span className="text-lg font-black text-black leading-none">{stat.displayVolume}</span>
                                            <span className="text-[8px] text-gray-400 font-bold">sets</span>
                                        </div>
                                    </div>
                                ))}
                            </div>
                        )}
                        {/* ------------------------------------------- */}

                        <SessionAuditAlerts 
                                sessionExercises={sessionData?.exercises || []}
                                allExercisesDB={exerciseList as any}
                        />
                            <div className="overflow-x-auto custom-scrollbar pb-2">
                                <div className="min-w-[350px]">

                                {/* Headers */}
                                <div className="grid grid-cols-[1.5fr_0.5fr_0.5fr_0.5fr_0.6fr_24px] gap-2 mb-2 px-1">
                                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-widest">Ejercicio</span>
                                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-widest text-center">Sets</span>
                                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-widest text-center">Reps</span>
                                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-widest text-center">RPE</span>
                                    <span className="text-[9px] font-black text-gray-400 uppercase tracking-widest text-center">Desc.</span>
                                    <span></span>
                                </div>

                                <div className="space-y-1 max-h-60 overflow-y-auto custom-scrollbar pr-1">
                                    {sessionData?.exercises?.map((ex, i) => (
                                        <InlineExerciseRow
                                            key={i}
                                            index={i}
                                            exercise={ex}
                                            updateExercise={updateExercise}
                                            removeExercise={removeExercise}
                                            exerciseList={exerciseList} // <--- AGREGAR ESTA LÍNEA
                                        />
                                    ))}
                                    {(!sessionData?.exercises || sessionData.exercises.length === 0) && (
                                        <p className="text-center text-xs text-gray-400 py-4 italic">No hay ejercicios. ¡Añade uno!</p>
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* EL BOTÓN AHORA ABRE EL MODAL */}
                        <button 
                            onClick={() => setIsPickerOpen(true)}
                            className="w-full py-3 border-2 border-dashed border-gray-200 rounded-xl text-xs font-bold text-gray-400 hover:text-black hover:border-black/20 hover:bg-gray-50 transition-all flex items-center justify-center gap-2 mt-2"
                        >
                            <PlusIcon size={14}/> Añadir Ejercicio
                        </button>
                        
                        {sessionData?.exercises && sessionData.exercises.length > 0 && (
                            <div className="flex justify-between items-center pt-2 border-t border-gray-100">
                                <span className="text-[10px] text-gray-400 font-medium">
                                    {sessionData.exercises.reduce((acc, ex) => acc + ex.sets.length, 0)} series totales
                                </span>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </>
    );
};

const TimelineEventCircle: React.FC<{
    ev: any;
    displayMaxWeeks: number;
    totalProgramWeeks: number;
    maxEventWeek: number;
    onEdit: (ev: any) => void;
    onUpdateWeek: (ev: any, newWeek: number) => void;
}> = ({ ev, displayMaxWeeks, totalProgramWeeks, maxEventWeek, onEdit, onUpdateWeek }) => {
    const [dragPct, setDragPct] = useState<number | null>(null);
    const [hoverWeek, setHoverWeek] = useState<number | null>(null);

    const pos = dragPct !== null ? dragPct * 100 : ((ev.calculatedWeek + 1) / displayMaxWeeks) * 100;
    const currentCalculatedWeek = hoverWeek !== null ? hoverWeek : ev.calculatedWeek;

    // Calcular si es un período (tiene fecha de fin)
    let isPeriod = false;
    let periodWidthPct = 0;
    if (ev.date && ev.endDate) {
        const start = new Date(ev.date).getTime();
        const end = new Date(ev.endDate).getTime();
        if (end > start) {
            isPeriod = true;
            const diffWeeks = Math.ceil((end - start) / (1000 * 60 * 60 * 24 * 7));
            periodWidthPct = (diffWeeks / displayMaxWeeks) * 100;
        }
    }

    const isLastEvent = (currentCalculatedWeek + 1) === maxEventWeek;
    const diff = totalProgramWeeks - (currentCalculatedWeek + 1);
    const isPerfect = isLastEvent && diff === 0;
    const isOver = isLastEvent && diff > 0;

    let borderColor = 'border-black';
    let glow = 'shadow-lg';
    if (isPerfect) { borderColor = 'border-emerald-400'; glow = 'shadow-[0_0_20px_rgba(52,211,153,0.8)] animate-pulse'; }
    else if (isOver) { borderColor = 'border-red-500'; glow = 'shadow-[0_0_15px_rgba(239,68,68,0.6)]'; }

    const handlePointerDown = (e: React.PointerEvent) => {
        // Evitamos arrastrar eventos autogenerados por las sesiones (solo en el editor avanzado)
        if (ev.isExplicit === false) { e.stopPropagation(); return; }
        
        const el = e.currentTarget.parentElement;
        if (!el) return;
        e.preventDefault();
        e.stopPropagation();
        const rect = el.getBoundingClientRect();

        const handlePointerMove = (moveEvent: PointerEvent) => {
            let pct = (moveEvent.clientX - rect.left) / rect.width;
            pct = Math.max(0, Math.min(1, pct));
            setDragPct(pct);
            const week = Math.max(0, Math.round(pct * displayMaxWeeks) - 1);
            setHoverWeek(week);
        };

        const handlePointerUp = (upEvent: PointerEvent) => {
            document.removeEventListener('pointermove', handlePointerMove);
            document.removeEventListener('pointerup', handlePointerUp);
            let pct = (upEvent.clientX - rect.left) / rect.width;
            pct = Math.max(0, Math.min(1, pct));
            const week = Math.max(0, Math.round(pct * displayMaxWeeks) - 1);
            setDragPct(null);
            setHoverWeek(null);
            onUpdateWeek(ev, week);
        };

        document.addEventListener('pointermove', handlePointerMove);
        document.addEventListener('pointerup', handlePointerUp);
    };

    return (
        <div className={`absolute flex flex-col items-center group z-20 ${ev.isExplicit !== false ? 'cursor-grab active:cursor-grabbing' : 'cursor-pointer'}`} 
             style={{ 
                 left: isPeriod ? `calc(${pos}% + ${periodWidthPct / 2}%)` : `${pos}%`, 
                 top: '50%', 
                 transform: 'translate(-50%, -50%)', 
                 touchAction: 'none',
                 width: isPeriod ? `${Math.max(28, periodWidthPct * 5)}px` : 'auto' // Asegura un ancho mínimo para el rectángulo
             }}
             onPointerDown={handlePointerDown}
             onClick={(e) => { e.stopPropagation(); onEdit(ev); }}
        >
            <div className={`h-7 ${isPeriod ? 'w-full rounded-md px-2' : 'w-7 rounded-full'} border-2 ${borderColor} flex items-center justify-center ${glow} transition-all duration-300 overflow-hidden ${ev.type?.includes('body') ? 'bg-pink-500' : ev.type?.includes('power') ? 'bg-yellow-400' : ev.type?.includes('vacation') ? 'bg-orange-400' : 'bg-blue-500'}`}>
                <div className="flex items-center gap-1.5 shrink-0">
                    {ev.type?.includes('power') ? <TrophyIcon size={14} className="text-black"/> : ev.type?.includes('vacation') ? <span className="text-[10px]">🌴</span> : <TargetIcon size={14} className="text-white"/>}
                    {isPeriod && <span className="text-[8px] font-black uppercase text-white truncate max-w-[60px]">{ev.title}</span>}
                </div>
            </div>
            
            <div className={`absolute top-10 flex flex-col items-center transition-opacity pointer-events-none ${dragPct !== null ? 'opacity-100' : 'opacity-0 group-hover:opacity-100'} z-50`}>
                <div className="bg-black/90 backdrop-blur-md border border-white/20 text-[9px] font-black uppercase px-3 py-1.5 rounded whitespace-nowrap text-white text-center shadow-xl">
                    <span className="block text-gray-400 mb-0.5">
                        {isPeriod ? `Semana ${currentCalculatedWeek + 1} a ${currentCalculatedWeek + 1 + Math.ceil(periodWidthPct / (100/displayMaxWeeks))}` : `Semana ${currentCalculatedWeek + 1}`}
                    </span>
                    {ev.title}
                </div>
                {isPerfect && <span className="text-[8px] text-emerald-400 font-bold mt-1 bg-emerald-950/50 px-1 rounded border border-emerald-500/30">CALCE PERFECTO</span>}
                {isOver && <span className="text-[8px] text-red-400 font-bold mt-1 bg-red-950/50 px-1 rounded border border-red-500/30">PROGRAMA MUY LARGO</span>}
            </div>
        </div>
    );
};

// --- MAIN COMPONENT ---

interface ProgramEditorProps {
  onSave: (program: Program) => void;
  onCancel: () => void;
  existingProgram: Program | null;
  isOnline: boolean;
  saveTrigger: number;
  onStartProgram?: (programId: string) => void;
}

const ProgramEditor: React.FC<ProgramEditorProps> = ({ onSave, onCancel, existingProgram, isOnline, saveTrigger, onStartProgram }) => {
    const { settings, setIsDirty, isDirty: isAppContextDirty, addToast, navigateTo, exerciseList, handleStartProgram, postSessionFeedback } = useAppContext();
  
  // =================================================================
  // 1. DECLARACIÓN DE ESTADOS (Mover esto AL PRINCIPIO)
  // =================================================================
  
  // Referencias
  const nameInputRef = useRef<HTMLInputElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);
  const prevSaveTriggerRef = useRef(saveTrigger);
  const blockListRef = useRef<HTMLDivElement>(null);

  // Estados Básicos del Wizard
  const [programName, setProgramName] = useState('');
  const [autoActivate, setAutoActivate] = useState(false);
  const [selectedTemplateId, setSelectedTemplateId] = useState<string>('simple-1');
  const [competitionDate, setCompetitionDate] = useState('');
  const [wizardStep, setWizardStep] = useState(0); 
  const [activeInfo, setActiveInfo] = useState<'structure' | 'split' | null>(null);

  // Estados para el Profiling (KPKN Algorithm)
  const [showProfilingWizard, setShowProfilingWizard] = useState(false);
  const [athleteScore, setAthleteScore] = useState<AthleteProfileScore | null>(null);
  const [showCalibrationAlert, setShowCalibrationAlert] = useState(false); // Nuevo estado

  const handleAttemptNextStep = () => {
    if (!athleteScore) {
        setShowCalibrationAlert(true);
    } else {
        handleCreate(false);
    }
};

  // Estados de Configuración de Tiempo y Días
  const [startDay, setStartDay] = useState(1);
  const daysOfWeek = [{ label: 'Domingo', value: 0 }, { label: 'Lunes', value: 1 }, { label: 'Martes', value: 2 }, { label: 'Miércoles', value: 3 }, { label: 'Jueves', value: 4 }, { label: 'Viernes', value: 5 }, { label: 'Sábado', value: 6 }];
  const [cycleDuration, setCycleDuration] = useState(7);
  const [selectedSplit, setSelectedSplit] = useState<SplitTemplate | null>(null);
  const [splitPattern, setSplitPattern] = useState<string[]>(Array(7).fill('Descanso'));

  // Lógica de Roadmap Dinámico (Fechas Clave y Semanas)
  const [wizardSimpleWeeks, setWizardSimpleWeeks] = useState(1); 

  const [blockDurations, setBlockDurations] = useState<number[]>([4, 4, 4, 3, 1]); 
  
  // Nombres de bloques dinámicos según plantilla
  const wizardComplexBlocks = selectedTemplateId === 'bodybuilding-complex' 
      ? ['Volumen Base', 'Intensificación', 'Peaking Estético'] 
      : ['Hipertrofia', 'Fuerza Base', 'Volumen', 'Peaking', 'Tapering'];
  
  // Lógica de Splits por Bloque
  const [splitMode, setSplitMode] = useState<'global' | 'per_block'>('global');
  const [activeSplitBlockStep0, setActiveSplitBlockStep0] = useState(0); 
  const [blockSplits, setBlockSplits] = useState<Record<number, SplitTemplate>>({});
  const [assignmentSuccess, setAssignmentSuccess] = useState<string | null>(null);
  
  // Lógica de Diseño y Edición
  const [activeBlockEdit, setActiveBlockEdit] = useState(0); 
  const [programDesigns, setProgramDesigns] = useState<Record<number, Record<number, Session>>>({}); 
  const [applyToAllBlocks, setApplyToAllBlocks] = useState(false); 
  const [detailedSessions, setDetailedSessions] = useState<Record<number, Session>>({});

  // UI Auxiliar Wizard
  const [showAllSplitsModal, setShowAllSplitsModal] = useState(false);
  const [modalFilter, setModalFilter] = useState<SplitTag | 'Todos'>('Todos');
  const [expandedInfoId, setExpandedInfoId] = useState<string | null>(null);

  // Estados del Editor Dashboard (Edición/Legacy)
  const [program, setProgram] = useState<Program | null>(null);
  const [isComplex, setIsComplex] = useState(false);
  const [activeTab, setActiveTab] = useState<'details' | 'structure' | 'goals'>('details');
  
  const [isTemplateModalOpen, setIsTemplateModalOpen] = useState(false);
  const [wizardCurrentStep, setWizardCurrentStep] = useState(1);
  const [wizardEvents, setWizardEvents] = useState<{ id?: string, title: string, type: string, date: string, endDate?: string, calculatedWeek: number, createMacrocycle?: boolean, repeatEveryXCycles?: number }[]>([]);
    
   // Estado para el overlay de edición de semanas
  const [editingWeekInfo, setEditingWeekInfo] = useState<{
        macroIndex: number; 
        blockIndex: number; 
        mesoIndex: number; 
        weekIndex: number; 
        week: ProgramWeek;
        isSimple: boolean;
    } | null>(null);
  


  const [newGoalExercise, setNewGoalExercise] = useState('');
  const [newGoalWeight, setNewGoalWeight] = useState('');
  const [splitSearchQuery, setSplitSearchQuery] = useState('');
  const [compareList, setCompareList] = useState<string[]>([]);
  const [showCompareView, setShowCompareView] = useState(false);
  const [showCoverEditor, setShowCoverEditor] = useState(false);
  const [coverFilters, setCoverFilters] = useState({ blur: 0, brightness: 60, contrast: 100, saturation: 100, grayscale: 0 });

  // Estados para Roadmap y Eventos (Editor Dashboard)
  const [newEventDate, setNewEventDate] = useState('');
  const [newEventEndDate, setNewEventEndDate] = useState(''); // Rango Hasta
  const [newEventTitle, setNewEventTitle] = useState('');
  const [newEventType, setNewEventType] = useState('powerlifting_comp');

  const POWER_BLOCK_NAMES = ['Hipertrofia', 'Fuerza Base', 'Volumen', 'Peaking', 'Tapering'];
  
  // Datos derivados
  const availableExercises = useMemo(() => exerciseList.map(e => e.name).sort(), [exerciseList]);

// --- CÁLCULO DE VOLUMEN (VERSIÓN AGRESIVA + DEBUG) ---
  const currentWeeklyVolume = useMemo(() => {
      const volumeMap: Record<string, { total: number; breakdown: Record<string, number> }> = {};

      // Helper para limpiar strings y comparar mejor
      const cleanStr = (s: string) => s?.toLowerCase().trim().normalize("NFD").replace(/[\u0300-\u036f]/g, "") || "";

      Object.values(detailedSessions).forEach((session: any) => {
        if (!session.exercises) return;

            session.exercises.forEach((ex: any) => {
            // 1. INTENTO DE BÚSQUEDA 1: Por ID exacto
              let exInfo = exerciseList.find(e => e.id === ex.exerciseDbId);

              // 2. INTENTO DE BÚSQUEDA 2: Por Nombre (Limpiando acentos y mayúsculas)
              if (!exInfo) {
                  const searchName = cleanStr(ex.name);
                  exInfo = exerciseList.find(e => cleanStr(e.name) === searchName);
              }

              // 3. INTENTO DE BÚSQUEDA 3: Contiene el nombre (Parcial)
              if (!exInfo) {
                  const searchName = cleanStr(ex.name);
                  exInfo = exerciseList.find(e => cleanStr(e.name).includes(searchName) || searchName.includes(cleanStr(e.name)));
              }

              // Determinar series (Tú confirmaste que viene con 1, así que esto debería funcionar)
              let setsCount = 0;
              if (Array.isArray(ex.sets)) {
                  setsCount = ex.sets.length > 0 ? ex.sets.length : 0; 
              } else if (typeof ex.sets === 'number') {
                  setsCount = ex.sets;
              }

              // DEBUG: Descomenta esto si necesitas ver qué pasa en la consola
              // console.log(`Analizando: ${ex.name}`, { encontrado: !!exInfo, sets: setsCount, musculos: exInfo?.involvedMuscles });

              if (exInfo && exInfo.involvedMuscles && setsCount > 0) {
                  
                  const exerciseImpactOnParent: Record<string, number> = {};

                  exInfo.involvedMuscles.forEach(muscleData => {
                      let parentMuscle = muscleData.muscle; 
                      try {
                          parentMuscle = normalizeMuscleGroup(muscleData.muscle);
                      } catch (e) {
                          // Si falla la normalización, usamos el nombre original capitalizado
                          parentMuscle = muscleData.muscle.charAt(0).toUpperCase() + muscleData.muscle.slice(1);
                      }

                      const factor = muscleData.role === 'primary' ? 1.0 : 0.5;
                      const addedVolume = setsCount * factor;
                      
                      // Deduplicación
                      if (!exerciseImpactOnParent[parentMuscle] || addedVolume > exerciseImpactOnParent[parentMuscle]) {
                          exerciseImpactOnParent[parentMuscle] = addedVolume;
                      }

                      // Desglose
                      const specificMuscle = muscleData.muscle.charAt(0).toUpperCase() + muscleData.muscle.slice(1);
                      
                      if (!volumeMap[parentMuscle]) volumeMap[parentMuscle] = { total: 0, breakdown: {} };
                      if (!volumeMap[parentMuscle].breakdown[specificMuscle]) volumeMap[parentMuscle].breakdown[specificMuscle] = 0;
                      
                      volumeMap[parentMuscle].breakdown[specificMuscle] += addedVolume;
                  });

                  // Sumar totales
                  Object.entries(exerciseImpactOnParent).forEach(([parent, impact]) => {
                      if (!volumeMap[parent]) volumeMap[parent] = { total: 0, breakdown: {} };
                      volumeMap[parent].total += impact;
                  });
              }
          });
      });

      return volumeMap;
  }, [JSON.stringify(detailedSessions), exerciseList]);

  // Calcular límites personalizados basados en el perfil del atleta
  const volumeLimits = useMemo(() => {
      // Si estamos en modo complejo, podríamos detectar la fase del bloque activo
      // Por ahora, usamos 'Acumulación' como base para el diseño inicial
      return calculateWeeklyVolume(athleteScore, settings, 'Acumulación'); 
  }, [athleteScore, settings]);


  // =================================================================
  // 2. FUNCIONES DE LÓGICA (Ahora sí pueden acceder a los estados)
  // =================================================================

  const handleSwitchBlockEdit = (newIndex: number) => {
      // Guardar trabajo actual
      setProgramDesigns(prev => ({ ...prev, [activeBlockEdit]: detailedSessions }));
      
      const currentSplitId = blockSplits[activeBlockEdit]?.id;
      const nextSplitId = blockSplits[newIndex]?.id;

      if (currentSplitId !== nextSplitId) {
          setApplyToAllBlocks(false);
      }
      
      setActiveBlockEdit(newIndex);
      setDetailedSessions(programDesigns[newIndex] || {});

      // Actualizar Split Visual
      if (selectedTemplateId === 'power-complex' && splitMode === 'per_block') {
          const splitForBlock = blockSplits[newIndex];
          if (splitForBlock) {
              const newPattern = Array(cycleDuration).fill('Descanso').map((_, i) => splitForBlock.pattern[i] || 'Descanso');
              setSplitPattern(newPattern);
              setSelectedSplit(splitForBlock);
          }
      }
  };

  const handleUpdateSessionSmart = (dayIndex: number, session: Session) => {
      const newSessions = { ...detailedSessions, [dayIndex]: session };
      setDetailedSessions(newSessions);
      
      setProgramDesigns(prev => {
          const updated = { ...prev };
          updated[activeBlockEdit] = newSessions;
          
          if (applyToAllBlocks) {
              if (splitMode === 'global') {
                  POWER_BLOCK_NAMES.forEach((_, idx) => { updated[idx] = newSessions; });
              } else if (splitMode === 'per_block') {
                  const currentSplitId = blockSplits[activeBlockEdit]?.id;
                  if (currentSplitId) {
                      POWER_BLOCK_NAMES.forEach((_, idx) => {
                          if (blockSplits[idx]?.id === currentSplitId) { updated[idx] = newSessions; }
                      });
                  }
              }
          }
          return updated;
      });
  };

  // --- Funciones Faltantes/Reconstruidas para evitar errores ---

  // Comparación de Splits
  const toggleCompareSplit = (id: string) => {
    setCompareList(prev => prev.includes(id) ? prev.filter(x => x !== id) : [...prev, id]);
  };
  const getComparisonData = () => SPLIT_TEMPLATES.filter(s => compareList.includes(s.id));

  // Edición de Programa (Dashboard)
  const handleProgramFieldChange = (field: keyof Program, value: any) => {
    if(program) setProgram({ ...program, [field]: value });
    setIsDirty(true);
  };
  const handleProgramInfoChange = (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => {
      handleProgramFieldChange(e.target.name as keyof Program, e.target.value);
  };
  const handleFilterChange = (filter: string, value: number) => {
    setCoverFilters(prev => ({...prev, [filter]: value}));
    // Aquí deberías actualizar también el program.coverStyle si deseas persistirlo
    setIsDirty(true);
  };
  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
      const file = e.target.files?.[0];
      if (file && program) {
          const reader = new FileReader();
          reader.onloadend = () => {
              setProgram({ ...program, coverImage: reader.result as string });
          };
          reader.readAsDataURL(file);
      }
  };
  
  // Handlers de Estructura Compleja
  const handleSelectTemplate = (templateData: any) => { 
      updateProgramStructure(p => {
          p.macrocycles = templateData.macros;
          p.structure = 'complex';
          if (templateData.mode) p.mode = templateData.mode;
      });
      setIsTemplateModalOpen(false); 
      setIsComplex(true);
      addToast("Plantilla aplicada con éxito", "success");
  };

  const handleAddSpecialSession = (mIdx: number, bIdx: number, meIdx: number, wIdx: number, type: 'powerlifting' | 'bodybuilding' | '1rm' | 'admission' | 'vacation' | 'exams') => {
      updateProgramStructure(p => {
          const meso = isComplex ? p.macrocycles[mIdx].blocks![bIdx].mesocycles[meIdx] : p.macrocycles[0].blocks![0].mesocycles[0];
          const week = meso.weeks[wIdx];
          
          let name = ''; let desc = ''; let day = 6;
          if (type === 'powerlifting') { name = '🏆 COMP: POWERLIFTING'; desc = '{"type":"powerlifting_comp"}'; day = 6; }
          if (type === 'bodybuilding') { name = '✨ COMP: CULTURISMO'; desc = '{"type":"bodybuilding_comp"}'; day = 6; }
          if (type === '1rm') { name = '🎯 TEST DE 1RM'; desc = '{"type":"1rm_test"}'; day = 5; }
          if (type === 'admission') { name = '🏅 PRUEBA DE ADMISIÓN'; desc = '{"type":"admission_test"}'; day = 5; }
          if (type === 'vacation') { name = '🌴 VACACIONES / VIAJE'; desc = '{"type":"vacation"}'; day = 0; }
          if (type === 'exams') { name = '📚 SEMANA EXÁMENES'; desc = '{"type":"exams"}'; day = 0; }

          const specialSession: Session = { id: crypto.randomUUID(), name, description: desc, exercises: [], dayOfWeek: day };
          week.sessions.push(specialSession);
      });
      addToast("Evento Especial añadido al Roadmap", "success");
  };
  
  // Manejadores genéricos para arrays anidados (Macros/Bloques/Mesos/Weeks)
  const updateProgramStructure = (updater: (p: Program) => void) => {
      if (!program) return;
      const clone = JSON.parse(JSON.stringify(program));
      updater(clone);
      setProgram(clone);
      setIsDirty(true);
  };

  const handleAddMacro = () => updateProgramStructure(p => { 
        if (!p.macrocycles) p.macrocycles = [];
        p.macrocycles.push({ id: crypto.randomUUID(), name: 'Nuevo Macro', blocks: [] }); 
    });
    const handleRemoveMacro = (idx: number) => updateProgramStructure(p => { 
        if (p.macrocycles) p.macrocycles.splice(idx, 1); 
    });
    const handleMacroChange = (idx: number, val: string) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[idx]) p.macrocycles[idx].name = val; 
    });

    const handleAddBlock = (mIdx: number) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx]) {
            if (!p.macrocycles[mIdx].blocks) p.macrocycles[mIdx].blocks = [];
            p.macrocycles[mIdx].blocks!.push({ id: crypto.randomUUID(), name: 'Nuevo Bloque', mesocycles: [] });
        }
    });
    const handleRemoveBlock = (mIdx: number, bIdx: number) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx] && p.macrocycles[mIdx].blocks) {
            p.macrocycles[mIdx].blocks!.splice(bIdx, 1);
        }
    });
    const handleBlockChange = (mIdx: number, bIdx: number, val: string) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx] && p.macrocycles[mIdx].blocks && p.macrocycles[mIdx].blocks![bIdx]) {
            p.macrocycles[mIdx].blocks![bIdx].name = val;
        }
    });

    const handleAddMeso = (mIdx: number, bIdx: number) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx] && p.macrocycles[mIdx].blocks && p.macrocycles[mIdx].blocks![bIdx]) {
            if (!p.macrocycles[mIdx].blocks![bIdx].mesocycles) p.macrocycles[mIdx].blocks![bIdx].mesocycles = [];
            p.macrocycles[mIdx].blocks![bIdx].mesocycles.push({ id: crypto.randomUUID(), name: 'Nuevo Ciclo', goal: 'Acumulación', weeks: [] });
        }
    });
    const handleRemoveMeso = (mIdx: number, bIdx: number, meIdx: number) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx] && p.macrocycles[mIdx].blocks && p.macrocycles[mIdx].blocks![bIdx] && p.macrocycles[mIdx].blocks![bIdx].mesocycles) {
            p.macrocycles[mIdx].blocks![bIdx].mesocycles.splice(meIdx, 1);
        }
    });
    const handleMesoChange = (mIdx: number, bIdx: number, meIdx: number, field: string, val: any) => updateProgramStructure(p => { 
        if (p.macrocycles && p.macrocycles[mIdx] && p.macrocycles[mIdx].blocks && p.macrocycles[mIdx].blocks![bIdx] && p.macrocycles[mIdx].blocks![bIdx].mesocycles && p.macrocycles[mIdx].blocks![bIdx].mesocycles[meIdx]) {
            (p.macrocycles[mIdx].blocks![bIdx].mesocycles[meIdx] as any)[field] = val;
        }
    });

    const handleAddWeek = (mIdx: number, bIdx: number, meIdx: number) => updateProgramStructure(p => {
        if (!p.macrocycles || !p.macrocycles.length) return;
        const macro = isComplex && p.macrocycles[mIdx] ? p.macrocycles[mIdx] : p.macrocycles[0];
        if (!macro || !macro.blocks || !macro.blocks.length) return;
        const block = isComplex && macro.blocks[bIdx] ? macro.blocks[bIdx] : macro.blocks[0];
        if (!block || !block.mesocycles || !block.mesocycles.length) return;
        const meso = isComplex && block.mesocycles[meIdx] ? block.mesocycles[meIdx] : block.mesocycles[0];
        
        if (!meso.weeks) meso.weeks = [];
        meso.weeks.push({ id: crypto.randomUUID(), name: `Semana ${meso.weeks.length + 1}`, sessions: [] });
    });
    const handleRemoveWeek = (mIdx: number, bIdx: number, meIdx: number, wIdx: number) => updateProgramStructure(p => {
        if (!p.macrocycles || !p.macrocycles.length) return;
        const macro = isComplex && p.macrocycles[mIdx] ? p.macrocycles[mIdx] : p.macrocycles[0];
        if (!macro || !macro.blocks || !macro.blocks.length) return;
        const block = isComplex && macro.blocks[bIdx] ? macro.blocks[bIdx] : macro.blocks[0];
        if (!block || !block.mesocycles || !block.mesocycles.length) return;
        const meso = isComplex && block.mesocycles[meIdx] ? block.mesocycles[meIdx] : block.mesocycles[0];
        
        if (meso.weeks) meso.weeks.splice(wIdx, 1);
    });
    const handleWeekChange = (mIdx: number, bIdx: number, meIdx: number, wIdx: number, val: string) => updateProgramStructure(p => {
        if (!p.macrocycles || !p.macrocycles.length) return;
        const macro = isComplex && p.macrocycles[mIdx] ? p.macrocycles[mIdx] : p.macrocycles[0];
        if (!macro || !macro.blocks || !macro.blocks.length) return;
        const block = isComplex && macro.blocks[bIdx] ? macro.blocks[bIdx] : macro.blocks[0];
        if (!block || !block.mesocycles || !block.mesocycles.length) return;
        const meso = isComplex && block.mesocycles[meIdx] ? block.mesocycles[meIdx] : block.mesocycles[0];
        
        if (meso.weeks && meso.weeks[wIdx]) meso.weeks[wIdx].name = val;
    });

  // Manejadores de Metas (Goals)
  const handleAddGoal = () => {
      if (newGoalExercise && newGoalWeight && program) {
          setProgram({
              ...program,
              exerciseGoals: { ...program.exerciseGoals, [newGoalExercise]: parseFloat(newGoalWeight) }
          });
          setNewGoalExercise('');
          setNewGoalWeight('');
          setIsDirty(true);
      }
  };
  const handleRemoveGoal = (exName: string) => {
      if(program && program.exerciseGoals) {
          const newGoals = { ...program.exerciseGoals };
          delete newGoals[exName];
          setProgram({ ...program, exerciseGoals: newGoals });
          setIsDirty(true);
      }
  };
  
  const INFO_TEXTS = {
      structure: "Una estructura temporal es clave para crear tu programa, porque es la planificación, simple o avanzada, de lo que será tu progreso. Puede ser de una sola semana o contener múltiples semanas ordenadas como bloques. Acá eres libre de hacer la estructura que desees. Pero como punto de partida, selecciona una de las opciones.",
      split: "Un split semanal es la distribución de tus sesiones de entrenamientos dentro de los 7 días de una semana normal (o podría ser de más días si eres avanzado/a). Aquí podrás definir como punto de partida, cuándo entrenarás pierna u otro grupo muscular, esto es clave para asegurar un buen balance entre estímulo y recuperación. Aquí puedes escoger entre varias plantillas de splits conocidos, sin embargo, podrás crear uno personalizado a tú gusto cuando quieras."
  };

  // =================================================================
  // 4. EFECTOS (Inicialización y Guardado)
  // =================================================================

      // Initialize for Editing
      useEffect(() => {
        const initializeEditor = async () => {
            if (existingProgram) {
                let initialData = JSON.parse(JSON.stringify(existingProgram));
                let complexMode = isProgramComplex(existingProgram);
                
                setIsDirty(false);
                const draft = await storageService.get<{ programData: Program; associatedId: string | null }>(PROGRAM_DRAFT_KEY);
                if (draft) {
                    const draftAssociatedId = draft.associatedId;
                    const currentId = existingProgram?.id || null;
                    if (draftAssociatedId === currentId) {
                        if (window.confirm('Se encontró un borrador no guardado. ¿Restaurar?')) {
                            initialData = draft.programData;
                            complexMode = isProgramComplex(initialData);
                            setIsDirty(true);
                        } else {
                            await storageService.remove(PROGRAM_DRAFT_KEY);
                        }
                    }
                }
                setProgram(initialData);
                setIsComplex(complexMode);
                
                // Si es un borrador, restaurar el paso del wizard y los datos
                if (initialData.isDraft && initialData.lastSavedStep !== undefined && initialData.draftData) {
                    setWizardStep(initialData.lastSavedStep);
                    const draft = initialData.draftData;
                    
                    if (draft.selectedSplitId) {
                        const split = SPLIT_TEMPLATES.find(s => s.id === draft.selectedSplitId);
                        if (split) setSelectedSplit(split);
                    }
                    if (draft.detailedSessions) setDetailedSessions(draft.detailedSessions);
                    if (draft.wizardEvents) setWizardEvents(draft.wizardEvents);
                    if (draft.blockSplits) {
                        const restoredBlockSplits: Record<number, SplitTemplate> = {};
                        Object.entries(draft.blockSplits).forEach(([key, id]) => {
                            const split = SPLIT_TEMPLATES.find(s => s.id === id);
                            if (split) restoredBlockSplits[Number(key)] = split;
                        });
                        setBlockSplits(restoredBlockSplits);
                    }
                    if (draft.splitMode) setSplitMode(draft.splitMode);
                    if (draft.startDay !== undefined) setStartDay(draft.startDay);
                    if (draft.cycleDuration !== undefined) setCycleDuration(draft.cycleDuration);
                }

                if (initialData.background?.style) {
                    setCoverFilters(prev => ({
                        ...prev,
                        blur: initialData.background.style.blur || 0,
                        brightness: Math.round((1 - (initialData.background.style.brightness || 0.6)) * 100),
                    }));
                }
                if (initialData.coverStyle?.filters) {
                    setCoverFilters(prev => ({
                        ...prev,
                        contrast: initialData.coverStyle.filters.contrast,
                        saturation: initialData.coverStyle.filters.saturation,
                        grayscale: initialData.coverStyle.filters.grayscale
                    }));
                }
            }
        };
        initializeEditor();
    }, [existingProgram, setIsDirty]);


  // EFECTO: Auto-scroll al bloque activo en el Paso 0
  useEffect(() => {
      if (wizardStep === 0 && splitMode === 'per_block' && blockListRef.current) {
          const container = blockListRef.current;
          // Buscamos el botón activo dentro del contenedor
          // La estructura es: Container > div (flex) > buttons
          const buttonsContainer = container.firstElementChild;
          if (buttonsContainer && buttonsContainer.children[activeSplitBlockStep0]) {
              const activeButton = buttonsContainer.children[activeSplitBlockStep0] as HTMLElement;
              
              // Hacemos scroll suave hacia el botón
              activeButton.scrollIntoView({ 
                  behavior: 'smooth', 
                  block: 'nearest', 
                  inline: 'center' // Esto lo centra horizontalmente
              });
          }
      }
  }, [activeSplitBlockStep0, wizardStep, splitMode]);

  // Draft saving
  useEffect(() => {
    if (isAppContextDirty && program && existingProgram) {
        storageService.set(PROGRAM_DRAFT_KEY, {
            programData: program,
            associatedId: existingProgram?.id || null,
        });
    }
  }, [program, isAppContextDirty, existingProgram]);

  // External Save Trigger
  const handleSave = useCallback(async () => {
      if (program && program.name && program.name.trim()) {
          onSave(program);
          await storageService.remove(PROGRAM_DRAFT_KEY);
          setIsDirty(false);
      }
  }, [onSave, program, setIsDirty]);

  useEffect(() => {
      if (saveTrigger > prevSaveTriggerRef.current) {
          handleSave();
      }
      prevSaveTriggerRef.current = saveTrigger;
  }, [saveTrigger, handleSave]);
  
// --- MANEJADORES DE LÓGICA DE SPLITS (CON TRANSICIÓN) ---

  const handleSelectSplit = (split: SplitTemplate) => {
      // 1. Actualización Visual Inmediata
      setSelectedSplit(split);
      const newPattern = Array(cycleDuration).fill('Descanso').map((_, i) => split.pattern[i] || 'Descanso');
      setSplitPattern(newPattern);
      setShowAllSplitsModal(false);

      // 2. Lógica de Guardado y Transición
      if (selectedTemplateId === 'power-complex' && splitMode === 'per_block') {
          // Guardar selección
          setBlockSplits(prev => ({ ...prev, [activeSplitBlockStep0]: split }));
          
          // ACTIVAR TRANSICIÓN DE ÉXITO
          setAssignmentSuccess(POWER_BLOCK_NAMES[activeSplitBlockStep0]);

          // Esperar 1.5 segundos y avanzar
          setTimeout(() => {
              setAssignmentSuccess(null); // Quitar mensaje de éxito
              setSelectedSplit(null);     // Volver a la lista (reset visual)
              setSplitPattern([]);        // Limpiar patrón visual
              
              // Avanzar al siguiente bloque automáticamente si no es el último
              if (activeSplitBlockStep0 < POWER_BLOCK_NAMES.length - 1) {
                  setActiveSplitBlockStep0(prev => prev + 1);
              }
          }, 1500);

      } else {
          // Modo Global (Comportamiento estándar)
          const newBlockSplits: Record<number, SplitTemplate> = {};
          POWER_BLOCK_NAMES.forEach((_, i) => newBlockSplits[i] = split);
          setBlockSplits(newBlockSplits);
          // Aquí no auto-avanzamos ni cerramos porque el usuario podría querer editar días del global
      }
      
      setDetailedSessions({});
  };

 const handleMoveSession = (index: number, direction: 'up' | 'down') => {
      const newPattern = [...splitPattern];
      const swapIndex = direction === 'up' ? index - 1 : index + 1;
      
      if (swapIndex < 0 || swapIndex >= newPattern.length) return;
      
      [newPattern[index], newPattern[swapIndex]] = [newPattern[swapIndex], newPattern[index]];
      setSplitPattern(newPattern);

      setDetailedSessions(prev => {
          const newSessions = { ...prev };
          const sessionA = newSessions[index];
          const sessionB = newSessions[swapIndex];
          
          if (sessionA) newSessions[swapIndex] = sessionA; else delete newSessions[swapIndex];
          if (sessionB) newSessions[index] = sessionB; else delete newSessions[index];
          
          return newSessions;
      });
  };
  
  const handleUpdateInlineSession = (index: number, updatedSession: Session) => {
      setDetailedSessions(prev => ({ ...prev, [index]: updatedSession }));
      if (updatedSession.name !== splitPattern[index]) {
          const newPattern = [...splitPattern];
          newPattern[index] = updatedSession.name;
          setSplitPattern(newPattern);
      }
  };

  const handleRenameSession = (index: number, newName: string) => { 
      const newPattern = [...splitPattern]; 
      newPattern[index] = newName; 
      setSplitPattern(newPattern);
      if (detailedSessions[index]) {
          setDetailedSessions(prev => ({ ...prev, [index]: { ...prev[index], name: newName } }));
      }
  };
  
  const handleDurationChange = (val: number) => {
      setCycleDuration(val);
      if (val !== splitPattern.length) {
          setSplitPattern(Array(val).fill('Descanso'));
          setDetailedSessions({});
      }
  }

  // Nueva función de navegación segura
  const handleNextStep = () => {
      if (!programName.trim()) {
          addToast("Por favor, dale un nombre a tu programa primero.", "danger");
          nameInputRef.current?.focus();
          return;
      }
      if (!selectedSplit) {
           addToast("Selecciona una distribución (Split) para continuar.", "danger");
           return;
      }
      setWizardStep(1);
  };
  const handleCreate = (isDraft: boolean = false) => {
    const template = TEMPLATES.find(t => t.id === selectedTemplateId)!;
    const totalWeeks = template.weeks; // Para modos simples

       const newProgram: Program = { 
            id: crypto.randomUUID(), 
            name: programName, 
            description: `Estructura: ${template.name} - Split: ${selectedSplit?.name || 'Custom'}`, 
            structure: template.type as 'simple' | 'complex', // <--- AGREGAR EL "AS" AQUÍ
            mode: template.id === 'power-complex' ? 'powerlifting' : 'hypertrophy', 
            macrocycles: [] 
        };
        
        const generateSessionsForWeek = (weekId: string, globalWeekIndex: number): Session[] => {
            const sessions: Session[] = [];
            splitPattern.forEach((label, dayIndex) => {
                if (label && label.toLowerCase() !== 'descanso' && label.trim() !== '') {
                    const assignedDay = (startDay + dayIndex) % 7; 
                    const existingDetail = detailedSessions[dayIndex];
                    if (existingDetail) {
                        sessions.push({ ...existingDetail, id: crypto.randomUUID(), dayOfWeek: assignedDay });
                    } else {
                        sessions.push({ id: crypto.randomUUID(), name: label, description: '', exercises: [], dayOfWeek: assignedDay });
                    }
                }
            });
            return sessions;
        };

        if (template.id === 'power-complex' || template.id === 'bodybuilding-complex') {
             newProgram.description = `Preparación Avanzada de ${template.id === 'power-complex' ? 'Fuerza' : 'Hipertrofia'}.`;
             newProgram.mode = template.id === 'power-complex' ? 'powerlifting' : 'hypertrophy';
             
             const blocks: Block[] = wizardComplexBlocks.map((name, index) => {
                 const duration = blockDurations[index] || 4;
                 const previousWeeks = blockDurations.slice(0, index).reduce((a, b) => a + b, 0);
                 
                 return {
                     id: crypto.randomUUID(), name: `Bloque ${name}`,
                     mesocycles: [{
                         id: crypto.randomUUID(), name: name, goal: index === wizardComplexBlocks.length - 1 ? 'Realización' : (index === 0 ? 'Acumulación' : 'Intensificación'),
                         weeks: Array.from({ length: duration }, (_, i) => ({
                             id: crypto.randomUUID(), name: `Semana ${previousWeeks + i + 1}`, sessions: generateSessionsForWeek(`w${previousWeeks + i}`, previousWeeks + i)
                         }))
                     }]
                 };
             });

             newProgram.macrocycles.push({ id: crypto.randomUUID(), name: 'Macrociclo Principal', blocks: blocks });

            } else {
                // LÓGICA REFINADA: PROGRAMA SIMPLE (CÍCLICO)
                const newMacro: Macrocycle = { id: crypto.randomUUID(), name: 'Macrociclo Cíclico', blocks: [] };
                const newBlock: Block = { id: crypto.randomUUID(), name: 'BLOQUE CÍCLICO', mesocycles: [] };
                const newMeso: Mesocycle = { id: crypto.randomUUID(), name: 'Ciclo Base', goal: 'Custom', weeks: [] };
                
                // Si es un programa simple, el ciclo es estrictamente de las semanas definidas por el usuario en el template (1 o 2)
                const isAB = template.id === 'simple-2';
                const finalWeeksCount = template.weeks;
    
                for (let i = 0; i < finalWeeksCount; i++) {
                    newMeso.weeks.push({ 
                        id: crypto.randomUUID(), 
                        name: isAB ? `Semana ${i === 0 ? 'A' : 'B'}` : `Semana ${i+1}`,
                        sessions: generateSessionsForWeek(`w${i}`, i), 
                        variant: isAB ? (i === 0 ? 'A' : 'B') : 'A' 
                    });
                }
                newBlock.mesocycles.push(newMeso); 
                newMacro.blocks = [newBlock]; 
                newProgram.macrocycles.push(newMacro);
                newProgram.structure = 'simple';
            }
        
            newProgram.events = wizardEvents.map(e => ({ id: crypto.randomUUID(), title: e.title, type: e.type, date: e.date, endDate: e.endDate, calculatedWeek: e.calculatedWeek, createMacrocycle: e.createMacrocycle, repeatEveryXCycles: e.repeatEveryXCycles }));
        
            if (isDraft) {
                newProgram.isDraft = true;
                newProgram.lastSavedStep = wizardStep;
                newProgram.draftData = {
                    selectedSplitId: selectedSplit?.id,
                    detailedSessions: detailedSessions,
                    wizardEvents: wizardEvents,
                    blockSplits: Object.fromEntries(Object.entries(blockSplits).map(([k, v]) => [k, v.id])), // <-- guarda solo IDs
                    splitMode: splitMode,
                    startDay: startDay,
                    cycleDuration: cycleDuration,
                };
            }
    
            onSave(newProgram);
            
            if (isDraft) {
                addToast("Borrador guardado con éxito.", "success");
                onCancel(); // Salir al guardar borrador para volver a la vista de programas
            } else if (autoActivate && handleStartProgram) { 
                handleStartProgram(newProgram.id); 
            } else { 
                addToast("Programa creado con éxito.", "success"); 
            }
      };
    

  const getDayLabel = (offset: number) => { const dayIndex = (startDay + offset) % 7; return daysOfWeek.find(d => d.value === dayIndex)?.label || `Día ${offset + 1}`; };
  
    const filteredSplits = SPLIT_TEMPLATES.filter(split => {
        const matchesTag = modalFilter === 'Todos' ? split.id !== 'custom' : split.tags.includes(modalFilter);
        const query = splitSearchQuery.toLowerCase();
        const matchesSearch = split.name.toLowerCase().includes(query) || split.description.toLowerCase().includes(query);
        return matchesTag && matchesSearch;
    });
 const tagFilters: (SplitTag | 'Todos')[] = ['Todos', 'Recomendado por KPKN', 'Powerlifting', 'Alta Frecuencia', 'Baja Frecuencia', 'Balanceado', 'Alto Volumen', 'Alta Tolerancia'];
  
  const getDynamicFontSize = (text: string) => {
      if (!text) return 'text-4xl';
      const len = text.length;
      if (len > 35) return 'text-xl';
      if (len > 25) return 'text-2xl';
      if (len > 15) return 'text-3xl';
      return 'text-4xl';
  };

  // =================================================================
  // RENDER: DASHBOARD (EDIT MODE)
  // =================================================================
  if (existingProgram && program) {
      const simpleWeeks = program.macrocycles[0]?.blocks?.[0]?.mesocycles?.[0]?.weeks || [];
      const imageFilters = `contrast(${coverFilters.contrast}%) saturate(${coverFilters.saturation}%) grayscale(${coverFilters.grayscale}%)`;

      return (
        <div className="fixed inset-0 bg-black z-50 flex flex-col font-sans text-white overflow-hidden">
           
           {/* MODAL DE PERFILADO EN MODO EDICIÓN */}
           {showProfilingWizard && (
                <AthleteProfilingWizard 
                    onCancel={() => setShowProfilingWizard(false)}
                    onComplete={(score) => {
                        setAthleteScore(score);
                        setShowProfilingWizard(false);
                        let newMode: 'hypertrophy' | 'powerlifting' | 'powerbuilding' = 'powerbuilding';
                        if (score.trainingStyle === 'Bodybuilder') newMode = 'hypertrophy';
                        if (score.trainingStyle === 'Powerlifter') newMode = 'powerlifting';
                        
                        setProgram({ ...program, athleteProfile: score as any, mode: newMode });
                        setIsDirty(true);
                        addToast(`Perfil actualizado a: ${score.trainingStyle}`, 'success');
                    }}
                />
            )}

           <PeriodizationTemplateModal isOpen={isTemplateModalOpen} onClose={() => setIsTemplateModalOpen(false)} onSelect={handleSelectTemplate} />
            
            <div className="px-6 pt-12 pb-4 bg-black border-b border-white/10 shrink-0 z-20">
                 <div className="flex justify-between items-start mb-6">
                    <button onClick={onCancel} className="text-[10px] font-black uppercase tracking-widest text-gray-500 hover:text-white transition-colors">Cancelar</button>
                    <button onClick={handleSave} className="bg-white text-black px-6 py-2 rounded-full text-[10px] font-black uppercase tracking-widest hover:scale-105 transition-transform">Guardar Cambios</button>
                </div>
                
                <input 
                    type="text" 
                    name="name" 
                    value={program.name} 
                    onChange={handleProgramInfoChange} 
                    className="w-full bg-transparent border-none p-0 text-3xl font-black text-white uppercase tracking-tighter placeholder-gray-800 focus:ring-0" 
                    placeholder="NOMBRE DEL PROGRAMA" 
                />
                
                <div className="flex gap-4 mt-6 overflow-x-auto hide-scrollbar">
                    <button onClick={() => setActiveTab('details')} className={`text-[10px] font-black uppercase tracking-widest pb-2 border-b-2 transition-all whitespace-nowrap ${activeTab === 'details' ? 'border-white text-white' : 'border-transparent text-gray-600'}`}>Detalles</button>
                    {/* La estructura ahora se editará visualmente en el Roadmap del ProgramDetail, aquí dejamos configuraciones técnicas */}
                    <button onClick={() => setActiveTab('structure')} className={`text-[10px] font-black uppercase tracking-widest pb-2 border-b-2 transition-all whitespace-nowrap ${activeTab === 'structure' ? 'border-white text-white' : 'border-transparent text-gray-600'}`}>Configuración Técnica</button>
                    <button onClick={() => setActiveTab('goals')} className={`text-[10px] font-black uppercase tracking-widest pb-2 border-b-2 transition-all whitespace-nowrap ${activeTab === 'goals' ? 'border-white text-white' : 'border-transparent text-gray-600'}`}>Metas y Progreso</button>
                </div>
            </div>
    
            <div className="flex-1 overflow-y-auto custom-scrollbar p-6">
                <div className="max-w-2xl mx-auto space-y-8 pb-20">
    
                    {activeTab === 'details' && (
                        <div className="space-y-6 animate-fade-in">
                            <div className="space-y-2">
                                 <label className="text-[9px] font-black text-gray-500 uppercase tracking-widest">Descripción</label>
                                 <textarea 
                                    name="description" 
                                    value={program.description} 
                                    onChange={handleProgramInfoChange} 
                                    rows={3} 
                                    className="w-full bg-[#111] border border-white/10 rounded-xl p-4 text-sm text-gray-300 focus:border-white/30 focus:ring-0 outline-none transition-all placeholder:text-gray-700" 
                                    placeholder="Describe el objetivo del programa..." 
                                />
                            </div>
    
                            <div className="space-y-4">
                                <div className="flex items-center justify-between">
                                    <label className="text-[9px] font-black text-gray-500 uppercase tracking-widest flex items-center gap-2">
                                        <ImageIcon size={12}/> Diseño de Portada
                                    </label>
                                    <button onClick={() => setShowCoverEditor(!showCoverEditor)} className="text-[9px] font-bold text-white bg-white/10 px-3 py-1 rounded-full uppercase tracking-wider hover:bg-white/20 transition-colors">
                                        {showCoverEditor ? 'Ocultar Editor' : 'Personalizar'}
                                    </button>
                                </div>
                                <div className="relative aspect-video w-full rounded-2xl overflow-hidden border border-white/10 group bg-[#050505]">
                                    <div className="absolute inset-0 z-0">
                                        {program.coverImage ? (
                                             <img 
                                                src={program.coverImage} 
                                                alt="Cover" 
                                                className="w-full h-full object-cover transition-all duration-300"
                                                style={{ filter: `${imageFilters} blur(${coverFilters.blur}px)` }}
                                             />
                                        ) : (
                                            <div className="w-full h-full flex items-center justify-center bg-zinc-900">
                                                <ImageIcon size={48} className="text-zinc-800" />
                                            </div>
                                        )}
                                        <div className="absolute inset-0 bg-black transition-opacity duration-300" style={{ opacity: coverFilters.brightness / 100 }} />
                                        <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent opacity-80" />
                                    </div>
                                    <div className="absolute bottom-6 left-6 right-6 z-10">
                                        <div className="flex items-center gap-2 mb-2 opacity-80">
                                            <span className="bg-white/10 backdrop-blur-md border border-white/20 text-[8px] font-black uppercase tracking-widest px-2 py-0.5 rounded-full text-white">Programa</span>
                                        </div>
                                        <h1 className={`${getDynamicFontSize(program.name)} font-black text-white uppercase tracking-tighter leading-none`}>{program.name || 'TU PROGRAMA'}</h1>
                                    </div>
                                    {!showCoverEditor && (
                                        <div className="absolute top-4 right-4 flex gap-2">
                                            <button onClick={() => fileInputRef.current?.click()} className="p-2 bg-black/50 backdrop-blur-md rounded-full text-white hover:bg-white hover:text-black transition-all border border-white/10">
                                                <UploadIcon size={16}/>
                                            </button>
                                            <input type="file" ref={fileInputRef} onChange={handleImageUpload} className="hidden" accept="image/*" />
                                        </div>
                                    )}
                                </div>
                                {showCoverEditor && (
                                    <div className="bg-[#111] border border-white/10 rounded-xl p-4 animate-fade-in-up">
                                        <div className="grid grid-cols-2 gap-x-6 gap-y-4">
                                            <div className="space-y-2">
                                                <div className="flex justify-between text-[9px] font-bold text-gray-500 uppercase"><span>Oscuridad (Overlay)</span><span>{coverFilters.brightness}%</span></div>
                                                <input type="range" min="0" max="90" value={coverFilters.brightness} onChange={(e) => handleFilterChange('brightness', parseInt(e.target.value))} className="w-full h-1 bg-gray-800 rounded-lg appearance-none cursor-pointer accent-white"/>
                                            </div>
                                            <div className="space-y-2">
                                                <div className="flex justify-between text-[9px] font-bold text-gray-500 uppercase"><span>Desenfoque (Blur)</span><span>{coverFilters.blur}px</span></div>
                                                <input type="range" min="0" max="20" value={coverFilters.blur} onChange={(e) => handleFilterChange('blur', parseInt(e.target.value))} className="w-full h-1 bg-gray-800 rounded-lg appearance-none cursor-pointer accent-white"/>
                                            </div>
                                            <div className="space-y-2">
                                                <div className="flex justify-between text-[9px] font-bold text-gray-500 uppercase"><span>Contraste</span><span>{coverFilters.contrast}%</span></div>
                                                <input type="range" min="50" max="150" value={coverFilters.contrast} onChange={(e) => handleFilterChange('contrast', parseInt(e.target.value))} className="w-full h-1 bg-gray-800 rounded-lg appearance-none cursor-pointer accent-white"/>
                                            </div>
                                             <div className="space-y-2">
                                                <div className="flex justify-between text-[9px] font-bold text-gray-500 uppercase"><span>Saturación</span><span>{coverFilters.saturation}%</span></div>
                                                <input type="range" min="0" max="200" value={coverFilters.saturation} onChange={(e) => handleFilterChange('saturation', parseInt(e.target.value))} className="w-full h-1 bg-gray-800 rounded-lg appearance-none cursor-pointer accent-white"/>
                                            </div>
                                        </div>
                                        <div className="mt-4 pt-4 border-t border-white/5 flex gap-2">
                                            <button onClick={() => fileInputRef.current?.click()} className="flex-1 py-2 bg-white/5 hover:bg-white/10 rounded-lg text-[10px] font-bold uppercase tracking-wider text-white border border-white/10 transition-all flex items-center justify-center gap-2">
                                                <UploadIcon size={14}/> Subir Imagen
                                            </button>
                                        </div>
                                    </div>
                                )}
                            </div>    
                        </div>
                    )}
    
    {activeTab === 'structure' && (
                        <div className="space-y-8 animate-fade-in">
                            
                            <div className="bg-blue-500/10 border border-blue-500/20 rounded-2xl p-4 flex items-start gap-3">
                                <InfoIcon size={20} className="text-blue-400 shrink-0 mt-0.5" />
                                <div>
                                    <h4 className="text-[10px] font-black text-blue-400 uppercase tracking-widest mb-1">El Roadmap se ha movido</h4>
                                    <p className="text-xs text-blue-200/70 leading-relaxed">
                                        Ahora puedes editar la estructura temporal, cambiar el orden de las semanas, y gestionar tus eventos directamente desde la vista del programa en el <strong>Program Detail</strong>. Aquí solo encontrarás configuraciones técnicas.
                                    </p>
                                </div>
                            </div>

                            {/* 1. PERFIL Y MOTOR LÓGICO */}
                            <div className="bg-[#111] p-6 rounded-3xl border border-white/10 flex flex-col gap-5 relative overflow-hidden group shadow-xl">
                                <div className="absolute top-0 right-0 w-32 h-32 bg-yellow-500/10 blur-3xl rounded-full pointer-events-none"></div>
                                <div className="flex justify-between items-start relative z-10">
                                    <div>
                                        <div className="flex items-center gap-2 mb-2">
                                            <TargetIcon size={14} className="text-yellow-500" />
                                            <h4 className="text-[9px] font-black text-gray-500 uppercase tracking-widest">Motor Lógico KPKN</h4>
                                        </div>
                                        <p className="text-2xl font-black text-white uppercase tracking-tighter">
                                            {(program as any).athleteProfile?.trainingStyle || settings.athleteScore?.trainingStyle || 'Híbrido'}
                                        </p>
                                        <p className="text-[10px] text-gray-400 font-bold uppercase tracking-widest mt-1">
                                            Nivel: {(program as any).athleteProfile?.profileLevel === 'Advanced' ? 'Avanzado (High Responder)' : 'Principiante / Intermedio'}
                                        </p>
                                    </div>
                                    <button onClick={() => setShowProfilingWizard(true)} className="px-5 py-2.5 bg-white/10 hover:bg-white text-white hover:text-black rounded-xl text-[10px] font-black uppercase tracking-widest transition-all shadow-sm">
                                        Recalibrar Perfil
                                    </button>
                                </div>
                            </div>

                            {/* 2. CONFIGURACIÓN DE VOLUMEN (AUTO VS MANUAL) */}
                            <div className="bg-[#111] p-6 rounded-3xl border border-white/10 space-y-6 shadow-xl">
                                <div className="flex justify-between items-center border-b border-white/5 pb-4">
                                    <div>
                                        <h3 className="text-sm font-black text-white uppercase tracking-tight flex items-center gap-2">
                                            <ActivityIcon size={16}/> Objetivos de Volumen (Series Semanales)
                                        </h3>
                                        <p className="text-[10px] text-gray-500 mt-1 max-w-md leading-relaxed">
                                            Controla el presupuesto de series. En Automático, KPKN ajusta los límites según tu perfil. En Manual, puedes forzar tus propios límites.
                                        </p>
                                    </div>
                                    <div className="flex items-center gap-3 bg-black px-4 py-2 rounded-full border border-white/10 shrink-0">
                                        <span className={`text-[9px] font-bold uppercase tracking-widest ${(program as any).autoVolumeEnabled !== false ? 'text-yellow-400' : 'text-gray-500'}`}>
                                            Auto
                                        </span>
                                        <ToggleSwitch checked={(program as any).autoVolumeEnabled !== false} onChange={(c) => handleProgramFieldChange('autoVolumeEnabled', c)} size='sm' />
                                    </div>
                                </div>

                                {!(program as any).autoVolumeEnabled && (
                                    <div className="grid grid-cols-2 sm:grid-cols-3 gap-3 animate-fade-in-up">
                                        {['Pectoral', 'Espalda', 'Cuádriceps', 'Isquiosurales', 'Glúteos', 'Hombros', 'Brazos', 'Core'].map(muscle => {
                                            const targets = (program as any).manualVolumeTargets || {};
                                            return (
                                                <div key={muscle} className="bg-black border border-white/5 rounded-xl p-3 flex justify-between items-center group hover:border-white/20 transition-colors">
                                                    <span className="text-[10px] font-black text-gray-400 uppercase tracking-widest group-hover:text-white transition-colors">{muscle}</span>
                                                    <input 
                                                        type="number" 
                                                        value={targets[muscle] || ''} 
                                                        placeholder="Auto"
                                                        onChange={e => {
                                                            const val = parseInt(e.target.value);
                                                            handleProgramFieldChange('manualVolumeTargets', {
                                                                ...targets,
                                                                [muscle]: isNaN(val) ? undefined : val
                                                            });
                                                        }}
                                                        className="w-12 bg-zinc-900 border border-white/10 rounded-md px-2 py-1 text-xs font-bold text-white text-center focus:outline-none focus:border-yellow-400"
                                                    />
                                                </div>
                                            )
                                        })}
                                    </div>
                                )}
                            </div>

                            {/* 3. ALERTAS Y NOTIFICACIONES */}
                            <div className="bg-[#111] p-6 rounded-3xl border border-white/10 space-y-4 shadow-xl">
                                <h3 className="text-sm font-black text-white uppercase tracking-tight flex items-center gap-2 mb-4">
                                    <BellIcon size={16}/> Preferencias y Alertas
                                </h3>
                                
                                <div className="space-y-2">
                                    <div className="flex items-center justify-between p-4 bg-black rounded-2xl border border-white/5">
                                        <div>
                                            <span className="text-xs font-bold text-white uppercase tracking-wider block">Sugerencias de Descarga (Deload)</span>
                                            <span className="text-[9px] text-gray-500">KPKN te alertará cuando la fatiga acumulada detectada sea crítica.</span>
                                        </div>
                                        <ToggleSwitch checked={(program as any).alerts?.deload !== false} onChange={(c) => handleProgramFieldChange('alerts' as keyof Program, { ...(program as any).alerts, deload: c })} size="sm" />
                                    </div>
                                    
                                    <div className="flex items-center justify-between p-4 bg-black rounded-2xl border border-white/5">
                                        <div>
                                            <span className="text-xs font-bold text-white uppercase tracking-wider block">Celebración de Récords (PRs)</span>
                                            <span className="text-[9px] text-gray-500">Notificaciones visuales al superar marcas históricas de fuerza.</span>
                                        </div>
                                        <ToggleSwitch checked={(program as any).alerts?.prs !== false} onChange={(c) => handleProgramFieldChange('alerts' as keyof Program, { ...(program as any).alerts, prs: c })} size="sm" />
                                    </div>
                                </div>
                            </div>

                        </div>
                    )}

                    {activeTab === 'goals' && (
                        <div className="space-y-8 animate-fade-in">
                            <div className="flex items-center justify-between p-5 bg-[#111] rounded-2xl border border-white/10">
                                <div className="flex items-center gap-3">
                                    <BellIcon size={20} className="text-white"/>
                                    <div>
                                        <p className="text-sm font-bold text-white uppercase">Alertas de Progreso</p>
                                        <p className="text-[10px] text-gray-500 mt-0.5">Notificar al superar 1RM estimado</p>
                                    </div>
                                </div>
                                <ToggleSwitch checked={program.progressAlertsEnabled || false} onChange={(c) => handleProgramFieldChange('progressAlertsEnabled', c)} size="sm" />
                            </div>

                            <div className="space-y-4">
                                <div className="flex justify-between items-center px-1">
                                    <label className="text-[10px] font-black text-gray-500 uppercase tracking-widest">Metas de Carga (1RM)</label>
                                </div>

                                <div className="bg-[#111] p-4 rounded-2xl border border-white/10 space-y-3">
                                    <div className="grid grid-cols-2 gap-3">
                                        <select value={newGoalExercise} onChange={(e) => setNewGoalExercise(e.target.value)} className="w-full bg-black border border-white/10 rounded-xl px-3 py-2 text-xs font-bold text-white focus:border-white transition-colors outline-none">
                                            <option value="">Seleccionar Ejercicio...</option>
                                            {availableExercises.map(ex => (
                                                <option key={ex} value={ex}>{ex}</option>
                                            ))}
                                        </select>
                                        <div className="flex items-center gap-2">
                                            <input type="number" value={newGoalWeight} onChange={(e) => setNewGoalWeight(e.target.value)} placeholder="Peso" className="w-full bg-black border border-white/10 rounded-xl px-3 py-2 text-xs font-bold text-white focus:border-white transition-colors outline-none text-center" />
                                            <span className="text-[10px] font-bold text-gray-500">{settings.weightUnit}</span>
                                        </div>
                                    </div>
                                    <button onClick={handleAddGoal} disabled={!newGoalExercise || !newGoalWeight} className="w-full py-2 bg-white text-black text-[10px] font-black uppercase rounded-lg hover:bg-gray-200 transition-colors disabled:opacity-50 disabled:cursor-not-allowed">
                                        + Añadir Meta
                                    </button>
                                </div>

                                <div className="space-y-2">
                                    {program.exerciseGoals && Object.entries(program.exerciseGoals).length > 0 ? (
                                        Object.entries(program.exerciseGoals).map(([exercise, weight]) => (
                                            <div key={exercise} className="flex items-center justify-between p-3 bg-transparent border-b border-white/10 group hover:bg-white/5 transition-colors">
                                                <span className="text-xs font-bold text-white">{exercise}</span>
                                                <div className="flex items-center gap-4">
                                                    <span className="text-sm font-mono font-black text-white">{weight}<span className="text-[10px] text-gray-500 ml-1">{settings.weightUnit}</span></span>
                                                    <button onClick={() => handleRemoveGoal(exercise)} className="text-gray-600 hover:text-white transition-colors"><XIcon size={14}/></button>
                                                </div>
                                            </div>
                                        ))
                                    ) : (
                                        <div className="text-center py-8 border-2 border-dashed border-white/5 rounded-2xl">
                                            <TargetIcon size={24} className="mx-auto text-gray-700 mb-2"/>
                                            <p className="text-[10px] text-gray-600 font-bold uppercase tracking-widest">Sin metas definidas</p>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    )}
    
                </div>
            </div>
        </div>
      );
  }

  // =================================================================
  // RENDER: WIZARD (CREATION MODE) - PAGINATED STEPS
  // =================================================================
  return (
        <div className="fixed inset-0 bg-black z-[200] flex flex-col animate-fade-in text-white font-sans">

        {/* --- KPKN PASO CERO: PROFILING WIZARD --- */}
            {showProfilingWizard && (
                <AthleteProfilingWizard 
                    onCancel={() => setShowProfilingWizard(false)}
                    onComplete={(score) => {
                        setAthleteScore(score);
                        setShowProfilingWizard(false);
                        // Opcional: Auto-seleccionar template según perfil
                        addToast(`Perfil detectado: ${score.profileLevel === 'Advanced' ? 'Avanzado (High Responder)' : 'Principiante (Low Responder)'}`, 'success');
                    }}
                />
            )}
            
            {/* Header / Title - Common for both steps */}
            <div className="pt-8 pb-4 px-6 bg-black flex-shrink-0 z-20 border-b border-white/5">
                 <div className="relative max-w-md mx-auto text-center">
                 <div className="flex justify-between items-center mb-2">
                        <button onClick={onCancel} className="text-gray-500 hover:text-red-500 transition-colors" title="Salir del Wizard">
                            <XIcon size={20}/>
                        </button>
                        <h2 className="text-[10px] font-black uppercase tracking-[0.4em] text-slate-500 flex-1 text-center">
                            Creación de Programa
                        </h2>
                     </div>
                     <input
                        ref={nameInputRef}
                        type="text"
                        value={programName}
                        onChange={(e) => setProgramName(e.target.value)}
                        placeholder="NOMBRE DEL PROGRAMA"
                        className="w-full bg-transparent border-none py-2 text-2xl font-black text-center text-white placeholder-white/20 focus:ring-0 transition-all uppercase tracking-tighter"
                        autoFocus={true}
                    />
                </div>
            </div>

            {/* Main Content Area */}
            <div className="flex-1 overflow-y-auto custom-scrollbar px-4 pb-32 scroll-smooth bg-[#050505]">
                <div className="max-w-md mx-auto py-8 relative">
                    
                    {/* === PASO 0: SELECCIÓN DE ESTRUCTURA Y SPLIT === */}
                    {wizardStep === 0 && (
                        <div className="space-y-8 animate-fade-in px-2 transition-all duration-500 ease-out relative">
                            
                            {/* 1.1 Time Structure */}
                            <div className="space-y-4">
                                <div className="text-center px-4 flex items-center justify-center gap-2">
                                    <h3 className="text-sm font-black text-white uppercase tracking-[0.2em]">Estructura Temporal</h3>
                                    <button onClick={() => setActiveInfo('structure')} className="text-gray-500 hover:text-white transition-colors"><InfoIcon size={14} /></button>
                                </div>

                            {/* ALERTA DE CALIBRACIÓN MODAL */}
                            {showCalibrationAlert && (
                                <div className="fixed inset-0 z-[9999] bg-black/80 backdrop-blur-md flex items-center justify-center p-6 animate-fade-in font-sans">
                                    <div className="bg-[#111] border border-white/10 rounded-3xl p-6 max-w-sm w-full shadow-2xl text-center relative overflow-hidden">
                                        <div className="absolute top-0 left-0 w-full h-1 bg-yellow-500"></div>
                                        <div className="w-16 h-16 bg-yellow-500/10 rounded-full flex items-center justify-center mx-auto mb-4 border border-yellow-500/20">
                                            <TargetIcon size={32} className="text-yellow-500" />
                                        </div>
                                        <h3 className="text-xl font-black text-white uppercase tracking-tight mb-2">Paso Recomendado</h3>
                                        <p className="text-xs text-gray-400 font-medium leading-relaxed mb-6">
                                            Identificar tu nivel de atleta permite calcular límites exactos de volumen y fatiga para ti. ¿Quieres calibrar tu perfil antes de continuar?
                                        </p>
                                        <div className="flex flex-col gap-3">
                                            <button onClick={() => { setShowCalibrationAlert(false); setShowProfilingWizard(true); }} className="w-full py-4 bg-white text-black rounded-xl font-black text-xs uppercase tracking-widest hover:scale-105 transition-transform shadow-lg shadow-white/10">
                                                Calibrar Perfil
                                            </button>
                                            <button onClick={() => { setShowCalibrationAlert(false); handleCreate(false); }} className="text-[10px] font-bold text-gray-500 hover:text-white uppercase tracking-widest transition-colors py-2">
                                                Omitir por ahora
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            )}

                            {/* Banner de Calibración Prominente */}
                            <div className="mb-10 px-4">
                                <button 
                                    onClick={() => setShowProfilingWizard(true)}
                                    className={`w-full relative overflow-hidden rounded-3xl p-6 text-left transition-all duration-300 group border-2
                                        ${athleteScore 
                                            ? 'bg-gradient-to-br from-emerald-950/40 to-black border-emerald-500/30 hover:border-emerald-400' 
                                            : 'bg-gradient-to-br from-zinc-900 to-black border-white/10 hover:border-white/40'
                                        }
                                    `}
                                >
                                    <div className="flex justify-between items-center relative z-10">
                                        <div>
                                            <div className="flex items-center gap-2 mb-2">
                                                <div className={`w-2.5 h-2.5 rounded-full shadow-[0_0_10px_currentColor] animate-pulse-slow ${athleteScore ? 'text-emerald-500 bg-emerald-500' : 'text-yellow-500 bg-yellow-500'}`}></div>
                                                <span className="text-[9px] font-black uppercase tracking-[0.2em] text-gray-400">Paso Inicial Recomendado</span>
                                            </div>
                                            <h3 className="text-xl font-black text-white uppercase tracking-tighter">Calibrador KPKN</h3>
                                            <p className="text-xs text-gray-500 font-medium mt-1 pr-8">
                                                {athleteScore 
                                                    ? `Perfil detectado: ${athleteScore.profileLevel === 'Advanced' ? 'Avanzado' : 'Principiante'}` 
                                                    : 'Identifica tu nivel para ajustar el volumen a tu medida.'
                                                }
                                            </p>
                                        </div>
                                        <div className={`p-3 rounded-full transition-colors ${athleteScore ? 'bg-emerald-500/20 text-emerald-400' : 'bg-white/10 text-white group-hover:bg-white group-hover:text-black'}`}>
                                            {athleteScore ? <CheckCircleIcon size={24} /> : <TargetIcon size={24} />}
                                        </div>
                                    </div>
                                </button>
                            </div>

                                <p className="text-[10px] text-slate-400 text-center -mt-2">Duración y enfoque macro.</p>

                                {/* Carrusel de Plantillas */}
                                <div className="flex overflow-x-auto snap-x snap-mandatory gap-4 pb-4 -mx-4 px-4 hide-scrollbar items-start">
                                    {TEMPLATES.map((template) => {
                                        const isSelected = selectedTemplateId === template.id;
                                        const isPowerlifting = template.id === 'power-complex';
                                        return (
                                            <button
                                                key={template.id}
                                                onClick={() => setSelectedTemplateId(template.id)}
                                                className={`snap-center shrink-0 w-64 p-5 rounded-[2rem] flex flex-col items-center text-center transition-all duration-300 ease-out border relative overflow-hidden group
                                                    ${isSelected
                                                        ? (isPowerlifting ? 'bg-yellow-400 text-black border-yellow-400 scale-[1.02] shadow-lg shadow-yellow-900/20' : 'bg-white text-black border-white scale-[1.02] shadow-lg shadow-white/10')
                                                        : 'bg-black text-white border-white/10 opacity-60 hover:opacity-100 hover:scale-[1.01] hover:border-white/30'
                                                    }
                                                `}
                                            >
                                                <div className={`p-3 rounded-full mb-3 transition-colors duration-300 ${isSelected ? 'bg-black/10' : 'bg-white/10'}`}>
                                                    {React.cloneElement(template.icon as React.ReactElement<any>, { className: isSelected ? 'text-black' : 'text-white', size: 20 })}
                                                </div>
                                                <h3 className="text-lg font-black uppercase tracking-tight leading-none mb-2">{template.name}</h3>
                                                <p className={`text-[10px] font-bold leading-relaxed mb-0 transition-colors duration-300 ${isSelected ? 'text-black/70' : 'text-slate-400'}`}>{template.description}</p>
                                            </button>
                                        );
                                    })}
                                </div>
                            </div>

                            {/* 1.2 Split Selection Area */}
                            <div className="space-y-6 relative min-h-[400px] w-full">
                                
                                {/* Título de Sección */}
                                <div className="text-center px-4 flex flex-col items-center justify-center gap-2 mb-8">
                                    <div className="flex items-center gap-2 border-b border-white/20 pb-2 px-8">
                                        <h3 className="text-sm font-black text-white uppercase tracking-[0.3em]">Distribución Semanal</h3>
                                        <button onClick={() => setActiveInfo('split')} className="text-white/50 hover:text-white transition-colors"><InfoIcon size={14} /></button>
                                    </div>
                                    <p className="text-[10px] text-gray-500 font-mono uppercase tracking-widest mt-2">
                                        {splitMode === 'per_block' 
                                            ? `Configurando: ${POWER_BLOCK_NAMES[activeSplitBlockStep0]}` 
                                            : "Base única para todo el ciclo"}
                                    </p>
                                </div>

                                {/* A. SELECTOR DE MODO (Global vs Por Bloque) - DISEÑO B&W PURO */}
                                {selectedTemplateId === 'power-complex' && (
                                    <div className="px-4 mb-8">
                                        <div className="w-full max-w-md mx-auto bg-black border border-white/30 rounded-full p-1.5 flex relative">
                                            {/* Fondo animado (opcional, o cambio directo de color) */}
                                            <button 
                                                onClick={() => {
                                                    setSplitMode('global');
                                                    if (selectedSplit) {
                                                        const newSplits: Record<number, SplitTemplate> = {};
                                                        POWER_BLOCK_NAMES.forEach((_, i) => newSplits[i] = selectedSplit);
                                                        setBlockSplits(newSplits);
                                                    }
                                                }}
                                                className={`flex-1 py-3 rounded-full text-[10px] font-black uppercase tracking-widest transition-all duration-300 flex items-center justify-center gap-2
                                                    ${splitMode === 'global' ? 'bg-white text-black shadow-lg' : 'text-gray-500 hover:text-white'}
                                                `}
                                            >
                                                <span>Global</span>
                                            </button>
                                            <button 
                                                onClick={() => {
                                                    setSplitMode('per_block');
                                                    if (selectedSplit) {
                                                        setBlockSplits(prev => ({ ...prev, [activeSplitBlockStep0]: selectedSplit }));
                                                    }
                                                }}
                                                className={`flex-1 py-3 rounded-full text-[10px] font-black uppercase tracking-widest transition-all duration-300 flex items-center justify-center gap-2
                                                    ${splitMode === 'per_block' ? 'bg-white text-black shadow-lg' : 'text-gray-500 hover:text-white'}
                                                `}
                                            >
                                                <span>Por Bloques</span>
                                            </button>
                                        </div>
                                    </div>
                                )}

                                {/* B. SELECTOR DE BLOQUES (Scroll Full-Width con Auto-Scroll) */}
                                {selectedTemplateId === 'power-complex' && splitMode === 'per_block' && (
                                    <div className="mb-8 w-full">
                                        {/* AGREGAMOS ref={blockListRef} AQUÍ */}
                                        <div ref={blockListRef} className="overflow-x-auto hide-scrollbar -mx-4 px-4 md:-mx-8 md:px-8 scroll-smooth">
                                            <div className="flex gap-3 min-w-max pb-4">
                                                {POWER_BLOCK_NAMES.map((name, idx) => {
                                                    // ... (el resto del código de los botones se mantiene igual)
                                                    const isActive = activeSplitBlockStep0 === idx;
                                                    const assignedSplit = blockSplits[idx];
                                                    const isJustAssigned = assignmentSuccess === name;

                                                    return (
                                                        <button
                                                            key={idx}
                                                            onClick={() => {
                                                                if (!assignmentSuccess) {
                                                                    setActiveSplitBlockStep0(idx);
                                                                    if (assignedSplit) {
                                                                        setSelectedSplit(assignedSplit);
                                                                        const pat = Array(cycleDuration).fill('Descanso').map((_, i) => assignedSplit.pattern[i] || 'Descanso');
                                                                        setSplitPattern(pat);
                                                                    } else {
                                                                        setSelectedSplit(null);
                                                                        setSplitPattern(Array(cycleDuration).fill('Descanso'));
                                                                    }
                                                                }
                                                            }}
                                                            className={`
                                                                relative flex flex-col items-start justify-center p-5 min-w-[140px] rounded-2xl border-2 transition-all duration-300 group
                                                                ${isActive 
                                                                    ? 'bg-white border-white text-black scale-[1.02] shadow-[0_10px_30px_-10px_rgba(255,255,255,0.3)]' 
                                                                    : 'bg-black border-white/10 text-gray-500 hover:border-white hover:text-white'
                                                                }
                                                            `}
                                                        >
                                                            <span className={`absolute top-3 right-4 text-[40px] font-black leading-none opacity-5 pointer-events-none ${isActive ? 'text-black' : 'text-white'}`}>
                                                                {idx + 1}
                                                            </span>
                                                            <span className="text-[9px] font-black uppercase tracking-widest mb-1 z-10">{name}</span>
                                                            <div className="flex items-center gap-1.5 z-10">
                                                                <div className={`w-1.5 h-1.5 rounded-full ${assignedSplit ? (isActive ? 'bg-black' : 'bg-white') : 'bg-gray-700'}`}></div>
                                                                <span className={`text-[8px] font-bold uppercase truncate max-w-[90px] ${isActive ? 'text-black/70' : 'text-gray-500'}`}>
                                                                    {isJustAssigned ? 'Guardado' : (assignedSplit ? assignedSplit.name : 'Pendiente')}
                                                                </span>
                                                            </div>
                                                            {assignedSplit && !isActive && (
                                                                <div className="absolute bottom-3 right-3 text-white opacity-20"><CheckCircleIcon size={14} /></div>
                                                            )}
                                                        </button>
                                                    )
                                                })}
                                            </div>
                                        </div>
                                    </div>
                                )}
                                
                                {/* C. LISTA DE SPLITS (Carrusel Full-Width) */}
                                {!selectedSplit && !assignmentSuccess && (
                                    <div className="animate-fade-in w-full">
                                        <div className="overflow-x-auto snap-x snap-mandatory hide-scrollbar -mx-4 px-4 md:-mx-8 md:px-8 pb-8">
                                            <div className="flex gap-4 items-stretch">
                                                {/* Card: Crear Nuevo */}
                                                <button onClick={() => handleSelectSplit(SPLIT_TEMPLATES[0])} className="snap-center shrink-0 w-56 p-6 rounded-[2rem] border-2 border-dashed border-white/20 flex flex-col items-center justify-center gap-4 transition-all hover:bg-white/5 hover:border-white group">
                                                    <div className="w-12 h-12 rounded-full bg-white/5 flex items-center justify-center group-hover:bg-white group-hover:text-black transition-colors">
                                                        <PlusIcon size={24} />
                                                    </div>
                                                    <span className="font-black text-xs uppercase tracking-widest text-white">Crear desde Cero</span>
                                                </button>

                                                {/* Cards: Templates */}
                                                {SPLIT_TEMPLATES.slice(1, 5).map(split => (
                                                    <button key={split.id} onClick={() => handleSelectSplit(split)} className="snap-center shrink-0 w-64 p-6 rounded-[2rem] border border-white/10 bg-[#0a0a0a] flex flex-col justify-between text-left transition-all duration-300 hover:border-white hover:bg-black hover:scale-[1.02] group">
                                                        <div>
                                                            <div className="flex justify-between items-start mb-4">
                                                                <div className="bg-white/10 px-2 py-1 rounded text-[8px] font-black uppercase text-white/70 group-hover:bg-white group-hover:text-black transition-colors">
                                                                    {split.tags[0] === 'Recomendado por KPKN' ? 'KPKN Select' : split.tags[0]}
                                                                </div>
                                                                {split.difficulty === 'Principiante' && <div className="w-2 h-2 rounded-full bg-white"></div>}
                                                            </div>
                                                            <h4 className="font-black text-2xl leading-none mb-2 uppercase text-white">{split.name}</h4>
                                                            <div className="w-8 h-1 bg-white/20 mb-4 group-hover:w-16 transition-all duration-500"></div>
                                                        </div>
                                                        <div className="space-y-3">
                                                            <p className="text-[10px] font-medium leading-relaxed text-gray-500 group-hover:text-gray-300">{split.description}</p>
                                                            {/* Mini Preview de los días */}
                                                            <div className="flex gap-0.5 opacity-30 group-hover:opacity-100 transition-opacity">
                                                                {split.pattern.map((d, i) => (
                                                                    <div key={i} className={`h-1 flex-1 rounded-full ${d === 'Descanso' ? 'bg-gray-800' : 'bg-white'}`}></div>
                                                                ))}
                                                            </div>
                                                        </div>
                                                    </button>
                                                ))}
                                                
                                                 <button onClick={() => setShowAllSplitsModal(true)} className="snap-center shrink-0 w-48 p-6 rounded-[2rem] border border-white/10 bg-[#0a0a0a] flex flex-col items-center justify-center gap-3 hover:border-white hover:bg-black transition-all">
                                                    <GridIcon size={32} strokeWidth={1.5} />
                                                    <span className="font-black text-xs uppercase tracking-widest text-center">Ver<br/>Catálogo<br/>Completo</span>
                                                </button>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                {/* D. TARJETA SELECCIONADA / ÉXITO (Overlay) */}
                                {selectedSplit && (
                                    <div className="w-full bg-black rounded-[2rem] border-2 border-white shadow-[0_20px_50px_rgba(0,0,0,0.5)] relative overflow-hidden animate-fade-in-up mx-auto max-w-sm transition-all duration-500 min-h-[350px] flex flex-col z-20">
                                        
                                        {assignmentSuccess ? (
                                            <div className="absolute inset-0 z-30 bg-white flex flex-col items-center justify-center text-black p-6 animate-fade-in text-center overflow-hidden">
                                                
                                                {/* --- MARCA DE AGUA: CAUPOLICÁN --- */}
                                                <div className="absolute inset-0 flex items-center justify-center pointer-events-none opacity-[0.05]">
                                                    <div className="transform -rotate-12 scale-[2] grayscale">
                                                        <CaupolicanIcon size={150} />
                                                    </div>
                                                </div>

                                                {/* Contenido (z-10 para estar encima del fondo) */}
                                                <div className="relative z-10 flex flex-col items-center w-full">
                                                    <div className="w-20 h-20 rounded-full bg-black text-white flex items-center justify-center mb-6 animate-bounce-short shadow-xl">
                                                        <CheckCircleIcon size={32} strokeWidth={3} />
                                                    </div>
                                                    <h3 className="text-3xl font-black uppercase tracking-tighter mb-2">Asignado</h3>
                                                    <p className="text-xs font-bold uppercase tracking-widest text-gray-500 mb-8 px-8 border-t border-b border-gray-200 py-2">
                                                        Bloque: {assignmentSuccess}
                                                    </p>
                                                    
                                                    {activeSplitBlockStep0 < POWER_BLOCK_NAMES.length - 1 ? (
                                                        <div className="flex flex-col items-center gap-2 animate-pulse">
                                                            <span className="text-[9px] font-black uppercase text-gray-400">Siguiente Bloque</span>
                                                            <ArrowDownIcon size={20} className="text-black"/>
                                                        </div>
                                                    ) : (
                                                        <div className="text-[10px] font-black uppercase bg-black text-white px-3 py-1 rounded">Listo</div>
                                                    )}
                                                </div>
                                            </div>
                                        ) : (
                                            // VISTA DE DETALLES (NORMAL)
                                            <>
                                                <div className="bg-white p-6 text-black relative z-10 transition-colors">
                                                    <div className="flex justify-between items-start mb-4">
                                                        <h3 className="font-black text-3xl uppercase tracking-tighter w-3/4 leading-none">{selectedSplit.name}</h3>
                                                        <button 
                                                            onClick={() => {
                                                                setSelectedSplit(null); 
                                                                setSplitPattern([]);
                                                                if (splitMode === 'per_block') {
                                                                    const newSplits = { ...blockSplits };
                                                                    delete newSplits[activeSplitBlockStep0];
                                                                    setBlockSplits(newSplits);
                                                                }
                                                            }} 
                                                            className="w-8 h-8 flex items-center justify-center rounded-full border-2 border-black hover:bg-black hover:text-white transition-colors"
                                                        >
                                                            <XIcon size={16} strokeWidth={3} />
                                                        </button>
                                                    </div>
                                                    <p className="text-xs font-bold opacity-70 leading-relaxed mb-6 border-l-2 border-black pl-3">{selectedSplit.description}</p>
                                                    
                                                    {/* Selector de Día de Inicio Compacto */}
                                                    <div className="flex items-center justify-between border-t border-black/10 pt-3">
                                                        <span className="text-[9px] font-black uppercase tracking-widest opacity-50">Inicio de Semana</span>
                                                        <div className="relative group">
                                                            <select value={startDay} onChange={(e) => setStartDay(parseInt(e.target.value))} className="appearance-none bg-transparent text-[10px] font-black text-black uppercase outline-none border border-black/20 rounded px-2 py-1 pr-6 cursor-pointer hover:border-black">
                                                                {daysOfWeek.map(d => <option key={d.value} value={d.value}>{d.label}</option>)}
                                                            </select>
                                                            <ChevronDownIcon size={10} className="absolute right-2 top-1.5 pointer-events-none"/>
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Lista de Días */}
                                                <div className="p-4 space-y-2 flex-1 overflow-hidden flex flex-col bg-black">
                                                    <div className="flex-1 overflow-y-auto custom-scrollbar pr-1 space-y-1">
                                                        {splitPattern.map((label, index) => {
                                                            const dayLabel = getDayLabel(index);
                                                            const isRest = label.toLowerCase() === 'descanso';
                                                            return (
                                                                <div key={index} className={`flex items-center justify-between p-3 rounded-xl border transition-all duration-300 ${isRest ? 'bg-[#111] border-white/5 opacity-50' : 'bg-[#1a1a1a] border-white/20'}`}>
                                                                    <div className="flex items-center gap-4">
                                                                        <div className="w-6 text-[9px] font-black uppercase text-gray-500">{dayLabel.substring(0,3)}</div>
                                                                        <span className={`text-xs font-bold uppercase tracking-wide ${isRest ? 'text-gray-600' : 'text-white'}`}>{label}</span>
                                                                    </div>
                                                                </div>
                                                            );
                                                        })}
                                                    </div>
                                                    
                                                    <button 
                                                        onClick={() => {
                                                            setSelectedSplit(null); 
                                                            setSplitPattern([]);
                                                            if (splitMode === 'per_block') {
                                                                const newSplits = { ...blockSplits };
                                                                delete newSplits[activeSplitBlockStep0];
                                                                setBlockSplits(newSplits);
                                                            }
                                                        }} 
                                                        className="w-full mt-3 py-4 border-2 border-white text-white rounded-xl font-black text-xs uppercase tracking-[0.2em] hover:bg-white hover:text-black transition-all"
                                                    >
                                                        Cambiar Selección
                                                    </button>
                                                </div>
                                            </>
                                        )}
                                    </div>
                                )}
                            </div>

                            {/* Botón Flotante Siguiente */}
                             <div className="fixed bottom-6 left-0 right-0 px-6 flex justify-center z-50 pointer-events-none">
                                <button 
                                    onClick={handleAttemptNextStep} // Validar calibración antes de ir al paso intermedio
                                    disabled={!selectedSplit} 
                                    className={`pointer-events-auto bg-white text-black px-8 py-4 rounded-full font-black text-xs uppercase tracking-widest shadow-[0_0_30px_rgba(255,255,255,0.2)] hover:scale-105 active:scale-95 transition-all duration-500 flex items-center gap-3 ${!selectedSplit ? 'opacity-0 translate-y-10' : 'opacity-100 translate-y-0'}`}
                                >
                                    Siguiente: Vista General <ArrowDownIcon size={16} className="-rotate-90"/>
                                </button> 

                            {/* === BOTÓN DE CONTINUAR PERMANENTE === */}
                            <div className="fixed bottom-0 left-0 right-0 p-8 bg-gradient-to-t from-black via-black/90 to-transparent pt-32 z-40 flex flex-col items-center justify-end pointer-events-none">
                                <button
                                    onClick={handleAttemptNextStep}
                                    // Deshabilitado si: 
                                    // 1. Modo Simple/Global y NO hay split seleccionado.
                                    // 2. Modo Por Bloques y NO están asignados los 5 bloques.
                                    disabled={(() => {
                                        if (selectedTemplateId !== 'power-complex' || splitMode === 'global') return !selectedSplit;
                                        return Object.keys(blockSplits).length < POWER_BLOCK_NAMES.length;
                                    })()}
                                    className={`pointer-events-auto px-12 py-5 rounded-full font-black text-xs uppercase tracking-[0.2em] flex items-center gap-3 transition-all duration-300 shadow-2xl
                                        ${(() => {
                                            const isGlobalReady = (selectedTemplateId !== 'power-complex' || splitMode === 'global') && selectedSplit;
                                            const isPerBlockReady = selectedTemplateId === 'power-complex' && splitMode === 'per_block' && Object.keys(blockSplits).length === POWER_BLOCK_NAMES.length;
                                            
                                            return (isGlobalReady || isPerBlockReady)
                                                ? 'bg-white text-black hover:scale-105 active:scale-95 shadow-[0_0_40px_rgba(255,255,255,0.2)] opacity-100 translate-y-0 cursor-pointer' 
                                                : 'bg-[#222] text-gray-500 border border-white/5 cursor-not-allowed'
                                        })()}
                                    `}
                                >
                                    <span>
                                        {(() => {
                                            if (selectedTemplateId === 'power-complex' && splitMode === 'per_block') {
                                                const missing = POWER_BLOCK_NAMES.length - Object.keys(blockSplits).length;
                                                if (missing > 0) return `Faltan ${missing} Bloques`;
                                            }
                                            if ((selectedTemplateId !== 'power-complex' || splitMode === 'global') && !selectedSplit) {
                                                return "Selecciona un Split";
                                            }
                                            return "Crear Programa";
                                        })()}
                                    </span>
                                    {/* Flecha condicional */}
                                    <SaveIcon size={16} className={`transition-all duration-300 ${
                                        ((selectedTemplateId === 'power-complex' && splitMode === 'per_block' && Object.keys(blockSplits).length < POWER_BLOCK_NAMES.length) || 
                                        ((selectedTemplateId !== 'power-complex' || splitMode === 'global') && !selectedSplit))
                                        ? 'opacity-0 w-0' : 'opacity-100 -rotate-90'
                                    }`}/>
                                </button>
                            </div>

                             </div>    
                        </div>
                    )}

                    {/* === PASO 1: ROADMAP Y PREPARACIÓN DE SESIONES (FUSIONADOS) === */}
                    {wizardStep === 1 && (
                        <div className="fixed inset-0 z-[210] bg-[#050505] flex flex-col animate-fade-in-up">
                            
                            {/* --- FOOTER FLOTANTE (BOTONES FINALES) --- */}
                            <div className="fixed bottom-0 left-0 right-0 p-6 bg-gradient-to-t from-black via-[#050505] to-transparent pt-20 z-40 flex flex-col items-center gap-4 pointer-events-none">
                                <div className="pointer-events-auto flex items-center gap-3 bg-black/80 backdrop-blur-md px-5 py-2.5 rounded-full border border-white/10 shadow-xl">
                                    <span className="text-[9px] font-bold text-gray-300 uppercase tracking-widest">Activar al crear</span>
                                    <ToggleSwitch checked={autoActivate} onChange={setAutoActivate} size="sm" />
                                </div>

                                <div className="flex gap-3 pointer-events-auto w-full max-w-md justify-center">
                                    <button 
                                        onClick={() => handleCreate(true)} 
                                        className="bg-zinc-900 text-zinc-400 px-6 py-4 rounded-full font-black text-xs uppercase tracking-widest hover:text-white hover:bg-zinc-800 transition-colors border border-white/10 shadow-lg"
                                    >
                                        Borrador
                                    </button>
                                    <button 
                                        onClick={handleAttemptNextStep}
                                        disabled={(() => {
                                            if (selectedTemplateId !== 'power-complex' || splitMode === 'global') return !selectedSplit;
                                            return Object.keys(blockSplits).length < POWER_BLOCK_NAMES.length;
                                        })()}
                                        className={`flex-1 px-8 py-4 rounded-full font-black text-xs uppercase tracking-[0.2em] shadow-[0_0_50px_rgba(255,255,255,0.25)] transition-all flex items-center justify-center gap-2 border border-transparent 
                                            ${((selectedTemplateId !== 'power-complex' || splitMode === 'global') && !selectedSplit) || (selectedTemplateId === 'power-complex' && splitMode === 'per_block' && Object.keys(blockSplits).length < POWER_BLOCK_NAMES.length)
                                                ? 'bg-[#222] text-gray-500 cursor-not-allowed border-white/5'
                                                : 'bg-white text-black hover:scale-105 active:scale-95 hover:border-black cursor-pointer'
                                            }`}
                                    >
                                        <SaveIcon size={16}/>
                                        <span>
                                            {(() => {
                                                if (selectedTemplateId === 'power-complex' && splitMode === 'per_block') {
                                                    const missing = POWER_BLOCK_NAMES.length - Object.keys(blockSplits).length;
                                                    if (missing > 0) return `Faltan ${missing} Bloques`;
                                                }
                                                if ((selectedTemplateId !== 'power-complex' || splitMode === 'global') && !selectedSplit) {
                                                    return "Selecciona Split";
                                                }
                                                return "Crear Programa";
                                            })()}
                                        </span>
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                    {wizardStep === 2 && (
                        <div className="fixed inset-0 z-[210] bg-[#050505] flex flex-col animate-fade-in">
                            
                            {/* --- BOTONES DE NAVEGACIÓN FLOTANTES --- */}
                            <button 
                                onClick={() => setWizardStep(1)} 
                                className="fixed top-4 left-4 z-[220] p-2.5 rounded-full bg-black/40 text-white border border-white/10 backdrop-blur-md hover:bg-white/20 transition-all shadow-lg"
                                title="Volver al Roadmap"
                            >
                                <ArrowLeftIcon size={18} />
                            </button>
                            
                            <button 
                                onClick={() => {
                                    if (window.confirm('¿Seguro que quieres salir? Perderás los cambios no guardados.')) {
                                        onCancel();
                                    }
                                }} 
                                className="fixed top-4 right-4 z-[220] p-2.5 rounded-full bg-black/40 text-gray-400 border border-white/10 backdrop-blur-md hover:bg-red-500/20 hover:text-red-400 hover:border-red-500/30 transition-all shadow-lg"
                                title="Salir del editor"
                            >
                                <XIcon size={18} />
                            </button>

                            {/* --- HEADER COMPACTO Y FUNCIONAL --- */}
                            <div className="px-6 pt-20 pb-4 bg-[#050505] border-b border-white/5 z-20 shrink-0">
                                <div className="flex flex-col gap-1 max-w-4xl mx-auto w-full">
                                    <div className="flex items-center gap-2">
                                        <EditIcon size={20} className="text-white"/>
                                        <h3 className="text-xl font-black text-white uppercase tracking-tight">Prepara tus sesiones</h3>
                                    </div>
                                    <p className="text-[10px] text-gray-500 font-medium leading-relaxed">
                                        Podrás hacer una modificación más detallada más adelante una vez creado el programa.
                                    </p>

                                    {/* SELECTOR DE BLOQUES (Integrado en el Header para ahorrar espacio) */}
                                    {selectedTemplateId === 'power-complex' && (
                                        <div className="mt-4 w-full overflow-x-auto hide-scrollbar">
                                            <div className="flex gap-2 pb-1">
                                                {POWER_BLOCK_NAMES.map((name, idx) => {
                                                    const isActive = activeBlockEdit === idx;
                                                    return (
                                                        <button
                                                            key={name}
                                                            onClick={() => handleSwitchBlockEdit(idx)}
                                                            className={`px-5 py-2.5 rounded-full text-[9px] font-black uppercase tracking-wider transition-all whitespace-nowrap border
                                                                ${isActive 
                                                                    ? 'bg-white text-black border-white shadow-lg scale-[1.02]' 
                                                                    : 'bg-[#111] text-gray-500 border-white/10 hover:border-white/30 hover:text-gray-300'
                                                                }
                                                            `}
                                                        >
                                                            {name}
                                                        </button>
                                                    );
                                                })}
                                            </div>
                                        </div>
                                    )}
                                </div>
                            </div>

                            {/* --- ÁREA DE EDICIÓN (MAXIMIZADA) --- */}
                            <div className="flex-1 overflow-y-auto custom-scrollbar p-0 bg-[#050505]">
                                <div className="max-w-4xl mx-auto p-4 pb-40 space-y-4">

                                {/* KPKN Feedback Loop: Barra de Volumen Inteligente */}
                                <VolumeBudgetBar 
                                    currentVolume={Object.fromEntries(Object.entries(currentWeeklyVolume).map(([k, v]) => [k, v.total]))} 
                                    recommendation={volumeLimits} 
                                />
                                    
                                    {/* Barra de Herramientas Contextual (Switch Corregido) */}
                                    {selectedTemplateId === 'power-complex' && splitMode === 'per_block' && (
                                        <div className="flex justify-end mb-2 px-1">
                                            <div className="flex items-center gap-3 bg-[#111] px-4 py-2 rounded-full border border-white/10 shadow-sm transition-colors duration-300">
                                                <span className={`text-[9px] font-bold uppercase tracking-wider transition-colors ${applyToAllBlocks ? 'text-white' : 'text-gray-500'}`}>
                                                    {/* NOMBRE DINÁMICO DEL SPLIT */}
                                                    Aplicar a bloques con mismo split <span className="text-gray-400">({blockSplits[activeBlockEdit]?.name || 'Actual'})</span>
                                                </span>
                                                <ToggleSwitch checked={applyToAllBlocks} onChange={setApplyToAllBlocks} size="sm" />
                                            </div>
                                        </div>
                                    )}

                                    {/* Lista de Sesiones */}
                                    <div className="space-y-3">
                                        {splitPattern.map((label, index) => {
                                            const dayLabel = getDayLabel(index);
                                            const isRest = label.toLowerCase() === 'descanso';
                                            return (
                                                <InlineSessionCreator
                                                    key={index}
                                                    dayLabel={dayLabel}
                                                    sessionName={label}
                                                    isRest={isRest}
                                                    sessionData={detailedSessions[index]}
                                                    onRename={(name) => handleRenameSession(index, name)}
                                                    onUpdateSession={(s) => handleUpdateSessionSmart(index, s)}
                                                    onMoveUp={() => handleMoveSession(index, 'up')}
                                                    onMoveDown={() => handleMoveSession(index, 'down')}
                                                    isFirst={index === 0}
                                                    isLast={index === cycleDuration - 1}
                                                    exerciseList={exerciseList}
                                                />
                                            );
                                        })}
                                    </div>
                                </div>
                            </div>

                            {/* --- FOOTER FLOTANTE --- */}
                            <div className="fixed bottom-0 left-0 right-0 p-6 bg-gradient-to-t from-black via-[#050505] to-transparent pt-20 z-40 flex flex-col items-center gap-4 pointer-events-none">
                                {/* Toggle Activación */}
                                <div className="pointer-events-auto flex items-center gap-3 bg-black/80 backdrop-blur-md px-5 py-2.5 rounded-full border border-white/10 shadow-xl">
                                    <span className="text-[9px] font-bold text-gray-300 uppercase tracking-widest">Activar al guardar</span>
                                    <ToggleSwitch checked={autoActivate} onChange={setAutoActivate} size="sm" />
                                </div>

                                <div className="flex gap-3 pointer-events-auto">
                                    <button 
                                        onClick={() => handleCreate(true)} 
                                        className="bg-zinc-900 text-zinc-400 px-6 py-4 rounded-full font-black text-xs uppercase tracking-widest hover:text-white hover:bg-zinc-800 transition-colors border border-white/10"
                                    >
                                        Guardar Borrador
                                    </button>
                                    {/* Botón Principal */}
                                    <button 
                                        onClick={() => handleCreate(false)} 
                                        className="bg-white text-black px-8 py-4 rounded-full font-black text-xs uppercase tracking-[0.2em] shadow-[0_0_50px_rgba(255,255,255,0.25)] hover:scale-105 active:scale-95 transition-all flex items-center gap-2 border border-transparent hover:border-black"
                                    >
                                        <SaveIcon size={16}/>
                                        <span>Crear Programa</span>
                                    </button>
                                </div>
                            </div>
                        </div>
                    )}

                </div>
            </div>
            
            {showAllSplitsModal && (
                <div className="fixed inset-0 z-[250] bg-black flex flex-col animate-fade-in">
                     <div className="p-4 border-b border-white/10 bg-black/50 backdrop-blur-xl flex flex-col gap-4">
                        <div className="flex justify-between items-center">
                             <h2 className="text-lg font-black text-white uppercase tracking-tight">Catálogo de Splits</h2>
                             <div className="flex gap-2">
                                 {compareList.length > 0 && (
                                     <button onClick={() => setShowCompareView(true)} className="bg-yellow-400 text-black px-4 py-2 rounded-full text-[10px] font-black uppercase tracking-widest hover:scale-105 transition-transform">Comparar ({compareList.length})</button>
                                 )}
                                 <button onClick={() => setShowAllSplitsModal(false)} className="p-2 bg-slate-900 rounded-full text-white"><XIcon size={20}/></button>
                             </div>
                        </div>
                        <div className="relative">
                            <input type="text" placeholder="Buscar split..." value={splitSearchQuery} onChange={(e) => setSplitSearchQuery(e.target.value)} className="w-full bg-black border border-white/30 rounded-xl py-3 px-4 pl-10 text-xs text-white placeholder-gray-500 focus:border-white outline-none transition-colors" />
                            <div className="absolute left-3 top-3 text-gray-500"><RefreshCwIcon size={14} /></div>
                        </div>
                        <div className="flex overflow-x-auto gap-2 hide-scrollbar pb-2">
                            {tagFilters.map(tag => (
                                <button key={tag} onClick={() => setModalFilter(tag)} className={`px-3 py-1.5 rounded-full text-[10px] font-black uppercase tracking-wider whitespace-nowrap border transition-all ${modalFilter === tag ? 'bg-white text-black border-white' : 'bg-transparent border-white/30 text-white/60'}`}>{tag}</button>
                            ))}
                        </div>
                    </div>

                    {!showCompareView ? (
                        <div className="flex-1 overflow-y-auto p-4 custom-scrollbar bg-black">
                            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4 pb-20">
                                {filteredSplits.map(split => {
                                    const isSelected = compareList.includes(split.id);
                                    return (
                                    <div key={split.id} className={`relative group bg-white rounded-3xl p-5 flex flex-col h-full justify-between transition-all duration-300 ${isSelected ? 'ring-4 ring-yellow-400 scale-[0.98]' : 'hover:scale-[1.01]'}`}>
                                        <button onClick={(e) => { e.stopPropagation(); toggleCompareSplit(split.id); }} className={`absolute top-4 right-4 z-20 w-6 h-6 rounded-full border-2 flex items-center justify-center transition-colors ${isSelected ? 'bg-yellow-400 border-yellow-400 text-black' : 'border-gray-300 text-transparent hover:border-yellow-400'}`}>
                                            <CheckCircleIcon size={14} />
                                        </button>
                                        <button onClick={() => handleSelectSplit(split)} className="text-left flex-1 relative z-10">
                                            <h4 className="font-black text-black text-xl tracking-tight leading-none mb-2 pr-8">{split.name}</h4>
                                            <div className="flex flex-wrap gap-1 mb-3">
                                                 {split.tags.slice(0, 3).map(t => (<span key={t} className="text-[8px] font-black uppercase px-2 py-0.5 rounded border border-black/20 text-black/60">{t}</span>))}
                                            </div>
                                            <p className="text-xs font-bold text-gray-500 leading-relaxed mb-4">{split.description}</p>
                                        </button>
                                        <div className="w-full pt-3 border-t border-gray-100 relative z-10 pointer-events-none flex flex-wrap gap-1">
                                            {split.pattern.slice(0, 5).map((s, i) => (<span key={i} className="text-[9px] font-bold text-gray-400 bg-gray-50 px-1.5 py-0.5 rounded border border-gray-100">{typeof s === 'string' ? s.substring(0, 3) : s}</span>))}
                                            {split.pattern.length > 5 && <span className="text-[9px] text-gray-400 self-center">...</span>}
                                        </div>
                                    </div>
                                )})}
                            </div>
                        </div>
                    ) : (
                        <div className="flex-1 overflow-y-auto bg-[#0a0a0a] p-4 animate-fade-in-up">
                            <div className="max-w-6xl mx-auto">
                                <button onClick={() => setShowCompareView(false)} className="mb-6 text-xs font-bold text-gray-400 hover:text-white flex items-center gap-2">
                                    <ArrowUpIcon size={14} className="rotate-[-90deg]"/> Volver al catálogo
                                </button>
                                <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                                    {getComparisonData().map(split => (
                                        <div key={split.id} className="bg-[#111] border border-white/10 rounded-2xl p-6 flex flex-col">
                                            <h3 className="text-xl font-black text-white uppercase tracking-tight mb-4">{split.name}</h3>
                                            <div className="space-y-4 flex-1">
                                                <div><span className="text-[9px] font-black text-gray-500 uppercase">Dificultad</span><br/><span className="text-xs text-white">{split.difficulty}</span></div>
                                                <div><span className="text-[9px] font-black text-gray-500 uppercase">Descripción</span><br/><p className="text-xs text-gray-400">{split.description}</p></div>
                                                <div>
                                                    <span className="text-[9px] font-black text-gray-500 uppercase">Estructura</span>
                                                    <div className="mt-1 space-y-1">
                                                        {split.pattern.map((day, i) => (
                                                            <div key={i} className="flex justify-between text-[10px] border-b border-white/5 py-1">
                                                                <span className="text-gray-500">Día {i+1}</span>
                                                                <span className={String(day).toLowerCase() === 'descanso' ? 'text-gray-600' : 'text-white font-bold'}>{day}</span>
                                                            </div>
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>
                                            <button onClick={() => handleSelectSplit(split)} className="w-full mt-6 py-3 bg-white text-black rounded-xl font-black text-xs uppercase tracking-widest hover:scale-105 transition-transform">Usar este Split</button>
                                        </div>
                                    ))}
                                </div>
                            </div>
                        </div>
                    )}
                </div>
            )}
            
            {/* === MODAL DE INFORMACIÓN (GLOBAL Y FIJO) === */}
            {activeInfo && (
                <div className="fixed inset-0 z-[300] flex items-center justify-center p-6 bg-black/80 backdrop-blur-sm animate-fade-in" onClick={() => setActiveInfo(null)}>
                    <div className="bg-[#151515] border border-white/10 p-6 rounded-3xl max-w-sm w-full shadow-2xl relative transform transition-all scale-100" onClick={e => e.stopPropagation()}>
                        <div className="flex justify-between items-start mb-4">
                            <h4 className="text-lg font-black text-white uppercase tracking-tight">
                                {activeInfo === 'structure' ? 'Estructura Temporal' : 'Split Semanal'}
                            </h4>
                            <button onClick={() => setActiveInfo(null)} className="text-gray-500 hover:text-white bg-white/5 p-2 rounded-full transition-colors">
                                <XIcon size={20}/>
                            </button>
                        </div>
                        <div className="bg-black/20 rounded-xl p-4 border border-white/5 mb-4">
                            <p className="text-xs text-gray-300 leading-relaxed font-medium text-justify">
                                {activeInfo === 'structure' ? INFO_TEXTS.structure : INFO_TEXTS.split}
                            </p>
                        </div>
                        <button onClick={() => setActiveInfo(null)} className="w-full py-3 bg-white text-black rounded-xl font-black text-xs uppercase tracking-widest hover:scale-[1.02] active:scale-95 transition-all shadow-lg shadow-white/10">
                            Entendido
                        </button>
                    </div>
                </div>
            )}

            {/* === OVERLAY DE EDICIÓN DE SEMANA === */}
            {editingWeekInfo && (
                <InteractiveWeekOverlay 
                    week={editingWeekInfo.week}
                    weekTitle={`Semana ${editingWeekInfo.weekIndex + 1}`}
                    onClose={() => setEditingWeekInfo(null)}
                    onSave={(updatedWeek) => {
                        if (editingWeekInfo.isSimple) {
                            updateProgramStructure(p => {
                                if (p.macrocycles && p.macrocycles[0] && p.macrocycles[0].blocks && p.macrocycles[0].blocks[0] && p.macrocycles[0].blocks[0].mesocycles && p.macrocycles[0].blocks[0].mesocycles[0]) {
                                    p.macrocycles[0].blocks[0].mesocycles[0].weeks[editingWeekInfo.weekIndex] = updatedWeek;
                                }
                            });
                        } else {
                            updateProgramStructure(p => {
                                if (p.macrocycles && p.macrocycles[editingWeekInfo.macroIndex] && p.macrocycles[editingWeekInfo.macroIndex].blocks) {
                                    p.macrocycles[editingWeekInfo.macroIndex]
                                     .blocks![editingWeekInfo.blockIndex]
                                     .mesocycles[editingWeekInfo.mesoIndex]
                                     .weeks[editingWeekInfo.weekIndex] = updatedWeek;
                                }
                            });
                        }
                        setEditingWeekInfo(null);
                        addToast('Split semanal guardado correctamente', 'success');
                    }}
                />
            )}
        </div>
    );
};

export default ProgramEditor;
