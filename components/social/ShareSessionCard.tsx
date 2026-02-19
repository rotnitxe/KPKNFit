
import React from 'react';
import { Clock, Flame, Calendar, TrendingUp, Trophy, Activity, Dumbbell } from 'lucide-react';

interface ShareSessionData {
  sessionName: string;
  programName: string;
  tonnage: number;
  duration: number; // minutos
  rpe: number; // 1-10
  prCount: number;
  date: string;
}

const ShareSessionCard: React.FC<{ data: ShareSessionData }> = ({ data }) => {
  // Lógica de color según intensidad (RPE)
  const getTheme = (rpe: number) => {
    if (rpe >= 9) return { 
      bg: "from-rose-950 via-slate-950 to-black", 
      accent: "text-rose-500", 
      border: "border-rose-500/30",
      glow: "shadow-rose-500/40",
      gradientText: "from-rose-400 to-orange-500",
      chartFill: "bg-rose-500"
    };
    if (rpe >= 7) return { 
      bg: "from-amber-950 via-slate-950 to-black", 
      accent: "text-amber-400", 
      border: "border-amber-500/30",
      glow: "shadow-amber-500/40",
      gradientText: "from-amber-300 to-orange-500",
      chartFill: "bg-amber-500"
    };
    return { 
      bg: "from-cyan-950 via-slate-950 to-black", 
      accent: "text-cyan-400", 
      border: "border-cyan-500/30",
      glow: "shadow-cyan-500/40",
      gradientText: "from-cyan-300 to-blue-500",
      chartFill: "bg-cyan-500"
    };
  };

  const theme = getTheme(data.rpe);

  return (
    <div className={`relative w-[375px] h-[667px] overflow-hidden bg-gradient-to-br ${theme.bg} font-sans select-none flex flex-col`}>
      
      {/* 1. Background Texture & Noise */}
      <div className="absolute inset-0 opacity-20 pointer-events-none" 
           style={{ backgroundImage: `radial-gradient(circle at 50% 0%, rgba(255,255,255,0.15), transparent 70%)` }}>
      </div>
      <div className="absolute inset-0 opacity-[0.05] pointer-events-none" 
           style={{ filter: 'contrast(300%) brightness(100%)', backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.8' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }}>
      </div>

      {/* Decorative Grid Lines */}
      <div className="absolute inset-0 pointer-events-none" 
           style={{ backgroundImage: 'linear-gradient(rgba(255, 255, 255, 0.03) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.03) 1px, transparent 1px)', backgroundSize: '60px 60px' }}>
      </div>

      {/* 2. Header */}
      <div className="relative z-10 pt-10 px-6 flex justify-between items-start">
        <div>
          <div className="flex items-center gap-2 mb-2">
            <span className={`h-2 w-2 rounded-full ${theme.accent} bg-current animate-pulse shadow-[0_0_10px_currentColor]`}></span>
            <span className="text-[9px] font-black text-white/60 tracking-[0.25em] uppercase">Session Report</span>
          </div>
          <h1 className="text-3xl font-black text-white italic tracking-tighter uppercase leading-[0.9]">
            {data.sessionName}
          </h1>
          <p className="text-[10px] text-white/40 font-bold uppercase tracking-widest mt-1">{data.programName}</p>
        </div>
        <div className="bg-white/5 backdrop-blur-md border border-white/10 rounded-lg px-3 py-1.5 flex items-center gap-2">
            <Calendar size={12} className="text-white/60" />
            <span className="text-[10px] font-black text-white/90 tracking-wider">{new Date(data.date).toLocaleDateString('es-ES', { day: '2-digit', month: 'short' })}</span>
        </div>
      </div>

      {/* 3. Hero Metric (Explosive Impact) */}
      <div className="flex-1 flex flex-col justify-center items-center relative z-10 my-4">
        {/* Glow Background behind text */}
        <div className={`absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-64 h-64 ${theme.accent} bg-current rounded-full blur-[120px] opacity-20 pointer-events-none`}></div>
        
        <p className="text-[10px] font-black text-white/50 tracking-[0.4em] uppercase mb-[-10px] relative z-20">Volumen Total</p>
        <div className="relative">
          <h2 className={`text-[7rem] leading-none font-black text-transparent bg-clip-text bg-gradient-to-b ${theme.gradientText} tracking-tighter drop-shadow-2xl`}>
            {(data.tonnage / 1000).toFixed(1)}k
          </h2>
          {/* Text Duplicate for Glow/Blur effect */}
          <h2 className={`absolute inset-0 text-[7rem] leading-none font-black ${theme.accent} tracking-tighter blur-xl opacity-50 -z-10`}>
             {(data.tonnage / 1000).toFixed(1)}k
          </h2>
        </div>
        <span className="text-[10px] font-black text-white/40 tracking-[0.5em] uppercase mt-2">KILOGRAMOS</span>

        {/* Minimalist Chart Visualization */}
        <div className="w-full px-12 mt-12 h-12 flex items-end justify-between gap-1.5 opacity-80">
            {[30, 50, 45, 75, 50, 90, 65, 100, 80, 40].map((h, i) => (
                <div key={i} className={`w-full rounded-t-sm bg-gradient-to-t from-white/5 to-white/30`} style={{ height: `${h}%` }}>
                    <div className={`w-full h-1 ${i === 7 ? theme.chartFill : 'bg-transparent'} absolute top-0`}></div>
                </div>
            ))}
        </div>
      </div>

      {/* 4. Stats Grid - Glassmorphism Level God */}
      <div className="relative z-10 px-6 pb-10 space-y-3">
        <div className="grid grid-cols-2 gap-3">
          
          {/* Duration Card */}
          <div className="bg-white/5 backdrop-blur-2xl border border-white/10 rounded-2xl p-4 flex flex-col justify-between group shadow-lg inset-shadow">
            <div className="flex justify-between items-start">
               <span className="text-[8px] font-black text-slate-400 uppercase tracking-widest">Tiempo</span>
               <Clock size={14} className="text-white/40" />
            </div>
            <p className="text-3xl font-black text-white mt-1 tracking-tight">{data.duration}<span className="text-[10px] text-slate-500 ml-1 font-bold align-top">MIN</span></p>
          </div>

          {/* Intensity Card */}
          <div className={`bg-white/5 backdrop-blur-2xl border ${theme.border} rounded-2xl p-4 flex flex-col justify-between relative overflow-hidden`}>
             <div className={`absolute -right-4 -top-4 w-12 h-12 ${theme.accent} bg-current blur-2xl opacity-20`}></div>
            <div className="flex justify-between items-start relative z-10">
               <span className="text-[8px] font-black text-slate-400 uppercase tracking-widest">Intensidad</span>
               <Flame size={14} className={theme.accent} />
            </div>
            <div className="flex items-baseline mt-1 relative z-10">
                <p className="text-3xl font-black text-white tracking-tight">{data.rpe}</p>
                <span className="text-[10px] text-slate-500 ml-1 font-bold">/ 10 RPE</span>
            </div>
          </div>
        </div>

        {/* PR Card (Conditional) */}
        {data.prCount > 0 && (
            <div className="bg-gradient-to-r from-white/10 to-white/5 backdrop-blur-2xl border border-white/10 rounded-2xl p-4 flex items-center justify-between shadow-2xl relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-yellow-500/10 to-transparent opacity-50"></div>
                <div className="relative z-10">
                     <p className="text-[9px] font-black text-yellow-400 uppercase tracking-widest mb-1 flex items-center gap-1.5">
                        <Trophy size={10} strokeWidth={3} /> Récords
                     </p>
                     <p className="text-lg font-bold text-white leading-none">¡{data.prCount} Nuevas Marcas!</p>
                </div>
                <div className="h-10 w-10 rounded-full bg-yellow-500/20 flex items-center justify-center border border-yellow-500/50 shadow-[0_0_15px_rgba(234,179,8,0.3)] relative z-10">
                    <TrendingUp size={20} className="text-yellow-400" />
                </div>
            </div>
        )}
        
        {/* Footer Branding */}
        <div className="pt-6 flex justify-center items-center opacity-60">
             <div className="h-[1px] w-6 bg-white/30 mr-3"></div>
             <div className="flex items-center gap-1.5">
                <Dumbbell size={12} className="text-white" fill="currentColor"/>
                <p className="text-[9px] font-black uppercase tracking-[0.3em] text-white">#YourPrime</p>
             </div>
             <div className="h-[1px] w-6 bg-white/30 ml-3"></div>
        </div>
      </div>

    </div>
  );
};

export default ShareSessionCard;
