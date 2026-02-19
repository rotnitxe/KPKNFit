// data/initialMuscleHierarchy.ts
import { MuscleHierarchy } from '../types';

export const INITIAL_MUSCLE_HIERARCHY: MuscleHierarchy = {
  bodyPartHierarchy: {
    'Abdomen': [
        'Recto Abdominal', 
        'Oblicuos', 
        'Transverso Abdominal'
    ],
    'Brazos': [
        { 'Bíceps': ['Cabeza Larga (Bíceps)', 'Cabeza Corta (Bíceps)'] },
        { 'Tríceps': ['Cabeza Larga (Tríceps)', 'Cabeza Lateral (Tríceps)', 'Cabeza Medial (Tríceps)'] },
        'Braquial', 
        'Braquiorradial', 
        { 'Antebrazo': ['Flexores de Antebrazo', 'Extensores de Antebrazo'] }
    ],
    'Espalda': [
        'Dorsal Ancho', 
        'Redondo Mayor', 
        { 'Trapecio': ['Trapecio Superior', 'Trapecio Medio', 'Trapecio Inferior'] }, 
        'Erectores Espinales', 
        'Romboides'
    ],
    'Hombros': [
        { 'Deltoides': ['Deltoides Anterior', 'Deltoides Lateral', 'Deltoides Posterior'] },
        'Serrato Anterior'
    ],
    'Pecho': [
        { 'Pectoral': ['Pectoral Superior', 'Pectoral Medio', 'Pectoral Inferior'] }
    ],
    'Piernas': [
        { 'Cuádriceps': ['Recto Femoral', 'Vasto Lateral', 'Vasto Medial'] },
        { 'Isquiosurales': ['Bíceps Femoral', 'Semitendinoso', 'Semimembranoso'] },
        'Aductores',
        { 'Glúteos': ['Glúteo Mayor', 'Glúteo Medio', 'Glúteo Menor'] },
        { 'Pantorrillas': ['Gastrocnemio', 'Sóleo'] },
        'Tibial Anterior'
    ]
  },
  specialCategories: {
    'Tren Superior': ['Pecho', 'Espalda', 'Hombros', 'Brazos'],
    'Tren Inferior': ['Piernas'],
    'Core': ['Recto Abdominal', 'Oblicuos', 'Transverso Abdominal', 'Erectores Espinales'],
    'Cadena Anterior': ['Pectoral', 'Deltoides Anterior', 'Recto Abdominal', 'Cuádriceps'],
    'Cadena Posterior': ['Dorsal Ancho', 'Trapecio', 'Erectores Espinales', 'Glúteos', 'Isquiosurales', 'Pantorrillas']
  },
  muscleToBodyPart: {
    // Abdomen
    'Recto Abdominal': 'Abdomen',
    'Oblicuos': 'Abdomen',
    'Transverso Abdominal': 'Abdomen',
    'Suelo Pélvico': 'Abdomen',
    'Diafragma': 'Abdomen',
    // Brazos
    'Bíceps': 'Brazos',
    'Cabeza Larga (Bíceps)': 'Brazos',
    'Cabeza Corta (Bíceps)': 'Brazos',
    'Tríceps': 'Brazos',
    'Cabeza Larga (Tríceps)': 'Brazos',
    'Cabeza Lateral (Tríceps)': 'Brazos',
    'Cabeza Medial (Tríceps)': 'Brazos',
    'Braquial': 'Brazos',
    'Braquiorradial': 'Brazos',
    'Antebrazo': 'Brazos',
    'Flexores de Antebrazo': 'Brazos',
    'Extensores de Antebrazo': 'Brazos',
    // Espalda
    'Dorsal Ancho': 'Espalda',
    'Redondo Mayor': 'Espalda',
    'Trapecio': 'Espalda',
    'Trapecio Superior': 'Espalda',
    'Trapecio Medio': 'Espalda',
    'Trapecio Inferior': 'Espalda',
    'Erectores Espinales': 'Espalda',
    'Romboides': 'Espalda',
    'Multífidos': 'Espalda',
    // Hombros
    'Deltoides': 'Hombros',
    'Deltoides Anterior': 'Hombros',
    'Deltoides Lateral': 'Hombros',
    'Deltoides Posterior': 'Hombros',
    'Serrato Anterior': 'Hombros',
    // Pecho
    'Pectoral': 'Pecho',
    'Pectoral Superior': 'Pecho',
    'Pectoral Medio': 'Pecho',
    'Pectoral Inferior': 'Pecho',
    // Piernas
    'Cuádriceps': 'Piernas',
    'Recto Femoral': 'Piernas',
    'Vasto Lateral': 'Piernas',
    'Vasto Medial': 'Piernas',
    'Isquiosurales': 'Piernas',
    'Bíceps Femoral': 'Piernas',
    'Semitendinoso': 'Piernas',
    'Semimembranoso': 'Piernas',
    'Aductores': 'Piernas',
    'Glúteos': 'Piernas',
    'Glúteo Mayor': 'Piernas',
    'Glúteo Medio': 'Piernas',
    'Glúteo Menor': 'Piernas',
    'Pantorrillas': 'Piernas',
    'Gastrocnemio': 'Piernas',
    'Sóleo': 'Piernas',
    'Tibial Anterior': 'Piernas'
  },
};