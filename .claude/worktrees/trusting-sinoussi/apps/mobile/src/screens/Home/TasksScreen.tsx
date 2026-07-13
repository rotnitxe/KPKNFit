import React, { useEffect, useMemo, useState } from 'react';
import {
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { ScreenShell } from '@/components/ScreenShell';
import { LiquidGlassCard } from '@/components/ui/LiquidGlassCard';
import { Button } from '@/components/ui/Button';
import { CheckCircleIcon, PlusIcon, TrashIcon } from '@/components/icons';
import { useWellbeingStore } from '@/stores/wellbeingStore';
import { useColors } from '@/theme';

function TaskRow({
  id,
  title,
  description,
  completed,
  onToggle,
  onDelete,
}: {
  id: string;
  title: string;
  description?: string;
  completed: boolean;
  onToggle: (taskId: string) => void;
  onDelete: (taskId: string) => void;
}) {
  const colors = useColors();

  return (
    <View
      style={[
        styles.taskRow,
        {
          backgroundColor: colors.surface,
          borderColor: `${colors.outlineVariant}66`,
          opacity: completed ? 0.62 : 1,
        },
      ]}
    >
      <Pressable style={styles.taskMain} onPress={() => onToggle(id)}>
        <View
          style={[
            styles.taskCheck,
            {
              backgroundColor: completed ? colors.primary : 'transparent',
              borderColor: completed ? colors.primary : colors.outlineVariant,
            },
          ]}
        >
          {completed ? <CheckCircleIcon size={16} color={colors.onPrimary} /> : null}
        </View>

        <View style={styles.taskTextWrap}>
          <Text
            style={[
              styles.taskTitle,
              { color: colors.onSurface },
              completed && styles.completedText,
            ]}
          >
            {title}
          </Text>
          {description ? (
            <Text
              style={[
                styles.taskDescription,
                { color: colors.onSurfaceVariant },
                completed && styles.completedText,
              ]}
            >
              {description}
            </Text>
          ) : null}
        </View>
      </Pressable>

      <Pressable style={styles.deleteButton} onPress={() => onDelete(id)}>
        <TrashIcon size={16} color={colors.error} />
      </Pressable>
    </View>
  );
}

export function TasksScreen() {
  const colors = useColors();
  const status = useWellbeingStore(state => state.status);
  const hydrateWellbeing = useWellbeingStore(state => state.hydrateFromMigration);
  const tasks = useWellbeingStore(state => state.tasks);
  const notice = useWellbeingStore(state => state.notice);
  const addTask = useWellbeingStore(state => state.addTask);
  const toggleTask = useWellbeingStore(state => state.toggleTask);
  const deleteTask = useWellbeingStore(state => state.deleteTask);
  const clearNotice = useWellbeingStore(state => state.clearNotice);

  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');

  useEffect(() => {
    if (status === 'idle') void hydrateWellbeing();
  }, [hydrateWellbeing, status]);

  useEffect(() => {
    if (!notice) return undefined;
    const timeout = setTimeout(() => clearNotice(), 2800);
    return () => clearTimeout(timeout);
  }, [clearNotice, notice]);

  const pendingTasks = useMemo(() => tasks.filter(task => !task.completed), [tasks]);
  const completedTasks = useMemo(() => tasks.filter(task => task.completed), [tasks]);

  const handleCreate = async () => {
    if (!title.trim()) return;
    await addTask(title, description);
    setTitle('');
    setDescription('');
  };

  return (
    <ScreenShell
      title="Mis tareas"
      subtitle="Checklist operativo del wellbeing, con alta compatibilidad respecto a la PWA."
    >
      <View style={styles.container}>
        {notice ? (
          <View style={[styles.noticeBanner, { backgroundColor: `${colors.primary}18` }]}>
            <Text style={[styles.noticeText, { color: colors.primary }]}>{notice}</Text>
          </View>
        ) : null}

        <LiquidGlassCard style={styles.formCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Nueva tarea</Text>
          <TextInput
            value={title}
            onChangeText={setTitle}
            placeholder="Titulo de la tarea"
            placeholderTextColor={colors.onSurfaceVariant}
            style={[
              styles.input,
              {
                backgroundColor: colors.surfaceContainer,
                color: colors.onSurface,
                borderColor: colors.outlineVariant,
              },
            ]}
          />
          <TextInput
            value={description}
            onChangeText={setDescription}
            placeholder="Descripcion opcional"
            placeholderTextColor={colors.onSurfaceVariant}
            multiline
            style={[
              styles.textarea,
              {
                backgroundColor: colors.surfaceContainer,
                color: colors.onSurface,
                borderColor: colors.outlineVariant,
              },
            ]}
          />
          <Button onPress={() => void handleCreate()} style={styles.createButton}>
            <View style={styles.buttonContent}>
              <PlusIcon size={16} color={colors.primary} />
              <Text style={[styles.buttonText, { color: colors.primary }]}>Crear tarea manual</Text>
            </View>
          </Button>
        </LiquidGlassCard>

        <LiquidGlassCard style={styles.listCard} padding={20}>
          <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Pendientes ({pendingTasks.length})</Text>
          {pendingTasks.length === 0 ? (
            <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
              No tienes tareas pendientes. Cuando aparezcan objetivos operativos o recordatorios, viviran aqui.
            </Text>
          ) : (
            <View style={styles.taskList}>
              {pendingTasks.map(task => (
                <TaskRow
                  key={task.id}
                  id={task.id}
                  title={task.title}
                  description={task.description}
                  completed={task.completed}
                  onToggle={taskId => void toggleTask(taskId)}
                  onDelete={taskId => void deleteTask(taskId)}
                />
              ))}
            </View>
          )}
        </LiquidGlassCard>

        {completedTasks.length > 0 ? (
          <LiquidGlassCard style={styles.listCard} padding={20}>
            <Text style={[styles.eyebrow, { color: colors.onSurfaceVariant }]}>Completadas ({completedTasks.length})</Text>
            <View style={styles.taskList}>
              {completedTasks.map(task => (
                <TaskRow
                  key={task.id}
                  id={task.id}
                  title={task.title}
                  description={task.description}
                  completed={task.completed}
                  onToggle={taskId => void toggleTask(taskId)}
                  onDelete={taskId => void deleteTask(taskId)}
                />
              ))}
            </View>
          </LiquidGlassCard>
        ) : null}
      </View>
    </ScreenShell>
  );
}

const styles = StyleSheet.create({
  container: {
    gap: 16,
  },
  noticeBanner: {
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  noticeText: {
    fontSize: 13,
    fontWeight: '700',
  },
  formCard: {
    borderRadius: 28,
  },
  listCard: {
    borderRadius: 28,
  },
  eyebrow: {
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
    marginBottom: 12,
  },
  input: {
    minHeight: 48,
    borderWidth: 1,
    borderRadius: 18,
    paddingHorizontal: 14,
    marginBottom: 10,
  },
  textarea: {
    minHeight: 92,
    borderWidth: 1,
    borderRadius: 18,
    paddingHorizontal: 14,
    paddingTop: 12,
    textAlignVertical: 'top',
    marginBottom: 12,
  },
  createButton: {
    minWidth: 0,
  },
  buttonContent: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
  },
  buttonText: {
    fontSize: 14,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 0.8,
  },
  emptyText: {
    fontSize: 14,
    lineHeight: 20,
  },
  taskList: {
    gap: 10,
  },
  taskRow: {
    borderRadius: 20,
    borderWidth: 1,
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  taskMain: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'flex-start',
    gap: 12,
  },
  taskCheck: {
    width: 24,
    height: 24,
    borderRadius: 12,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
    marginTop: 2,
  },
  taskTextWrap: {
    flex: 1,
  },
  taskTitle: {
    fontSize: 15,
    fontWeight: '700',
  },
  taskDescription: {
    fontSize: 13,
    lineHeight: 18,
    marginTop: 4,
  },
  completedText: {
    textDecorationLine: 'line-through',
  },
  deleteButton: {
    padding: 8,
    marginLeft: 10,
  },
});
