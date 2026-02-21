// components/ui/ErrorBoundary.tsx
import React, { Component, ErrorInfo, ReactNode } from 'react';

interface Props {
  children?: ReactNode;
  fallbackLabel?: string;
  onRecover?: () => void;
}

interface State {
  hasError: boolean;
  error: Error | null;
  errorInfo: ErrorInfo | null;
  copied: boolean;
}

class ErrorBoundary extends Component<Props, State> {
  public state: State = {
    hasError: false,
    error: null,
    errorInfo: null,
    copied: false
  };

  public static getDerivedStateFromError(error: Error): Partial<State> {
    return { hasError: true, error, errorInfo: null, copied: false };
  }

  public componentDidCatch(error: Error, errorInfo: ErrorInfo) {
    this.setState({ errorInfo });
    console.error("KPKN Error Caught:", error, errorInfo);
  }

  private getErrorReport = () => {
    const timestamp = new Date().toISOString();
    const ua = navigator.userAgent;
    return `KPKN ERROR REPORT (${timestamp})\nUA: ${ua}\n\nError: ${this.state.error?.message}\n\nStack:\n${this.state.error?.stack}\n\nComponent:\n${this.state.errorInfo?.componentStack}`;
  };

  private handleCopy = () => {
    navigator.clipboard.writeText(this.getErrorReport());
    this.setState({ copied: true });
    setTimeout(() => this.setState({ copied: false }), 3000);
  };

  private handleRecover = () => {
    this.setState({ hasError: false, error: null, errorInfo: null, copied: false });
    this.props.onRecover?.();
  };

  public render() {
    if (this.state.hasError) {
      const isViewLevel = !!this.props.onRecover || !!this.props.fallbackLabel;

      if (isViewLevel) {
        return (
          <div className="flex flex-col items-center justify-center p-6 text-white min-h-[300px]">
            <div className="bg-zinc-900/80 backdrop-blur-xl border border-red-500/30 p-5 rounded-2xl max-w-sm w-full shadow-xl">
              <div className="flex items-center gap-3 mb-4">
                <div className="w-10 h-10 bg-red-500/10 rounded-full flex items-center justify-center text-red-500 shrink-0 border border-red-500/20">
                  <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"/><line x1="12" y1="9" x2="12" y2="13"/><line x1="12" y1="17" x2="12.01" y2="17"/></svg>
                </div>
                <div>
                  <h2 className="text-sm font-black uppercase tracking-tight text-white">Error en {this.props.fallbackLabel || 'Vista'}</h2>
                  <p className="text-[10px] text-zinc-500 font-bold">Copia el error y envíalo a tu IA para solucionarlo.</p>
                </div>
              </div>

              <div className="bg-black rounded-xl p-3 mb-4 overflow-auto max-h-32 border border-white/5 text-[9px] font-mono text-zinc-400">
                <span className="text-red-400 font-bold block mb-1">{this.state.error?.toString()}</span>
                <div className="whitespace-pre-wrap opacity-50 leading-relaxed">{this.state.errorInfo?.componentStack?.slice(0, 500)}</div>
              </div>

              <div className="flex gap-2">
                <button onClick={this.handleCopy} className={`flex-1 py-3 rounded-xl flex items-center justify-center gap-1.5 text-[10px] font-black uppercase tracking-widest transition-all ${this.state.copied ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-zinc-800 text-white border border-white/5 hover:bg-zinc-700'}`}>
                  {this.state.copied ? 'Copiado' : 'Copiar Error'}
                </button>
                <button onClick={this.handleRecover} className="flex-1 py-3 rounded-xl flex items-center justify-center gap-1.5 text-[10px] font-black uppercase tracking-widest bg-blue-600/20 border border-blue-500/30 hover:bg-blue-600/40 text-blue-400 transition-all">
                  Reintentar
                </button>
              </div>
            </div>
          </div>
        );
      }

      return (
        <div className="min-h-screen bg-[#0a0a0a] flex flex-col items-center justify-center p-6 text-white font-sans absolute inset-0 z-[9999]">
          <div className="bg-zinc-900/80 backdrop-blur-xl border border-red-500/30 p-6 rounded-3xl max-w-md w-full shadow-2xl shadow-red-500/10">
            <div className="flex flex-col items-center text-center mb-6">
              <div className="w-16 h-16 bg-red-500/10 rounded-full flex items-center justify-center text-red-500 mb-4 border border-red-500/20">
                <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z"></path><line x1="12" y1="9" x2="12" y2="13"></line><line x1="12" y1="17" x2="12.01" y2="17"></line></svg>
              </div>
              <h1 className="text-xl font-black uppercase tracking-tight text-white">Sistema Protegido</h1>
              <p className="text-[11px] text-zinc-400 font-bold uppercase tracking-widest mt-2 leading-relaxed">
                Hemos interceptado una falla crítica.<br/>Copia el error y envíaselo a tu IA.
              </p>
            </div>

            <div className="bg-black rounded-2xl p-4 mb-6 overflow-auto max-h-48 border border-white/5 text-[10px] font-mono text-zinc-400 relative">
              <span className="text-red-400 font-bold block mb-2">{this.state.error?.toString()}</span>
              <div className="whitespace-pre-wrap opacity-60 leading-relaxed">
                {this.state.errorInfo?.componentStack}
              </div>
            </div>

            <div className="flex flex-col gap-3">
              <button 
                onClick={this.handleCopy}
                className={`w-full py-4 rounded-xl flex items-center justify-center gap-2 text-[11px] font-black uppercase tracking-widest transition-all ${this.state.copied ? 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30' : 'bg-zinc-800 text-white border border-white/5 hover:bg-zinc-700'}`}
              >
                {this.state.copied ? (
                    <><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path><polyline points="22 4 12 14.01 9 11.01"></polyline></svg> ¡Copiado con Éxito!</>
                ) : (
                    <><svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><rect x="9" y="9" width="13" height="13" rx="2" ry="2"></rect><path d="M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1"></path></svg> Copiar Informe de Error</>
                )}
              </button>
              <button 
                onClick={() => window.location.reload()}
                className="w-full py-4 rounded-xl flex items-center justify-center gap-2 text-[11px] font-black uppercase tracking-widest bg-red-600/20 border border-red-500/30 hover:bg-red-600/40 text-red-400 transition-all"
              >
                <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"><polyline points="23 4 23 10 17 10"></polyline><polyline points="1 20 1 14 7 14"></polyline><path d="M3.51 9a9 9 0 0 1 14.85-3.36L23 10M1 14l4.64 4.36A9 9 0 0 0 20.49 15"></path></svg> 
                Reiniciar Aplicación
              </button>
            </div>
          </div>
        </div>
      );
    }

    return this.props.children;
  }
}

export default ErrorBoundary;