import React, { useCallback, useMemo } from 'react';
import { FlatList, Text, TouchableOpacity, View, StyleSheet } from 'react-native';
import type { CoachConversation } from '../../types/coach';
import { useColors } from '../../theme';

interface CoachConversationListProps {
  conversations: CoachConversation[];
  activeConversationId: string | null;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
}

const ConversationItem = React.memo(({ 
  item, 
  isActive, 
  onSelect, 
  onDelete, 
  colors 
}: { 
  item: CoachConversation; 
  isActive: boolean; 
  onSelect: (id: string) => void; 
  onDelete: (id: string) => void;
  colors: any;
}) => {
  const date = useMemo(() => new Date(item.updatedAt).toLocaleDateString('es-CL', {
    day: '2-digit',
    month: 'short',
  }), [item.updatedAt]);

  return (
    <View
      style={[
        styles.itemContainer,
        isActive ? { borderColor: colors.primary, backgroundColor: `${colors.primary}1A` } 
                 : { borderColor: `${colors.onSurface}1A`, backgroundColor: colors.surface }
      ]}
    >
      <TouchableOpacity
        onPress={() => onSelect(item.id)}
        style={styles.content}
        accessibilityRole="button"
        accessibilityLabel={`Abrir ${item.title}`}
      >
        <View>
          <Text
            numberOfLines={1}
            style={[styles.title, isActive ? { color: colors.primary } : { color: colors.onSurface }]}
          >
            {item.title}
          </Text>
          <Text style={[styles.date, { color: colors.onSurfaceVariant }]}>{date}</Text>
        </View>
      </TouchableOpacity>
      <TouchableOpacity
        onPress={() => onDelete(item.id)}
        style={[styles.deleteButton, { backgroundColor: `${colors.onSurface}0D` }]}
        accessibilityRole="button"
        accessibilityLabel={`Eliminar ${item.title}`}
      >
        <Text style={[styles.deleteText, { color: colors.onSurfaceVariant }]}>✕</Text>
      </TouchableOpacity>
    </View>
  );
});

export function CoachConversationList({
  conversations,
  activeConversationId,
  onSelect,
  onDelete,
}: CoachConversationListProps) {
  const colors = useColors();

  const renderItem = useCallback(({ item }: { item: CoachConversation }) => (
    <ConversationItem
      item={item}
      isActive={item.id === activeConversationId}
      onSelect={onSelect}
      onDelete={onDelete}
      colors={colors}
    />
  ), [activeConversationId, onSelect, onDelete, colors]);

  return (
    <View style={styles.container}>
      <FlatList
        data={conversations}
        renderItem={renderItem}
        keyExtractor={(item) => item.id}
        horizontal
        showsHorizontalScrollIndicator={false}
        contentContainerStyle={styles.listContent}
        ListEmptyComponent={
          <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>
            Aún no tienes conversaciones con el Coach. Pulsa "+ Nueva" para iniciar una conversación y obtener asesoramiento personalizado.
          </Text>
        }
      />
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    marginBottom: 16,
  },
  listContent: {
    paddingHorizontal: 4,
  },
  itemContainer: {
    marginRight: 12,
    flexDirection: 'row',
    borderRadius: 16,
    borderWidth: 1,
    paddingHorizontal: 16,
    paddingVertical: 12,
    alignItems: 'center',
  },
  content: {
    flexShrink: 1,
    minWidth: 100,
  },
  title: {
    fontSize: 14,
    fontWeight: 'bold',
  },
  date: {
    fontSize: 10,
    marginTop: 2,
  },
  deleteButton: {
    marginLeft: 12,
    height: 24,
    width: 24,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 12,
  },
  deleteText: {
    fontSize: 10,
  },
  emptyText: {
    fontSize: 12,
    fontWeight: '600',
    paddingVertical: 8,
  },
});
