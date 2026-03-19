import React, { useEffect, useState } from 'react';
import { View, Text, StyleSheet, Modal, Animated, Easing, TouchableOpacity } from 'react-native';
import { useColors } from '../../theme';

interface GoalReachedModalProps {
    visible: boolean;
    onClose: () => void;
    calories: number;
    target: number;
    protein: number;
    carbs: number;
    fats: number;
}

const GoalReachedModal: React.FC<GoalReachedModalProps> = ({
    visible,
    onClose,
    calories,
    target,
    protein,
    carbs,
    fats,
}) => {
    const colors = useColors();
    const [particles, setParticles] = useState<{ id: number; x: number; y: number; color: string }[]>([]);
    const [animation] = useState(new Animated.Value(0));

    useEffect(() => {
        if (visible) {
            // Reset animation
            animation.setValue(0);
            
            // Create particles for confetti effect
            const newParticles = [];
            for (let i = 0; i < 30; i++) {
                newParticles.push({
                    id: i,
                    x: Math.random() * 100,
                    y: -10,
                    color: ['#FF6B6B', '#4ECDC4', '#FFE66D', '#95E1D3', '#F38181'][Math.floor(Math.random() * 5)]
                });
            }
            setParticles(newParticles);

            // Start animation
            Animated.loop(
                Animated.sequence([
                    Animated.timing(animation, {
                        toValue: 1,
                        duration: 2000,
                        easing: Easing.linear,
                        useNativeDriver: true,
                    }),
                ])
            ).start();
        } else {
            animation.setValue(0);
        }
    }, [visible]);

    if (!visible) return null;

    return (
        <Modal transparent animationType="fade" visible={visible} onRequestClose={onClose}>
            <View style={styles.overlay}>
                <View style={[styles.container, { backgroundColor: colors.surface }]}>
                    {/* Confetti Animation */}
                    <View style={styles.confettiContainer}>
                        {particles.map(p => (
                            <Animated.View
                                key={p.id}
                                style={[
                                    styles.particle,
                                    {
                                        backgroundColor: p.color,
                                        left: `${p.x}%`,
                                        transform: [
                                            {
                                                translateY: animation.interpolate({
                                                    inputRange: [0, 1],
                                                    outputRange: [0, 400],
                                                }),
                                            },
                                            {
                                                rotate: animation.interpolate({
                                                    inputRange: [0, 1],
                                                    outputRange: ['0deg', '360deg'],
                                                }),
                                            },
                                        ],
                                    },
                                ]}
                            />
                        ))}
                    </View>

                    <Text style={[styles.title, { color: colors.onSurface }]}>
                        ¡Meta Alcanzada! 🎉
                    </Text>

                    <Text style={[styles.subtitle, { color: colors.onSurfaceVariant }]}>
                        Has consumido {Math.round(calories)} kcal de {target} kcal
                    </Text>

                    <View style={styles.summaryRow}>
                        <View style={styles.macroItem}>
                            <Text style={[styles.macroValue, { color: colors.primary }]}>
                                {Math.round(protein)}g
                            </Text>
                            <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Proteína</Text>
                        </View>
                        <View style={styles.macroItem}>
                            <Text style={[styles.macroValue, { color: '#F59E0B' }]}>
                                {Math.round(carbs)}g
                            </Text>
                            <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Carbos</Text>
                        </View>
                        <View style={styles.macroItem}>
                            <Text style={[styles.macroValue, { color: '#F43F5E' }]}>
                                {Math.round(fats)}g
                            </Text>
                            <Text style={[styles.macroLabel, { color: colors.onSurfaceVariant }]}>Grasas</Text>
                        </View>
                    </View>

                    <TouchableOpacity
                        onPress={onClose}
                        style={[styles.button, { backgroundColor: colors.primary }]}
                    >
                        <Text style={[styles.buttonText, { color: colors.onPrimary }]}>Cerrar</Text>
                    </TouchableOpacity>
                </View>
            </View>
        </Modal>
    );
};

const styles = StyleSheet.create({
    overlay: {
        flex: 1,
        backgroundColor: 'rgba(0,0,0,0.7)',
        justifyContent: 'center',
        alignItems: 'center',
    },
    container: {
        width: '80%',
        padding: 24,
        borderRadius: 24,
        alignItems: 'center',
        position: 'relative',
        overflow: 'hidden',
    },
    confettiContainer: {
        position: 'absolute',
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        zIndex: 1,
        pointerEvents: 'none',
    },
    particle: {
        position: 'absolute',
        width: 8,
        height: 8,
        borderRadius: 4,
    },
    title: {
        fontSize: 24,
        fontWeight: '900',
        marginBottom: 8,
        zIndex: 2,
    },
    subtitle: {
        fontSize: 14,
        marginBottom: 20,
        zIndex: 2,
    },
    summaryRow: {
        flexDirection: 'row',
        justifyContent: 'space-around',
        width: '100%',
        marginBottom: 24,
        zIndex: 2,
    },
    macroItem: {
        alignItems: 'center',
    },
    macroValue: {
        fontSize: 16,
        fontWeight: '800',
    },
    macroLabel: {
        fontSize: 10,
        fontWeight: '600',
        marginTop: 4,
    },
    button: {
        paddingHorizontal: 24,
        paddingVertical: 12,
        borderRadius: 12,
        zIndex: 2,
    },
    buttonText: {
        fontSize: 14,
        fontWeight: '900',
    },
});

export default React.memo(GoalReachedModal);
