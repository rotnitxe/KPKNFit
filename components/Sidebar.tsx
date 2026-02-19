
import React from 'react';
import { View } from '../types';
import { XIcon, HomeIcon, SettingsIcon, CoachIcon, TrendingUpIcon, BookOpenIcon, DumbbellIcon, ClipboardListIcon, BodyIcon, FlaskConical, UtensilsIcon } from './icons';

interface SidebarProps {
  isOpen: boolean;
  onClose: () => void;
  navigate: (view: View) => void;
}

const Sidebar: React.FC<SidebarProps> = ({ isOpen, onClose, navigate }) => {
  
  const navItems: { view: View; label: string; icon: React.FC<any> }[] = [
    { view: 'home', label: 'Home', icon: HomeIcon },
    { view: 'your-lab', label: 'YourLab', icon: FlaskConical },
    { view: 'tasks', label: 'Tareas', icon: ClipboardListIcon },
    { view: 'progress', label: 'Progreso', icon: TrendingUpIcon },
    { view: 'nutrition', label: 'Nutrición', icon: UtensilsIcon },
    { view: 'coach', label: 'Coach IA', icon: CoachIcon },
    { view: 'settings', label: 'Ajustes', icon: SettingsIcon },
  ];

  return (
    <>
      <div 
        className={`fixed inset-0 bg-black z-40 transition-opacity duration-300 md:hidden ${isOpen ? 'bg-opacity-50 backdrop-blur-sm' : 'bg-opacity-0 pointer-events-none'}`}
        onClick={onClose}
      />
      <div 
        className={`fixed top-0 left-0 h-full w-64 z-50 transform transition-transform duration-300 ease-in-out bg-slate-900/80 backdrop-blur-lg border-r border-slate-700/50 ${isOpen ? 'translate-x-0' : '-translate-x-full'}`}
      >
        <div className="p-4 flex justify-between items-center border-b border-white/10">
          <h2 className="text-xl font-bold">KPKN</h2>
          <button onClick={onClose} className="p-2 text-slate-400 hover:text-white">
            <XIcon />
          </button>
        </div>
        <nav className="p-4">
          <ul className="space-y-2">
            {navItems.map(item => (
                <li key={item.view}>
                  <button 
                    onClick={() => navigate(item.view)} 
                    className="w-full text-left p-3 rounded-md text-lg text-slate-300 hover:bg-white/10 hover:text-primary-color transition flex items-center gap-3"
                  >
                    <item.icon /> {item.label}
                  </button>
                </li>
            ))}
          </ul>
        </nav>
      </div>
    </>
  );
};

export default Sidebar;