import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Modal as RNModal } from 'react-native';
import Animated, { FadeIn, FadeOut, SlideInDown, SlideOutDown, ZoomIn, ZoomOut } from 'react-native-reanimated';
import { XIcon } from '../icons';
import TacticalBackdrop from './TacticalBackdrop';
import { useColors } from '../../theme';

export type TacticalVariant = 'default' | 'failure' | 'pr' | 'sheet';

interface TacticalModalProps {
  isOpen: boolean;
  onClose: () => void;
  children: React.ReactNode;
  title?: string;
  variant?: TacticalVariant;
  useCustomContent?: boolean;
}

const TacticalModal: React.FC<TacticalModalProps> = ({
  isOpen,
  onClose,
  children,
  title,
  variant = 'default',
  useCustomContent = false
}) => {
  const isSheet = variant === 'sheet';
  const colors = useColors();

  const getBorderColor = (v: TacticalVariant) => {
    switch (v) {
      case 'failure':
        return colors.error;
      case 'pr':
        return colors.cyberWarning;
      default:
        return colors.outlineVariant;
    }
  };

  const getElevation = (v: TacticalVariant) => {
    return v === 'default' || v === 'sheet' ? 24 : 12;
  };

  return (
    <RNModal
      visible={isOpen}
      transparent={true}
      statusBarTranslucent={true}
      animationType="none"
      onRequestClose={onClose}
    >
      <View style={[styles.overlay, isSheet && styles.sheetOverlay]}>
        <TacticalBackdrop onPress={onClose} variant={isSheet ? 'overlay' : 'modal'} />
        <Animated.View
          style={[
            styles.modal,
            isSheet && styles.sheetModal,
            { borderColor: getBorderColor(variant), elevation: getElevation(variant) }
          ]}
          entering={isSheet ? SlideInDown.duration(200) : FadeIn.duration(200)}
          exiting={isSheet ? SlideOutDown.duration(200) : FadeOut.duration(200)}
        >
          <Animated.View
            style={[styles.panel, { backgroundColor: colors.surface, borderColor: getBorderColor(variant) }]}
            entering={isSheet ? undefined : ZoomIn.duration(200).springify()}
            exiting={isSheet ? undefined : ZoomOut.duration(200).springify()}
          >
            {title !== undefined && (
              <View style={[styles.header, { borderBottomColor: colors.outlineVariant }]}>
                <Text style={[styles.title, { color: colors.onSurface }]}>{title}</Text>
                <TouchableOpacity onPress={onClose} hitSlop={8} style={styles.closeButton}>
                  <XIcon size={18} color={colors.onSurfaceVariant} />
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
  sheetOverlay: {
    justifyContent: 'flex-end',
  },
  modal: {
    width: '90%',
    maxWidth: 400,
    maxHeight: '85%',
  },
  sheetModal: {
    width: '100%',
    height: '90%',
  },
  panel: {
    borderRadius: 16,
    borderWidth: 1,
    shadowColor: '#000',
    shadowOpacity: 0.15,
    shadowRadius: 10,
    shadowOffset: { width: 0, height: 4 },
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: 20,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  title: {
    fontSize: 16,
    fontWeight: '700',
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
    paddingHorizontal: 20,
    paddingBottom: 24,
    paddingTop: 20,
  },
  contentWithoutTitle: {
    paddingHorizontal: 20,
    paddingBottom: 24,
    paddingTop: 24,
  },
});

export default TacticalModal;