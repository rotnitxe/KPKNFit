// apply_catalog_p1_jalon_dominadas.js
const fs = require('fs');
const {
  loadDb, saveDb, loadAliases, saveAliases,
  makeAspect, makeOption, makeModifiers,
  mergeInto, rebuildCatalog, finalizeAliases,
} = require('./catalog_transform_base');
const path = require('path');

const db = loadDb();
const aliases = loadAliases();
const byId = new Map(db.map(e => [e.id, e]));
const removeIds = new Set();
const aliasAdds = {};
const applied = [];

// ===== JALÓN AL PECHO =====

const jalonPoleaBase = byId.get('back_jalon_pecho_polea_ancho');
const jalonPolea = {
  ...jalonPoleaBase,
  id: 'back_jalon_pecho_polea',
  name: 'Jalón al Pecho en Polea',
  description: 'Jalón al pecho en polea alta. Ajustable en amplitud, orientación de agarre y lateralidad.',
  alias: 'Jalón al Pecho en Polea',
};
jalonPolea.technicalAspects = [
  makeAspect('grip_width', 'Amplitud', 'Ancho del agarre.', 'wide', [
    makeOption(jalonPolea.involvedMuscles, 'wide', 'Amplio', 'Mayor énfasis en dorsales y trapecio superior.', [['Dorsales', 'add', 0.1], ['Trapecio', 'add', 0.1]]),
    makeOption(jalonPolea.involvedMuscles, 'medium', 'Medio', 'Equilibrio general.', []),
    makeOption(jalonPolea.involvedMuscles, 'close', 'Cerrado', 'Mayor énfasis en bíceps y trapecio inferior.', [['Bíceps', 'add', 0.15], ['Trapecio', 'add', 0.05]]),
  ]),
  makeAspect('grip_orientation', 'Tipo de agarre', 'Orientación de las manos.', 'prono', [
    makeOption(jalonPolea.involvedMuscles, 'prono', 'Prono', 'Palmas abajo; énfasis dorsal.', []),
    makeOption(jalonPolea.involvedMuscles, 'supino', 'Supino', 'Palmas arriba; mayor activación de bíceps.', [['Bíceps', 'add', 0.15], ['Dorsales', 'add', -0.05]]),
    makeOption(jalonPolea.involvedMuscles, 'neutro', 'Neutro', 'Agarre neutro; énfasis puro dorsal, menos bíceps.', [['Bíceps', 'add', -0.1], ['Dorsales', 'add', 0.1]]),
  ]),
  makeAspect('laterality', 'Lateralidad', 'Bilateral o unilateral.', 'bilateral', [
    makeOption(jalonPolea.involvedMuscles, 'bilateral', 'Bilateral', 'Ambos brazos simultáneos.', []),
    makeOption(jalonPolea.involvedMuscles, 'unilateral', 'Unilateral', 'Un brazo a la vez; mayor demanda de core.', [['Erectores Espinales', 'add', 0.15]]),
  ]),
];
byId.set(jalonPolea.id, jalonPolea);

mergeInto('back_jalon_pecho_polea_ancho', 'back_jalon_pecho_polea', { grip_width: 'wide', grip_orientation: 'prono', laterality: 'bilateral' }, 'Jalón polea', { byId, removeIds, aliasAdds, applied });
mergeInto('back_jalon_pecho_polea_cerrado', 'back_jalon_pecho_polea', { grip_width: 'close', grip_orientation: 'prono', laterality: 'bilateral' }, 'Jalón polea', { byId, removeIds, aliasAdds, applied });
mergeInto('back_jalon_pecho_polea_unilateral', 'back_jalon_pecho_polea', { grip_width: 'wide', grip_orientation: 'prono', laterality: 'unilateral' }, 'Jalón polea', { byId, removeIds, aliasAdds, applied });
mergeInto('back_jalon_neutro_polea', 'back_jalon_pecho_polea', { grip_width: 'medium', grip_orientation: 'neutro', laterality: 'bilateral' }, 'Jalón polea', { byId, removeIds, aliasAdds, applied });

