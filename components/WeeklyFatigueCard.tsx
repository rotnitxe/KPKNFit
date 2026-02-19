
// components/WeeklyFatigueCard.tsx
import React, { useMemo, useState, useEffect } from 'react';
import { useAppState } from '../contexts/AppContext';
import { calculateHistoricalFatigueData } from '../services/analysisService';
import { calculateCompletedSessionStress } from '../services/fatigueService';
import Card from './ui/Card';
import { ActivityIcon } from './icons';
import { InfoTooltip } from './ui/InfoTooltip';
import { formatLargeNumber } from '../utils/calculations';
import { LineChart, Line, Bar, ComposedChart, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, ReferenceArea, Legend } from 'recharts';

const CustomTooltip = ({ active, payload, label }: any) => {
  if (active && payload && payload.length) {
    return (
      <div className="bg-slate-800/80 backdrop-blur-sm p-3 rounded-lg border border-slate-700 text-sm">
        <p className="font-bold text-white">{label}</p>
        {payload.map((p: any) => (
          <p key={p.dataKey} style={{ color: p.color }}>
            {`${p.name}: ${p.value}${p.unit || ''}`}
          </p>
        ))}
      </div>
    );
  }
  return null;
};


const WeeklyFatigueCard: React.FC = () => {
    const { history, settings, exerciseList } = useAppState();
    const [historicalData, setHistoricalData] = useState<any[]>([]);

    useEffect(() => {
        if (history.length > 0) {
            const data = calculateHistoricalFatigueData(history, settings, exerciseList);
            setHistoricalData(data);
        }
    }, [history, settings, exerciseList]);

    const currentWeekData = useMemo(() => {
        if (historicalData.length === 0) return null;
        return historicalData[historicalData.length - 1];
    }, [historicalData]);

    const getAcwrInterpretation = (acwr: number) => {
        if (acwr < 0.8) return { text: "Muy Fresco / Sub-entrenando", color: "text-sky-400" };
        if (acwr <= 1.3) return { text: "Zona Segura de Progresión", color: "text-green-400" };
        if (acwr < 1.5) return { text: "Zona de Riesgo", color: "text-yellow-400" };
        return { text: "Alto Riesgo de Lesión", color: "text-red-400" };
    };

    if (!currentWeekData) {
        return (
            <Card>
                <h3 className="text-xl font-bold text-white mb-2 flex items-center gap-2"><ActivityIcon /> Análisis de Fatiga</h3>
                <p className="text-sm text-center text-slate-400 py-4">Completa más entrenamientos para ver tu análisis de fatiga y estrés.</p>
            </Card>
        );
    }
    
    const { text: interpretation, color } = getAcwrInterpretation(currentWeekData.acwr);

    return (
        <Card>
            <h3 className="text-xl font-bold text-white mb-3 flex items-center gap-2">
                <ActivityIcon /> Análisis de Fatiga y Estrés
            </h3>
            <div className="space-y-8">
                {/* Systemic Fatigue Section */}
                <div>
                    <h4 className="font-semibold text-slate-200 mb-2 flex items-center gap-1">
                        Fatiga Sistémica <InfoTooltip term="ACWR" />
                    </h4>
                    <div className="glass-card-nested p-4 space-y-4">
                        <div className="flex flex-col sm:flex-row items-center gap-4">
                            <div className="relative w-32 h-32 flex-shrink-0">
                                <svg className="w-full h-full" viewBox="0 0 36 36">
                                    <path className="text-slate-700" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" fill="none" stroke="currentColor" strokeWidth="2.5"></path>
                                </svg>
                                <div className="absolute inset-0 flex flex-col items-center justify-center">
                                    <span className={`text-5xl font-black ${color}`}>{currentWeekData.acwr.toFixed(2)}</span>
                                    <span className="text-xs font-bold text-slate-400">ACWR</span>
                                </div>
                            </div>
                            <div className="text-center sm:text-left flex-grow">
                                <p className={`font-semibold text-lg ${color}`}>{interpretation}</p>
                                <p className="text-xs text-slate-500 mt-1">Este valor indica si tu carga de entrenamiento actual es sostenible o si estás en riesgo de sobreentrenamiento.</p>
                                <div className="grid grid-cols-2 gap-2 text-center mt-2 text-xs">
                                    <div className="bg-slate-800/50 p-2 rounded-lg"><p className="font-bold text-lg text-white">{currentWeekData.acuteLoad}</p><p className="text-slate-400">Carga Aguda (7d)</p></div>
                                    <div className="bg-slate-800/50 p-2 rounded-lg"><p className="font-bold text-lg text-white">{currentWeekData.chronicLoad}</p><p className="text-slate-400">Carga Crónica (28d)</p></div>
                                </div>
                            </div>
                        </div>
                        {historicalData.length > 1 && (
                            <div className="mt-4">
                                <h5 className="text-sm font-semibold text-slate-300 mb-2">Historial de ACWR y Sueño (Últimas 12 semanas)</h5>
                                <ResponsiveContainer width="100%" height={150}>
                                    <ComposedChart data={historicalData} margin={{ top: 5, right: 10, left: -25, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                                        <XAxis dataKey="name" stroke="#94a3b8" fontSize={10} />
                                        <YAxis yAxisId="left" stroke="#38bdf8" fontSize={10} domain={[0, 'dataMax + 0.5']} />
                                        <YAxis yAxisId="right" orientation="right" stroke="#fcd34d" fontSize={10} domain={[1, 5]} />
                                        <Tooltip content={<CustomTooltip />} />
                                        <Legend wrapperStyle={{fontSize: "10px"}} />
                                        <ReferenceArea yAxisId="left" y1={0.8} y2={1.3} fill="#22c55e" fillOpacity={0.1} />
                                        <ReferenceArea yAxisId="left" y1={1.3} y2={1.5} fill="#facc15" fillOpacity={0.1} />
                                        <ReferenceArea yAxisId="left" y1={1.5} y2={10} fill="#f87171" fillOpacity={0.1} />
                                        <Line yAxisId="left" type="monotone" dataKey="acwr" name="ACWR" stroke="#38bdf8" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 6 }} />
                                        <Line yAxisId="right" type="monotone" dataKey="avgSleepQuality" name="Sueño (1-5)" stroke="#fcd34d" strokeWidth={2} dot={{ r: 3 }} activeDot={{ r: 6 }} connectNulls />
                                    </ComposedChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </div>
                </div>

                {/* Articular Stress Section */}
                <div className="pt-4 border-t border-slate-700/50">
                    <h4 className="font-semibold text-slate-200 mb-2 flex items-center gap-1">
                        Estrés Articular <InfoTooltip term="Carga Articular" />
                    </h4>
                    <div className="glass-card-nested p-4 space-y-4">
                        <div className="grid grid-cols-2 gap-3 text-center">
                            <div className="bg-slate-800/50 p-3 rounded-lg">
                                <p className="text-xs text-slate-400 font-semibold uppercase flex items-center justify-center gap-1">Tonelaje Semanal <InfoTooltip term="Tonelaje" /></p>
                                <p className="text-3xl font-bold font-mono text-white mt-1">{formatLargeNumber(currentWeekData.tonnage)}</p>
                                <p className="text-xs text-slate-500">{settings.weightUnit} movidos</p>
                            </div>
                            <div className="bg-slate-800/50 p-3 rounded-lg">
                                <p className="text-xs text-slate-400 font-semibold uppercase flex items-center justify-center gap-1">Intensidad Media <InfoTooltip term="IMR" /></p>
                                <p className="text-3xl font-bold font-mono text-white mt-1">{currentWeekData.avgRMI}%</p>
                                <p className="text-xs text-slate-500">{currentWeekData.avgRMI > 85 ? 'Estrés de alta intensidad' : currentWeekData.avgRMI > 75 ? 'Estrés moderado-alto' : 'Estrés de alto volumen'}</p>
                            </div>
                        </div>
                        {historicalData.length > 1 && (
                            <div className="mt-4">
                                <h5 className="text-sm font-semibold text-slate-300 mb-2">Historial de Estrés (Últimas 12 semanas)</h5>
                                <ResponsiveContainer width="100%" height={200}>
                                    <ComposedChart data={historicalData} margin={{ top: 5, right: 10, left: -15, bottom: 0 }}>
                                        <CartesianGrid strokeDasharray="3 3" stroke="#475569" />
                                        <XAxis dataKey="name" stroke="#94a3b8" fontSize={10} />
                                        <YAxis yAxisId="left" stroke="#8884d8" fontSize={10} tickFormatter={(value) => formatLargeNumber(value)} />
                                        <YAxis yAxisId="right" orientation="right" stroke="#82ca9d" fontSize={10} unit="%" />
                                        <Tooltip content={<CustomTooltip />} />
                                        <Legend wrapperStyle={{fontSize: "12px"}} />
                                        <Bar yAxisId="left" dataKey="tonnage" name="Tonelaje" fill="#8884d8" />
                                        <Line yAxisId="right" type="monotone" dataKey="avgRMI" name="IMR Prom." stroke="#82ca9d" strokeWidth={2} />
                                    </ComposedChart>
                                </ResponsiveContainer>
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </Card>
    );
};

export default WeeklyFatigueCard;