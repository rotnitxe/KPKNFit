import React, { useState } from 'react';
import { View, Text, StyleSheet, Modal, TouchableOpacity, ScrollView } from 'react-native';
import { useColors } from '../../theme';

interface PeriodizationTemplate {
    id: string;
    name: string;
    description: string;
    duration: string;
    progression: string;
}

const TEMPLATES: PeriodizationTemplate[] = [
    {
        id: 'lineal',
        name: 'Lineal',
        description: 'Incremento progresivo de carga con deload periódico.',
        duration: '4-8 semanas',
        progression: 'Carga +2.5% cada semana, deload cada 4 semanas'
    },
    {
        id: 'ondulante',
        name: 'Ondulante Diaria',
        description: 'Variación de intensidad dentro de la misma semana.',
        duration: '4-6 semanas',
        progression: 'Alta/Baja/Media intensidad por día'
    },
    {
        id: 'bloques',
        name: 'Bloques',
        description: 'Acumulación → Intensificación → Realización.',
        duration: '12-16 semanas',
        progression: 'Bloques de 4 semanas con objetivos específicos'
    },
    {
        id: '531',
        name: '5/3/1 (Wendler)',
        description: 'Sistema de 5/3/1 con porcentajes fijos.',
        duration: 'Cíclico (4 semanas)',
        progression: 'Ciclos de 5/3/1 con aumento de 2.5-5kg por ciclo'
    }
];

interface PeriodizationTemplateModalProps {
    visible: boolean;
    onClose: () => void;
    onSelect: (template: PeriodizationTemplate) => void;
}

const PeriodizationTemplateModal: React.FC<PeriodizationTemplateModalProps> = ({
    visible,
    onClose,
    onSelect,
}) => {
    const colors = useColors();
    const [selectedId, setSelectedId] = useState<string | null>(null);

    const handleSelect = (template: PeriodizationTemplate) => {
        setSelectedId(template.id);
        onSelect(template);
        onClose();
    };

    return (
        <Modal visible={visible} transparent animationType="slide" onRequestClose={onClose}>
            <View style={styles.overlay}>
                <View style={[styles.container, { backgroundColor: colors.surface }]}>
                    <View style={styles.header}>
                        <Text style={[styles.title, { color: colors.onSurface }]}>
                            Plantilla de Periodización
                        </Text>
                        <TouchableOpacity onPress={onClose}>
                            <Text style={[styles.closeText, { color: colors.primary }]}>Cerrar</Text>
                        </TouchableOpacity>
                    </View>

                    <ScrollView contentContainerStyle={styles.list}>
                        {TEMPLATES.map(template => (
                            <TouchableOpacity
                                key={template.id}
                                onPress={() => handleSelect(template)}
                                style={[
                                    styles.templateCard,
                                    {
                                        backgroundColor: colors.surfaceContainer,
                                        borderColor: selectedId === template.id ? colors.primary : colors.outlineVariant
                                    }
                                ]}
                            >
                                <View style={styles.templateHeader}>
                                    <Text style={[styles.templateName, { color: colors.onSurface }]}>
                                        {template.name}
                                    </Text>
                                    <Text style={[styles.templateDuration, { color: colors.onSurfaceVariant }]}>
                                        {template.duration}
                                    </Text>
                                </View>
                                <Text style={[styles.templateDesc, { color: colors.onSurfaceVariant }]}>
                                    {template.description}
                                </Text>
                                <Text style={[styles.templateProgress, { color: colors.primary }]}>
                                    {template.progression}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </ScrollView>
                </View>
            </View>
        </Modal>
    );
};

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.5)',
        justifyContent: 'flex-end',
    },
    container: {
        borderTopLeftRadius: 24,
        borderTopRightRadius: 24,
        padding: 20,
        maxHeight: '80%',
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 20,
    },
    title: {
        fontSize: 20,
        fontWeight: '900',
    },
    closeText: {
        fontSize: 14,
        fontWeight: '700',
    },
    list: {
        paddingBottom: 20,
    },
    templateCard: {
        borderRadius: 12,
        padding: 16,
        marginBottom: 12,
        borderWidth: 1,
    },
    templateHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 8,
    },
    templateName: {
        fontSize: 16,
        fontWeight: '800',
    },
    templateDuration: {
        fontSize: 12,
        fontWeight: '600',
    },
    templateDesc: {
        fontSize: 13,
        marginBottom: 8,
    },
    templateProgress: {
        fontSize: 12,
        fontWeight: '700',
    },
});

export default React.memo(PeriodizationTemplateModal);
