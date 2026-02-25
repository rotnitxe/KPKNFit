
// components/GlobalVoiceAssistant.tsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { GoogleGenAI, LiveSession, LiveServerMessage, Modality, Blob, FunctionDeclaration, Type } from '@google/genai';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { encode, decode, decodeAudioData } from '../services/geminiService';
import * as aiService from '../services/aiService';
import { KeepAwake } from '@capacitor-community/keep-awake';
import { MicIcon } from './icons';
import { configureAudioSession } from '../services/soundService';
import { getLocalDateString } from '../utils/dateUtils';

const functionDeclarations: FunctionDeclaration[] = [
    {
        name: 'getAppState',
        description: "Usa esta herramienta SIEMPRE que necesites consultar información sobre el usuario, su entrenamiento actual, historial o nutrición. No asumas valores, consúltalos.",
        parameters: {
            type: Type.OBJECT,
            properties: {
                domain: {
                    type: Type.STRING,
                    enum: ['workout', 'nutrition', 'history', 'profile', 'programs', 'all'],
                    description: "El área de datos a consultar."
                },
                query: {
                    type: Type.STRING,
                    description: "Término de búsqueda específico, ej. el nombre de un ejercicio en el historial."
                }
            },
            required: ['domain']
        }
    },
    {
        name: 'logFood',
        description: 'Registra un alimento o comida en el diario de nutrición basándose en una descripción de texto.',
        parameters: { type: Type.OBJECT, properties: { description: { type: Type.STRING, description: 'Descripción de la comida (ej. "2 huevos fritos y pan").' } }, required: ['description'] }
    },
    {
        name: 'startWorkout',
        description: 'Inicia una sesión de entrenamiento de un programa específico.',
        parameters: { type: Type.OBJECT, properties: { programName: { type: Type.STRING, description: 'Nombre o palabra clave del programa.' } }, required: ['programName'] }
    },
    {
        name: 'navigateTo',
        description: 'Navega a una sección específica de la aplicación.',
        parameters: { type: Type.OBJECT, properties: { view: { type: Type.STRING, enum: ['home', 'progress', 'nutrition', 'settings', 'kpkn'], description: 'La vista a la que navegar.' } }, required: ['view'] }
    },
    {
        name: 'deleteProgram',
        description: 'Elimina un programa de entrenamiento por su nombre.',
        parameters: { type: Type.OBJECT, properties: { programName: { type: Type.STRING, description: 'Nombre exacto o palabra clave del programa a eliminar.' } }, required: ['programName'] }
    },
    {
        name: 'activateProgram',
        description: 'Activa un programa para que sea el plan de entrenamiento actual del usuario.',
        parameters: { type: Type.OBJECT, properties: { programName: { type: Type.STRING, description: 'Nombre del programa a activar.' } }, required: ['programName'] }
    },
    {
        name: 'addExerciseToSession',
        description: 'Añade un nuevo ejercicio a la sesión de entrenamiento que está en curso.',
        parameters: {
            type: Type.OBJECT,
            properties: {
                exerciseName: { type: Type.STRING, description: 'Nombre del ejercicio.' },
                sets: { type: Type.NUMBER, description: 'Número de series. Por defecto 3.' },
                reps: { type: Type.NUMBER, description: 'Repeticiones por serie. Por defecto 8.' }
            },
            required: ['exerciseName']
        }
    },
    {
        name: 'updateUserSettings',
        description: 'Actualiza una configuración específica del usuario.',
        parameters: {
            type: Type.OBJECT,
            properties: {
                key: { type: Type.STRING, description: 'La clave de configuración a cambiar (ej. "appTheme", "userVitals.weight").' },
                value: { type: Type.STRING, description: 'El nuevo valor para la configuración.' }
            },
            required: ['key', 'value']
        }
    },
    {
        name: 'syncData',
        description: 'Inicia una sincronización manual de los datos de la aplicación con Google Drive.',
        parameters: { type: Type.OBJECT, properties: {} }
    }
];

// Helper to update nested settings object
const updateNestedSettings = (settings: any, key: string, value: any): any => {
    const keys = key.split('.');
    if (keys.length === 1) {
        return { [key]: value };
    }
    const newSettings: any = JSON.parse(JSON.stringify(settings));
    let temp = newSettings;
    for (let i = 0; i < keys.length - 1; i++) {
        temp = temp[keys[i]];
        if (temp === undefined) return {}; // Invalid path
    }
    temp[keys[keys.length - 1]] = value;
    return { [keys[0]]: newSettings[keys[0]] };
};

const parseValue = (value: string): any => {
    if (value.toLowerCase() === 'true') return true;
    if (value.toLowerCase() === 'false') return false;
    const num = parseFloat(value);
    if (!isNaN(num) && isFinite(num) && String(num) === value) return num;
    return value;
};

