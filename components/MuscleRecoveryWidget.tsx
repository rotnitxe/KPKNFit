// components/MuscleRecoveryWidget.tsx
import React, { useState, useEffect, useRef } from 'react';
import { useAppState, useAppDispatch } from '../contexts/AppContext';
import { BatteryAuditLog } from '../services/auge';
import { calculateGlobalBatteriesAsync } from '../services/computeWorkerService';
import { getCachedAdaptiveData, AugeAdaptiveCache } from '../services/augeAdaptiveService';
import AugeDeepView, { SelfImprovementScore } from './ui/AugeDeepView';
import { ActivityIcon, BrainIcon, SettingsIcon, XIcon, ZapIcon, InfoIcon, TargetIcon, LayersIcon } from './icons';
import Modal from './ui/Modal';
import SkeletonLoader from './ui/SkeletonLoader';

const BatteryRing: React.FC<{ label: string; value: number; colorClass: string; icon: React.ReactNode }> = ({ label, value, colorClass, icon }) => {
    const isCritical = value < 30;
    return (
        <div className="flex flex-col items-center justify-center p-3 bg-[#111] border border-white/5 rounded-2xl relative overflow-hidden group">
            <div className={`absolute inset-0 opacity-10 transition-opacity group-hover:opacity-20 ${colorClass}`} style={{ backgroundColor: 'currentColor' }}></div>
            <div className="relative z-10 flex items-center justify-center w-14 h-14 mb-2">
                <svg className="w-full h-full transform -rotate-90 drop-shadow-xl" viewBox="0 0 36 36">
                    <path className="text-black" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="3" />
                    <path className={`${colorClass} transition-all duration-1000 ${isCritical ? 'animate-pulse' : ''}`} d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" strokeWidth="3" strokeDasharray={`${value}, 100`} strokeLinecap="round" />
                </svg>
                <div className="absolute inset-0 flex flex-col items-center justify-center">
                    {icon}
                </div>
            </div>
            <span className={`text-xl font-black tracking-tighter leading-none ${colorClass}`}>{value}%</span>
            <span className="text-[8px] text-zinc-500 font-black uppercase tracking-widest mt-1">{label}</span>
        </div>
    );
};

const AuditList: React.FC<{ logs: BatteryAuditLog[]; title: string; color: string }> = ({ logs, title, color }) => (
    <div className="mb-6">
        <h4 className={`text-[10px] font-black uppercase tracking-widest mb-3 flex items-center gap-2 ${color}`}><LayersIcon size={12}/> Detalles de {title}</h4>
        {logs.length === 0 ? (
            <div className="text-[10px] text-zinc-600 bg-black p-3 rounded-xl border border-white/5 text-center">Batería sin desgaste reciente.</div>
        ) : (
            <div className="space-y-2">
                {logs.map((log, i) => (
                    <div key={i} className="flex justify-between items-center bg-[#0a0a0a] p-3 rounded-xl border border-white/5">
                        <div className="flex items-center gap-3">
                            <span className="text-base">{log.icon}</span>
                            <span className="text-[10px] font-bold text-zinc-300 uppercase tracking-wide">{log.label}</span>
                        </div>
                        <span className={`text-[10px] font-black font-mono px-2 py-1 rounded bg-black border ${log.type === 'workout' || log.type === 'penalty' ? 'text-red-400 border-red-900/50' : log.type === 'bonus' ? 'text-emerald-400 border-emerald-900/50' : 'text-blue-400 border-blue-900/50'}`}>
                            {log.val}{typeof log.val === 'number' ? '%' : ''}
                        </span>
                    </div>
                ))}
            </div>
        )}
    </div>
);

