import React, { useState, useEffect, useCallback } from 'react';
import { FoodAI, FoodAnalysisResult } from '../../services/FoodAI';
import { modelManager } from '../../services/ModelManager';
import NutritionPreview from './NutritionPreview';
import { CheckCircleIcon, AlertTriangleIcon, SparklesIcon, BrainIcon } from '../icons';

interface FoodLoggerUnifiedProps {
    onFoodRegistered?: (result: FoodAnalysisResult) => void;
    onError?: (error: Error) => void;
}

/**
 * FoodLoggerUnified - Sistema Unificado de Registro Nutricional
 * 
 * Reemplaza al sistema anterior de búsqueda + campos manuales.
 * Único input descriptivo → Análisis automático → Confirmar
 * 
 * Características:
 * - Input descriptivo natural ("2 huevos con pan integral")
 * - Análisis en cascada (caché → BD → heurística → IA)
 * - Preview automático de resultados
 * - Confirmación rápida o ajuste manual
 * - IA solo como último recurso (3-5% de casos)
 */
const FoodLoggerUnified: React.FC<FoodLoggerUnifiedProps> = ({
    onFoodRegistered,
    onError,
}) => {
    // Estados
    const [description, setDescription] = useState('');
    const [analyzing, setAnalyzing] = useState(false);
    const [result, setResult] = useState<FoodAnalysisResult | null>(null);
    const [aiStatus, setAiStatus] = useState({
        loaded: false,
        loading: false,
        ready: false,
        size: '',
    });
    const [progress, setProgress] = useState('');
    const [error, setError] = useState<string | null>(null);

    // Verificar estado del modelo al montar
    useEffect(() => {
        const checkModelStatus = async () => {
            const status = await modelManager.getFullStatus();
            
            setAiStatus({
                loaded: status.exists,
                loading: false,
                ready: status.ready,
                size: status.size || 'Desconocido',
            });

            if (!status.exists) {
                console.warn('⚠️ Modelo no encontrado. La IA no estará disponible.');
            }
        };

        checkModelStatus();

        // Configurar callbacks de FoodAI
        FoodAI.onProgress = (report) => setProgress(report);
        FoodAI.onLoadingStart = () => setAiStatus(s => ({ ...s, loading: true }));
        FoodAI.onLoadingEnd = () => setAiStatus(s => ({ ...s, loading: false }));
    }, []);

    // Manejar análisis de comida
    const handleAnalyze = useCallback(async () => {
        if (!description.trim()) return;

        setAnalyzing(true);
        setError(null);
        setResult(null);

        try {
            const analysisResult = await FoodAI.analyzeFood(description);
            setResult(analysisResult);

            // Trackear fuente del resultado
            console.log('📊 Resultado:', {
                source: analysisResult.source,
                confidence: analysisResult.confidence,
                food: analysisResult.foodName,
            });

        } catch (err) {
            const errorMessage = err instanceof Error ? err.message : 'Error desconocido';
            setError(errorMessage);
            onError?.(err instanceof Error ? err : new Error(errorMessage));
        } finally {
            setAnalyzing(false);
            setProgress('');
        }
    }, [description, onError]);

    // Manejar confirmación
    const handleConfirm = useCallback(() => {
        if (!result) return;

        onFoodRegistered?.(result);
        
        // Resetear para siguiente registro
        setDescription('');
        setResult(null);
    }, [result, onFoodRegistered]);

    // Manejar ajuste manual (abre editor)
    const handleEdit = useCallback(() => {
        // Aquí se podría abrir un drawer de edición
        // Por ahora, solo logueamos
        console.log('✏️ Ajustar manualmente:', result?.foodName);
    }, [result]);

    // Auto-analizar cuando el usuario deja de escribir (debounce)
    useEffect(() => {
        const timer = setTimeout(() => {
            if (description.length > 5 && !analyzing) {
                handleAnalyze();
            }
        }, 800); // 800ms de debounce

        return () => clearTimeout(timer);
    }, [description, analyzing, handleAnalyze]);

    // Render
    return (
        <div className="bg-white rounded-3xl shadow-sm border border-zinc-200/60 overflow-hidden">
            {/* Header */}
            <div className="px-5 py-4 border-b border-zinc-100 bg-gradient-to-br from-zinc-50 to-white">
                <div className="flex items-center justify-between">
                    <div>
                        <h2 className="text-base font-black text-zinc-900 tracking-tight">
                            Registrar Comida
                        </h2>
                        <p className="text-[10px] text-zinc-500 uppercase tracking-widest mt-0.5">
                            Describí lo que comiste
                        </p>
                    </div>

                    {/* Indicador de IA */}
                    <div className="flex items-center gap-2">
                        {aiStatus.ready ? (
                            <span className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-emerald-50 border border-emerald-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 animate-pulse" />
                                <span className="text-[8px] font-black uppercase tracking-wider text-emerald-700">
                                    IA Lista
                                </span>
                            </span>
                        ) : aiStatus.loading ? (
                            <span className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-amber-50 border border-amber-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-spin" />
                                <span className="text-[8px] font-black uppercase tracking-wider text-amber-700">
                                    Cargando
                                </span>
                            </span>
                        ) : (
                            <span className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-full bg-zinc-100 border border-zinc-200">
                                <span className="w-1.5 h-1.5 rounded-full bg-zinc-400" />
                                <span className="text-[8px] font-black uppercase tracking-wider text-zinc-500">
                                    Modo Básico
                                </span>
                            </span>
                        )}
                    </div>
                </div>
            </div>

            {/* Input descriptivo */}
            <div className="px-5 py-4">
                <textarea
                    value={description}
                    onChange={(e) => setDescription(e.target.value)}
                    placeholder="Ej: 2 huevos revueltos con pan integral y café con leche"
                    className="w-full h-24 px-4 py-3 rounded-2xl bg-zinc-50 border border-zinc-200 focus:border-purple-400 focus:ring-2 focus:ring-purple-500/20 outline-none transition-all resize-none text-sm text-zinc-900 placeholder-zinc-400"
                    disabled={analyzing}
                />

                {/* Tips */}
                <div className="mt-2 flex items-start gap-2">
                    <span className="text-[9px] text-zinc-400 mt-0.5">💡</span>
                    <p className="text-[9px] text-zinc-500 leading-relaxed">
                        Sé específico: mencioná cantidades, preparación y acompañamientos. 
                        Ej: "150g de pollo a la plancha con ensalada y arroz"
                    </p>
                </div>
            </div>

            {/* Estado de análisis */}
            {analyzing && (
                <div className="px-5 py-3 bg-purple-50/50 border-t border-purple-100">
                    <div className="flex items-center gap-3">
                        <div className="w-5 h-5 rounded-full border-2 border-purple-500 border-t-transparent animate-spin" />
                        <div className="flex-1">
                            <p className="text-[9px] font-black uppercase tracking-wider text-purple-700">
                                Analizando...
                            </p>
                            {progress && (
                                <p className="text-[8px] text-purple-600 mt-0.5 truncate">
                                    {progress}
                                </p>
                            )}
                        </div>
                    </div>
                </div>
            )}

            {/* Error */}
            {error && (
                <div className="px-5 py-3 bg-red-50 border-t border-red-100">
                    <div className="flex items-start gap-2">
                        <AlertTriangleIcon size={16} className="text-red-500 mt-0.5 flex-shrink-0" />
                        <div className="flex-1">
                            <p className="text-[9px] font-black uppercase tracking-wider text-red-700">
                                Error en análisis
                            </p>
                            <p className="text-[10px] text-red-600 mt-0.5">
                                {error}
                            </p>
                        </div>
                    </div>
                </div>
            )}

            {/* Preview de resultados */}
            {result && (
                <div className="border-t border-zinc-100">
                    <NutritionPreview result={result} />

                    {/* Acciones */}
                    <div className="px-5 py-4 bg-zinc-50 flex items-center gap-3">
                        <button
                            onClick={handleConfirm}
                            className="flex-1 h-12 rounded-2xl bg-gradient-to-r from-emerald-500 to-emerald-600 text-white text-[9px] font-black uppercase tracking-[0.15em] hover:opacity-90 transition-all shadow-lg shadow-emerald-500/30 flex items-center justify-center gap-2"
                        >
                            <CheckCircleIcon size={16} />
                            Confirmar
                        </button>

                        <button
                            onClick={handleEdit}
                            className="h-12 px-5 rounded-2xl border-2 border-zinc-200 text-zinc-600 text-[9px] font-black uppercase tracking-[0.15em] hover:bg-zinc-100 transition-all"
                        >
                            Ajustar
                        </button>
                    </div>
                </div>
            )}

            {/* Footer con estado */}
            {!result && !analyzing && !error && (
                <div className="px-5 py-3 bg-zinc-50 border-t border-zinc-100">
                    <div className="flex items-center justify-center gap-2 text-[9px] text-zinc-400">
                        <BrainIcon size={14} />
                        <span>
                            {aiStatus.ready 
                                ? 'IA disponible para análisis complejo' 
                                : 'Usando búsqueda local'}
                        </span>
                    </div>
                </div>
            )}
        </div>
    );
};

export default FoodLoggerUnified;
