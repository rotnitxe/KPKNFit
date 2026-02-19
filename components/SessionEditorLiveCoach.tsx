// components/SessionEditorLiveCoach.tsx
import React, { useEffect, useRef, useCallback, useState } from 'react';
import { GoogleGenAI, LiveSession, LiveServerMessage, Modality, Blob, FunctionDeclaration, Type } from '@google/genai';
import { useAppState } from '../contexts/AppContext';
import { encode } from '../services/geminiService';
import { Session, ExerciseMuscleInfo, Exercise } from '../types';
import { calculateSessionVolume } from '../services/analysisService';
import { INITIAL_MUSCLE_HIERARCHY } from '../data/initialMuscleHierarchy';

interface SessionEditorLiveCoachProps {
    onAddExercise: (details: { name: string, sets?: number, reps?: number, rest?: number }) => void;
    contextPrompt: string;
    session: Session;
    exerciseList: ExerciseMuscleInfo[];
}

const functionDeclarations: FunctionDeclaration[] = [
    {
        name: 'addExercise',
        description: 'Añade un nuevo ejercicio a la sesión de entrenamiento.',
        parameters: {
            type: Type.OBJECT,
            properties: {
                name: { type: Type.STRING, description: 'Nombre del ejercicio.' },
                sets: { type: Type.NUMBER, description: 'Series.' },
                reps: { type: Type.NUMBER, description: 'Repeticiones.' },
                rest: { type: Type.NUMBER, description: 'Descanso en segundos.' }
            },
            required: ['name'],
        },
    },
];

