import React, { useState, useRef, useEffect } from 'react';
import { Program, WorkoutLog, ChatMessage, Settings, GenerateContentResponse } from '../types';
import { getCoachChatResponseStream } from '../services/aiService';
import Button from './ui/Button';
import Card from './ui/Card';
import { CoachIcon, TrashIcon } from './icons';
import useStorage from '../hooks/useLocalStorage';

interface ChatWithCoachProps {
  programs: Program[];
  history: WorkoutLog[];
  isOnline: boolean;
  settings: Settings;
}

const ChatWithCoach: React.FC<ChatWithCoachProps> = ({ programs, history, isOnline, settings }) => {
  const [messages, setMessages] = useStorage<ChatMessage[]>('coach-chat-history', []);
  const [input, setInput] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  };

  useEffect(scrollToBottom, [messages]);

  const handleClearHistory = () => {
    if (window.confirm('¿Estás seguro de que quieres borrar el historial de este chat?')) {
        setMessages([]);
    }
  };

  const handleSend = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || isLoading || !isOnline) return;

    const userMessage: ChatMessage = { role: 'user', parts: [{ text: input }] };
    setMessages(prev => [...prev, userMessage]);
    setInput('');
    setIsLoading(true);

    try {
      const stream: AsyncGenerator<GenerateContentResponse> = await getCoachChatResponseStream(input, messages, programs, history, settings);
      
      let newBotMessage: ChatMessage = { role: 'model', parts: [{ text: '' }] };
      setMessages(prev => [...prev, newBotMessage]);

      for await (const chunk of stream) {
        const chunkText = chunk.text;
        newBotMessage.parts[0].text += chunkText;
        setMessages(prev => [...prev.slice(0, -1), newBotMessage]);
      }
    } catch (error) {
      console.error("Error en el chat con el coach:", error);
      const errorMessage: ChatMessage = { role: 'model', parts: [{ text: "Lo siento, ha ocurrido un error al contactar con el Coach IA." }] };
      setMessages(prev => [...prev, errorMessage]);
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <Card className="h-full flex flex-col">
        <div className="flex justify-between items-center mb-4">
            <h2 className="text-2xl font-bold text-white flex items-center gap-2"><CoachIcon /> Habla con tu Coach</h2>
            {messages.length > 0 && (
                <button onClick={handleClearHistory} title="Borrar historial" className="p-2 text-slate-500 hover:text-red-400 transition-colors">
                    <TrashIcon size={18} />
                </button>
            )}
        </div>
        <div className="flex-grow bg-slate-950/70 p-4 rounded-lg overflow-y-auto h-96 flex flex-col gap-4">
            {messages.length === 0 && (
                <div className="m-auto text-center text-slate-400">
                    <p>¡Hola! Soy tu Coach IA.</p>
                    <p className="text-sm">Pregúntame cualquier cosa sobre tus programas o tu progreso.</p>
                </div>
            )}
            {messages.map((msg, index) => (
                <div key={index} className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}>
                    <div className={`max-w-xs md:max-w-md p-3 rounded-lg ${msg.role === 'user' ? 'bg-[var(--primary-color-600)] text-white' : 'bg-slate-800 text-slate-200'}`}>
                        <div className="prose prose-sm prose-invert" dangerouslySetInnerHTML={{ __html: msg.parts[0].text.replace(/\n/g, '<br />') }} />
                    </div>
                </div>
            ))}
             <div ref={messagesEndRef} />
        </div>
        <form onSubmit={handleSend} className="mt-4 flex gap-2">
            <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                placeholder={!isOnline ? "Sin conexión" : "Escribe tu pregunta..."}
                disabled={isLoading || !isOnline}
                className="flex-grow bg-slate-800 border border-slate-700 rounded-md px-4 py-2 focus:outline-none focus:ring-2 focus:ring-[var(--primary-color-500)] disabled:opacity-50"
            />
            <Button type="submit" isLoading={isLoading} disabled={!input.trim() || !isOnline}>
                Enviar
            </Button>
        </form>
    </Card>
  );
};

export default ChatWithCoach;