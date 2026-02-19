// components/WorkoutLiveCoach.tsx
import React, { useState, useEffect, useRef, useCallback } from 'react';
import { GoogleGenAI, LiveSession, LiveServerMessage, Modality, Blob, FunctionDeclaration, Type } from '@google/genai';
import { useAppState } from '../contexts/AppContext';
import { encode, decode, decodeAudioData } from '../services/geminiService';
import { Exercise } from '../types';

interface WorkoutLiveCoachProps {
    onLogSet: (reps: number, weight: number, rpe?: number, rir?: number) => void;
    onMoveToNextSet: () => void;
    onMoveToNextExercise: () => void;
    onProvideFeedback: (feedback: { jointLoad: number, technicalQuality: number, perceivedFatigue: number }) => void;
    activeExercise: Exercise | null;
    historyContext: string; // New prop for context injection
}

const functionDeclarations: FunctionDeclaration[] = [
    {
        name: 'logSet',
        description: 'Log a completed set for the current exercise.',
        parameters: {
            type: Type.OBJECT,
            properties: {
                reps: { type: Type.NUMBER, description: 'Number of repetitions completed.' },
                weight: { type: Type.NUMBER, description: 'Weight used (kg/lbs).' },
                rpe: { type: Type.NUMBER, description: 'Rating of Perceived Exertion (1-10).' },
                rir: { type: Type.NUMBER, description: 'Reps In Reserve.' }
            },
            required: ['reps', 'weight'],
        },
    },
    {
        name: 'nextSet',
        description: 'Move to the next set of the current exercise.',
        parameters: { type: Type.OBJECT, properties: {} }
    },
    {
        name: 'nextExercise',
        description: 'Move to the next exercise in the session.',
        parameters: { type: Type.OBJECT, properties: {} }
    },
    {
        name: 'provideFeedback',
        description: 'Provide feedback on the exercise execution.',
        parameters: {
             type: Type.OBJECT,
             properties: {
                jointLoad: { type: Type.NUMBER, description: 'Joint load sensation (1-10).' },
                technicalQuality: { type: Type.NUMBER, description: 'Technical quality of execution (1-10).' },
                perceivedFatigue: { type: Type.NUMBER, description: 'Perceived fatigue (1-10).' }
             },
             required: ['jointLoad', 'technicalQuality', 'perceivedFatigue']
        }
    }
];

export const WorkoutLiveCoach: React.FC<WorkoutLiveCoachProps> = (props) => {
    const { settings } = useAppState();
    const sessionPromiseRef = useRef<Promise<LiveSession> | null>(null);
    const audioContextRef = useRef<AudioContext | null>(null);
    const outputAudioContextRef = useRef<AudioContext | null>(null);
    const streamRef = useRef<MediaStream | null>(null);
    const scriptProcessorRef = useRef<ScriptProcessorNode | null>(null);
    const audioSourcesRef = useRef<Set<AudioBufferSourceNode>>(new Set());
    const nextStartTimeRef = useRef(0);

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
        stopAllAudio();
    }, [stopAllAudio]);

    useEffect(() => {
        const apiKey = settings.apiKeys?.gemini;
        if (!apiKey) return;
        
        const ai = new GoogleGenAI({ apiKey });
        const voiceName = settings.aiVoice || 'Puck';
        
        const systemInstruction = `Eres "Prime Coach", un entrenador personal de élite por voz.
        
        OBJETIVOS:
        1. Asistir al usuario durante su entrenamiento de forma proactiva.
        2. Usar los datos históricos para motivar y sugerir cargas. Por ejemplo: "La última vez hiciste 100kg, ¡hoy intenta 102.5kg!".
        3. Registrar series y datos con precisión.
        
        CONTEXTO DE DATOS EN TIEMPO REAL:
        - Ejercicio Actual: "${props.activeExercise?.name || 'Desconocido'}".
        - Historial y PRs: ${props.historyContext || "Sin datos previos."}
        
        COMPORTAMIENTO:
        - Sé breve, enérgico y directo. Estás en el gimnasio, no en una biblioteca.
        - Si el usuario termina una serie, pregúntale los datos (peso/reps) si no los dice.
        `;

        const connect = async () => {
            try {
                const stream = await navigator.mediaDevices.getUserMedia({ 
                    audio: {
                        echoCancellation: true,
                        noiseSuppression: true,
                        autoGainControl: true,
                    } 
                });
                streamRef.current = stream;

                audioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ 
                    sampleRate: 16000,
                    latencyHint: 'interactive' 
                });
                outputAudioContextRef.current = new (window.AudioContext || (window as any).webkitAudioContext)({ 
                    sampleRate: 24000, 
                    latencyHint: 'interactive' 
                });
                
                sessionPromiseRef.current = ai.live.connect({
                    model: 'gemini-2.5-flash-native-audio-preview-09-2025',
                    config: {
                        responseModalities: [Modality.AUDIO],
                        tools: [{ functionDeclarations }],
                        systemInstruction,
                        speechConfig: {
                            voiceConfig: { prebuiltVoiceConfig: { voiceName } }
                        },
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
                                    let result = "ok";
                                    if (fc.name === 'logSet') {
                                        props.onLogSet(fc.args.reps as number, fc.args.weight as number, fc.args.rpe as number, fc.args.rir as number);
                                        result = "Set logged";
                                    } else if (fc.name === 'nextSet') {
                                        props.onMoveToNextSet();
                                        result = "Moved to next set";
                                    } else if (fc.name === 'nextExercise') {
                                        props.onMoveToNextExercise();
                                        result = "Moved to next exercise";
                                    } else if (fc.name === 'provideFeedback') {
                                        props.onProvideFeedback(fc.args as any);
                                        result = "Feedback saved";
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
                console.error('Failed to start live coach', error);
                cleanup();
            }
        };

        connect();

        return () => cleanup();
    }, [props.onLogSet, props.onMoveToNextSet, props.onMoveToNextExercise, props.onProvideFeedback, props.activeExercise, props.historyContext, settings.aiVoice, settings.apiKeys?.gemini, cleanup]);

    return null;
};