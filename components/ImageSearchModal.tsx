// components/ImageSearchModal.tsx
import React, { useState, useEffect } from 'react';
import { generateImages, searchGoogleImages } from '../services/aiService';
import { useAppState } from '../contexts/AppContext';
import Modal from './ui/Modal';
import Button from './ui/Button';
import { SparklesIcon, SearchIcon } from './icons';
import SkeletonLoader from './ui/SkeletonLoader';

interface ImageSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSelectImage: (imageUrl: string) => void;
  initialQuery: string;
}

const ImageSearchModal: React.FC<ImageSearchModalProps> = ({ isOpen, onClose, onSelectImage, initialQuery }) => {
  const { settings, isOnline } = useAppState();
  const [activeTab, setActiveTab] = useState<'generate' | 'search'>('generate');
  const [query, setQuery] = useState(initialQuery);
  const [results, setResults] = useState<string[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (isOpen) {
      setQuery(initialQuery);
      setResults([]);
      setError(null);
      setIsLoading(false);
      setActiveTab('generate');
    }
  }, [isOpen, initialQuery]);

  const handleSubmit = async (e?: React.FormEvent) => {
    e?.preventDefault();
    if (!query.trim() || !isOnline) return;

    setIsLoading(true);
    setError(null);
    setResults([]);
    try {
      let response;
      if (activeTab === 'generate') {
        const fullPrompt = `cinematic fitness wallpaper, ${query}, dramatic lighting, high quality photo`;
        response = await generateImages(fullPrompt, "16:9", settings);
      } else { // search
        response = await searchGoogleImages(query, settings);
      }
      setResults(response.imageUrls);
      if (response.imageUrls.length === 0) {
          setError("No se encontraron imágenes para esta descripción.");
      }
    } catch (err: any) {
      setError(err.message || `Error al ${activeTab === 'generate' ? 'generar' : 'buscar'} imágenes.`);
    } finally {
      setIsLoading(false);
    }
  };
  
  const handleImageClick = (url: string) => {
    onSelectImage(url);
    onClose();
  }

  return (
    <Modal isOpen={isOpen} onClose={onClose} title="Buscar o Generar Imagen">
      <div className="space-y-4">
        <div className="flex bg-slate-800 p-1 rounded-full">
            <button onClick={() => setActiveTab('generate')} className={`flex-1 py-1 rounded-full text-sm flex items-center justify-center gap-1 ${activeTab === 'generate' ? 'bg-primary-color' : ''}`}><SparklesIcon size={14}/> Generar (IA)</button>
            <button onClick={() => setActiveTab('search')} disabled={!isOnline} className={`flex-1 py-1 rounded-full text-sm flex items-center justify-center gap-1 disabled:opacity-50 ${activeTab === 'search' ? 'bg-primary-color' : ''}`}><SearchIcon size={14}/> Buscar (Web)</button>
        </div>

        <form onSubmit={handleSubmit} className="space-y-2">
          <textarea
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            placeholder={activeTab === 'generate' ? 'Describe la imagen que quieres crear...' : 'Busca imágenes en la web...'}
            className="w-full"
            rows={3}
            disabled={!isOnline}
          />
          <Button type="submit" isLoading={isLoading} disabled={isLoading || !isOnline} className="w-full">
            {activeTab === 'generate' ? <><SparklesIcon size={16} /> Generar</> : <><SearchIcon size={16} /> Buscar</>}
          </Button>
        </form>

        {isLoading && (
          <div className="grid grid-cols-2 sm:grid-cols-2 gap-2">
            {Array.from({ length: 4 }).map((_, i) => (
              <div key={i} className="aspect-video bg-slate-700/50 rounded-lg animate-pulse" />
            ))}
          </div>
        )}
        
        {error && <p className="text-center text-red-400">{error}</p>}
        
        {results.length > 0 && !isLoading && (
          <div className="grid grid-cols-2 gap-2 max-h-[50vh] overflow-y-auto">
            {results.map((url, i) => (
              <button key={i} onClick={() => handleImageClick(url)} className="aspect-video bg-slate-800 rounded-lg overflow-hidden focus:outline-none focus:ring-2 ring-primary-color ring-offset-2 ring-offset-slate-900">
                <img src={url} alt={`Result ${i + 1}`} className="w-full h-full object-cover" onError={(e) => (e.currentTarget.style.display = 'none')} />
              </button>
            ))}
          </div>
        )}
      </div>
    </Modal>
  );
};

export default ImageSearchModal;