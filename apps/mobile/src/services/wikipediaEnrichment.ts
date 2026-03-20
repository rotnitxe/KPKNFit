export async function fetchWikipediaSummary(
  term: string,
  lang: 'es' | 'en' = 'es',
): Promise<{ extract?: string; title?: string; lang: string } | null> {
  const normalized = term.trim();
  if (!normalized) return null;

  const endpoint =
    lang === 'es'
      ? `https://es.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(normalized)}`
      : `https://en.wikipedia.org/api/rest_v1/page/summary/${encodeURIComponent(normalized)}`;

  try {
    const response = await fetch(endpoint, { method: 'GET' });
    if (!response.ok) return null;
    const payload = (await response.json()) as { extract?: string; title?: string };
    return {
      extract: payload.extract,
      title: payload.title,
      lang,
    };
  } catch {
    return null;
  }
}

export async function enrichWithWikipedia(term: string) {
  const spanish = await fetchWikipediaSummary(term, 'es');
  if (spanish?.extract) return spanish;
  return fetchWikipediaSummary(term, 'en');
}
