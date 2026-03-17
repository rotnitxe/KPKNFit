import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Modal as RNModal } from 'react-native';
import Animated, { FadeIn, FadeOut, SlideInDown, SlideOutDown } from 'react-native-reanimated';
import { XIcon } from '../icons';

interface ModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  useCustomContent?: boolean;
}

const Modal: React.FC<ModalProps> = ({ isOpen, onClose, children, title, useCustomContent = false }) => {
  return (
    <RNModal
      visible={isOpen}
      transparent={true}
      animationType="fade"
      statusBarTranslucent={true}
      onRequestClose={onClose}
    >
      <View style={styles.overlay}>
        <TouchableOpacity style={styles.backdrop} onPress={onClose} />
        <Animated.View
          style={styles.modal}
          entering={FadeIn.duration(300)}
          exiting={FadeOut.duration(300)}
        >
          <Animated.View
            style={styles.panel}
            entering={SlideInDown.duration(300).springify()}
            exiting={SlideOutDown.duration(300).springify()}
          >
            {title !== undefined && (
              <View style={styles.header}>
                <Text style={styles.title}>{title}</Text>
                <TouchableOpacity onPress={onClose} hitSlop={8} style={styles.closeButton}>
                  <XIcon size={18} color="#79747E" />
                </TouchableOpacity>
              </View>
            )}
            {useCustomContent ? (
              <View style={styles.customContent}>
                {children}
              </View>
            ) : (
              <ScrollView
                style={styles.content}
                contentContainerStyle={title !== undefined ? styles.contentWithTitle : styles.contentWithoutTitle}
              >
                {children}
              </ScrollView>
            )}
          </Animated.View>
        </Animated.View>
      </View>
    </RNModal>
  );
};

const styles = StyleSheet.create({
  overlay: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  backdrop: {
    ...StyleSheet.absoluteFillObject,
    backgroundColor: 'rgba(0, 0, 0, 0.8)',
  },
  modal: {
    width: '90%',
    maxWidth: 400,
    maxHeight: '85%',
  },
  panel: {
    backgroundColor: '#FEF7FF',
    borderRadius: 24,
    elevation: 24,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 4 },
    shadowOpacity: 0.3,
    shadowRadius: 8,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 24,
    paddingVertical: 20,
    borderBottomWidth: 1,
    borderBottomColor: '#E6E0E9',
    backgroundColor: 'rgba(254, 247, 255, 0.5)',
  },
  title: {
    fontSize: 18,
    fontWeight: '700',
    color: '#1D1B20',
  },
  closeButton: {
    padding: 4,
  },
  customContent: {
    flex: 1,
    minHeight: 0,
  },
  content: {
    flex: 1,
  },
  contentWithTitle: {
    paddingHorizontal: 24,
    paddingBottom: 32,
    paddingTop: 20,
  },
  contentWithoutTitle: {
    paddingHorizontal: 24,
    paddingBottom: 32,
    paddingTop: 32,
  },
});

export default Modal;