// services/wikipediaEnrichment.ts
// Enriquecimiento opcional con Wikipedia cuando el contenido curado sea breve.

const CACHE_KEY = 'kpkn_wikipedia_cache';
const CACHE_TTL_MS = 7 * 24 * 60 * 60 * 1000; // 7 días

interface CacheEntry {
  content: string;
  fetchedAt: number;
}

function getCache(): Record<string, CacheEntry> {
  try {
    const raw = localStorage.getItem(CACHE_KEY);
    if (!raw) return {};
    return JSON.parse(raw);
  } catch {
    return {};
  }
}

function setCache(key: string, content: string) {
  try {
    const cache = getCache();
    cache[key] = { content, fetchedAt: Date.now() };
    localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
  } catch {
    // Ignore storage errors
  }
}

function getCached(key: string): string | null {
  const cache = getCache();
  const entry = cache[key];
  if (!entry) return null;
  if (Date.now() - entry.fetchedAt > CACHE_TTL_MS) {
    delete cache[key];
    try {
      localStorage.setItem(CACHE_KEY, JSON.stringify(cache));
    } catch {}
    return null;
  }
  return entry.content;
}

const WIKI_ES_API = 'https://es.wikipedia.org/api/rest_v1/page/summary/';
const WIKI_EN_API = 'https://en.wikipedia.org/api/rest_v1/page/summary/';

function slugify(text: string): string {
  return encodeURIComponent(text.replace(/\s+/g, '_'));
}

export async function fetchWikipediaSummary(term: string, lang: 'es' | 'en' = 'es'): Promise<{ extract?: string; title?: string; lang: string } | null> {
  const cacheKey = `${lang}:${term}`;
  const cached = getCached(cacheKey);
  if (cached) {
    try {
      return JSON.parse(cached);
    } catch {
      // Invalid cache
    }
  }

  const api = lang === 'es' ? WIKI_ES_API : WIKI_EN_API;
  const slug = slugify(term);

  try {
    const res = await fetch(`${api}${slug}`);
    if (!res.ok) return null;
    const data = await res.json();
    const extract = data.extract || data.description;
    if (!extract) return null;
    const result = { extract, title: data.title, lang };
    setCache(cacheKey, JSON.stringify(result));
    return result;
  } catch {
    return null;
  }
}

const MUSCLE_SEARCH_VARIANTS: Record<string, string[]> = {
  'Pectoral': ['Pectoral mayor', 'Músculo pectoral mayor'],
  'Deltoides': ['Músculo deltoides', 'Deltoides'],
  'Bíceps': ['Bíceps braquial', 'Músculo bíceps braquial'],
  'Tríceps': ['Tríceps braquial', 'Músculo tríceps braquial'],
  'Cuádriceps': ['Cuádriceps femoral', 'Músculo cuádriceps'],
  'Glúteos': ['Glúteo mayor', 'Músculo glúteo mayor'],
  'Dorsal Ancho': ['Dorsal ancho', 'Músculo dorsal ancho', 'Latissimus dorsi'],
  'Trapecio': ['Músculo trapecio', 'Trapecio'],
  'Isquiosurales': ['Isquiotibiales', 'Músculos isquiotibiales'],
  'Pantorrillas': ['Pantorrilla', 'Músculos de la pantorrilla'],
  'Abdomen': ['Músculos abdominales', 'Abdomen'],
};

export async function enrichWithWikipedia(term: string): Promise<{ extract?: string; title?: string; lang: string } | null> {
  const variants = MUSCLE_SEARCH_VARIANTS[term] || [term, `Músculo ${term}`, `${term} (anatomía)`];
  for (const t of variants) {
    const es = await fetchWikipediaSummary(t, 'es');
    if (es) return es;
  }
  for (const t of variants) {
    const en = await fetchWikipediaSummary(t, 'en');
    if (en) return en;
  }
  return null;
}