export const GlobalVoiceAssistant: React.FC = () => {
    const appState = useAppState();
    const { isGlobalVoiceActive } = appState;
    const dispatch = useAppDispatch();
    const { 
        setIsGlobalVoiceActive, addToast,
        handleDeleteProgram, handleStartProgram, setOngoingWorkout,
        setSettings
    } = dispatch;

    const appStateRef = useRef(appState);
    appStateRef.current = appState;
    
    const sessionPromiseRef = useRef<Promise<LiveSession> | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const outputAudioContextRef = useRef<AudioContext | null>(null);
    const streamRef = useRef<MediaStream | null>(null);
    const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);
    const audioSourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set());
    const nextStartTimeRef = useRef(0);
    const isConnectingRef = useRef(false);

    // Ensure audio session is configured for proper background handling
    useEffect(() => {
        configureAudioSession();
    }, []);

    const stopAllAudio = useCallback(() => {
        audioSourcesRef.current.forEach(source => { try { source.stop(); } catch(e) {} });
        audioSourcesRef.current.clear();
        nextStartTimeRef.current = 0;
    }, []);

    const cleanup = useCallback(() => {
        if (sessionPromiseRef.current) {
            sessionPromiseRef.current.then(session => session.close());
            sessionPromiseRef.current = null;
        }
        if (streamRef.current) {
            streamRef.current.getTracks().forEach(track => track.stop());
            streamRef.current = null;
        }
        if (scriptProcessorRef.current) {
            scriptProcessorRef.current.disconnect();
            scriptProcessorRef.current = null;
        }
        if (audioContextRef.current && audioContextRef.current.state !== 'closed') {
            audioContextRef.current.close();
            audioContextRef.current = null;
        }
        if (outputAudioContextRef.current && outputAudioContextRef.current.state !== 'closed') {
            outputAudioContextRef.current.close();
            outputAudioContextRef.current = null;
        }
        stopAllAudio();
        isConnectingRef.current = false;
    }, [stopAllAudio]);

    // Handle Active State Change
    useEffect(() => {
        if (isGlobalVoiceActive) {
            startSession();
            KeepAwake.keepAwake().catch(err => {
                // Ignore WakeLock errors in unsupported environments
                console.warn('WakeLock not supported or allowed:', err);
            });
        } else {
            cleanup();
            KeepAwake.allowSleep().catch(() => {});
        }
        return () => {
            if (isGlobalVoiceActive) {
                cleanup();
                KeepAwake.allowSleep().catch(() => {});
            }
        };
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [isGlobalVoiceActive]);

    const startSession = async () => {
        if (isConnectingRef.current) return;
        isConnectingRef.current = true;

        const apiKey = appState.settings.apiKeys?.gemini;
        if (!apiKey) {
             addToast("Configura tu API Key de Gemini en Ajustes para usar el asistente.", "danger");
             setIsGlobalVoiceActive(false);
             isConnectingRef.current = false;
             return;
        }

        const ai = new GoogleGenAI({ apiKey });
        const voiceName = appState.settings.aiVoice || 'Puck';
        
        const dateStr = new Date().toLocaleString('es-ES', { weekday: 'long', year: 'numeric', month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit' });
        
        const systemInstruction = `Eres "Prime God", el asistente de voz omnisciente de la app "YourPrime".
        
        CONTEXTO:
        - Fecha y Hora actual: ${dateStr}.
        - Tienes control total sobre la base de datos de la app.
        
        INSTRUCCIONES CRÍTICAS:
        1. Tu primera prioridad es CONSULTAR DATOS REALES. Si el usuario te pregunta "¿Qué entrené ayer?" o "¿Cuál es mi peso?", NO inventes. Usa la herramienta 'getAppState' para buscar la respuesta.
        2. Sé proactivo. Si el usuario dice "Quiero entrenar", consulta sus programas primero para saber qué toca hoy.
        3. Respuestas concisas y directas. No hables demasiado.
        4. Si te piden una acción (crear, borrar, navegar), usa la herramienta correspondiente.
        `;

        try {
            const stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true } });
            streamRef.current = stream;
            audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000, latencyHint: 'interactive' });
            outputAudioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 24000, latencyHint: 'interactive' });

            sessionPromiseRef.current = ai.live.connect({
                model: 'gemini-2.5-flash-native-audio-preview-09-2025',
                config: {
                    responseModalities: [Modality.AUDIO],
                    tools: [{ functionDeclarations }],
                    systemInstruction,
                    speechConfig: { voiceConfig: { prebuiltVoiceConfig: { voiceName } } },
                },
                callbacks: {
                    onopen: () => {
                        const inputCtx = audioContextRef.current;
                        if (!inputCtx || !streamRef.current) return;
                        const source = inputCtx.createMediaStreamSource(streamRef.current);
                        const scriptProcessor = inputCtx.createScriptProcessor(4096, 1, 1);
                        scriptProcessorRef.current = scriptProcessor;
                        
                        scriptProcessor.onaudioprocess = (audioProcessingEvent) => {
                            const inputData = audioProcessingEvent.inputBuffer.getChannelData(0);
                            const int16 = new Int16Array(inputData.length);
                            for (let i = 0; i < inputData.length; i++) int16[i] = inputData[i] * 32768;
                            const pcmBlob: Blob = {
                                data: encode(new Uint8Array(int16.buffer)),
                                mimeType: 'audio/pcm;rate=16000',
                            };
                            sessionPromiseRef.current?.then(session => session?.sendRealtimeInput({ media: pcmBlob }));
                        };
                        source.connect(scriptProcessor);
                        scriptProcessor.connect(inputCtx.destination);
                        isConnectingRef.current = false;
                    },
                    onmessage: async (message: LiveServerMessage) => {
                         const audioData = message.serverContent?.modelTurn?.parts[0]?.inlineData?.data;
                         if (audioData && outputAudioContextRef.current) {
                            const outCtx = outputAudioContextRef.current;
                            nextStartTimeRef.current = Math.max(nextStartTimeRef.current, outCtx.currentTime);
                            const audioBuffer = await decodeAudioData(decode(audioData), outCtx, 24000, 1);
                            const sourceNode = outCtx.createBufferSource();
                            sourceNode.buffer = audioBuffer;
                            sourceNode.connect(outCtx.destination);
                            sourceNode.onended = () => audioSourcesRef.current.delete(sourceNode);
                            sourceNode.start(nextStartTimeRef.current);
                            nextStartTimeRef.current += audioBuffer.duration;
                            audioSourcesRef.current.add(sourceNode);
                         }

                        if (message.toolCall?.functionCalls) {
                            for (const fc of message.toolCall.functionCalls) {
                                let result: any = { status: "ok" };
                                try {
                                    const state = appStateRef.current;
                                    switch (fc.name) {
                                        case 'getAppState': {
                                            const { domain, query } = fc.args as { domain: string, query?: string };
                                            
                                            switch (domain) {
                                                case 'workout':
                                                    result = {
                                                        isWorkoutActive: !!state.ongoingWorkout,
                                                        currentWorkout: state.ongoingWorkout ? {
                                                            sessionName: state.ongoingWorkout.session.name,
                                                            activeExercise: state.ongoingWorkout.session.exercises.find(e => e.id === state.ongoingWorkout!.activeExerciseId)?.name,
                                                        } : null
                                                    };
                                                    break;
                                                case 'nutrition':
                                                    const todayStr = getLocalDateString();
                                                    const todaysLogs = state.nutritionLogs.filter(log => log.date && log.date.startsWith(todayStr));
                                                    const consumed = todaysLogs.reduce((acc, log) => {
                                                        log.foods.forEach(food => {
                                                            acc.calories += food.calories || 0;
                                                            acc.protein += food.protein || 0;
                                                        });
                                                        return acc;
                                                    }, { calories: 0, protein: 0 });
                                                    result = { consumed, goals: { calories: state.settings.dailyCalorieGoal, protein: state.settings.dailyProteinGoal } };
                                                    break;
                                                case 'history':
                                                    let filteredHistory = [...state.history].sort((a,b) => new Date(b.date).getTime() - new Date(a.date).getTime());
                                                    if (query) {
                                                        const q = query.toLowerCase();
                                                        filteredHistory = filteredHistory.filter(log => log.sessionName.toLowerCase().includes(q) || log.completedExercises.some(ex => ex.exerciseName.toLowerCase().includes(q)));
                                                    }
                                                    // Summarize last 5 logs for brevity
                                                    result = {
                                                        history: filteredHistory.slice(0, 5).map(log => ({
                                                            date: new Date(log.date).toLocaleDateString(),
                                                            session: log.sessionName,
                                                            exercises: log.completedExercises.map(e => `${e.exerciseName} (${e.sets.length} sets)`).join(', ')
                                                        }))
                                                    };
                                                    break;
                                                case 'profile':
                                                    result = { userVitals: state.settings.userVitals };
                                                    break;
                                                case 'programs':
                                                    let programs = state.programs;
                                                    if (query) programs = programs.filter(p => p.name.toLowerCase().includes(query.toLowerCase()));
                                                    result = { programs: programs.map(p => ({name: p.name, description: p.description})) };
                                                    break;
                                                default:
                                                    result = { error: "Dominio desconocido" };
                                            }
                                            break;
                                        }
                                        case 'deleteProgram': {
                                            const prog = state.programs.find(p => p.name.toLowerCase().includes((fc.args.programName as string).toLowerCase()));
                                            if (prog) { handleDeleteProgram(prog.id); result = { message: `Programa ${prog.name} eliminado.` }; }
                                            else result = { error: "Programa no encontrado." };
                                            break;
                                        }
                                        case 'activateProgram': {
                                            const prog = state.programs.find(p => p.name.toLowerCase().includes((fc.args.programName as string).toLowerCase()));
                                            if (prog) { handleStartProgram(prog.id); result = { message: `Programa ${prog.name} activado.` }; }
                                            else result = { error: "Programa no encontrado." };
                                            break;
                                        }
                                        case 'addExerciseToSession': {
                                            if (!state.ongoingWorkout) { result = { error: 'No hay un entrenamiento activo.' }; break; }
                                            const { exerciseName, sets, reps } = fc.args as { exerciseName: string, sets?: number, reps?: number };
                                            const newExercise: any = { id: crypto.randomUUID(), name: exerciseName, restTime: 90, sets: Array.from({ length: sets || 3 }).map(() => ({ id: crypto.randomUUID(), targetReps: reps || 8, intensityMode: 'rpe', targetRPE: 8 })), isFavorite: false, trainingMode: 'reps' };
                                            setOngoingWorkout(prev => {
                                                if (!prev) return null;
                                                const updatedSession = { ...prev.session };
                                                if (updatedSession.parts && updatedSession.parts.length > 0) {
                                                    updatedSession.parts[updatedSession.parts.length - 1].exercises.push(newExercise);
                                                } else {
                                                    updatedSession.exercises = [...updatedSession.exercises, newExercise];
                                                }
                                                return { ...prev, session: updatedSession };
                                            });
                                            result = { message: `Ejercicio ${exerciseName} añadido.` };
                                            break;
                                        }
                                        case 'updateUserSettings': {
                                            const { key, value } = fc.args as { key: string, value: string };
                                            const parsedValue = parseValue(value);
                                            const settingUpdate = updateNestedSettings(state.settings, key, parsedValue);
                                            if (Object.keys(settingUpdate).length > 0) {
                                                setSettings(settingUpdate);
                                                result = { message: `Ajuste ${key} actualizado.` };
                                            } else {
                                                result = { error: `No se pudo actualizar el ajuste ${key}.` };
                                            }
                                            break;
                                        }
                                        case 'syncData': {
                                            if (state.drive.isSignedIn) { state.drive.syncToDrive(); result = { message: "Sincronización iniciada." }; }
                                            else result = { error: "No has iniciado sesión en Google Drive." };
                                            break;
                                        }
                                    }
                                } catch (e: any) {
                                    result = { error: e.message };
                                }
                                sessionPromiseRef.current?.then(session => {
                                    session?.sendToolResponse({ functionResponses: { id: fc.id, name: fc.name, response: { result } } });
                                });
                            }
                        }
                    },
                    onerror: (e) => { 
                         console.error('Gemini error:', e);
                         cleanup();
                         setIsGlobalVoiceActive(false); 
                    },
                    onclose: () => { cleanup(); setIsGlobalVoiceActive(false); },
                }
            });
        } catch (e) {
            console.error(e);
            cleanup();
            setIsGlobalVoiceActive(false);
        }
    };

    if (!isGlobalVoiceActive) return null;

    return (
        <div className="fixed bottom-[100px] left-0 right-0 flex justify-center items-center pointer-events-none z-50 animate-fade-in-up">
            <div className="bg-black/80 backdrop-blur-md px-6 py-3 rounded-full border border-cyan-500/50 flex items-center gap-4 shadow-[0_0_30px_rgba(6,182,212,0.3)] pointer-events-auto">
                 <div className="flex gap-1 items-center h-4">
                    <div className="w-1 bg-cyan-400 h-full animate-[wave_1s_ease-in-out_infinite]"></div>
                    <div className="w-1 bg-cyan-400 h-2/3 animate-[wave_1s_ease-in-out_0.1s_infinite]"></div>
                    <div className="w-1 bg-cyan-400 h-full animate-[wave_1s_ease-in-out_0.2s_infinite]"></div>
                    <div className="w-1 bg-cyan-400 h-1/2 animate-[wave_1s_ease-in-out_0.3s_infinite]"></div>
                </div>
                <span className="text-white font-semibold text-sm">Escuchando...</span>
                <button onClick={() => setIsGlobalVoiceActive(false)} className="text-slate-400 hover:text-white">
                    ✕
                </button>
            </div>
        </div>
    );
};