const jalonMaquinaBase = byId.get('back_jalon_pecho_maquina_ancho');
const jalonMaquina = {
  ...jalonMaquinaBase,
  id: 'back_jalon_pecho_maquina',
  name: 'Jalón al Pecho en Máquina',
  description: 'Jalón al pecho en máquina selectorizada. Ajustable en amplitud de agarre.',
  alias: 'Jalón al Pecho en Máquina',
};
jalonMaquina.technicalAspects = [
  makeAspect('grip_width', 'Amplitud', 'Ancho del agarre.', 'wide', [
    makeOption(jalonMaquina.involvedMuscles, 'wide', 'Amplio', 'Mayor énfasis dorsal.', [['Dorsales', 'add', 0.1]]),
    makeOption(jalonMaquina.involvedMuscles, 'close', 'Cerrado', 'Mayor énfasis bíceps.', [['Bíceps', 'add', 0.15]]),
  ]),
];
byId.set(jalonMaquina.id, jalonMaquina);

mergeInto('back_jalon_pecho_maquina_ancho', 'back_jalon_pecho_maquina', { grip_width: 'wide' }, 'Jalón máquina', { byId, removeIds, aliasAdds, applied });
mergeInto('back_jalon_pecho_maquina_cerrado', 'back_jalon_pecho_maquina', { grip_width: 'close' }, 'Jalón máquina', { byId, removeIds, aliasAdds, applied });

const jalonBandaBase = byId.get('back_jalon_banda_ancho');
const jalonBanda = {
  ...jalonBandaBase,
  id: 'back_jalon_banda',
  name: 'Jalón al Pecho con Banda',
  description: 'Jalón al pecho con banda elástica. Ajustable en amplitud de agarre.',
  alias: 'Jalón al Pecho con Banda',
};
jalonBanda.technicalAspects = [
  makeAspect('grip_width', 'Amplitud', 'Ancho del agarre.', 'wide', [
    makeOption(jalonBanda.involvedMuscles, 'wide', 'Amplio', 'Mayor énfasis dorsal.', [['Dorsales', 'add', 0.05]]),
    makeOption(jalonBanda.involvedMuscles, 'close', 'Cerrado', 'Mayor control, énfasis bíceps.', [['Bíceps', 'add', 0.1]]),
  ]),
];
byId.set(jalonBanda.id, jalonBanda);

mergeInto('back_jalon_banda_ancho', 'back_jalon_banda', { grip_width: 'wide' }, 'Jalón banda', { byId, removeIds, aliasAdds, applied });
mergeInto('back_jalon_banda_cerrado', 'back_jalon_banda', { grip_width: 'close' }, 'Jalón banda', { byId, removeIds, aliasAdds, applied });

// ===== DOMINADAS =====

const domBase = byId.get('back_dominadas_pronas');
const domCanon = {
  ...domBase,
  id: 'back_dominadas',
  name: 'Dominadas',
  description: 'Dominadas en barra fija. Ajustables en tipo de agarre y carga.',
  alias: 'Dominadas',
};
domCanon.technicalAspects = [
  makeAspect('grip_type', 'Tipo de agarre', 'Orientación de las manos.', 'prono', [
    makeOption(domCanon.involvedMuscles, 'prono', 'Prono', 'Palmas hacia adelante; énfasis dorsal.', []),
    makeOption(domCanon.involvedMuscles, 'supino', 'Supino', 'Palmas hacia ti; mayor bíceps.', [['Bíceps', 'add', 0.2], ['Deltoides', 'add', -0.05]]),
    makeOption(domCanon.involvedMuscles, 'neutro', 'Neutro', 'Agarre paralelo; énfasis puro dorsal.', [['Dorsales', 'add', 0.1], ['Bíceps', 'add', -0.05]]),
  ]),
  makeAspect('grip_width', 'Amplitud', 'Solo aplica a supino/prono fijas.', 'wide', [
    makeOption(domCanon.involvedMuscles, 'wide', 'Amplio', 'Mayor dorsal ancho.', [['Dorsales', 'add', 0.1]]),
    makeOption(domCanon.involvedMuscles, 'close', 'Cerrado', 'Más bíceps, menos dorsal.', [['Bíceps', 'add', 0.1], ['Dorsales', 'add', -0.05]]),
  ]),
  makeAspect('load_type', 'Carga', 'Condición de carga corporal.', 'bodyweight', [
    makeOption(domCanon.involvedMuscles, 'bodyweight', 'Peso corporal', 'Sin carga adicional.', []),
    makeOption(domCanon.involvedMuscles, 'loaded', 'Lastrada', 'Con peso añadido; mayor intensidad global.', [['Dorsales', 'add', 0.1], ['Bíceps', 'add', 0.1], ['Trapecio', 'add', 0.1]]),
    makeOption(domCanon.involvedMuscles, 'assisted_machine', 'Asistida Máquina', 'Con asistencia mecánica; menor intensidad.', [['Dorsales', 'add', -0.1], ['Bíceps', 'add', -0.1]]),
    makeOption(domCanon.involvedMuscles, 'assisted_band', 'Asistida Banda', 'Con banda elástica; escala progresiva.', [['Dorsales', 'add', -0.05], ['Bíceps', 'add', -0.05]]),
  ]),
];
byId.set(domCanon.id, domCanon);

