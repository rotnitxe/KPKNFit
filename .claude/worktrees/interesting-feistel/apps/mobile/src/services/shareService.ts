import { Share } from 'react-native';

export interface SharePayload {
  message?: string;
  url?: string;
  title?: string;
}

export interface ShareResult {
  completed: boolean;
  activityType?: string | null;
}

export async function shareContent(payload: SharePayload): Promise<ShareResult> {
  const response = await Share.share({
    message: payload.message,
    url: payload.url,
    title: payload.title,
  });

  return {
    completed: response.action === Share.sharedAction,
    activityType: response.activityType,
  };
}

export async function shareText(message: string, title = 'Compartir') {
  return shareContent({ message, title });
}

export async function shareUrl(url: string, message?: string, title = 'Compartir enlace') {
  return shareContent({ url, message, title });
}

