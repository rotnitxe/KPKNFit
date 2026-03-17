import React, { useState, useMemo, useEffect } from 'react';
import {
  View,
  Text,
  StyleSheet,
  TouchableOpacity,
  ScrollView,
  Alert,
  Modal,
  TextInput,
  LayoutAnimation,
  Platform,
  UIManager,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { useNavigation, useRoute, RouteProp } from '@react-navigation/native';
import { NativeStackNavigationProp } from '@react-navigation/native-stack';
import { WorkoutStackParamList } from '../../navigation/types';
import { useColors } from '../../theme';
import {
  ArrowLeftIcon,
  PlusIcon,
  TrashIcon,
  EditIcon,
  ChevronDownIcon,
  ChevronUpIcon,
  CalendarIcon,
  ActivityIcon,
  TargetIcon,
  XIcon,
  SaveIcon,
  CheckIcon,
} from '../../components/icons';
import { useProgramStore } from '../../stores/programStore';
import { generateId } from '../../utils/generateId';
import { Program, Block, Mesocycle, ProgramWeek } from '../../types/workout';

if (Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
  UIManager.setLayoutAnimationEnabledExperimental(true);
}

interface EditingBlock {
  type: 'add' | 'edit';
  blockIndex?: number;
  data?: Block;
}

interface EditingMesocycle {
  type: 'add' | 'edit';
  blockIndex: number;
  mesoIndex?: number;
  data?: Mesocycle;
}

interface EditingWeek {
  type: 'add' | 'edit';
  blockIndex: number;
  mesoIndex: number;
  weekIndex?: number;
  data?: ProgramWeek;
}

const MESOCYCLE_GOALS = [
  { value: 'Acumulación', label: 'Acumulación' },
  { value: 'Intensificación', label: 'Intensificación' },
  { value: 'Realización', label: 'Realización' },
  { value: 'Descarga', label: 'Descarga' },
  { value: 'Custom', label: 'Personalizado' },
];

export const MacrocycleEditorScreen: React.FC = () => {
  const navigation = useNavigation<NativeStackNavigationProp<WorkoutStackParamList>>();
  const route = useRoute<RouteProp<WorkoutStackParamList, 'MacrocycleEditor'>>();
  const colors = useColors();
  const { programId } = route.params;

  const { programs, updateProgram } = useProgramStore();
  const program = useMemo(() => programs.find(p => p.id === programId), [programs, programId]);

  const [expandedBlocks, setExpandedBlocks] = useState<Set<string>>(new Set(['0']));
  const [editingBlock, setEditingBlock] = useState<EditingBlock | null>(null);
  const [editingMesocycle, setEditingMesocycle] = useState<EditingMesocycle | null>(null);
  const [editingWeek, setEditingWeek] = useState<EditingWeek | null>(null);
  
  const [showEventModal, setShowEventModal] = useState(false);
  const [eventData, setEventData] = useState({ id: '', title: '', week: 0, type: '1rm_test' });

  if (!program) return null;

  const toggleBlock = (blockKey: string) => {
    LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
    const newExpanded = new Set(expandedBlocks);
    if (newExpanded.has(blockKey)) {
      newExpanded.delete(blockKey);
    } else {
      newExpanded.add(blockKey);
    }
    setExpandedBlocks(newExpanded);
  };

  const totalStats = useMemo(() => {
    let totalWeeks = 0;
    let totalSessions = 0;
    let totalMesocycles = 0;
    let totalBlocks = 0;

    program.macrocycles.forEach(macro => {
      (macro.blocks || []).forEach(block => {
        totalBlocks++;
        block.mesocycles.forEach(meso => {
          totalMesocycles++;
          totalWeeks += meso.weeks.length;
          totalSessions += meso.weeks.reduce((acc, w) => acc + w.sessions.length, 0);
        });
      });
    });

    return { totalWeeks, totalSessions, totalMesocycles, totalBlocks };
  }, [program]);

  // Handlers
  const handleSaveProgram = async (updatedProgram: Program) => {
    await updateProgram(updatedProgram);
  };

  const handleAddBlock = () => {
    setEditingBlock({
      type: 'add',
      data: {
        id: generateId(),
        name: 'Nuevo Bloque',
        mesocycles: [{
          id: generateId(),
          name: 'Fase Inicial',
          goal: 'Acumulación',
          weeks: [{ id: generateId(), name: 'Semana 1', sessions: [] }]
        }]
      }
    });
  };

  const handleSaveBlock = (blockData: Block) => {
    const updated = JSON.parse(JSON.stringify(program));
    if (editingBlock?.type === 'add') {
      if (!updated.macrocycles[0].blocks) updated.macrocycles[0].blocks = [];
      updated.macrocycles[0].blocks.push(blockData);
    } else if (editingBlock?.type === 'edit' && editingBlock.blockIndex !== undefined) {
      updated.macrocycles[0].blocks[editingBlock.blockIndex] = blockData;
    }
    handleSaveProgram(updated);
    setEditingBlock(null);
  };

  const handleDeleteBlock = (blockIndex: number) => {
    Alert.alert(
      "Eliminar Bloque",
      "¿Estás seguro de que quieres eliminar este bloque y todo su contenido?",
      [
        { text: "Cancelar", style: "cancel" },
        { 
          text: "Eliminar", 
          style: "destructive",
          onPress: () => {
            const updated = JSON.parse(JSON.stringify(program));
            updated.macrocycles[0].blocks.splice(blockIndex, 1);
            handleSaveProgram(updated);
          }
        }
      ]
    );
  };

  const handleAddMesocycle = (blockIndex: number) => {
    setEditingMesocycle({
      type: 'add',
      blockIndex,
      data: {
        id: generateId(),
        name: 'Nuevo Mesociclo',
        goal: 'Acumulación',
        weeks: [{ id: generateId(), name: 'Semana 1', sessions: [] }]
      }
    });
  };

  const handleSaveMesocycle = (mesoData: Mesocycle) => {
    const updated = JSON.parse(JSON.stringify(program));
    if (editingMesocycle?.type === 'add') {
      updated.macrocycles[0].blocks[editingMesocycle.blockIndex].mesocycles.push(mesoData);
    } else if (editingMesocycle?.type === 'edit' && editingMesocycle.mesoIndex !== undefined) {
      updated.macrocycles[0].blocks[editingMesocycle.blockIndex].mesocycles[editingMesocycle.mesoIndex] = mesoData;
    }
    handleSaveProgram(updated);
    setEditingMesocycle(null);
  };

  const handleAddWeek = (blockIndex: number, mesoIndex: number) => {
    const currentWeeks = program.macrocycles[0].blocks![blockIndex].mesocycles[mesoIndex].weeks.length;
    setEditingWeek({
      type: 'add',
      blockIndex,
      mesoIndex,
      data: { id: generateId(), name: `Semana ${currentWeeks + 1}`, sessions: [] }
    });
  };

  const handleSaveEvent = () => {
    if (!eventData.title.trim()) {
      Alert.alert("Error", "El título es requerido");
      return;
    }
    const updated = JSON.parse(JSON.stringify(program));
    if (!updated.events) updated.events = [];
    
    if (eventData.id) {
      const idx = updated.events.findIndex((e: any) => e.id === eventData.id);
      if (idx !== -1) updated.events[idx] = { ...eventData };
    } else {
      updated.events.push({
        id: generateId(),
        title: eventData.title,
        type: eventData.type,
        calculatedWeek: eventData.week,
        date: new Date().toISOString()
      });
    }
    handleSaveProgram(updated);
    setShowEventModal(false);
  };

  const handleDeleteEvent = (eventId: string) => {
    const updated = JSON.parse(JSON.stringify(program));
    updated.events = updated.events.filter((e: any) => e.id !== eventId);
    handleSaveProgram(updated);
  };

  const handleDeleteMesocycle = (blockIndex: number, mesoIndex: number) => {
    Alert.alert("Eliminar Mesociclo", "¿Confirmas eliminar este mesociclo?", [
      { text: "No", style: "cancel" },
      { text: "Eliminar", style: "destructive", onPress: () => {
        const updated = JSON.parse(JSON.stringify(program));
        updated.macrocycles[0].blocks[blockIndex].mesocycles.splice(mesoIndex, 1);
        handleSaveProgram(updated);
      }}
    ]);
  };

  const handleSaveWeek = (weekData: ProgramWeek) => {
    const updated = JSON.parse(JSON.stringify(program));
    const blocks = updated.macrocycles[0].blocks;
    if (!blocks) return;
    const weeks = blocks[editingWeek!.blockIndex].mesocycles[editingWeek!.mesoIndex].weeks;
    
    if (editingWeek?.type === 'add') {
      weeks.push(weekData);
    } else if (editingWeek?.weekIndex !== undefined) {
      weeks[editingWeek.weekIndex] = weekData;
    }
    
    handleSaveProgram(updated);
    setEditingWeek(null);
  };

  const handleDeleteWeek = (blockIndex: number, mesoIndex: number, weekIndex: number) => {
    const updated = JSON.parse(JSON.stringify(program));
    const weeks = updated.macrocycles[0].blocks[blockIndex].mesocycles[mesoIndex].weeks;
    if (weeks.length <= 1) {
      Alert.alert("Error", "Debe haber al menos una semana");
      return;
    }
    Alert.alert("Eliminar Semana", "¿Confirmas eliminar la " + weeks[weekIndex].name + "?", [
      { text: "No", style: "cancel" },
      { text: "Eliminar", style: "destructive", onPress: () => {
        weeks.splice(weekIndex, 1);
        handleSaveProgram(updated);
      }}
    ]);
  };

  return (
    <SafeAreaView style={[styles.container, { backgroundColor: colors.background }]}>
      <View style={styles.header}>
        <TouchableOpacity onPress={() => navigation.goBack()} style={styles.backBtn}>
          <ArrowLeftIcon size={24} color={colors.onSurface} />
        </TouchableOpacity>
        <Text style={[styles.title, { color: colors.onSurface }]}>Editor de Macrociclo</Text>
      </View>

      <ScrollView style={styles.scroll} showsVerticalScrollIndicator={false}>
        {/* Stats */}
        <View style={styles.statsRow}>
          <StatCard label="Bloques" value={totalStats.totalBlocks} color={colors.surfaceContainerHigh} />
          <StatCard label="Mesos" value={totalStats.totalMesocycles} color={colors.surfaceContainerHigh} />
          <StatCard label="Semanas" value={totalStats.totalWeeks} color={colors.surfaceContainerHigh} />
          <StatCard label="Sesiones" value={totalStats.totalSessions} color={colors.surfaceContainerHigh} />
        </View>

        {/* Road To */}
        <View style={[styles.roadTo, { backgroundColor: colors.primary }]}>
          <View style={styles.roadToHeader}>
            <View style={styles.roadToTitleGroup}>
              <TargetIcon size={18} color={colors.onPrimary} />
              <Text style={[styles.roadToTitle, { color: colors.onPrimary }]}>ROAD TO</Text>
            </View>
            <TouchableOpacity 
              onPress={() => {
                setEventData({ id: '', title: '', week: 0, type: '1rm_test' });
                setShowEventModal(true);
              }}
              style={[styles.addEventBtn, { backgroundColor: colors.onPrimary + '30' }]}
            >
              <PlusIcon size={12} color={colors.onPrimary} />
              <Text style={[styles.addEventText, { color: colors.onPrimary }]}>EVENTO</Text>
            </TouchableOpacity>
          </View>
          
          <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.eventScroll}>
            {program.events && program.events.length > 0 ? (
              program.events.map((ev: any, idx: number) => (
                <TouchableOpacity 
                  key={ev.id || idx} 
                  onPress={() => {
                    setEventData({ ...ev, week: ev.calculatedWeek });
                    setShowEventModal(true);
                  }}
                  style={[styles.eventTag, { backgroundColor: colors.onPrimary + '20' }]}
                >
                  <CalendarIcon size={10} color={colors.onPrimary} />
                  <View>
                    <Text style={[styles.eventTitle, { color: colors.onPrimary }]}>{ev.title}</Text>
                    <Text style={[styles.eventSub, { color: colors.onPrimary + '70' }]}>S{(ev.calculatedWeek || 0) + 1}</Text>
                  </View>
                </TouchableOpacity>
              ))
            ) : (
                <Text style={{ color: colors.onPrimary + '70', fontSize: 10, fontStyle: 'italic' }}>Sin eventos programados</Text>
            )}
          </ScrollView>
        </View>

        {/* Structure List */}
        <View style={styles.structureHeader}>
          <Text style={[styles.sectionTitle, { color: colors.onSurfaceVariant }]}>ESTRUCTURA DEL PROGRAMA</Text>
          <TouchableOpacity onPress={handleAddBlock} style={[styles.addBtn, { backgroundColor: colors.primary }]}>
            <PlusIcon size={16} color={colors.onPrimary} />
            <Text style={[styles.addBtnText, { color: colors.onPrimary }]}>BLOQUE</Text>
          </TouchableOpacity>
        </View>

        {(program.macrocycles[0]?.blocks || []).map((block, blockIdx) => {
          const isExpanded = expandedBlocks.has(blockIdx.toString());
          return (
            <View key={block.id || blockIdx} style={[styles.blockCard, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}>
              <TouchableOpacity onPress={() => toggleBlock(blockIdx.toString())} style={styles.blockHeader}>
                <View style={[styles.expandIcon, { backgroundColor: colors.primaryContainer }]}>
                  {isExpanded ? <ChevronUpIcon size={16} color={colors.onPrimaryContainer} /> : <ChevronDownIcon size={16} color={colors.onPrimaryContainer} />}
                </View>
                <View style={styles.blockInfo}>
                  <Text style={[styles.blockName, { color: colors.onSurface }]}>{block.name}</Text>
                  <Text style={[styles.blockSub, { color: colors.onSurfaceVariant }]}>
                    {block.mesocycles.length} mesos • {block.mesocycles.reduce((a, b) => a + b.weeks.length, 0)} semanas
                  </Text>
                </View>
                <View style={styles.blockActions}>
                  <TouchableOpacity onPress={() => setEditingBlock({ type: 'edit', blockIndex: blockIdx, data: block })} style={styles.iconBtn}>
                    <EditIcon size={16} color={colors.onSurfaceVariant} />
                  </TouchableOpacity>
                  <TouchableOpacity onPress={() => handleDeleteBlock(blockIdx)} style={styles.iconBtn}>
                    <TrashIcon size={16} color={colors.error} />
                  </TouchableOpacity>
                </View>
              </TouchableOpacity>

              {isExpanded && (
                <View style={styles.blockContent}>
                  {block.mesocycles.map((meso, mesoIdx) => (
                    <View key={meso.id || mesoIdx} style={[styles.mesoCard, { backgroundColor: colors.surfaceContainerHigh }]}>
                      <View style={styles.mesoHeader}>
                        <View style={styles.mesoTitleGroup}>
                          <View style={[styles.mesoIndicator, { backgroundColor: colors.primary }]} />
                          <View>
                            <Text style={[styles.mesoGoal, { color: colors.primary }]}>{meso.goal?.toUpperCase()}</Text>
                            <Text style={[styles.mesoName, { color: colors.onSurface }]}>{meso.name}</Text>
                          </View>
                        </View>
                        <View style={styles.blockActions}>
                           <TouchableOpacity onPress={() => setEditingMesocycle({ type: 'edit', blockIndex: blockIdx, mesoIndex: mesoIdx, data: meso })} style={styles.iconBtnSmall}>
                            <EditIcon size={12} color={colors.onSurfaceVariant} />
                          </TouchableOpacity>
                          <TouchableOpacity onPress={() => handleDeleteMesocycle(blockIdx, mesoIdx)} style={styles.iconBtnSmall}>
                            <TrashIcon size={12} color={colors.error} />
                          </TouchableOpacity>
                        </View>
                      </View>

                      <View style={styles.weeksGrid}>
                        {meso.weeks.map((week, weekIdx) => (
                          <TouchableOpacity 
                            key={week.id || weekIdx} 
                            onLongPress={() => handleDeleteWeek(blockIdx, mesoIdx, weekIdx)}
                            onPress={() => setEditingWeek({ type: 'edit', blockIndex: blockIdx, mesoIndex: mesoIdx, weekIndex: weekIdx, data: week })}
                            style={[styles.weekBadge, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                          >
                            <Text style={[styles.weekLabel, { color: colors.onSurface }]}>S{weekIdx + 1}</Text>
                            <Text style={[styles.weekSub, { color: colors.onSurfaceVariant }]}>{week.sessions.length} ses</Text>
                          </TouchableOpacity>
                        ))}
                        <TouchableOpacity onPress={() => handleAddWeek(blockIdx, mesoIdx)} style={[styles.addWeekBtn, { borderColor: colors.outlineVariant, borderStyle: 'dotted', borderWidth: 1 }]}>
                          <PlusIcon size={14} color={colors.onSurfaceVariant} />
                        </TouchableOpacity>
                      </View>
                    </View>
                  ))}
                  <TouchableOpacity onPress={() => handleAddMesocycle(blockIdx)} style={[styles.addMesoBtn, { borderColor: colors.outlineVariant, borderStyle: 'dotted' }]}>
                    <PlusIcon size={16} color={colors.onSurfaceVariant} />
                    <Text style={[styles.addMesoText, { color: colors.onSurfaceVariant }]}>AÑADIR MESOCICLO</Text>
                  </TouchableOpacity>
                </View>
              )}
            </View>
          );
        })}
      </ScrollView>

      {/* Entity Modal */}
      {(editingBlock || editingMesocycle || editingWeek) && (
        <EntityModal 
          onClose={() => {
            setEditingBlock(null);
            setEditingMesocycle(null);
            setEditingWeek(null);
          }}
          onSave={(data) => {
            if (editingBlock) handleSaveBlock(data as Block);
            else if (editingMesocycle) handleSaveMesocycle(data as Mesocycle);
            else if (editingWeek) handleSaveWeek(data as ProgramWeek);
          }}
          initialData={editingBlock?.data || editingMesocycle?.data || editingWeek?.data}
          type={editingBlock ? 'block' : editingMesocycle ? 'meso' : 'week'}
        />
      )}

      {/* Event Modal */}
      {showEventModal && (
        <Modal transparent animationType="fade" visible>
           <View style={styles.modalOverlay}>
             <View style={[styles.modalContent, { backgroundColor: colors.surfaceContainerHigh }]}>
                <View style={styles.modalHeader}>
                  <Text style={[styles.modalTitle, { color: colors.onSurface }]}>
                    {eventData.id ? 'Editar Evento' : 'Nuevo Evento'}
                  </Text>
                  <TouchableOpacity onPress={() => setShowEventModal(false)}>
                    <XIcon size={24} color={colors.onSurfaceVariant} />
                  </TouchableOpacity>
                </View>

                <View style={styles.formGroup}>
                  <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>TÍTULO</Text>
                  <TextInput 
                    value={eventData.title} 
                    onChangeText={(t) => setEventData({...eventData, title: t})}
                    placeholder="Ej: Max Squat Test"
                    placeholderTextColor={colors.onSurfaceVariant + '60'}
                    style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                  />
                </View>

                <View style={styles.formGroup}>
                  <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>SEMANA (1-base)</Text>
                  <TextInput 
                    value={(eventData.week + 1).toString()} 
                    onChangeText={(v) => setEventData({...eventData, week: (parseInt(v) || 1) - 1})}
                    keyboardType="numeric"
                    style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
                  />
                </View>

                <View style={styles.modalFooter}>
                  {eventData.id && (
                    <TouchableOpacity onPress={() => { handleDeleteEvent(eventData.id); setShowEventModal(false); }} style={styles.deleteBtn}>
                      <TrashIcon size={20} color={colors.error} />
                    </TouchableOpacity>
                  )}
                  <TouchableOpacity onPress={handleSaveEvent} style={[styles.saveBtnFull, { backgroundColor: colors.primary }]}>
                    <SaveIcon size={18} color={colors.onPrimary} />
                    <Text style={[styles.saveBtnText, { color: colors.onPrimary }]}>GUARDAR EVENTO</Text>
                  </TouchableOpacity>
                </View>
             </View>
           </View>
        </Modal>
      )}
    </SafeAreaView>
  );
};

const StatCard = ({ label, value, color }: { label: string; value: number; color: string }) => (
  <View style={[styles.statCard, { backgroundColor: color }]}>
    <Text style={styles.statValue}>{value}</Text>
    <Text style={styles.statLabel}>{label.toUpperCase()}</Text>
  </View>
);

const EntityModal = ({ onClose, onSave, initialData, type }: { onClose: () => void; onSave: (data: any) => void; initialData: any; type: 'block' | 'meso' | 'week' }) => {
  const [data, setData] = useState(JSON.parse(JSON.stringify(initialData)));
  const colors = useColors();

  return (
    <Modal transparent animationType="fade" visible>
       <View style={styles.modalOverlay}>
         <View style={[styles.modalContent, { backgroundColor: colors.surfaceContainerHigh }]}>
            <View style={styles.modalHeader}>
              <Text style={[styles.modalTitle, { color: colors.onSurface }]}>
                {type === 'block' ? 'Editar Bloque' : type === 'meso' ? 'Editar Mesociclo' : 'Editar Semana'}
              </Text>
              <TouchableOpacity onPress={onClose}>
                <XIcon size={24} color={colors.onSurfaceVariant} />
              </TouchableOpacity>
            </View>

            <View style={styles.formGroup}>
              <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>NOMBRE</Text>
              <TextInput 
                value={data.name} 
                onChangeText={(t) => setData({...data, name: t})}
                style={[styles.input, { color: colors.onSurface, backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}
              />
            </View>

            {type === 'meso' && (
              <View style={styles.formGroup}>
                <Text style={[styles.label, { color: colors.onSurfaceVariant }]}>OBJETIVO</Text>
                <View style={styles.goalGrid}>
                  {MESOCYCLE_GOALS.map(g => (
                    <TouchableOpacity 
                      key={g.value} 
                      onPress={() => setData({...data, goal: g.value})}
                      style={[
                        styles.goalBadge, 
                        { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                        data.goal === g.value && { backgroundColor: colors.primaryContainer, borderColor: colors.primary }
                      ]}
                    >
                      <Text style={[styles.goalText, { color: data.goal === g.value ? colors.onPrimaryContainer : colors.onSurfaceVariant }]}>{g.label}</Text>
                    </TouchableOpacity>
                  ))}
                </View>
              </View>
            )}

            <View style={styles.modalFooter}>
              <TouchableOpacity onPress={() => onSave(data)} style={[styles.saveBtnFull, { backgroundColor: colors.primary }]}>
                <SaveIcon size={18} color={colors.onPrimary} />
                <Text style={[styles.saveBtnText, { color: colors.onPrimary }]}>GUARDAR CAMBIOS</Text>
              </TouchableOpacity>
            </View>
         </View>
       </View>
    </Modal>
  );
};

const styles = StyleSheet.create({
  container: { flex: 1 },
  header: { flexDirection: 'row', alignItems: 'center', padding: 16, gap: 16 },
  backBtn: { padding: 4 },
  title: { fontSize: 20, fontWeight: '900' },
  scroll: { flex: 1 },
  statsRow: { flexDirection: 'row', gap: 8, paddingHorizontal: 16, marginBottom: 16 },
  statCard: { flex: 1, padding: 12, borderRadius: 20, alignItems: 'center', justifyContent: 'center' },
  statValue: { fontSize: 18, fontWeight: '900' },
  statLabel: { fontSize: 7, fontWeight: '900', letterSpacing: 1, marginTop: 2, opacity: 0.6 },
  roadTo: { margin: 16, padding: 16, borderRadius: 24, gap: 12 },
  roadToHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  roadToTitleGroup: { flexDirection: 'row', alignItems: 'center', gap: 8 },
  roadToTitle: { fontSize: 10, fontWeight: '900', letterSpacing: 2 },
  addEventBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 10, paddingVertical: 6, borderRadius: 20 },
  addEventText: { fontSize: 9, fontWeight: '900', letterSpacing: 1 },
  eventScroll: { gap: 8, paddingBottom: 4 },
  eventTag: { flexDirection: 'row', alignItems: 'center', gap: 8, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 16 },
  eventTitle: { fontSize: 10, fontWeight: '900' },
  eventSub: { fontSize: 8, fontWeight: '600' },
  structureHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center', paddingHorizontal: 16, marginBottom: 12 },
  sectionTitle: { fontSize: 10, fontWeight: '900', letterSpacing: 1.5 },
  addBtn: { flexDirection: 'row', alignItems: 'center', gap: 6, paddingHorizontal: 12, paddingVertical: 8, borderRadius: 12 },
  addBtnText: { fontSize: 9, fontWeight: '900', letterSpacing: 1 },
  blockCard: { marginHorizontal: 16, marginBottom: 12, borderRadius: 24, borderWidth: 1, overflow: 'hidden' },
  blockHeader: { flexDirection: 'row', alignItems: 'center', padding: 12, gap: 12 },
  expandIcon: { width: 32, height: 32, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  blockInfo: { flex: 1 },
  blockName: { fontSize: 15, fontWeight: '900' },
  blockSub: { fontSize: 10, fontWeight: '600' },
  blockActions: { flexDirection: 'row', gap: 4 },
  iconBtn: { width: 36, height: 36, borderRadius: 10, alignItems: 'center', justifyContent: 'center' },
  iconBtnSmall: { width: 28, height: 28, borderRadius: 8, alignItems: 'center', justifyContent: 'center' },
  blockContent: { padding: 12, paddingTop: 0, gap: 12 },
  mesoCard: { padding: 12, borderRadius: 16, gap: 12 },
  mesoHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  mesoTitleGroup: { flexDirection: 'row', gap: 8, alignItems: 'center' },
  mesoIndicator: { width: 3, height: 16, borderRadius: 2 },
  mesoGoal: { fontSize: 8, fontWeight: '900', letterSpacing: 1 },
  mesoName: { fontSize: 13, fontWeight: '800' },
  weeksGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 6 },
  weekBadge: { width: 50, height: 50, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  weekLabel: { fontSize: 11, fontWeight: '900' },
  weekSub: { fontSize: 8, fontWeight: '600', opacity: 0.6 },
  addWeekBtn: { width: 50, height: 50, borderRadius: 12, borderWidth: 1, alignItems: 'center', justifyContent: 'center' },
  addMesoBtn: { height: 44, borderRadius: 16, borderWidth: 1, borderStyle: 'dotted', alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 8 },
  addMesoText: { fontSize: 9, fontWeight: '900', letterSpacing: 1 },
  modalOverlay: { flex: 1, backgroundColor: 'rgba(0,0,0,0.5)', justifyContent: 'center', padding: 24 },
  modalContent: { padding: 24, borderRadius: 32, gap: 16 },
  modalHeader: { flexDirection: 'row', justifyContent: 'space-between', alignItems: 'center' },
  modalTitle: { fontSize: 18, fontWeight: '900' },
  formGroup: { gap: 8 },
  label: { fontSize: 10, fontWeight: '900', letterSpacing: 1 },
  input: { height: 48, borderRadius: 12, borderWidth: 1, paddingHorizontal: 16, fontSize: 15, fontWeight: '700' },
  modalFooter: { flexDirection: 'row', gap: 12, marginTop: 8 },
  deleteBtn: { width: 48, height: 48, borderRadius: 12, backgroundColor: '#FFEDEC', alignItems: 'center', justifyContent: 'center' },
  saveBtnFull: { flex: 1, height: 48, borderRadius: 12, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 8 },
  saveBtnText: { fontSize: 11, fontWeight: '900', letterSpacing: 1 },
  goalGrid: { flexDirection: 'row', flexWrap: 'wrap', gap: 8 },
  goalBadge: { paddingHorizontal: 12, paddingVertical: 8, borderRadius: 12, borderWidth: 1 },
  goalText: { fontSize: 11, fontWeight: '700' },
});
