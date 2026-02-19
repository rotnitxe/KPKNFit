
import React from 'react';
import { Clock, Activity, Dumbbell, Trophy, User, Hash, Calendar, BarChart3, ScanLine } from 'lucide-react';

interface ExerciseSummary {
  name: string;
  bestSet: string; // ej: "100kg x 5"
  isPR: boolean;
  muscleGroup: string;
}

interface ShareDetailData {
  sessionName: string;
  duration: number; // minutos
  tonnage: number;
  avgRpe: number;
  date: string;
  exercises: ExerciseSummary[];
  username: string;
  userAvatar?: string;
}

const ShareSessionDetailCard: React.FC<{ data: ShareDetailData }> = ({ data }) => {
  // Configuración de tema basado en intensidad
  const getTheme = (rpe: number) => {
    if (rpe >= 8.5) return {
      accent: "text-amber-400",
      border: "border-amber-500/30",
      bgGradient: "from-amber-950/40",
      badge: "bg-amber-500/10 text-amber-300",
      glow: "shadow-amber-500/20"
    };
    if (rpe >= 7) return {
      accent: "text-emerald-400",
      border: "border-emerald-500/30",
      bgGradient: "from-emerald-950/40",
      badge: "bg-emerald-500/10 text-emerald-300",
      glow: "shadow-emerald-500/20"
    };
    return {
      accent: "text-cyan-400",
      border: "border-cyan-500/30",
      bgGradient: "from-cyan-950/40",
      badge: "bg-cyan-500/10 text-cyan-300",
      glow: "shadow-cyan-500/20"
    };
  };

  const theme = getTheme(data.avgRpe);
  
  // Extraer grupos musculares únicos para las etiquetas
  const uniqueMuscles = Array.from(new Set(data.exercises.map(ex => ex.muscleGroup))).slice(0, 4);

  return (
    <div className="relative w-[375px] h-[667px] overflow-hidden bg-slate-950 font-sans flex flex-col">
      
      {/* 1. Background Tech Grid & Noise */}
      <div className="absolute inset-0 z-0 pointer-events-none" 
           style={{ 
             backgroundImage: 'linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px)', 
             backgroundSize: '30px 30px' 
           }}>
      </div>
      <div className={`absolute top-0 left-0 right-0 h-64 bg-gradient-to-b ${theme.bgGradient} to-transparent opacity-60 z-0`}></div>

      {/* 2. Header (Impacto) */}
      <div className="relative z-10 pt-8 px-6 pb-4">
        <div className="flex items-center gap-2 mb-2 opacity-70">
            <Activity size={14} className={theme.accent} />
            <span className="text-[10px] font-mono uppercase tracking-widest text-white">Mission Report</span>
        </div>
        <h1 className="text-3xl font-black text-white leading-[0.95] uppercase tracking-tighter mb-4">
          {data.sessionName}
        </h1>

        {/* Macro Stats Bar */}
        <div className="flex justify-between items-center bg-white/5 border border-white/10 rounded-lg p-3 backdrop-blur-sm">
            <div className="flex flex-col items-center flex-1 border-r border-white/10">
                <span className="text-[9px] text-slate-400 uppercase font-bold tracking-wider">Duración</span>
                <span className="text-lg font-mono font-bold text-white flex items-center gap-1">
                    {data.duration}<span className="text-[10px] text-slate-500">min</span>
                </span>
            </div>
            <div className="flex flex-col items-center flex-1 border-r border-white/10">
                <span className="text-[9px] text-slate-400 uppercase font-bold tracking-wider">Carga</span>
                <span className="text-lg font-mono font-bold text-white flex items-center gap-1">
                    {(data.tonnage/1000).toFixed(1)}<span className="text-[10px] text-slate-500">t</span>
                </span>
            </div>
             <div className="flex flex-col items-center flex-1">
                <span className="text-[9px] text-slate-400 uppercase font-bold tracking-wider">RPE Avg</span>
                <span className={`text-lg font-mono font-bold ${theme.accent}`}>
                    {data.avgRpe}
                </span>
            </div>
        </div>
      </div>

      {/* 3. The Body (The Receipt) */}
      <div className="flex-1 px-4 relative z-10 overflow-hidden flex flex-col">
          <div className={`flex-1 bg-slate-900/60 backdrop-blur-xl border border-white/10 rounded-t-2xl p-5 shadow-2xl ${theme.glow} flex flex-col`}>
              
              {/* Decorative Top Line */}
              <div className="flex justify-between items-center mb-4 pb-2 border-b border-white/10">
                  <span className="text-[9px] font-mono text-slate-500 uppercase">Ejercicio</span>
                  <span className="text-[9px] font-mono text-slate-500 uppercase">Top Set</span>
              </div>

              {/* Exercise List */}
              <div className="flex-1 space-y-3">
                  {data.exercises.slice(0, 7).map((ex, i) => (
                      <div key={i} className="flex justify-between items-center py-2 border-b border-dashed border-white/10 last:border-0 group">
                          <div className="flex items-center gap-2 overflow-hidden mr-2">
                              <div className="w-1 h-1 rounded-full bg-slate-600 group-hover:bg-white transition-colors"></div>
                              <span className="text-xs font-bold text-slate-200 uppercase truncate">{ex.name}</span>
                              {ex.isPR && (
                                  <Trophy size={10} className="text-yellow-400 flex-shrink-0 animate-pulse" fill="currentColor" />
                              )}
                          </div>
                          <div className={`text-xs font-mono font-bold whitespace-nowrap ${ex.isPR ? 'text-yellow-400' : theme.accent}`}>
                              {ex.bestSet}
                          </div>
                      </div>
                  ))}
                  {data.exercises.length > 7 && (
                      <div className="text-center py-2 text-[10px] text-slate-500 italic font-mono">
                          + {data.exercises.length - 7} ejercicios más...
                      </div>
                  )}
              </div>

              {/* Muscle Context Pills */}
              <div className="mt-6 pt-4 border-t-2 border-white/5">
                  <p className="text-[8px] font-mono text-slate-500 uppercase mb-2">Targeted Systems:</p>
                  <div className="flex flex-wrap gap-2">
                      {uniqueMuscles.map(m => (
                          <span key={m} className={`px-2 py-1 rounded text-[8px] font-black uppercase tracking-wider ${theme.badge}`}>
                              {m}
                          </span>
                      ))}
                  </div>
              </div>

          </div>
          
          {/* Jagged Edge Effect (CSS Clip Path or simple SVG) - Optional visual flair */}
          <div className="h-2 w-full bg-slate-900/60 backdrop-blur-xl border-x border-white/10" 
               style={{ clipPath: 'polygon(0 0, 5% 100%, 10% 0, 15% 100%, 20% 0, 25% 100%, 30% 0, 35% 100%, 40% 0, 45% 100%, 50% 0, 55% 100%, 60% 0, 65% 100%, 70% 0, 75% 100%, 80% 0, 85% 100%, 90% 0, 95% 100%, 100% 0)' }}>
          </div>
      </div>

      {/* 4. Footer (Identity) */}
      <div className="p-6 pt-4 relative z-10 bg-black">
          <div className="flex justify-between items-center">
              <div className="flex items-center gap-3">
                  <div className="w-8 h-8 rounded-full bg-slate-800 border border-white/20 overflow-hidden">
                       {data.userAvatar ? (
                           <img src={data.userAvatar} alt="User" className="w-full h-full object-cover"/>
                       ) : (
                           <div className="w-full h-full flex items-center justify-center text-slate-500"><User size={14}/></div>
                       )}
                  </div>
                  <div>
                      <p className="text-xs font-bold text-white uppercase tracking-wide">{data.username}</p>
                      <p className="text-[8px] font-mono text-slate-500 flex items-center gap-1">
                          <Hash size={8}/> YourPrime Athlete
                      </p>
                  </div>
              </div>

              <div className="text-right">
                  <div className="flex items-center justify-end gap-1 text-slate-400">
                      <ScanLine size={14} />
                      <span className="text-[10px] font-mono font-bold">{data.date}</span>
                  </div>
                  <p className="text-[8px] text-slate-600 uppercase tracking-widest mt-0.5">Verified Data</p>
              </div>
          </div>
      </div>

    </div>
  );
};

export default ShareSessionDetailCard;
