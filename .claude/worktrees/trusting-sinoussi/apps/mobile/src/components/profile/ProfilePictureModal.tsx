import React from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity, Alert } from 'react-native';
import { useColors } from '../../theme';
import { Button } from '../ui/Button';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { CameraIcon, ImageIcon, XIcon } from '../icons';

interface ProfilePictureModalProps {
  visible: boolean;
  onClose: () => void;
  onSelectCamera: () => void;
  onSelectGallery: () => void;
  currentPicture?: string | null;
}

export function ProfilePictureModal({
  visible,
  onClose,
  onSelectCamera,
  onSelectGallery,
  currentPicture,
}: ProfilePictureModalProps) {
  const colors = useColors();

  const handleCamera = () => {
    onSelectCamera();
    onClose();
  };

  const handleGallery = () => {
    onSelectGallery();
    onClose();
  };

  return (
    <Modal visible={visible} transparent animationType="fade" onRequestClose={onClose}>
      <View style={styles.overlay}>
        <TouchableOpacity style={StyleSheet.absoluteFill} onPress={onClose} />
        <LiquidGlassCard style={styles.sheet} padding={24}>
          <View style={styles.header}>
            <Text style={[styles.title, { color: colors.onSurface }]}>Foto de Perfil</Text>
            <TouchableOpacity onPress={onClose} style={styles.closeBtn}>
              <XIcon size={24} color={colors.onSurfaceVariant} />
            </TouchableOpacity>
          </View>

          <View style={styles.preview}>
            <View style={[styles.avatarPreview, { backgroundColor: `${colors.primary}20` }]}>
              {currentPicture ? (
                <Text style={[styles.avatarText, { color: colors.primary }]}>Foto</Text>
              ) : (
                <Text style={[styles.avatarText, { color: colors.primary }]}>?</Text>
              )}
            </View>
          </View>

          <View style={styles.actions}>
            <TouchableOpacity
              style={[styles.actionBtn, { backgroundColor: colors.primary }]}
              onPress={handleCamera}
            >
              <CameraIcon size={24} color="#fff" />
              <Text style={styles.actionText}>Tomar Foto</Text>
            </TouchableOpacity>

            <TouchableOpacity
              style={[styles.actionBtn, { backgroundColor: colors.secondary }]}
              onPress={handleGallery}
            >
              <ImageIcon size={24} color="#fff" />
              <Text style={styles.actionText}>Elegir de Galería</Text>
            </TouchableOpacity>
          </View>

          <Button variant="secondary" onPress={onClose} style={styles.cancelBtn}>
            Cancelar
          </Button>
        </LiquidGlassCard>
      </View>
    </Modal>
  );
}

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    backgroundColor: 'rgba(0,0,0,0.5)',
    justifyContent: 'center',
    alignItems: 'center',
    padding: 24,
  },
  sheet: {
    width: '100%',
    borderRadius: 24,
  },
  header: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: '900',
  },
  closeBtn: {
    padding: 4,
  },
  preview: {
    alignItems: 'center',
    marginBottom: 24,
  },
  avatarPreview: {
    width: 120,
    height: 120,
    borderRadius: 60,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarText: {
    fontSize: 32,
    fontWeight: '900',
  },
  actions: {
    gap: 12,
    marginBottom: 24,
  },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: 12,
    paddingVertical: 16,
    borderRadius: 16,
  },
  actionText: {
    fontSize: 16,
    fontWeight: '800',
    color: '#fff',
  },
  cancelBtn: {
    marginTop: 8,
  },
});