const SystemBatteryWidget: React.FC = () => {
    const { history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading } = useAppState();
    const { setSettings, addToast } = useAppDispatch();
    
    const [isAuditModalOpen, setIsAuditModalOpen] = useState(false);
    const [isCalibrating, setIsCalibrating] = useState(false);
    const [auditTab, setAuditTab] = useState<'logs' | 'precision'>('logs');
    
    const [calibCns, setCalibCns] = useState(0);
    const [calibMusc, setCalibMusc] = useState(0);
    const [calibSpinal, setCalibSpinal] = useState(0);

    const [batteries, setBatteries] = useState<Awaited<ReturnType<typeof calculateGlobalBatteriesAsync>> | null>(null);
    const [adaptiveCache, setAdaptiveCache] = useState<AugeAdaptiveCache | null>(null);
    const versionRef = useRef(0);

    useEffect(() => {
        if (isAppLoading || !history) { setBatteries(null); return; }
        const v = ++versionRef.current;
        calculateGlobalBatteriesAsync(history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList)
            .then(result => { if (versionRef.current === v) setBatteries(result); })
            .catch(() => {});
    }, [history, sleepLogs, dailyWellbeingLogs, nutritionLogs, settings, exerciseList, isAppLoading]);

    useEffect(() => {
        setAdaptiveCache(getCachedAdaptiveData());
    }, []);

    if (!batteries) return <SkeletonLoader lines={3} />;

    const openCalibration = () => {
        setCalibCns(batteries.cns);
        setCalibMusc(batteries.muscular);
        setCalibSpinal(batteries.spinal);
        setIsCalibrating(true);
    };

    const handleSaveCalibration = () => {
        const cnsDelta = calibCns - (batteries.cns - (settings.batteryCalibration?.cnsDelta || 0));
        const muscularDelta = calibMusc - (batteries.muscular - (settings.batteryCalibration?.muscularDelta || 0));
        const spinalDelta = calibSpinal - (batteries.spinal - (settings.batteryCalibration?.spinalDelta || 0));
        
        setSettings({
            batteryCalibration: { cnsDelta, muscularDelta, spinalDelta, lastCalibrated: new Date().toISOString() }
        });
        addToast("Sistemas biológicos recalibrados", "success");
        setIsCalibrating(false);
    };

    return (
        <>
            {/* WIDGET HOME */}
            <div onClick={() => setIsAuditModalOpen(true)} className="bg-[#0a0a0a] border border-[#222] rounded-[2rem] p-5 cursor-pointer hover:border-white/20 transition-all shadow-xl relative overflow-hidden group">
                <div className="absolute -right-10 -bottom-10 opacity-5 group-hover:opacity-10 transition-opacity"><ActivityIcon size={120} /></div>
                
                <div className="flex justify-between items-center mb-5 relative z-10">
                    <div>
                        <h3 className="text-[10px] font-black text-white uppercase tracking-[0.3em] flex items-center gap-2"><ZapIcon size={12} className="text-yellow-400"/> Telemetría AUGE <span className="sr-only">Recovery</span></h3>
                        <p className="text-[8px] text-zinc-500 font-bold uppercase tracking-widest mt-1">Sistemas Biológicos</p>
                    </div>
                    <button className="text-[8px] bg-[#111] border border-[#333] text-zinc-400 px-3 py-1.5 rounded-full font-black uppercase tracking-widest hover:text-white transition-colors">Auditar</button>
                </div>

                <div className="grid grid-cols-3 gap-3 relative z-10">
                    <BatteryRing label="SNC" value={batteries.cns} colorClass="text-sky-400" icon={<BrainIcon size={16} className="text-sky-400 drop-shadow-[0_0_8px_currentColor]"/>} />
                    <BatteryRing label="Músculo" value={batteries.muscular} colorClass="text-rose-400" icon={<ActivityIcon size={16} className="text-rose-400 drop-shadow-[0_0_8px_currentColor]"/>} />
                    <BatteryRing label="Axial" value={batteries.spinal} colorClass="text-amber-400" icon={<TargetIcon size={16} className="text-amber-400 drop-shadow-[0_0_8px_currentColor]"/>} />
                </div>
                
                <div className="mt-4 pt-4 border-t border-white/5 relative z-10 flex gap-3 items-start">
                    <InfoIcon size={16} className="text-zinc-500 shrink-0 mt-0.5" />
                    <p className="text-[9px] text-zinc-400 font-medium leading-relaxed italic">"{batteries.verdict}"</p>
                </div>

                {adaptiveCache && adaptiveCache.banister && (
                    <div className="mt-3 pt-3 border-t border-white/5 relative z-10 flex items-start gap-2">
                        <ZapIcon size={12} className="text-yellow-400 mt-0.5 shrink-0" />
                        <p className="text-[9px] text-zinc-300 font-medium italic">{adaptiveCache.banister.verdict || 'Fitness vs fatiga: sin datos suficientes.'}</p>
                    </div>
                )}
            </div>

            {adaptiveCache && (
                <div className="mt-3" onClick={e => e.stopPropagation()}>
                    <AugeDeepView
                        cache={adaptiveCache}
                        showSections={['gp', 'bayesian', 'banister']}
                        compact={true}
                    />
                </div>
            )}

            {/* MODAL AUDITORÍA (RECIBO DE BANCO) */}
            <Modal isOpen={isAuditModalOpen} onClose={() => setIsAuditModalOpen(false)} title="Auditoría de Sistemas" useCustomContent={true}>
                <div className="bg-[#050505] w-full max-w-md mx-auto h-[85vh] sm:h-[700px] flex flex-col text-white">
                    <div className="flex justify-between items-center p-5 border-b border-[#222] shrink-0 bg-black relative z-20">
                        <h2 className="text-sm font-black uppercase tracking-[0.2em] flex items-center gap-2"><SettingsIcon size={16} className="text-white"/> Diagnóstico</h2>
                        <button onClick={() => setIsAuditModalOpen(false)} className="text-zinc-500 hover:text-white transition-colors p-1 bg-zinc-900 rounded-full"><XIcon size={16}/></button>
                    </div>

                    {!isCalibrating && (
                        <div className="flex border-b border-[#222] shrink-0">
                            <button onClick={() => setAuditTab('logs')} className={`flex-1 py-3 text-[10px] font-black uppercase tracking-widest transition-colors ${auditTab === 'logs' ? 'text-white border-b-2 border-white' : 'text-zinc-500 hover:text-zinc-300'}`}>Diagnóstico</button>
                            <button onClick={() => setAuditTab('precision')} className={`flex-1 py-3 text-[10px] font-black uppercase tracking-widest transition-colors ${auditTab === 'precision' ? 'text-yellow-400 border-b-2 border-yellow-400' : 'text-zinc-500 hover:text-zinc-300'}`}>Precisión AUGE</button>
                        </div>
                    )}

                    <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
                        {isCalibrating ? (
                            <div className="bg-[#111] border border-blue-500/30 p-5 rounded-2xl space-y-6 animate-fade-in shadow-[0_0_30px_rgba(59,130,246,0.1)]">
                                <h3 className="text-xs font-black uppercase tracking-widest text-blue-400 text-center">Sobreescritura Manual</h3>
                                <p className="text-[9px] text-zinc-400 text-center px-4">Si las matemáticas no coinciden con cómo te sientes hoy, ajusta las barras. El sistema aprenderá de tus correcciones (Deltas).</p>
                                
                                <div className="space-y-5">
                                    <div>
                                        <div className="flex justify-between text-[10px] font-black uppercase tracking-widest mb-2"><span className="text-sky-400">SNC</span><span>{calibCns}%</span></div>
                                        <input type="range" min="0" max="100" value={calibCns} onChange={e => setCalibCns(parseInt(e.target.value))} className="w-full accent-sky-400 h-2 bg-zinc-900 rounded-lg appearance-none" />
                                    </div>
                                    <div>
                                        <div className="flex justify-between text-[10px] font-black uppercase tracking-widest mb-2"><span className="text-rose-400">Muscular</span><span>{calibMusc}%</span></div>
                                        <input type="range" min="0" max="100" value={calibMusc} onChange={e => setCalibMusc(parseInt(e.target.value))} className="w-full accent-rose-400 h-2 bg-zinc-900 rounded-lg appearance-none" />
                                    </div>
                                    <div>
                                        <div className="flex justify-between text-[10px] font-black uppercase tracking-widest mb-2"><span className="text-amber-400">Axial / Espinal</span><span>{calibSpinal}%</span></div>
                                        <input type="range" min="0" max="100" value={calibSpinal} onChange={e => setCalibSpinal(parseInt(e.target.value))} className="w-full accent-amber-400 h-2 bg-zinc-900 rounded-lg appearance-none" />
                                    </div>
                                </div>
                                <div className="flex gap-2 pt-2">
                                    <button onClick={() => setIsCalibrating(false)} className="flex-1 py-3 rounded-xl text-[10px] font-black uppercase tracking-widest bg-zinc-900 text-zinc-400 hover:text-white">Cancelar</button>
                                    <button onClick={handleSaveCalibration} className="flex-1 py-3 rounded-xl text-[10px] font-black uppercase tracking-widest bg-blue-600 text-white hover:bg-blue-500 shadow-lg">Aplicar Delta</button>
                                </div>
                            </div>
                        ) : auditTab === 'logs' ? (
                            <>
                                <AuditList logs={batteries.auditLogs.cns} title="Sistema Nervioso Central (SNC)" color="text-sky-400" />
                                <AuditList logs={batteries.auditLogs.muscular} title="Recuperación Muscular" color="text-rose-400" />
                                <AuditList logs={batteries.auditLogs.spinal} title="Estructura Axial (Espinal)" color="text-amber-400" />
                                
                                <div className="pt-4 border-t border-white/10 flex justify-center">
                                    <button onClick={openCalibration} className="w-full bg-[#111] border border-white/10 text-white px-6 py-4 rounded-xl text-[10px] font-black uppercase tracking-[0.2em] hover:bg-white hover:text-black transition-all flex items-center justify-center gap-2">
                                        <ActivityIcon size={14} /> Calibrar mi estado real
                                    </button>
                                </div>
                            </>
                        ) : (
                            <div className="space-y-6 animate-fade-in">
                                {adaptiveCache?.selfImprovement ? (
                                    <>
                                        <div className="bg-[#111] border border-yellow-500/20 p-5 rounded-2xl">
                                            <h4 className="text-[10px] font-black uppercase tracking-widest text-yellow-400 mb-4 flex items-center gap-2"><ZapIcon size={12}/> Score de Precisión</h4>
                                            <SelfImprovementScore
                                                score={adaptiveCache.selfImprovement.overall_prediction_score}
                                                trend={adaptiveCache.selfImprovement.improvement_trend}
                                                recommendations={adaptiveCache.selfImprovement.recommendations}
                                            />
                                        </div>

                                        {adaptiveCache.selfImprovement.accuracy_by_system.length > 0 && (
                                            <div className="bg-[#111] border border-white/5 p-5 rounded-2xl">
                                                <h4 className="text-[10px] font-black uppercase tracking-widest text-zinc-400 mb-4">Precisión por Sistema</h4>
                                                <div className="space-y-3">
                                                    {adaptiveCache.selfImprovement.accuracy_by_system.map(sys => (
                                                        <div key={sys.system} className="flex justify-between items-center">
                                                            <span className="text-[10px] font-bold text-zinc-300 uppercase">{sys.system}</span>
                                                            <div className="flex items-center gap-3">
                                                                <span className="text-[9px] text-zinc-500 font-mono">R²={sys.r_squared.toFixed(2)}</span>
                                                                <span className="text-[9px] text-zinc-500 font-mono">MAE={sys.mae.toFixed(1)}</span>
                                                                <span className="text-[10px] font-bold text-zinc-300">{sys.sample_size} obs</span>
                                                            </div>
                                                        </div>
                                                    ))}
                                                </div>
                                            </div>
                                        )}
                                    </>
                                ) : (
                                    <div className="text-center py-12">
                                        <BrainIcon size={32} className="text-zinc-700 mx-auto mb-4" />
                                        <p className="text-[10px] text-zinc-500 font-bold uppercase tracking-widest">AUGE aún no tiene datos de auto-mejora</p>
                                        <p className="text-[9px] text-zinc-600 mt-2">Completa sesiones y reporta tu estado para que AUGE aprenda.</p>
                                    </div>
                                )}
                            </div>
                        )}
                    </div>
                </div>
            </Modal>
        </>
    );
};

export default SystemBatteryWidget;