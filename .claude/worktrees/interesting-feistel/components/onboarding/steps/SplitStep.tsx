// Paso: Selección de split desde BD. Estética Tú.
import React, { useState } from 'react';
import { SPLIT_TEMPLATES, SplitTemplate } from '../../../data/splitTemplates';
import { ChevronDownIcon, ChevronUpIcon } from '../../icons';

interface SplitStepProps {
  selectedSplitId: string | null;
  onSelect: (split: SplitTemplate) => void;
  onNext: () => void;
  onBack: () => void;
  showAdvanced: boolean;
  onToggleAdvanced: () => void;
}

const RECOMMENDED_IDS = ['ul_x4', 'fullbody_x3', 'ppl_ul', 'push_pull_x4', 'pl_classic_4'];

export const SplitStep: React.FC<SplitStepProps> = ({
  selectedSplitId,
  onSelect,
  onNext,
  onBack,
  showAdvanced,
  onToggleAdvanced,
}) => {
  const [expandedId, setExpandedId] = useState<string | null>(null);
  const [search, setSearch] = useState('');

  const recommended = SPLIT_TEMPLATES.filter((s) => RECOMMENDED_IDS.includes(s.id));
  const advancedTemplates = SPLIT_TEMPLATES.filter((s) => !RECOMMENDED_IDS.includes(s.id) && s.id !== 'custom');

  const filteredAdvanced = search.trim()
    ? advancedTemplates.filter(
        (s) =>
          s.name.toLowerCase().includes(search.toLowerCase()) ||
          s.description.toLowerCase().includes(search.toLowerCase())
      )
    : advancedTemplates;

  const listToRender = showAdvanced ? filteredAdvanced : recommended;

  return (
    <div className="flex flex-col min-h-0 flex-1">
      <div className="flex-1 overflow-y-auto px-4 py-6 custom-scrollbar">
        <div className="flex justify-between items-start mb-4">
          <div>
            <h2 className="text-lg font-medium text-white mb-1">Elige tu split</h2>
            <p className="text-sm text-[#a3a3a3]">Cómo organizas tus días de entrenamiento.</p>
          </div>
          <button
            onClick={onToggleAdvanced}
            className="text-xs text-[#737373] underline shrink-0"
          >
            {showAdvanced ? 'Ver recomendados' : 'Mostrar opciones avanzadas'}
          </button>
        </div>
        
        {showAdvanced && (
          <input
            type="text"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            placeholder="Buscar split..."
            className="w-full bg-[#252525] border border-[#3f3f3f] px-4 py-2 text-white text-sm placeholder-[#737373] mb-4 focus:border-[#525252] outline-none"
          />
        )}
        
        <div className="space-y-2">
          {listToRender.map((split) => {
            const isExpanded = expandedId === split.id;
            const isSelected = selectedSplitId === split.id;
            return (
              <div
                key={split.id}
                className={`border ${isSelected ? 'border-white bg-[#2a2a2a]' : 'border-[#3f3f3f] bg-[#252525]'}`}
              >
                <button
                  onClick={() => onSelect(split)}
                  className="w-full text-left px-4 py-3 flex items-center justify-between"
                >
                  <div>
                    <span className="font-medium text-white block">{split.name}</span>
                    <span className="text-xs text-[#737373]">{split.difficulty}</span>
                  </div>
                  <button
                    onClick={(e) => {
                      e.stopPropagation();
                      setExpandedId(isExpanded ? null : split.id);
                    }}
                    className="p-1 text-[#737373]"
                  >
                    {isExpanded ? <ChevronUpIcon size={16} /> : <ChevronDownIcon size={16} />}
                  </button>
                </button>
                {isExpanded && (
                  <div className="px-4 pb-3 text-xs text-[#a3a3a3] border-t border-[#2a2a2a] pt-2">
                    <p className="mb-2">{split.description}</p>
                    <div className="flex flex-wrap gap-1">
                      {split.pattern.map((p, i) => (
                        <span key={i} className="bg-[#1a1a1a] px-1.5 py-0.5 text-[#737373]">
                          {p}
                        </span>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      </div>
      
      <div className="shrink-0 p-4 flex gap-3 border-t border-[#2a2a2a]">
        <button onClick={onBack} className="flex-1 py-4 bg-[#252525] text-white font-medium text-sm border border-[#3f3f3f]">
          Atrás
        </button>
        <button
          onClick={onNext}
          disabled={!selectedSplitId}
          className="flex-1 py-4 bg-white text-[#1a1a1a] font-medium text-sm disabled:opacity-50 disabled:cursor-not-allowed"
        >
          Continuar
        </button>
      </div>
    </div>
  );
};