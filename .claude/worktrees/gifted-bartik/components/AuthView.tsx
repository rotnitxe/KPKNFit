
import React, { useState } from 'react';
import { motion, AnimatePresence } from 'framer-motion';
import { useAuthStore } from '../stores/authStore';
import { useAppDispatch } from '../contexts/AppContext';
import { KpknLogoIcon, ArrowLeftIcon } from './icons';
import Button from './ui/Button';

const AuthView: React.FC = () => {
    const { signIn, signUp, signInWithGoogle, signInWithApple } = useAuthStore();
    const { addToast } = useAppDispatch();
    
    const [mode, setMode] = useState<'login' | 'signup'>('login');
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [loading, setLoading] = useState(false);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setLoading(true);
        
        try {
            const { error } = mode === 'login' 
                ? await signIn(email, password) 
                : await signUp(email, password);
            
            if (error) {
                addToast(error.message, 'danger');
            } else if (mode === 'signup') {
                addToast('¡Registro exitoso! Por favor verifica tu email.', 'success');
                setMode('login');
            }
        } catch (err: any) {
            addToast(err.message || 'Ocurrió un error', 'danger');
        } finally {
            setLoading(false);
        }
    };

    const handleOAuth = async (provider: 'google' | 'apple') => {
        try {
            if (provider === 'google') await signInWithGoogle();
            else await signInWithApple();
        } catch (err: any) {
            addToast(err.message || 'Error al conectar con el proveedor', 'danger');
        }
    };

    return (
        <div className="min-h-screen bg-black text-white flex flex-col items-center justify-center px-6 py-12">
            {/* Logo y Encabezado */}
            <motion.div 
                initial={{ opacity: 0, y: -20 }}
                animate={{ opacity: 1, y: 0 }}
                className="flex flex-col items-center mb-12"
            >
                <div className="w-24 h-24 mb-6 text-white">
                    <KpknLogoIcon size={96} />
                </div>
                <h1 className="text-3xl font-black tracking-tighter uppercase italic">KPKN FIT</h1>
                <p className="text-white/40 text-xs font-bold tracking-[0.3em] uppercase mt-2">The Ultimate Training Engine</p>
            </motion.div>

            {/* Formulario */}
            <motion.div 
                layout
                className="w-full max-w-sm bg-white/5 border border-white/10 p-8 backdrop-blur-md"
            >
                <div className="flex justify-between items-center mb-8">
                    <h2 className="text-xl font-black tracking-tight uppercase">
                        {mode === 'login' ? 'Iniciar Sesión' : 'Crear Cuenta'}
                    </h2>
                    <button 
                        onClick={() => setMode(mode === 'login' ? 'signup' : 'login')}
                        className="text-xs font-bold text-white/50 hover:text-white transition-colors uppercase tracking-widest"
                    >
                        {mode === 'login' ? 'Registrarse' : 'Ya tengo cuenta'}
                    </button>
                </div>

                <form onSubmit={handleSubmit} className="flex flex-col gap-4">
                    <div className="flex flex-col gap-1">
                        <label className="text-[10px] font-black uppercase tracking-widest text-white/40">Email</label>
                        <input 
                            type="email" 
                            value={email}
                            onChange={(e) => setEmail(e.target.value)}
                            required
                            className="bg-white/5 border border-white/10 px-4 py-3 text-sm focus:outline-none focus:border-white/30 transition-colors"
                            placeholder="tu@email.com"
                        />
                    </div>
                    
                    <div className="flex flex-col gap-1">
                        <label className="text-[10px] font-black uppercase tracking-widest text-white/40">Contraseña</label>
                        <input 
                            type="password" 
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                            className="bg-white/5 border border-white/10 px-4 py-3 text-sm focus:outline-none focus:border-white/30 transition-colors"
                            placeholder="••••••••"
                        />
                    </div>

                    <Button 
                        type="submit" 
                        isLoading={loading}
                        className="w-full mt-4 !py-4 !bg-white !text-black !font-black !uppercase !tracking-widest"
                    >
                        {mode === 'login' ? 'ENTRAR' : 'REGISTRARME'}
                    </Button>
                </form>

                <div className="relative my-8 flex items-center justify-center">
                    <div className="absolute inset-0 flex items-center">
                        <div className="w-full border-t border-white/10"></div>
                    </div>
                    <span className="relative px-4 bg-black text-[10px] font-black text-white/30 uppercase tracking-widest">O continuar con</span>
                </div>

                <div className="flex gap-4">
                    <button 
                        onClick={() => handleOAuth('google')}
                        className="flex-1 bg-white/5 border border-white/10 py-3 flex items-center justify-center hover:bg-white/10 transition-colors"
                    >
                        <svg className="w-5 h-5" viewBox="0 0 24 24">
                            <path fill="currentColor" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" />
                            <path fill="currentColor" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z" />
                            <path fill="currentColor" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l3.66-2.84z" />
                            <path fill="currentColor" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z" />
                        </svg>
                    </button>
                    <button 
                        onClick={() => handleOAuth('apple')}
                        className="flex-1 bg-white/5 border border-white/10 py-3 flex items-center justify-center hover:bg-white/10 transition-colors"
                    >
                        <svg className="w-5 h-5" viewBox="0 0 24 24">
                            <path fill="currentColor" d="M17.05 20.28c-.98.95-2.05.8-3.08.35-1.09-.46-2.09-.48-3.24 0-1.44.62-2.2.44-3.06-.35C2.79 15.25 3.51 7.59 9.05 7.31c1.35.07 2.29.74 3.08.75 1.18-.02 2.31-.93 3.57-.84 1.51.12 2.65.72 3.4 1.8-3.12 1.87-2.39 5.98.6 7.25-.6 1.51-1.38 3.01-2.65 4.01zM12.03 7.25c-.02-2.23 1.76-4.07 3.9-4.25.3 2.5-2.18 4.54-3.9 4.25z" />
                        </svg>
                    </button>
                </div>
            </motion.div>

            {/* Footer */}
            <motion.div 
                initial={{ opacity: 0 }}
                animate={{ opacity: 1 }}
                transition={{ delay: 0.5 }}
                className="mt-12 text-center"
            >
                <p className="text-[10px] font-bold text-white/20 uppercase tracking-[0.4em]">Propulsado por AUGE Adaptive Engine</p>
            </motion.div>
        </div>
    );
};

export default AuthView;