mergeInto('back_dominadas_pronas', 'back_dominadas', { grip_type: 'prono', load_type: 'bodyweight' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_supinas', 'back_dominadas', { grip_type: 'supino', load_type: 'bodyweight' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_neutras', 'back_dominadas', { grip_type: 'neutro', load_type: 'bodyweight' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_lastradas', 'back_dominadas', { grip_type: 'prono', load_type: 'loaded' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_asistidas_maquina', 'back_dominadas', { grip_type: 'prono', load_type: 'assisted_machine' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_asistidas_banda', 'back_dominadas', { grip_type: 'prono', load_type: 'assisted_band' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('back_dominadas_anillas', 'back_dominadas', { grip_type: 'neutro', load_type: 'bodyweight' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('biceps_dominadas_supinas_cerradas', 'back_dominadas', { grip_type: 'supino', grip_width: 'close', load_type: 'bodyweight' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });
mergeInto('biceps_dominadas_supinas_lastradas', 'back_dominadas', { grip_type: 'supino', load_type: 'loaded' }, 'Dominadas', { byId, removeIds, aliasAdds, applied });

const domEsc = byId.get('back_dominadas_escapulares');
if (domEsc) {
  domEsc.name = 'Dominadas Escapulares';
  domEsc.technicalAspects = [
    makeAspect('range_type', 'Rango', 'Tipo de recorrido escapular.', 'scapular_only', [
      makeOption(domEsc.involvedMuscles, 'scapular_only', 'Solo escápula', 'Retracción y depresión escapular sin flexión de codo.', []),
      makeOption(domEsc.involvedMuscles, 'full', 'Completo', 'Añadir la fase de tirón completo tras la escapular.', [['Dorsales', 'add', 0.2], ['Bíceps', 'add', 0.15]]),
    ]),
  ];
}

const out = rebuildCatalog(db, byId, removeIds, [
  ['back_jalon_pecho_polea', 'back_jalon_pecho_polea_ancho'],
  ['back_jalon_pecho_maquina', 'back_jalon_pecho_maquina_ancho'],
  ['back_jalon_banda', 'back_jalon_banda_ancho'],
  ['back_dominadas', 'back_dominadas_pronas'],
]);

finalizeAliases(aliases, aliasAdds, removeIds, byId);

saveDb(out);
saveAliases(aliases);

const withTA = out.filter(e => e.technicalAspects && e.technicalAspects.length > 0);
console.log('=== P1a Jalón + Dominadas ===');
console.log('Filas:', db.length, '→', out.length);
console.log('Eliminadas:', removeIds.size);
console.log('Con technicalAspects:', withTA.length, 'de', out.length);
withTA.forEach(e => console.log(' ', e.id, '-', e.technicalAspects.length, 'aspectos'));

const root = path.join(__dirname, '..');
const auditPath = path.join(root, '../docs/EXERCISE_CATALOG_AUDIT.md');
const report = [
  '',
  '## Aplicado — oleada P1a Jalón + Dominadas',
  '',
  `- Filas antes: ${db.length}`,
  `- Filas después: ${out.length}`,
  `- Eliminadas/fusionadas: ${removeIds.size}`,
  `- Aliases nuevos: ${Object.keys(aliasAdds).length}`,
  '',
  '### Detalle',
  '',
  ...applied.map(l => `- ${l}`),
  '',
].join('\n');
fs.appendFileSync(auditPath, report, 'utf8');
console.log('OK — audit actualizado');

