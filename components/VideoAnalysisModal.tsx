// components/VideoAnalysisModal.tsx
import React, { useState, useRef, useEffect } from 'react';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { startRecording, stopRecording } from '../services/videoService';
import { analyzeExerciseVideo } from '../services/aiService';
import { CameraIcon, SparklesIcon, CheckIcon, XIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';
import { Settings } from '../types';

interface VideoAnalysisModalProps {
  isOpen: boolean;
  onClose: () => void;
  exerciseName: string;
  isOnline: boolean;
  settings: Settings;
}

const VideoAnalysisModal: React.FC<VideoAnalysisModalProps> = ({ isOpen, onClose, exerciseName, isOnline, settings }) => {
  const videoRef = useRef<HTMLVideoElement>(null);
  const [isRecording, setIsRecording] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [analysis, setAnalysis] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [videoDataUrl, setVideoDataUrl] = useState<string | null>(null);

  const cleanup = () => {
    if (videoRef.current && videoRef.current.srcObject) {
        (videoRef.current.srcObject as MediaStream).getTracks().forEach(track => track.stop());
        videoRef.current.srcObject = null;
    }
    setIsRecording(false);
    setIsLoading(false);
    setAnalysis(null);
    setError(null);
    setVideoDataUrl(null);
  };
  
  const handleClose = () => {
      cleanup();
      onClose();
  };
  
  useEffect(() => {
      if (!isOpen) {
          cleanup();
      }
  }, [isOpen]);

  const handleStartRecording = async () => {
    if (!videoRef.current || !isOnline) return;
    setAnalysis(null);
    setError(null);
    setVideoDataUrl(null);
    try {
      await startRecording(videoRef.current);
      setIsRecording(true);
    } catch (err: any) {
      setError(err.message);
    }
  };

  const handleStopRecording = async () => {
    if (!videoRef.current) return;
    const result = await stopRecording(videoRef.current);
    setIsRecording(false);
    if (result) {
      setVideoDataUrl(result);
      handleAnalyze(result);
    }
  };

  const handleAnalyze = async (dataUrl: string) => {
    setIsLoading(true);
    setError(null);
    try {
        const base64Video = dataUrl.split(',')[1];
        const result = await analyzeExerciseVideo(base64Video, exerciseName, settings);
        setAnalysis(result.text);
    } catch (err: any) {
        setError(err.message || "Error al analizar el video.");
    } finally {
        setIsLoading(false);
    }
  };

  return (
    <Modal isOpen={isOpen} onClose={handleClose} title={`Análisis de Técnica: ${exerciseName}`}>
      <div className="space-y-4">
        <div className="bg-black rounded-lg aspect-video flex items-center justify-center">
          <video ref={videoRef} className="w-full h-full rounded-lg" muted playsInline />
        </div>
        
        <div className="flex justify-center">
            {!isRecording && (
                <Button onClick={handleStartRecording} disabled={!isOnline}>
                    <CameraIcon size={16} /> Grabar (5-10s)
                </Button>
            )}
            {isRecording && (
                <Button onClick={handleStopRecording} variant="danger">
                    Detener Grabación
                </Button>
            )}
        </div>
        
        {isLoading && (
            <div className="p-4 bg-slate-800/50 rounded-lg">
                <p className="text-center text-slate-300 mb-2">Analizando tu técnica con IA...</p>
                <SkeletonLoader />
            </div>
        )}

        {error && <p className="text-red-400 text-center">{error}</p>}
        
        {analysis && !isLoading && (
            <div className="p-4 bg-slate-800/50 rounded-lg space-y-4 animate-fade-in">
                <div className="prose prose-sm prose-invert max-w-none" dangerouslySetInnerHTML={{ __html: analysis.replace(/\n/g, '<br />') }} />
            </div>
        )}
      </div>
    </Modal>
  );
};

export default VideoAnalysisModal;