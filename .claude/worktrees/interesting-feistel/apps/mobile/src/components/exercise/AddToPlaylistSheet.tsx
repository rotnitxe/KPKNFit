import React, { useState } from 'react';
import { View, Text, Modal, TouchableOpacity, StyleSheet, TextInput, FlatList, Dimensions } from 'react-native';
import { useExerciseStore } from '../../stores/exerciseStore';
import { useColors } from '../../theme';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

interface AddToPlaylistSheetProps {
  visible: boolean;
  onClose: () => void;
  exerciseId: string;
  exerciseName: string;
}

export function AddToPlaylistSheet({ visible, onClose, exerciseId, exerciseName }: AddToPlaylistSheetProps) {
  const colors = useColors();
  const playlists = useExerciseStore(state => state.exercisePlaylists);
  const addPlaylist = useExerciseStore(state => state.addPlaylist);
  const addExerciseToPlaylist = useExerciseStore(state => state.addExerciseToPlaylist);
  const removeExerciseFromPlaylist = useExerciseStore(state => state.removeExerciseFromPlaylist);
  const [showCreate, setShowCreate] = useState(false);
  const [newPlaylistName, setNewPlaylistName] = useState('');

  const handleTogglePlaylist = (playlistId: string, isAdded: boolean) => {
    if (isAdded) {
      removeExerciseFromPlaylist(playlistId, exerciseId);
    } else {
      addExerciseToPlaylist(playlistId, exerciseId);
    }
  };

  const handleCreatePlaylist = () => {
    if (newPlaylistName.trim()) {
      addPlaylist(newPlaylistName.trim());
      setNewPlaylistName('');
      setShowCreate(false);
    }
  };

  const isInPlaylist = (playlistId: string) => {
    const playlist = playlists.find(p => p.id === playlistId);
    return playlist?.exerciseIds.includes(exerciseId) ?? false;
  };

  return (
    <Modal visible={visible} animationType="slide" transparent>
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} onPress={onClose} />
        <View style={[styles.sheet, { backgroundColor: colors.background }]}>
          <View style={styles.handle} />
          
          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Añadir a Playlist</Text>
            <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]} numberOfLines={1}>{exerciseName}</Text>
          </View>

          {showCreate ? (
            <View style={styles.createSection}>
              <TextInput
                value={newPlaylistName}
                onChangeText={setNewPlaylistName}
                placeholder="Nombre de playlist..."
                placeholderTextColor={colors.onSurfaceVariant}
                style={[styles.input, { backgroundColor: colors.surfaceContainer, color: colors.onSurface, borderColor: colors.outlineVariant }]}
              />
              <View style={styles.createActions}>
                <TouchableOpacity style={[styles.cancelBtn, { borderColor: colors.outlineVariant }]} onPress={() => setShowCreate(false)}>
                  <Text style={[styles.cancelText, { color: colors.onSurface }]}>Cancelar</Text>
                </TouchableOpacity>
                <TouchableOpacity style={[styles.createBtn, { backgroundColor: colors.primary }]} onPress={handleCreatePlaylist}>
                  <Text style={[styles.createText, { color: colors.onPrimary }]}>Crear</Text>
                </TouchableOpacity>
              </View>
            </View>
          ) : (
            <>
              <TouchableOpacity style={[styles.newPlaylistBtn, { borderColor: colors.primary }]} onPress={() => setShowCreate(true)}>
                <Text style={[styles.newPlaylistText, { color: colors.primary }]}>+ Nueva Playlist</Text>
              </TouchableOpacity>

              <FlatList
                data={playlists}
                keyExtractor={(item) => item.id}
                style={styles.list}
                renderItem={({ item }) => {
                  const added = isInPlaylist(item.id);
                  return (
                    <TouchableOpacity
                      style={[styles.playlistItem, { borderColor: colors.outlineVariant }]}
                      onPress={() => handleTogglePlaylist(item.id, added)}
                    >
                      <View style={styles.playlistInfo}>
                        <Text style={[styles.playlistName, { color: colors.onSurface }]}>{item.name}</Text>
                        <Text style={[styles.playlistCount, { color: colors.onSurfaceVariant }]}>
                          {item.exerciseIds.length} ejercicios
                        </Text>
                      </View>
                      <View style={[styles.checkbox, added && { backgroundColor: colors.primary, borderColor: colors.primary }]}>
                        {added && <Text style={styles.checkmark}>✓</Text>}
                      </View>
                    </TouchableOpacity>
                  );
                }}
                ListEmptyComponent={
                  <View style={styles.emptyState}>
                    <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>No hay playlists aún</Text>
                  </View>
                }
              />
            </>
          )}
        </View>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: { flex: 1, justifyContent: 'flex-end' },
  backdrop: { ...StyleSheet.absoluteFillObject, backgroundColor: 'rgba(0,0,0,0.3)' },
  sheet: { borderTopLeftRadius: 24, borderTopRightRadius: 24, paddingBottom: 40, maxHeight: SCREEN_HEIGHT * 0.6 },
  handle: { width: 40, height: 4, backgroundColor: '#ccc', borderRadius: 2, alignSelf: 'center', marginVertical: 12 },
  header: { paddingHorizontal: 20, paddingBottom: 16 },
  title: { fontSize: 18, fontWeight: '700' },
  subtitle: { fontSize: 14, marginTop: 4 },
  newPlaylistBtn: { marginHorizontal: 20, marginBottom: 16, padding: 14, borderRadius: 12, borderWidth: 1, alignItems: 'center' },
  newPlaylistText: { fontSize: 14, fontWeight: '600' },
  list: { flex: 1 },
  playlistItem: { flexDirection: 'row', alignItems: 'center', justifyContent: 'space-between', marginHorizontal: 20, padding: 14, borderRadius: 12, borderWidth: 1, marginBottom: 8 },
  playlistInfo: { flex: 1 },
  playlistName: { fontSize: 14, fontWeight: '600' },
  playlistCount: { fontSize: 12, marginTop: 2 },
  checkbox: { width: 24, height: 24, borderRadius: 6, borderWidth: 2, borderColor: '#ccc', alignItems: 'center', justifyContent: 'center' },
  checkmark: { color: '#fff', fontSize: 14, fontWeight: '700' },
  createSection: { paddingHorizontal: 20 },
  input: { borderRadius: 12, borderWidth: 1, paddingHorizontal: 16, paddingVertical: 12, fontSize: 14 },
  createActions: { flexDirection: 'row', gap: 12, marginTop: 16 },
  cancelBtn: { flex: 1, padding: 14, borderRadius: 12, borderWidth: 1, alignItems: 'center' },
  cancelText: { fontSize: 14, fontWeight: '600' },
  createBtn: { flex: 1, padding: 14, borderRadius: 12, alignItems: 'center' },
  createText: { fontSize: 14, fontWeight: '600' },
  emptyState: { padding: 40, alignItems: 'center' },
  emptyText: { fontSize: 14 },
});
