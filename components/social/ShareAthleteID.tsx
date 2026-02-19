
import React from 'react';
import { Zap, Activity, Brain, Award, Hexagon, Fingerprint, MapPin } from 'lucide-react';

interface AthleteIDData {
  username: string;
  athleteType: string; // e.g. "Powerlifter"
  levelTitle: string; // e.g. "Elite"
  avatarUrl?: string;
  cnsStatus: number; // 0-100
  muscleStatus: number; // 0-100
  badges: string[]; // Array of strings e.g. ["Consistency", "Heavy Lifter"]
  gymName?: string;
}

const ShareAthleteID: React.FC<{ data: AthleteIDData }> = ({ data }) => {
  return (
    <div className="relative w-[375px] h-[667px] overflow-hidden bg-black font-sans flex flex-col justify-center items-center p-6 select-none">
      
      {/* 1. Global Background Tech Grid */}
      <div className="absolute inset-0 pointer-events-none" 
           style={{ 
             backgroundImage: 'linear-gradient(rgba(255, 255, 255, 0.05) 1px, transparent 1px), linear-gradient(90deg, rgba(255, 255, 255, 0.05) 1px, transparent 1px)', 
             backgroundSize: '40px 40px' 
           }}>
      </div>
      
      {/* Glow Center Background */}
      <div className="absolute top-1/2 left-1/2 -translate-x-1/2 -translate-y-1/2 w-80 h-80 bg-blue-900/30 rounded-full blur-[100px] pointer-events-none"></div>

      {/* --- THE CARD --- */}
      <div className="relative w-full aspect-[9/15] bg-[#0A0A0F] border border-white/10 rounded-[2rem] overflow-hidden shadow-2xl flex flex-col group transform transition-transform">
          
          {/* Holographic Reflection Overlay */}
          <div className="absolute inset-0 bg-gradient-to-tr from-transparent via-white/[0.03] to-transparent z-20 pointer-events-none skew-x-12 translate-x-[-50%] w-[200%] h-full"></div>
          
          {/* Subtle Noise Texture on Card */}
          <div className="absolute inset-0 opacity-[0.03] pointer-events-none z-0" 
             style={{ filter: 'contrast(150%) brightness(100%)', backgroundImage: `url("data:image/svg+xml,%3Csvg viewBox='0 0 200 200' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='noiseFilter'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='3' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23noiseFilter)'/%3E%3C/svg%3E")` }}>
          </div>

          {/* HUD Tech Corners */}
          <div className="absolute top-3 left-3 w-6 h-6 border-t-2 border-l-2 border-cyan-400 rounded-tl-md z-30 opacity-80"></div>
          <div className="absolute top-3 right-3 w-6 h-6 border-t-2 border-r-2 border-cyan-400 rounded-tr-md z-30 opacity-80"></div>
          <div className="absolute bottom-3 left-3 w-6 h-6 border-b-2 border-l-2 border-cyan-400 rounded-bl-md z-30 opacity-80"></div>
          <div className="absolute bottom-3 right-3 w-6 h-6 border-b-2 border-r-2 border-cyan-400 rounded-br-md z-30 opacity-80"></div>

          {/* Card Header & Avatar */}
          <div className="pt-10 px-6 text-center relative z-10 flex flex-col items-center">
              <div className="inline-flex items-center gap-1.5 mb-4 px-3 py-1 bg-cyan-950/30 border border-cyan-500/30 rounded-full">
                 <Fingerprint size={12} className="text-cyan-400"/>
                 <span className="text-[8px] font-black uppercase tracking-[0.2em] text-cyan-300">Identity Verified</span>
              </div>
              
              {/* Avatar Construction */}
              <div className="relative w-36 h-36 mb-5">
                  {/* Rotating Outer Ring */}
                  <div className="absolute inset-[-6px] rounded-full border border-dashed border-white/20"></div>
                  {/* Static Arc */}
                  <div className="absolute inset-[-2px] rounded-full border-2 border-transparent border-t-cyan-500/80 border-r-cyan-500/40 rotate-12"></div>
                  
                  {/* Image Container */}
                  <div className="w-full h-full rounded-full overflow-hidden border-4 border-[#0F1115] shadow-2xl relative z-10 bg-slate-800">
                    {data.avatarUrl ? (
                         <img src={data.avatarUrl} alt="Profile" className="w-full h-full object-cover" />
                    ) : (
                         <div className="w-full h-full bg-gradient-to-br from-slate-700 to-slate-900 flex items-center justify-center">
                            <span className="text-5xl">👤</span>
                         </div>
                    )}
                  </div>
                  
                  {/* Level Badge Pill */}
                  <div className="absolute -bottom-3 left-1/2 -translate-x-1/2 bg-[#0F1115] border border-cyan-500/50 px-4 py-1 rounded-full z-20 shadow-lg flex items-center gap-1">
                      <div className="w-1.5 h-1.5 bg-cyan-400 rounded-full animate-pulse"></div>
                      <span className="text-[9px] font-black text-white uppercase tracking-widest">{data.levelTitle}</span>
                  </div>
              </div>

              <h2 className="text-3xl font-black text-white uppercase tracking-tight mb-1">{data.username}</h2>
              <div className="flex items-center gap-2 text-[10px] font-bold text-slate-500 uppercase tracking-widest">
                  <span className="text-cyan-400">{data.athleteType}</span>
                  {data.gymName && (
                      <>
                        <span className="text-slate-700">•</span>
                        <span className="flex items-center gap-0.5"><MapPin size={10}/> {data.gymName}</span>
                      </>
                  )}
              </div>
          </div>

          {/* Bio-Battery Stats */}
          <div className="px-8 mt-8 space-y-5 relative z-10 w-full">
              
              {/* CNS Bar (Purple) */}
              <div>
                  <div className="flex justify-between items-end mb-1.5">
                      <span className="text-[8px] font-black text-slate-400 uppercase tracking-widest flex items-center gap-1.5">
                          <Brain size={12} className="text-fuchsia-400" /> Neural System
                      </span>
                      <span className="text-[10px] font-bold text-white font-mono">{data.cnsStatus}%</span>
                  </div>
                  <div className="h-1.5 w-full bg-slate-800/50 rounded-full overflow-hidden border border-white/5">
                      <div className="h-full bg-gradient-to-r from-fuchsia-700 to-fuchsia-400 shadow-[0_0_12px_rgba(192,38,211,0.5)]" 
                           style={{ width: `${data.cnsStatus}%` }}></div>
                  </div>
              </div>

              {/* Muscle Bar (Green) */}
              <div>
                  <div className="flex justify-between items-end mb-1.5">
                      <span className="text-[8px] font-black text-slate-400 uppercase tracking-widest flex items-center gap-1.5">
                          <Activity size={12} className="text-emerald-400" /> Bio-Structure
                      </span>
                      <span className="text-[10px] font-bold text-white font-mono">{data.muscleStatus}%</span>
                  </div>
                  <div className="h-1.5 w-full bg-slate-800/50 rounded-full overflow-hidden border border-white/5">
                      <div className="h-full bg-gradient-to-r from-emerald-700 to-emerald-400 shadow-[0_0_12px_rgba(16,185,129,0.5)]" 
                           style={{ width: `${data.muscleStatus}%` }}></div>
                  </div>
              </div>
          </div>

          {/* Badges Grid */}
          <div className="mt-auto p-6 bg-white/[0.02] border-t border-white/5 backdrop-blur-sm w-full">
              <p className="text-[8px] font-black text-slate-600 uppercase tracking-[0.2em] mb-4 text-center">Achievements Unlocked</p>
              <div className="flex justify-center gap-6">
                  {data.badges.map((badge, i) => (
                      <div key={i} className="flex flex-col items-center gap-2 group">
                          <div className="w-10 h-10 flex items-center justify-center relative">
                             <Hexagon size={42} className="text-slate-800 fill-slate-800 absolute inset-0 z-0" strokeWidth={1} />
                             <Hexagon size={42} className="text-white/10 absolute inset-0 z-10" strokeWidth={1} />
                             <Award size={18} className="text-yellow-400 relative z-20 drop-shadow-md" />
                          </div>
                          <span className="text-[7px] font-bold text-slate-400 uppercase tracking-wider text-center max-w-[60px] leading-tight">{badge}</span>
                      </div>
                  ))}
                  {data.badges.length === 0 && <span className="text-[10px] text-slate-600 italic">No badges yet</span>}
              </div>
          </div>
      </div>

      {/* Bottom Branding */}
      <div className="mt-6 flex items-center gap-2 opacity-50">
          <Zap size={14} className="text-white" fill="currentColor"/>
          <span className="text-[10px] font-black text-white uppercase tracking-[0.3em]">Powered by YourPrime</span>
      </div>

    </div>
  );
};

export default ShareAthleteID;
