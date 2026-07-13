import React, { useState, useMemo } from 'react';
import { View, Text, StyleSheet, TouchableOpacity, ScrollView, TextInput } from 'react-native';
import { useColors } from '../../theme';
import { SPLIT_TEMPLATES } from '../../data/splitTemplates';
import { SplitTemplate, SplitTag } from '../../types/workout';
import { SearchIcon } from '../icons';

const FILTER_TAGS: (SplitTag | 'Todos')[] = [
    'Todos', 'Recomendado por KPKN', 'Alta Frecuencia', 'Baja Frecuencia',
    'Balanceado', 'Alto Volumen', 'Powerlifting', 'Personalizado',
];

interface SplitGalleryProps {
    onSelect: (split: SplitTemplate) => void;
    currentSplitId?: string;
    excludeCustom?: boolean;
}

const SplitGallery: React.FC<SplitGalleryProps> = ({
    onSelect, currentSplitId, excludeCustom = true,
}) => {
    const colors = useColors();
    const [filter, setFilter] = useState<SplitTag | 'Todos'>('Todos');
    const [search, setSearch] = useState('');

    const filteredSplits = useMemo(() => {
        return SPLIT_TEMPLATES.filter(s => {
            if (excludeCustom && s.id === 'custom') return false;
            const matchesTag = filter === 'Todos' || s.tags.includes(filter);
            const q = search.toLowerCase();
            const matchesSearch = !q || s.name.toLowerCase().includes(q) || s.description.toLowerCase().includes(q);
            return matchesTag && matchesSearch;
        });
    }, [filter, search, excludeCustom]);

    return (
        <View style={styles.container}>
            <View style={[styles.searchContainer, { borderBottomColor: colors.outlineVariant }]}>
                <View style={[styles.searchBar, { backgroundColor: colors.surfaceContainerHigh }]}>
                    <SearchIcon size={18} color={colors.onSurfaceVariant} />
                    <TextInput
                        value={search}
                        onChangeText={setSearch}
                        placeholder="Buscar split..."
                        placeholderTextColor={colors.onSurfaceVariant + '80'}
                        style={[styles.searchInput, { color: colors.onSurface }]}
                    />
                </View>
            </View>

            <View style={[styles.filterContainer, { borderBottomColor: colors.outlineVariant }]}>
                <ScrollView horizontal showsHorizontalScrollIndicator={false} contentContainerStyle={styles.filterScroll}>
                    {FILTER_TAGS.map(tag => (
                        <TouchableOpacity
                            key={tag}
                            onPress={() => setFilter(tag)}
                            style={[
                                styles.filterBadge,
                                { borderColor: colors.outlineVariant },
                                filter === tag && { backgroundColor: colors.primary, borderColor: colors.primary }
                            ]}
                        >
                            <Text style={[
                                styles.filterText,
                                { color: colors.onSurfaceVariant },
                                filter === tag && { color: colors.onPrimary }
                            ]}>
                                {tag}
                            </Text>
                        </TouchableOpacity>
                    ))}
                </ScrollView>
            </View>

            <ScrollView contentContainerStyle={styles.listContent} showsVerticalScrollIndicator={false}>
                {filteredSplits.map(split => {
                    const isCurrent = split.id === currentSplitId;
                    const trainingDays = split.pattern.filter(d => d.toLowerCase() !== 'descanso').length;
                    return (
                        <TouchableOpacity
                            key={split.id}
                            onPress={() => onSelect(split)}
                            activeOpacity={0.7}
                            style={[
                                styles.splitCard,
                                { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant },
                                isCurrent && { borderColor: colors.primary, borderWidth: 1.5 }
                            ]}
                        >
                            <View style={styles.cardHeader}>
                                <View style={styles.cardTitleRow}>
                                    <Text style={[styles.splitName, { color: colors.onSurface }]}>{split.name}</Text>
                                    {isCurrent && (
                                        <View style={[styles.currentBadge, { backgroundColor: colors.primaryContainer }]}>
                                            <Text style={[styles.currentBadgeText, { color: colors.onPrimaryContainer }]}>ACTUAL</Text>
                                        </View>
                                    )}
                                </View>
                                <Text style={[styles.trainingDays, { color: colors.onSurfaceVariant }]}>{trainingDays}d</Text>
                            </View>
                            
                            <Text style={[styles.splitDesc, { color: colors.onSurfaceVariant }]} numberOfLines={2}>
                                {split.description}
                            </Text>

                            <View style={styles.patternBar}>
                                {split.pattern.map((day, i) => (
                                    <View
                                        key={i}
                                        style={[
                                            styles.patternDot,
                                            { backgroundColor: day.toLowerCase() === 'descanso' ? colors.surfaceVariant : colors.primary + '60' }
                                        ]}
                                    />
                                ))}
                            </View>
                        </TouchableOpacity>
                    );
                })}
                {filteredSplits.length === 0 && (
                    <View style={styles.emptyState}>
                        <Text style={[styles.emptyText, { color: colors.onSurfaceVariant }]}>Sin resultados</Text>
                    </View>
                )}
            </ScrollView>
        </View>
    );
};

const styles = StyleSheet.create({
    container: { flex: 1 },
    searchContainer: {
        paddingHorizontal: 20,
        paddingVertical: 12,
        borderBottomWidth: 1,
    },
    searchBar: {
        flexDirection: 'row',
        alignItems: 'center',
        paddingHorizontal: 16,
        height: 44,
        borderRadius: 12,
        gap: 10,
    },
    searchInput: {
        flex: 1,
        fontSize: 14,
        padding: 0,
    },
    filterContainer: {
        borderBottomWidth: 1,
    },
    filterScroll: {
        paddingHorizontal: 20,
        paddingVertical: 12,
        gap: 8,
    },
    filterBadge: {
        paddingHorizontal: 14,
        paddingVertical: 6,
        borderRadius: 20,
        borderWidth: 1,
    },
    filterText: {
        fontSize: 11,
        fontWeight: '900',
        textTransform: 'uppercase',
        letterSpacing: 0.5,
    },
    listContent: {
        padding: 20,
        gap: 12,
    },
    splitCard: {
        padding: 16,
        borderRadius: 20,
        borderWidth: 1,
    },
    cardHeader: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'flex-start',
        marginBottom: 6,
    },
    cardTitleRow: {
        flex: 1,
        flexDirection: 'row',
        alignItems: 'center',
        gap: 8,
    },
    splitName: {
        fontSize: 15,
        fontWeight: '900',
    },
    currentBadge: {
        paddingHorizontal: 6,
        paddingVertical: 2,
        borderRadius: 6,
    },
    currentBadgeText: {
        fontSize: 8,
        fontWeight: '900',
    },
    trainingDays: {
        fontSize: 11,
        fontWeight: '700',
        opacity: 0.7,
    },
    splitDesc: {
        fontSize: 12,
        lineHeight: 18,
        opacity: 0.6,
        marginBottom: 12,
    },
    patternBar: {
        flexDirection: 'row',
        gap: 4,
    },
    patternDot: {
        flex: 1,
        height: 4,
        borderRadius: 2,
    },
    emptyState: {
        paddingVertical: 60,
        alignItems: 'center',
    },
    emptyText: {
        fontSize: 13,
        fontWeight: '700',
        opacity: 0.5,
    },
});

export default SplitGallery;
