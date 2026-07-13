import React, { useEffect } from 'react';
import { View, Text, StyleSheet, ScrollView } from 'react-native';
import Animated, { useSharedValue, useAnimatedStyle, withRepeat, withTiming, Easing } from 'react-native-reanimated';
import { useColors } from '../../theme';
import { LiquidGlassCard } from '../ui/LiquidGlassCard';
import { useWorkoutStore } from '../../stores/workoutStore';
import { Canvas, Circle, LinearGradient, vec, Blur, Mask, Group, Rect, Paint } from '@shopify/react-native-skia';

export const AugeDashboard: React.FC = () => {
    const colors = useColors();
    const overview = useWorkoutStore(s => s.overview);
    const battery = overview?.battery;

    if (!battery) {
        return (
            <LiquidGlassCard style={styles.container}>
                <Text style={styles.emptyText}>No hay datos de recuperación disponibles.</Text>
            </LiquidGlassCard>
        );
    }

    const BatteryOrb = ({ label, value, color }: { label: string, value: number, color: string }) => {
        const pulse = useSharedValue(1);
        
        useEffect(() => {
            pulse.value = withRepeat(
                withTiming(1.08, { duration: 2000 + Math.random() * 1000, easing: Easing.inOut(Easing.ease) }),
                -1,
                true
            );
        }, []);

        const animatedOrbStyle = useAnimatedStyle(() => ({
            transform: [{ scale: pulse.value }],
            opacity: 0.6 + (value / 250),
        }));

        return (
            <View style={styles.orbRow}>
                <View style={styles.orbContainer}>
                    <Canvas style={styles.orbCanvas}>
                        <Group>
                            <Mask
                                mask={
                                    <Circle cx={32} cy={32} r={28} />
                                }
                            >
                                <Rect x={0} y={0} width={64} height={64}>
                                    <LinearGradient
                                        start={vec(0, 64)}
                                        end={vec(0, 64 - (64 * value / 100))}
                                        colors={[color, color]}
                                    />
                                </Rect>
                            </Mask>
                            <Circle cx={32} cy={32} r={28} color={color} opacity={0.1} />
                            <Circle cx={32} cy={32} r={28} style="stroke" strokeWidth={1} color={color} opacity={0.3} />
                        </Group>
                        <Circle cx={32} cy={32} r={22}>
                            <Blur blur={8} />
                            <Paint color={color} opacity={0.2} />
                        </Circle>
                    </Canvas>
                    <Animated.View style={[styles.orbGlow, { backgroundColor: color }, animatedOrbStyle]} />
                    <Text style={styles.orbValueText}>{Math.round(value)}%</Text>
                </View>
                <View style={styles.orbInfo}>
                    <Text style={[styles.orbLabel, { color: colors.onSurface }]}>{label}</Text>
                    <Text style={[styles.orbSublabel, { color: colors.onSurfaceVariant }]}>
                        {value > 85 ? 'Excelente' : value > 60 ? 'Buena' : value > 30 ? 'Comprometida' : 'Crítica'}
                    </Text>
                </View>
            </View>
        );
    };

    const getCnsColor = (cnsValue: number) => {
        if (cnsValue < 30) return colors.error;
        return colors.primary;
    };

    return (
        <ScrollView contentContainerStyle={styles.scrollContent} showsVerticalScrollIndicator={false}>
            <LiquidGlassCard style={styles.mainCard}>
                <View style={styles.header}>
                    <Text style={[styles.title, { color: colors.primary }]}>Estado Global AUGE</Text>
                    <View style={[styles.overallBadge, { backgroundColor: battery.overall > 70 ? `${colors.batteryHigh}33` : `${colors.batteryLow}33` }]}>
                        <Text style={[styles.overallText, { color: battery.overall > 70 ? colors.batteryHigh : colors.batteryLow }]}>
                            {battery.overall > 70 ? 'ÓPTIMO' : battery.overall > 40 ? 'PRECAUCIÓN' : 'CRÍTICO'}
                        </Text>
                    </View>
                </View>

                <View style={styles.overallCircleContainer}>
                    <Text style={[styles.overallPercent, { color: colors.onSurface }]}>{Math.round(battery.overall)}%</Text>
                    <Text style={[styles.overallLabel, { color: colors.onSurfaceVariant }]}>Readiness</Text>
                </View>

                <View style={styles.orbList}>
                    <BatteryOrb 
                        label="CNS (Sistema Nervioso)" 
                        value={battery.cns} 
                        color={getCnsColor(battery.cns)} 
                    />
                    <BatteryOrb label="Muscular (Puntos Locales)" value={battery.muscular} color={colors.tertiary} />
                    <BatteryOrb label="Estructural (Soporte)" value={battery.spinal} color={colors.secondary} />
                </View>
            </LiquidGlassCard>

            <View style={styles.expertVerdict}>
                <Text style={[styles.verdictTitle, { color: colors.onSurface }]}>Veredicto del Motor</Text>
                <Text style={[styles.verdictText, { color: colors.onSurfaceVariant }]}>
                    {battery.overall > 80 
                        ? "Tu capacidad de trabajo es máxima. Es un excelente momento para sesiones de alta intensidad o volumen incremental."
                        : battery.overall > 60
                        ? "Recuperación adecuada. Puedes entrenar según lo planeado, pero vigila el RPE en los levantamientos principales."
                        : "La fatiga sistémica es notable. Considera reducir el volumen de la sesión o priorizar ejercicios de menor demanda técnica."}
                </Text>
            </View>
        </ScrollView>
    );
};

