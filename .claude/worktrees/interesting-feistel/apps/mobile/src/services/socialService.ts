import { shareContent, type ShareResult } from './shareService';

export interface SocialSharePayload {
  text: string;
  url?: string;
  hashtag?: string;
}

function withHashtag(text: string, hashtag?: string) {
  if (!hashtag) return text;
  const tag = hashtag.startsWith('#') ? hashtag : `#${hashtag}`;
  return `${text}\n\n${tag}`;
}

export async function shareWorkoutPost(payload: SocialSharePayload): Promise<ShareResult> {
  return shareContent({
    title: 'Compartir progreso',
    message: withHashtag(payload.text, payload.hashtag),
    url: payload.url,
  });
}

export async function shareNutritionPost(payload: SocialSharePayload): Promise<ShareResult> {
  return shareContent({
    title: 'Compartir nutricion',
    message: withHashtag(payload.text, payload.hashtag),
    url: payload.url,
  });
}