export const SessionEditorLiveCoach: React.FC<SessionEditorLiveCoachProps> = ({ onAddExercise, contextPrompt, session, exerciseList }) => {
    const { settings } = useAppState();
    const sessionPromiseRef = useRef<Promise<LiveSession> | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const outputAudioContextRef = useRef<AudioContext | null>(null);
    const streamRef = useRef<MediaStream | null>(null);
    const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);
    const audioSourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set());
    const nextStartTimeRef = useRef(0);
    
    // Track previous session state to detect changes
    const prevSessionRef = useRef<string>(JSON.stringify(session));

    // Debounce helper for session analysis
    const [debouncedSession, setDebouncedSession] = useState(session);

    useEffect(() => {
        const handler = setTimeout(() => {
            setDebouncedSession(session);
        }, 1500); // Wait 1.5 seconds after user stops typing/editing to analyze
        return () => clearTimeout(handler);
    }, [session]);

    const stopAllAudio = useCallback(() => {
        audioSourcesRef.current.forEach(source => {
            try { source.stop(); } catch(e) {}
        });
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
            audioContextRef.current.close().catch(e => console.warn("Error closing input audio context:", e));
            audioContextRef.current = null;
        }
        if (outputAudioContextRef.current && outputAudioContextRef.current.state !== 'closed') {
            outputAudioContextRef.current.close().catch(e => console.warn("Error closing output audio context:", e));
            outputAudioContextRef.current = null;
        }
    }, []);

    useEffect(() => {
        const apiKey = settings.apiKeys?.gemini;
        if (!apiKey) return;

        const ai = new GoogleGenAI({ apiKey });
        const systemInstruction = `Eres "Prime Architect", un asistente experto en diseño de programas de entrenamiento.
        
        TU ROL:
        1. Ayudar al usuario a construir su sesión.
        2. ANALIZAR ACTIVAMENTE: Recibirás actualizaciones de texto ocultas con el resumen del volumen actual. Úsalas.
        3. ALERTAR: Si detectas un volumen excesivo (ej. > 10 series directas por grupo muscular en una sesión, o > 20 series totales), advierte al usuario.
        4. OPINAR: Si el usuario añade un ejercicio redundante o lesivo, coméntalo brevemente.
        5. Sé conversacional, rápido y directo.
        
        ${contextPrompt}`;

        const connect = async () => {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
                streamRef.current = stream;

                audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 16000 });
                outputAudioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ sampleRate: 24000 });
                
                sessionPromiseRef.current = ai.live.connect({
                    model: 'gemini-2.5-flash-native-audio-preview-09-2025',
                    config: {
                        responseModalities: [Modality.AUDIO],
                        tools: [{ functionDeclarations }],
                        systemInstruction,
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
                                for (let i = 0; i < inputData.length; i++) {
                                    int16[i] = inputData[i] * 32768;
                                }
                                const pcmBlob: Blob = {
                                    data: encode(new Uint8Array(int16.buffer)),
                                    mimeType: 'audio/pcm;rate=16000',
                                };
                                sessionPromiseRef.current?.then((session) => {
                                    session?.sendRealtimeInput({ media: pcmBlob });
                                });
                            };
                            source.connect(scriptProcessor);
                            scriptProcessor.connect(inputCtx.destination);
                        },
                        onmessage: async (message: LiveServerMessage) => {
                             const audioData = message.serverContent?.modelTurn?.parts[0]?.inlineData?.data;
                            if (audioData) {
                                const outCtx = outputAudioContextRef.current;
                                if (!outCtx) return;
                                
                                const decode = (base64: string) => {
                                    const binaryString = atob(base64);
                                    const len = binaryString.length;
                                    const bytes = new Uint8Array(len);
                                    for (let i = 0; i < len; i++) bytes[i] = binaryString.charCodeAt(i);
                                    return bytes;
                                };
                                const decodeAudioData = async (data: Uint8Array, ctx: AudioContext) => {
                                     const dataInt16 = new Int16Array(data.buffer);
                                     const buffer = ctx.createBuffer(1, dataInt16.length, 24000);
                                     const channel = buffer.getChannelData(0);
                                     for(let i=0; i<dataInt16.length; i++) channel[i] = dataInt16[i] / 32768.0;
                                     return buffer;
                                }

                                nextStartTimeRef.current = Math.max(nextStartTimeRef.current, outCtx.currentTime);
                                const audioBuffer = await decodeAudioData(decode(audioData), outCtx);
                                const sourceNode = outCtx.createBufferSource();
                                sourceNode.buffer = audioBuffer;
                                sourceNode.connect(outCtx.destination);
                                sourceNode.start(nextStartTimeRef.current);
                                nextStartTimeRef.current += audioBuffer.duration;
                            }

                            if (message.toolCall?.functionCalls) {
                                for (const fc of message.toolCall.functionCalls) {
                                    let result = "ok";
                                    if (fc.name === 'addExercise') {
                                        onAddExercise(fc.args as any);
                                        result = `Added ${fc.args.name}`;
                                    }
                                    sessionPromiseRef.current?.then(session => {
                                        session?.sendToolResponse({ functionResponses: { id: fc.id, name: fc.name, response: { result } } });
                                    });
                                }
                            }
                        },
                        onclose: () => cleanup(),
                        onerror: () => cleanup(),
                    },
                });
            } catch (error) {
                console.error('Failed to start editor live coach', error);
                cleanup();
            }
        };

        connect();

        return () => cleanup();
    }, [contextPrompt, onAddExercise, cleanup, settings.apiKeys?.gemini]);

    // Monitor session changes and send updates to AI
    useEffect(() => {
        const currentSessionStr = JSON.stringify(debouncedSession);
        if (currentSessionStr === prevSessionRef.current) return;
        
        prevSessionRef.current = currentSessionStr;

        const volume = calculateSessionVolume(debouncedSession, exerciseList, INITIAL_MUSCLE_HIERARCHY);
        // Create a clear summary string for the AI
        const volumeSummary = volume.map(v => `${v.muscleGroup}: ${v.displayVolume} series`).join(', ');
        
        if (sessionPromiseRef.current) {
            const updateMsg = `[SYSTEM UPDATE: Session Data Changed]
            Exercises count: ${debouncedSession.exercises.length + (debouncedSession.parts?.reduce((a,b)=>a+b.exercises.length,0) || 0)}.
            Volume Breakdown: ${volumeSummary || "None"}.
            If any muscle group > 10 sets/session or total volume > 25 sets, casually mention it might be high volume.`;
            
            sessionPromiseRef.current.then(s => {
                // Send text update to context without interrupting audio
                s.send({ parts: [{ text: updateMsg }] });
            });
        }
    }, [debouncedSession, exerciseList]);

    return null;
};