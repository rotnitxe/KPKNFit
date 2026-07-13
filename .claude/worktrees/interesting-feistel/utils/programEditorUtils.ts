// utils/programEditorUtils.ts
import type { Program } from '../types';

export const PROGRAM_DRAFT_KEY = 'program-editor-draft';

export const isProgramComplex = (p: Program | null): boolean => {
    if (!p) return false;
    if (p.structure === 'complex') return true;
    if (p.structure === 'simple') return false;
    if (p.macrocycles.length > 1) return true;
    const macro = p.macrocycles[0];
    if (!macro) return false;
    if ((macro.blocks || []).length > 1) return true;
    const block = (macro.blocks || [])[0];
    if (!block) return false;
    if (block.mesocycles.length > 1) return true;
    return false;
};
