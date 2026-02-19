import React, { useState } from 'react';
import { useAppContext } from '../contexts/AppContext';
import { WorkoutLog, CompletedExercise } from '../types';
import { XIcon, TrophyIcon, TargetIcon, ActivityIcon, StarIcon, SaveIcon } from './icons';

const SpecialSessionLoggerModal: React.FC = () => {
    const { isSpecialSessionModalOpen, setIsSpecialSessionModalOpen, specialSessionData, handleSaveLoggedWorkout, addToast } = useAppContext();
    
    // Powerlifting State
    const [fed, setFed] = useState('IPF');
    const [bw, setBw] = useState('');
    const [ranking, setRanking] = useState('');
    const [sq, setSq] = useState([{w:'', v:true}, {w:'', v:true}, {w:'', v:true}]);
    const [bp, setBp] = useState([{w:'', v:true}, {w:'', v:true}, {w:'', v:true}]);
    const [dl, setDl] = useState([{w:'', v:true}, {w:'', v:true}, {w:'', v:true}]);

    // General States
    const [notes, setNotes] = useState('');
    const [tests, setTests] = useState([{ name: '', result: '' }]);

    if (!isSpecialSessionModalOpen || !specialSessionData) return null;

    const { session, programId, programName } = specialSessionData;
    const descData = JSON.parse(session.description || '{}');
    const type = descData.type;

    const handleClose = () => {
        if(window.confirm('¿Descartar registro?')) setIsSpecialSessionModalOpen(false);
    };

    const handleSave = () => {
        let postTitle = session.name;
        let postSummary = notes;
        let completedExs: CompletedExercise[] = [];

        if (type === 'powerlifting_comp') {
            const sqMax = Math.max(0, ...sq.filter(a => a.v && a.w).map(a => parseFloat(a.w)));
            const bpMax = Math.max(0, ...bp.filter(a => a.v && a.w).map(a => parseFloat(a.w)));
            const dlMax = Math.max(0, ...dl.filter(a => a.v && a.w).map(a => parseFloat(a.w)));
            const total = sqMax + bpMax + dlMax;

            postTitle = `🏆 COMPETICIÓN POWERLIFTING (${fed})`;
            postSummary = `Peso Corporal: ${bw}kg\nSentadilla: ${sqMax}kg\nPress Banca: ${bpMax}kg\nPeso Muerto: ${dlMax}kg\nTOTAL: ${total}kg\nPosición: ${ranking}\n\n${notes}`;
        } else if (type === 'bodybuilding_comp') {
            postTitle = `✨ COMPETICIÓN DE CULTURISMO`;
            postSummary = `Categoría: ${fed}\nPosición: ${ranking}\n\n${notes}`;
        } else if (type === '1rm_test') {
            postTitle = `🎯 TESTEO DE 1RM`;
            let results = tests.filter(t => t.name && t.result).map(t => `${t.name}: ${t.result}kg`).join('\n');
            postSummary = `Resultados del Test:\n${results}\n\n${notes}`;
        } else {
            postTitle = `🏅 PRUEBA FÍSICA`;
            let results = tests.filter(t => t.name && t.result).map(t => `${t.name}: ${t.result}`).join('\n');
            postSummary = `Resultados:\n${results}\n\nEvaluación Final: ${ranking}\n${notes}`;
        }

        const log: WorkoutLog = {
            id: crypto.randomUUID(),
            programId, programName, sessionId: session.id, sessionName: session.name,
            date: new Date().toISOString(), fatigueLevel: 10, mentalClarity: 10,
            completedExercises: completedExs, isCustomPost: true, postTitle, postSummary
        };

        handleSaveLoggedWorkout(log);
        setIsSpecialSessionModalOpen(false);
        addToast("Evento registrado en tu historial con éxito", "success");
    };

    return (
        <div className="fixed inset-0 z-[9999] bg-black/90 backdrop-blur-xl flex flex-col font-sans text-white overflow-hidden animate-fade-in">
            <div className="p-6 flex justify-between items-center border-b border-white/10 bg-[#050505] shrink-0">
                <div className="flex items-center gap-3">
                    {type === 'powerlifting_comp' ? <TrophyIcon size={24} className="text-yellow-500"/> : <StarIcon size={24} className="text-blue-500"/>}
                    <h2 className="text-xl font-black uppercase tracking-tight leading-none">{session.name}</h2>
                </div>
                <button onClick={handleClose} className="p-2 bg-white/10 rounded-full hover:bg-red-500 transition-colors"><XIcon size={20}/></button>
            </div>

            <div className="flex-1 overflow-y-auto custom-scrollbar p-6 pb-32">
                <div className="max-w-md mx-auto space-y-8">
                    
                    {type === 'powerlifting_comp' && (
                        <div className="space-y-6 animate-slide-up">
                            <div className="grid grid-cols-2 gap-4">
                                <div>
                                    <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Federación</label>
                                    <select value={fed} onChange={e=>setFed(e.target.value)} className="w-full bg-[#111] border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold">
                                        <option>IPF</option><option>WRPF</option><option>IPL</option><option>USAPL</option><option>Otra</option>
                                    </select>
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Peso Corporal (kg)</label>
                                    <input type="number" value={bw} onChange={e=>setBw(e.target.value)} className="w-full bg-[#111] border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold" placeholder="Ej: 82.5"/>
                                </div>
                            </div>

                            {/* Intentos Componente Reutilizable */}
                            {[ {l:'Sentadilla', s:sq, f:setSq}, {l:'Press Banca', s:bp, f:setBp}, {l:'Peso Muerto', s:dl, f:setDl} ].map((lift, i) => (
                                <div key={i} className="bg-[#111] p-5 rounded-2xl border border-white/5">
                                    <h4 className="text-sm font-black uppercase text-white mb-4">{lift.l}</h4>
                                    <div className="flex gap-2">
                                        {lift.s.map((att, aIdx) => (
                                            <div key={aIdx} className="flex-1 flex flex-col gap-2">
                                                <span className="text-[9px] font-bold text-center text-gray-500 uppercase">Intento {aIdx+1}</span>
                                                <input type="number" value={att.w} onChange={e=>{const n=[...lift.s]; n[aIdx].w=e.target.value; lift.f(n);}} className="w-full bg-black border border-white/10 p-2 rounded-lg text-center font-bold text-sm outline-none focus:border-yellow-500" placeholder="kg"/>
                                                <button onClick={()=>{const n=[...lift.s]; n[aIdx].v=!n[aIdx].v; lift.f(n);}} className={`w-full py-1.5 rounded text-[10px] font-black uppercase transition-colors ${att.v ? 'bg-emerald-500/20 text-emerald-400' : 'bg-red-500/20 text-red-400'}`}>
                                                    {att.v ? 'Válido' : 'Nulo'}
                                                </button>
                                            </div>
                                        ))}
                                    </div>
                                </div>
                            ))}

                            <div>
                                <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Posición o Medalla</label>
                                <input type="text" value={ranking} onChange={e=>setRanking(e.target.value)} className="w-full bg-[#111] border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold" placeholder="Ej: 1er Lugar / Medalla de Oro"/>
                            </div>
                        </div>
                    )}

                    {(type === '1rm_test' || type === 'admission_test') && (
                        <div className="bg-[#111] p-5 rounded-2xl border border-white/5 space-y-4 animate-slide-up">
                            <h4 className="text-sm font-black uppercase text-white mb-2">Registrar Pruebas</h4>
                            {tests.map((t, i) => (
                                <div key={i} className="flex gap-2">
                                    <input type="text" value={t.name} onChange={e=>{const n=[...tests]; n[i].name=e.target.value; setTests(n);}} className="flex-1 bg-black border border-white/10 p-3 rounded-xl text-xs font-bold outline-none" placeholder={type === '1rm_test' ? 'Ejercicio (Ej: Squat)' : 'Prueba (Ej: Dominadas)'}/>
                                    <input type="text" value={t.result} onChange={e=>{const n=[...tests]; n[i].result=e.target.value; setTests(n);}} className="w-24 bg-black border border-white/10 p-3 rounded-xl text-xs font-bold outline-none text-center" placeholder={type === '1rm_test' ? 'kg' : 'Reps/Tiempo'}/>
                                </div>
                            ))}
                            <button onClick={()=>setTests([...tests, {name:'', result:''}])} className="w-full py-3 border border-dashed border-white/20 rounded-xl text-[10px] font-black text-gray-400 uppercase tracking-widest hover:text-white">+ Añadir Prueba</button>
                            
                            {type === 'admission_test' && (
                                <div className="pt-4">
                                    <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Resultado Final</label>
                                    <input type="text" value={ranking} onChange={e=>setRanking(e.target.value)} className="w-full bg-black border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold" placeholder="Ej: APTO / APROBADO"/>
                                </div>
                            )}
                        </div>
                    )}

                    {type === 'bodybuilding_comp' && (
                        <div className="space-y-6 animate-slide-up">
                            <div className="bg-[#111] p-5 rounded-2xl border border-white/5 space-y-4">
                                <div>
                                    <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Categoría</label>
                                    <input type="text" value={fed} onChange={e=>setFed(e.target.value)} className="w-full bg-black border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold" placeholder="Ej: Men's Physique, Classic..."/>
                                </div>
                                <div>
                                    <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Posición / Ranking</label>
                                    <input type="text" value={ranking} onChange={e=>setRanking(e.target.value)} className="w-full bg-black border border-white/10 p-3 rounded-xl outline-none mt-1 font-bold" placeholder="Ej: 2do Lugar, Top 5..."/>
                                </div>
                            </div>
                        </div>
                    )}

                    <div>
                        <label className="text-[9px] font-black uppercase text-gray-500 tracking-widest">Notas o Feedback de Jueces</label>
                        <textarea value={notes} onChange={e=>setNotes(e.target.value)} className="w-full bg-[#111] border border-white/10 p-4 rounded-xl outline-none mt-1 text-sm text-gray-300 min-h-[100px]" placeholder="Escribe tus impresiones del evento..."></textarea>
                    </div>

                </div>
            </div>

            <div className="p-6 bg-gradient-to-t from-black via-black to-transparent absolute bottom-0 left-0 right-0">
                <button onClick={handleSave} className="w-full py-4 bg-white text-black rounded-2xl font-black text-xs uppercase tracking-[0.2em] shadow-[0_0_40px_rgba(255,255,255,0.2)] hover:scale-[1.02] transition-transform flex justify-center items-center gap-2">
                    <SaveIcon size={18}/> Guardar Resultados
                </button>
            </div>
        </div>
    );
};

export default SpecialSessionLoggerModal;