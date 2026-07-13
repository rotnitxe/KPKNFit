export type SplitTag = 'Recomendado por KPKN' | 'Alta Frecuencia' | 'Baja Frecuencia' | 'Balanceado' | 'Alto Volumen' | 'Alta Tolerancia' | 'Personalizado' | 'Powerlifting';

export interface SplitTemplate {
    id: string;
    name: string;
    description: string;
    tags: SplitTag[];
    pattern: string[];
    difficulty: 'Principiante' | 'Intermedio' | 'Avanzado';
    pros: string[];
    cons: string[];
}

export const SPLIT_TEMPLATES: SplitTemplate[] = [
    { id: 'custom', name: 'Crear desde Cero', description: 'Lienzo en blanco.', tags: ['Personalizado'], pattern: Array(7).fill('Descanso'), difficulty: 'Avanzado', pros: ['Libertad total de diseño', 'Se adapta a necesidades específicas'], cons: ['Requiere conocimiento avanzado', 'Riesgo de mala programación'] },
    
    { id: 'ul_x4', name: 'Upper / Lower x4', description: 'El estándar de oro. Equilibrio perfecto.', tags: ['Recomendado por KPKN', 'Balanceado', 'Alta Tolerancia'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Descanso', 'Descanso'], difficulty: 'Intermedio', 
        pros: ['Frecuencia 2x/semana óptima para naturales (Nippard)', '48-72h recuperación por grupo muscular', 'Volumen por sesión manejable', 'Ideal para hipertrofia y fuerza'], 
        cons: ['Requiere 4 días mínimos', 'Sesiones de pierna demandantes', 'Menos volumen por músculo por sesión'] },
    
    { id: 'ppl_ul', name: 'PPL + Upper/Lower', description: 'Híbrido de 5 días. Volumen y frecuencia.', tags: ['Recomendado por KPKN', 'Alto Volumen', 'Balanceado'], pattern: ['Torso', 'Pierna', 'Descanso', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Combina frecuencia UL con especificidad PPL', '5 días manejables', 'Día de pierna dedicado + estímulo extra', 'Balance empuje/tracción'],
        cons: ['Coordinación de ejercicios compleja', 'Fatiga acumulativa media-alta', 'Requiere buena recuperación'] },
    
    { id: 'fullbody_x3', name: 'Full Body x3', description: 'Alta frecuencia, ideal para agendas ocupadas.', tags: ['Recomendado por KPKN', 'Baja Frecuencia'], pattern: ['Cuerpo Completo A', 'Descanso', 'Cuerpo Completo B', 'Descanso', 'Cuerpo Completo C', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Frecuencia 3x/semana máxima para principiantes', 'Ideal para aprendizaje motor', 'Mínimo tiempo requerido', 'Alta quema calórica', 'Flexible si fallas un día'],
        cons: ['Volumen por músculo limitado por sesión', 'Sesiones sistémicamente fatigosas', 'Menos especialización posible', 'No óptimo para avanzados'] },
    
    { id: 'ppl_x6', name: 'Push Pull Legs x6', description: 'Máximo volumen. Solo expertos.', tags: ['Alta Frecuencia', 'Alto Volumen', 'Alta Tolerancia'], pattern: ['Empuje', 'Tirón', 'Pierna', 'Empuje', 'Tirón', 'Pierna', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen máximo por grupo muscular', 'Frecuencia 2x/semana', 'Especificidad por patrón de movimiento', 'Estética balanceada', 'Recuperación local óptima'],
        cons: ['6 días requeridos (tolerancia alta)', 'Fatiga acumulativa extrema', 'Riesgo de sobreentrenamiento en naturales', 'Vida social limitada', 'Requiere nutrición precisa'] },
    
    { id: 'ul_x6', name: 'Upper / Lower x6', description: 'Frecuencia 3 por grupo muscular. Gestión de fatiga crítica.', tags: ['Alta Frecuencia', 'Alta Tolerancia', 'Alto Volumen'], pattern: ['Torso', 'Pierna', 'Torso', 'Pierna', 'Torso', 'Pierna', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Frecuencia 3x/semana por músculo', 'Ideal para fuerza y técnica', 'Volumen distribuido', 'Recuperación de 48h entre sesiones iguales'],
        cons: ['Gestión de fatiga CRÍTICA (Israetel)', 'Sesiones de pierna brutales', 'Requiere deloads frecuentes', 'Solo para avanzados con tolerancia alta'] },
    
    { id: 'ppl_arnold', name: 'PPL + Arnold', description: 'Lo mejor de la estética: PPL + Pecho/Espalda + Hombro/Brazo.', tags: ['Alto Volumen', 'Balanceado', 'Personalizado'], pattern: ['Empuje', 'Tirón', 'Pierna', 'Pecho/Espalda', 'Hombro/Brazo', 'Pierna', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Énfasis en上半身 estético', 'Día de pecho/espalda tipo Arnold', 'Brazos y hombros dedicados', 'Frecuencia 2x para piernas'],
        cons: ['Volumen de empuje muy alto (hombro anterior)', '5-6 días requeridos', 'Recuperación de pecho crítica', 'Riesgo de desbalance posterior'] },
    
    { id: 'phat_hybrid', name: 'UL x2 + Full Body', description: 'Híbrido de 5 días. Frecuencia y repaso total.', tags: ['Balanceado', 'Alta Frecuencia'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Full Body', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Combina estructura UL con día Full Body', 'Frecuencia 2-3x/semana', 'Día 6 para puntos débiles', 'Balance volumen/frecuencia'],
        cons: ['Día Full Body puede ser brutal', 'Coordinación de ejercicios compleja', 'Fatiga acumulativa media-alta'] },
    
    { id: 'ant_post_x4', name: 'Anterior / Posterior x4', description: 'Enfoque en cadenas musculares cinéticas.', tags: ['Balanceado', 'Personalizado'], pattern: ['Cadena Anterior', 'Cadena Posterior', 'Descanso', 'Cadena Anterior', 'Cadena Posterior', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Enfoque funcional por cadena', 'Balance estructural', 'Frecuencia 2x/semana', 'Ideal para atletas'],
        cons: ['Menos común (menos referencias)', 'Requiere conocimiento de cadenas', 'Equipamiento variado necesario'] },
    
    { id: 'ant_post_x6', name: 'Anterior / Posterior x6', description: 'Frecuencia agresiva por plano de movimiento.', tags: ['Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Cadena Anterior', 'Cadena Posterior', 'Cadena Anterior', 'Cadena Posterior', 'Cadena Anterior', 'Cadena Posterior', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Frecuencia 3x/semana por cadena', 'Ideal para fuerza funcional', 'Alta práctica de patrones', 'Desarrollo balanceado'],
        cons: ['Fatiga sistémica extrema', 'Requiere deloads cada 4-6 semanas', 'Solo para avanzados', 'Recuperación crítica'] },
    
    { id: 'bro_split', name: 'Bro Split Clásico', description: 'Un grupo muscular por día. Foco máximo por sesión.', tags: ['Baja Frecuencia', 'Alto Volumen'], pattern: ['Pecho', 'Espalda', 'Piernas', 'Hombros', 'Brazos', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Volumen máximo por sesión por músculo', 'Foco mental óptimo', 'Fácil de planificar', 'Ideal para bombeo', 'Recuperación local de 7 días'],
        cons: ['Frecuencia 1x/semana SUBÓPTIMA (Schoenfeld)', 'NO recomendado para naturales', 'Alta soreness (DOMS)', 'Menor síntesis proteica semanal'] },
    
    { id: 'hybrid_fb_ap', name: 'Híbrido FB + Ant/Post', description: '4 días: 2 Full Body + 1 Anterior + 1 Posterior.', tags: ['Balanceado', 'Personalizado'], pattern: ['Full Body', 'Descanso', 'Full Body', 'Descanso', 'Anterior', 'Posterior', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Combina frecuencia FB con especificidad A/P', '4 días manejables', 'Balance volumen/frecuencia', 'Flexible'],
        cons: ['Coordinación compleja', 'Fatiga variable', 'Menos datos de efectividad'] },
    
    { id: 'minimalist_x2', name: 'Minimalista x2', description: 'Dosis mínima efectiva. Ideal para padres o ejecutivos.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body A', 'Descanso', 'Descanso', 'Full Body B', 'Descanso', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Solo 2 días requeridos', 'Máxima eficiencia temporal', 'Ideal para mantenimiento', 'Baja fatiga acumulativa', 'Sostenible a largo plazo'],
        cons: ['Progreso lento', 'Volumen semanal limitado', 'No óptimo para hipertrofia máxima', 'Frecuencia subóptima'] },
    
    { id: 'weekend_warrior', name: 'Guerrero de Finde', description: '¿Solo puedes entrenar el fin de semana? Esto es para ti.', tags: ['Baja Frecuencia', 'Personalizado'], pattern: ['Descanso', 'Descanso', 'Descanso', 'Descanso', 'Descanso', 'Torso/Full Body', 'Pierna/Full Body'], difficulty: 'Intermedio',
        pros: ['Se adapta a agendas extremas', 'Mejor que no entrenar', 'Sesiones concentradas', 'Socialmente sostenible'],
        cons: ['Frecuencia 1x/semana por músculo', 'Volumen por sesión muy alto', 'Recuperación de 7 días', 'Progreso limitado', 'Riesgo de lesión por volumen concentrado'] },
    
    { id: 'glute_focus', name: 'Especialización Glúteo', description: '3 días de tren inferior con énfasis en cadena posterior.', tags: ['Personalizado', 'Alto Volumen'], pattern: ['Glúteo/Isquios', 'Torso Liviano', 'Descanso', 'Cuádriceps/Glúteo', 'Hombros/Abs', 'Glúteo Pump', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Frecuencia 3x/semana para glúteo', 'Énfasis estético femenino popular', 'Volumen especializado', 'Recuperación de torso liviana'],
        cons: ['Desbalance potencial si no se periodiza', 'Fatiga de cadena posterior alta', 'Requiere conocimiento de activación glútea'] },
    
    { id: 'beach_body', name: 'Torso Dominante', description: 'Enfoque "Beach Body". 3 días de torso, 1 de pierna mantenimiento.', tags: ['Personalizado', 'Alto Volumen'], pattern: ['Pecho/Espalda', 'Pierna Mantenimiento', 'Descanso', 'Hombros/Brazos', 'Descanso', 'Upper Completo', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Énfasis en上半身 estético', 'Ideal para apariencia "beach body"', '3 días de torso', 'Pierna mantenimiento suficiente'],
        cons: ['Desbalance torso/pierna', 'Estética "chicken legs"', 'No recomendado para atletas', 'Puede causar problemas posturales'] },
    
    { id: 'fullbody_x5', name: 'Full Body x5', description: 'Estilo "Norwegian" o "Squat Every Day" lite. Volumen bajo por sesión.', tags: ['Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Full Body', 'Full Body', 'Full Body', 'Full Body', 'Full Body', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Frecuencia 5x/semana MÁXIMA', 'Ideal para fuerza y técnica', 'Volumen por sesión bajo (recuperable)', 'Alta práctica motora', 'Estilo powerlifting noruego'],
        cons: ['Fatiga sistémica EXTREMA', 'Requiere deloads cada 3-4 semanas', 'Solo para avanzados', 'Vida social limitada', 'Riesgo de sobreentrenamiento alto'] },
    
    { id: 'push_pull_x4', name: 'Push / Pull x4', description: 'Simple y brutal. Sin día exclusivo de pierna, se integra.', tags: ['Balanceado', 'Recomendado por KPKN'], pattern: ['Empuje + Cuádriceps', 'Tirón + Isquios', 'Descanso', 'Empuje + Cuádriceps', 'Tirón + Isquios', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Frecuencia 2x/semana óptima', 'Integración pierna natural', '4 días manejables', 'Balance empuje/tracción', 'Simple de ejecutar'],
        cons: ['Sesiones de empuje largas', 'Cuádriceps puede quedar limitado', 'Menos énfasis en cadena posterior'] },
    
    { id: 'texas_method', name: 'Estilo Texas', description: 'Ondulación diaria: Volumen, Recuperación e Intensidad.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Día Volumen (5x5)', 'Descanso', 'Día Recuperación', 'Descanso', 'Día Intensidad (1RM/3RM)', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Progresión lineal probada', 'Ideal para fuerza', 'Ondulación de cargas inteligente', 'Recuperación integrada', 'Clásico del powerlifting'],
        cons: ['Solo 3 días de entrenamiento', 'Frecuencia baja para hipertrofia', 'Progreso se estanca en avanzados', 'Requiere cambio a programa intermedio'] },
    
    { id: 'smolov_base', name: 'Alta Frecuencia Base', description: 'Inspirado en ciclos de acumulación tipo Smolov Jr. (4 días).', tags: ['Powerlifting', 'Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Sesión 1 (4x9)', 'Descanso', 'Sesión 2 (5x7)', 'Sesión 3 (7x5)', 'Descanso', 'Sesión 4 (10x3)', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen de sentadilla EXTREMO', 'Ganancias de fuerza rápidas', 'Adaptación neural acelerada', 'Ciclo de acumulación brutal'],
        cons: ['SOLO para ciclos cortos (4-6 semanas)', 'Fatiga extrema', 'Técnica debe ser perfecta', 'Riesgo de lesión alto', 'No sostenible'] },
    
    { id: 'pl_sbd_x3', name: 'SBD Full Body x3', description: 'Alta especificidad. Los tres básicos, tres veces por semana.', tags: ['Powerlifting', 'Alta Tolerancia'], pattern: ['SBD Día 1', 'Descanso', 'SBD Día 2', 'Descanso', 'SBD Día 3', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Especificidad máxima en SBD', 'Frecuencia 3x/semana por levantamiento', 'Técnica altamente practicada', 'Ideal para competición'],
        cons: ['Fatiga articular alta', 'Requiere gestión de intensidad', 'Poco volumen de accesorios', 'Solo para powerlifters'] },
    
    { id: 'pl_hf_bench', name: 'PL: Bench Freq 4', description: 'Especialización Banca. Sq x3, Bp x4, Dl x2.', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Banca', 'Descanso', 'Sentadilla/Banca', 'Variante DL/Banca', 'Sentadilla/Accesorios', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Frecuencia 4x/semana banca', 'Ideal para especialización press', 'Sentadilla y DL mantenidos', 'Volumen de empuje alto'],
        cons: ['Fatiga de hombros crítica', 'Requiere gestión de codo/hombro', 'Peso muerto puede quedar limitado', 'Solo para especialistas'] },
    
    { id: 'pl_classic_4', name: 'PL: Clásico 4 Días', description: 'Base sólida. 3 Bancas, 2 Sentadillas, 2 Pesos Muertos.', tags: ['Powerlifting', 'Balanceado', 'Recomendado por KPKN'], pattern: ['Sentadilla/Banca', 'Peso Muerto', 'Descanso', 'Banca Volumen', 'Sentadilla/Peso Muerto', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Balance clásico de powerlifting', 'Frecuencia adecuada por lift', '4 días manejables', 'Volumen bien distribuido', 'Ideal para intermedios'],
        cons: ['Progreso puede estancarse en avanzados', 'Requiere accesorios adicionales', 'Fatiga de DL acumulativa'] },
    
    { id: 'sheiko_3day', name: 'Sheiko Clásico (3 Días)', description: 'Estilo soviético. Alta frecuencia de competición, gestión de fatiga brutal.', tags: ['Powerlifting', 'Alta Frecuencia', 'Alta Tolerancia'], pattern: ['Sentadilla/Banca', 'Descanso', 'Peso Muerto/Banca', 'Descanso', 'Sentadilla/Banca', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen de práctica ALTÍSIMO', 'Técnica altamente refinada', 'Frecuencia 3x banca/sentadilla', 'Metodología soviética probada', 'Ideal para fuerza máxima'],
        cons: ['Volumen BRUTAL (no es para débiles)', 'Fatiga acumulativa extrema', 'Requiere recuperación óptima', 'Técnica DEBE ser perfecta', 'Solo para avanzados dedicados'] },
    
    { id: 'sheiko_4day', name: 'Sheiko 4 Días', description: 'Volumen distribuido. Para atletas que necesitan más práctica técnica.', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla', 'Banca', 'Descanso', 'Peso Muerto', 'Banca', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen distribuido en 4 días', 'Más recuperación entre sesiones', 'Frecuencia alta de banca', 'Técnica refinada'],
        cons: ['Volumen total aún brutal', 'Fatiga acumulativa alta', 'Requiere recuperación óptima', 'Solo para avanzados'] },
    
    { id: 'bulgarian_lite', name: 'Método Búlgaro (Lite)', description: 'Inspirado en Abadjiev. Alta intensidad diaria. Solo para masoquistas.', tags: ['Alta Frecuencia', 'Alta Tolerancia', 'Powerlifting'], pattern: ['SBD Max', 'SBD Max', 'SBD Max', 'SBD Max', 'SBD Max', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Intensidad máxima diaria', 'Adaptación neural EXTREMA', 'Técnica bajo fatiga', 'Método legendario'],
        cons: ['SOLO para élite con genética superior', 'Fatiga del SNC brutal', 'Requiere recuperación perfecta', 'Riesgo de lesión ALTÍSIMO', 'No sostenible a largo plazo', 'Estilo "máximo o nada"'] },
    
    { id: 'russian_bear', name: 'Oso Ruso', description: 'Volumen brutal con cargas moderadas. Hipertrofia y fuerza base.', tags: ['Alto Volumen', 'Alta Tolerancia'], pattern: ['Sentadilla/Banca', 'Descanso', 'Peso Muerto/Press', 'Descanso', 'Sentadilla/Banca', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Volumen alto con cargas manejables', 'Balance fuerza/hipertrofia', '3 días de SBD', 'Recuperación integrada'],
        cons: ['Volumen total alto', 'Fatiga acumulativa media-alta', 'Requiere buena nutrición', 'Puede estancar en avanzados'] },
    
    { id: 'westside_conjugate', name: 'Westside (Conjugado)', description: 'Método Louie Simmons. Días de Esfuerzo Máximo (ME) y Dinámico (DE).', tags: ['Powerlifting', 'Recomendado por KPKN', 'Balanceado'], pattern: ['ME Lower', 'ME Upper', 'Descanso', 'DE Lower', 'DE Upper', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Desarrollo simultáneo fuerza/potencia', 'Variación de ejercicios constante', 'Método probado por campeones', 'Balance ME/DE inteligente'],
        cons: ['Requiere equipamiento específico', 'Curva de aprendizaje alta', 'Fatiga del SNC alta', 'Accesorios críticos (no omitir)'] },
    
    { id: 'coan_split', name: 'Split Ed Coan', description: 'La distribución del GOAT. 4 días, linealización clásica.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Sentadilla/Pierna', 'Descanso', 'Press Banca/Pecho', 'Peso Muerto/Espalda', 'Hombros/Brazos', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Diseñado por el mejor powerlifter de la historia', '4 días manejables', 'Balance SBD óptimo', 'Día de accesorios dedicado', 'Linealización probada'],
        cons: ['Requiere buena recuperación', 'Volumen de accesorios crítico', 'Técnica debe ser sólida', 'No es para principiantes'] },
    
    { id: 'bill_starr_5x5', name: 'Bill Starr 5x5', description: 'La base del atleta de fuerza. Pesado / Liviano / Medio.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body Pesado', 'Descanso', 'Full Body Liviano', 'Descanso', 'Full Body Medio', 'Descanso', 'Descanso'], difficulty: 'Principiante',
        pros: ['Ondulación de cargas clásica', 'Ideal para principiantes/intermedios', 'Progresión lineal probada', '3 días manejables', 'Base sólida de fuerza'],
        cons: ['Frecuencia baja para avanzados', 'Progreso se estanca', 'Volumen limitado', 'Requiere cambio a programa intermedio'] },
    
    { id: 'cube_method', name: 'Método Cubo', description: 'Rotación de esfuerzos (Pesado, Repeticiones, Explosivo) por Brandon Lilly.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Día Pesado (Rotativo)', 'Día Explosivo', 'Descanso', 'Día Repeticiones', 'Bodybuilding', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Variedad de estímulos (fuerza/potencia/volumen)', 'Previene estancamientos', 'Día bodybuilding para hipertrofia', 'Método moderno probado'],
        cons: ['Coordinación compleja', 'Requiere conocimiento de esfuerzos', 'Fatiga variable', 'Solo para intermedios/avanzados'] },
    
    { id: 'dorian_yates', name: 'Blood & Guts (Yates)', description: 'HIT (High Intensity Training). Bajo volumen, fallo absoluto.', tags: ['Baja Frecuencia', 'Alta Tolerancia'], pattern: ['Hombro/Tríceps', 'Espalda', 'Descanso', 'Pecho/Bíceps', 'Piernas', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Intensidad MÁXIMA por serie', 'Volumen bajo (recuperable)', 'Fallo muscular garantizado', 'Método de Mr. Olympia', 'Ideal para naturales con poco tiempo'],
        cons: ['Frecuencia 1x/semana SUBÓPTIMA', 'Requiere intensidad REAL (difícil de lograr)', 'Técnica bajo fallo crítica', 'No recomendado para principiantes', 'Riesgo de lesión si fallas mal'] },
    
    { id: 'mentzer_heavy_duty', name: 'Heavy Duty (Mentzer)', description: 'Frecuencia ultra baja. Una serie al fallo y a casa. Descanso extremo.', tags: ['Baja Frecuencia', 'Personalizado'], pattern: ['Pecho/Espalda', 'Descanso', 'Descanso', 'Piernas', 'Descanso', 'Descanso', 'Hombros/Brazos'], difficulty: 'Avanzado',
        pros: ['Volumen MÍNIMO (1-2 series)', 'Recuperación EXTREMA entre sesiones', 'Intensidad máxima por serie', 'Filosofía HIT pura de Mentzer', 'Ideal para sobreentrenados'],
        cons: ['Frecuencia 1x/7-10 días BAJÍSIMA', 'NO óptimo para naturales (Schoenfeld)', 'Progreso MUY lento', 'Requiere fallo REAL (difícil)', 'Psicológicamente demandante', 'Volumen insuficiente para mayoría'] },
    
    { id: 'arnold_classic_6', name: 'Arnold Clásico 6 Días', description: 'La rutina de la "Enciclopedia". Volumen olímpico.', tags: ['Alto Volumen', 'Alta Frecuencia'], pattern: ['Pecho/Espalda', 'Hombros/Brazos', 'Piernas', 'Pecho/Espalda', 'Hombros/Brazos', 'Piernas', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Volumen ALTÍSIMO por músculo', 'Frecuencia 2x/semana', 'Método del mejor culturista', 'Énfasis en grupos débiles', 'Estética clásica garantizada'],
        cons: ['6 días requeridos (vida social limitada)', 'Volumen puede ser excesivo para naturales', 'Recuperación CRÍTICA', 'Riesgo de sobreentrenamiento', 'Requiere nutrición perfecta', 'Solo para dedicados extremos'] },
    
    { id: 'chinese_hybrid', name: 'Híbrido Chino', description: 'Énfasis en Squat y Pull diario. Estructura de equipo nacional adaptada.', tags: ['Alta Frecuencia', 'Powerlifting', 'Personalizado'], pattern: ['Squat/Press', 'Pull/Accesorios', 'Squat/Press', 'Pull/Accesorios', 'Squat Max', 'Bodybuilding', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Frecuencia alta de sentadilla (4-5x)', 'Método de equipo nacional chino', 'Técnica altamente practicada', 'Volumen distribuido'],
        cons: ['Fatiga de sentadilla EXTREMA', 'Requiere recuperación óptima', 'Solo para avanzados', 'Puede causar sobreentrenamiento', 'Técnica debe ser perfecta'] },
    
    { id: '531_bbb', name: '5/3/1 Boring But Big', description: 'El clásico de Jim Wendler. Un movimiento principal + volumen básico.', tags: ['Powerlifting', 'Balanceado', 'Baja Frecuencia'], pattern: ['Press Militar/Hombro', 'Peso Muerto/Espalda', 'Descanso', 'Press Banca/Pecho', 'Sentadilla/Pierna', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Progresión lenta y sostenible (Wendler)', 'Volumen de accesorios "aburrido pero efectivo"', 'Frecuencia 1x/semana por lift (suficiente)', 'Ideal para fuerza a largo plazo', 'Flexible y adaptable'],
        cons: ['Progreso MUY lento (meses para ver cambios)', 'Frecuencia baja para hipertrofia', 'Requiere paciencia extrema', 'Accesorios pueden quedar limitados'] },
    
    { id: 'madcow_5x5', name: 'Madcow 5x5', description: 'Progresión lineal avanzada. Frecuencia media con ondulación de cargas.', tags: ['Powerlifting', 'Balanceado'], pattern: ['Volumen (5x5)', 'Descanso', 'Recuperación (Light)', 'Descanso', 'Intensidad (1x3/1x5)', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Progresión lineal probada', 'Ondulación de cargas inteligente', '3 días manejables', 'Ideal para intermedios', 'Base sólida de fuerza'],
        cons: ['Progreso se estanca en avanzados', 'Frecuencia baja para hipertrofia', 'Requiere cambio a programa avanzado', 'Volumen limitado'] },
    
    { id: 'korte_3x3', name: 'Korte 3x3', description: 'Escuela alemana. Solo SBD (Sentadilla, Banca, Peso Muerto), 3 veces por semana.', tags: ['Powerlifting', 'Alta Frecuencia', 'Baja Frecuencia'], pattern: ['SBD (Volumen)', 'Descanso', 'SBD (Técnica)', 'Descanso', 'SBD (Intensidad)', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Especificidad MÁXIMA en SBD', 'Frecuencia 3x/semana por lift', 'Técnica altamente practicada', 'Método alemán probado', 'Ideal para competición'],
        cons: ['Cero accesorios (puede causar desbalances)', 'Fatiga articular alta', 'Solo para powerlifters puros', 'Requiere técnica perfecta', 'Riesgo de lesión si técnica falla'] },
    
    { id: 'gzcl_method', name: 'Método GZCL (Tiered)', description: 'Estructura piramidal: T1 (Pesado), T2 (Volumen), T3 (Accesorios).', tags: ['Powerlifting', 'Personalizado', 'Balanceado'], pattern: ['T1 Sentadilla', 'T1 Banca', 'Descanso', 'T1 Peso Muerto', 'T1 Militar/Sling', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Estructura de niveles clara (T1/T2/T3)', 'Progresión lineal probada', 'Balance fuerza/volumen/accesorios', 'Altamente personalizable', 'Método moderno de Cody Lefever'],
        cons: ['Curva de aprendizaje media', 'Requiere entender los tiers', 'Fatiga acumulativa media', 'Accesorios críticos (no omitir)'] },
    
    { id: 'tsa_inter', name: 'TSA Intermedio', description: 'The Strength Athlete. Frecuencia y especificidad moderna (4 días).', tags: ['Powerlifting', 'Alta Frecuencia'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Accesorios', 'Descanso', 'Banca/Sentadilla Var.', 'Peso Muerto/Banca Var.', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Frecuencia 2x/semana por lift', 'Variación de ejercicios integrada', '4 días manejables', 'Método moderno probado', 'Balance SBD óptimo'],
        cons: ['Coordinación compleja', 'Fatiga acumulativa media-alta', 'Requiere buena recuperación', 'Accesorios críticos'] },
    
    { id: 'calgary_barbell', name: 'Estilo Calgary', description: 'Alta variedad de ejercicios y gestión de fatiga precisa (4 días).', tags: ['Powerlifting', 'Alta Frecuencia', 'Recomendado por KPKN'], pattern: ['Sentadilla/Banca', 'Peso Muerto/Press', 'Descanso', 'Sentadilla/Banca (Var.)', 'Peso Muerto (Var.)', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Variedad de ejercicios alta', 'Gestión de fatiga precisa', 'Frecuencia 2x/semana', '4 días manejables', 'Método canadiense probado'],
        cons: ['Coordinación compleja', 'Requiere conocimiento de variantes', 'Fatiga acumulativa media-alta', 'Solo para intermedios/avanzados'] },
    
    { id: 'deathbench_spec', name: 'Deathbench (Especialización)', description: 'Solo para fanáticos del Press Banca. Volumen masivo de empuje.', tags: ['Powerlifting', 'Alto Volumen', 'Personalizado'], pattern: ['Banca Volumen', 'Descanso', 'Tríceps/Hombro', 'Descanso', 'Banca Intensidad', 'Espalda/Bíceps', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Especialización EXTREMA en banca', 'Volumen de empuje altísimo', 'Frecuencia 2x/semana banca', 'Accesorios de empuje dedicados', 'Ideal para récords de banca'],
        cons: ['Fatiga de hombros/codos CRÍTICA', 'Sentadilla y DL quedán limitados', 'Riesgo de lesión de hombro ALTO', 'Solo para especialistas', 'Requiere recuperación perfecta'] },
    
    { id: 'lilliebridge_method', name: 'Método Lilliebridge', description: 'Heavy/Light rotativo. Brutal para pesos muertos pesados.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Sentadilla/PM Pesado', 'Descanso', 'Banca Pesada', 'Descanso', 'Sentadilla/PM Ligero', 'Banca Ligera/Acc.', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Ondulación Heavy/Light probada', 'Énfasis en peso muerto pesado', 'Frecuencia 2x/semana por lift', 'Método de familia legendaria', 'Ideal para fuerza máxima'],
        cons: ['Fatiga de peso muerto EXTREMA', 'Requiere recuperación óptima', 'Solo para avanzados', 'Riesgo de lesión si técnica falla', 'Volumen de accesorios limitado'] },
    
    { id: 'conjugate_3day', name: 'Conjugado 3 Días', description: 'Westside adaptado para recuperación limitada. Rota ME y DE semanalmente.', tags: ['Powerlifting', 'Baja Frecuencia'], pattern: ['Max Effort Lower', 'Descanso', 'Max Effort Upper', 'Descanso', 'Dynamic Effort (Full)', 'Descanso', 'Descanso'], difficulty: 'Avanzado',
        pros: ['Westside adaptado a 3 días', 'Rotación ME/DE semanal', 'Ideal para recuperación limitada', 'Método conjugado probado'],
        cons: ['Frecuencia más baja que Westside original', 'Progreso más lento', 'Requiere equipamiento específico', 'Curva de aprendizaje alta'] },
    
    { id: 'ul_arms', name: 'Upper / Lower + Brazos', description: 'Estructura torso/pierna con día de especialización de brazos.', tags: ['Alto Volumen', 'Personalizado'], pattern: ['Torso', 'Pierna', 'Descanso', 'Torso', 'Pierna', 'Brazos/Hombros', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Día dedicado de brazos', 'Frecuencia 2x/semana torso/pierna', 'Énfasis estético en brazos', '4 días base + 1 especializado', 'Balance funcional/estético'],
        cons: ['5 días requeridos', 'Fatiga de brazos alta', 'Puede quedar desbalanceado si no se periodiza', 'Día de brazos puede ser redundante'] },
    
    { id: 'heavy_light', name: 'Pesado / Liviano x4', description: 'Gestión ondulatoria diaria. Ideal recuperación.', tags: ['Baja Frecuencia', 'Balanceado'], pattern: ['Full Body Pesado', 'Descanso', 'Full Body Liviano', 'Descanso', 'Full Body Moderado', 'Descanso', 'Descanso'], difficulty: 'Intermedio',
        pros: ['Ondulación de cargas diaria', 'Recuperación óptima entre sesiones', '4 días manejables', 'Ideal para fuerza e hipertrofia', 'Gestión de fatiga inteligente'],
        cons: ['Coordinación de intensidades compleja', 'Requiere autoconocimiento', 'Progreso más lento que lineal', 'Puede estancar en avanzados'] },
];
