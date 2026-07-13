// data/initialMuscleHierarchy.ts
import { MuscleHierarchy } from '../types';

export const INITIAL_MUSCLE_HIERARCHY: MuscleHierarchy = {
  bodyPartHierarchy: {
    'Tren Superior': [
      { 'Pectorales': ['Pectoral Superior (Clavicular)', 'Pectoral Inferior (Esternal)'] },
      { 'Dorsales': [] },
      { 'Trapecio': ['Trapecio Superior', 'Trapecio Medio', 'Trapecio Inferior'] },
      { 'Deltoides': ['Deltoides Anterior', 'Deltoides Medio', 'Deltoides Posterior'] },
      { 'Tríceps': ['Cabeza Larga (Tríceps)', 'Cabeza Lateral (Tríceps)', 'Cabeza Medial (Tríceps)'] },
      { 'Bíceps': ['Cabeza Larga (Bíceps)', 'Cabeza Corta (Bíceps)', 'Braquial'] },
      'Antebrazo',
      'Abdomen',
    ],
    'Tren Inferior': [
      { 'Cuádriceps': ['Recto Femoral', 'Vasto Lateral', 'Vasto Medial'] },
      { 'Isquiosurales': ['Bíceps Femoral', 'Semitendinoso'] },
      { 'Glúteos': ['Glúteo Mayor', 'Glúteo Medio'] },
      'Aductores',
      { 'Pantorrillas': ['Gastrocnemio', 'Sóleo'] },
    ],
    'Especial': [
      'Core',
      'Erectores Espinales',
    ],
  },
  specialCategories: {
    'Tren Superior': ['Pectorales', 'Dorsales', 'Trapecio', 'Deltoides', 'Tríceps', 'Bíceps', 'Antebrazo', 'Abdomen'],
    'Tren Inferior': ['Cuádriceps', 'Isquiosurales', 'Glúteos', 'Aductores', 'Pantorrillas'],
    'Core': ['Core', 'Erectores Espinales', 'Abdomen'],
    'Cadena Anterior': ['Pectorales', 'Deltoides', 'Abdomen', 'Cuádriceps'],
    'Cadena Posterior': ['Dorsales', 'Trapecio', 'Erectores Espinales', 'Glúteos', 'Isquiosurales', 'Pantorrillas'],
  },
  muscleToBodyPart: {
    // Tren Superior
    'Pectorales': 'Tren Superior',
    'Dorsales': 'Tren Superior',
    'Trapecio': 'Tren Superior',
    'Deltoides': 'Tren Superior',
    'Tríceps': 'Tren Superior',
    'Bíceps': 'Tren Superior',
    'Antebrazo': 'Tren Superior',
    'Abdomen': 'Tren Superior',
    // Tren Inferior
    'Cuádriceps': 'Tren Inferior',
    'Isquiosurales': 'Tren Inferior',
    'Glúteos': 'Tren Inferior',
    'Aductores': 'Tren Inferior',
    'Pantorrillas': 'Tren Inferior',
    // Especial
    'Core': 'Especial',
    'Erectores Espinales': 'Especial',
  },
};