const styles = StyleSheet.create({
    container: {
        padding: 24,
        alignItems: 'center',
    },
    emptyText: {
        fontSize: 14,
        color: '#6B6474',
    },
    scrollContent: {
        paddingBottom: 24,
    },
    mainCard: {
        paddingHorizontal: 20,
        paddingVertical: 24,
        marginBottom: 16,
    },
    header: {
        flexDirection: 'row',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: 24,
    },
    title: {
        fontSize: 11,
        fontWeight: '900',
        letterSpacing: 1.5,
        textTransform: 'uppercase',
    },
    overallBadge: {
        paddingHorizontal: 10,
        paddingVertical: 4,
        borderRadius: 8,
    },
    overallText: {
        fontSize: 10,
        fontWeight: '900',
    },
    overallCircleContainer: {
        alignItems: 'center',
        marginBottom: 32,
    },
    overallPercent: {
        fontSize: 52,
        fontWeight: '900',
        letterSpacing: -1.5,
    },
    overallLabel: {
        fontSize: 11,
        fontWeight: '800',
        marginTop: -6,
        textTransform: 'uppercase',
        letterSpacing: 1.2,
    },
    orbList: {
        gap: 16,
    },
    orbRow: {
        flexDirection: 'row',
        alignItems: 'center',
        gap: 16,
        backgroundColor: 'rgba(255,255,255,0.4)',
        padding: 12,
        borderRadius: 20,
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.6)',
    },
    orbContainer: {
        width: 64,
        height: 64,
        justifyContent: 'center',
        alignItems: 'center',
    },
    orbCanvas: {
        width: 64,
        height: 64,
        position: 'absolute',
    },
    orbGlow: {
        position: 'absolute',
        width: 40,
        height: 40,
        borderRadius: 20,
    },
    orbValueText: {
        fontSize: 12,
        fontWeight: '900',
        color: '#1C1B1F',
    },
    orbInfo: {
        flex: 1,
    },
    orbLabel: {
        fontSize: 13,
        fontWeight: '700',
    },
    orbSublabel: {
        fontSize: 11,
        fontWeight: '600',
        marginTop: 2,
    },
    expertVerdict: {
        backgroundColor: 'rgba(255,255,255,0.5)',
        padding: 24,
        borderRadius: 32,
        borderWidth: 1,
        borderColor: 'rgba(255,255,255,0.8)',
    },
    verdictTitle: {
        fontSize: 16,
        fontWeight: '900',
        marginBottom: 8,
    },
    verdictText: {
        fontSize: 14,
        lineHeight: 22,
        fontWeight: '500',
    },
});
