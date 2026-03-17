import { create } from 'zustand';
import { 
  persistDomainPayload, 
  loadPersistedDomainPayload 
} from '../services/mobilePersistenceService';
import type { 
  CoachConversation, 
  CoachChatMessage, 
  CoachContextSnapshot 
} from '../types/coach';
import { generateCoachReply, summarizeConversationTitle } from '../services/coachChatService';

interface CoachStoreState {
  status: 'idle' | 'ready' | 'failed';
  conversations: CoachConversation[];
  activeConversationId: string | null;
  isSending: boolean;
  notice: string | null;
  errorMessage: string | null;
  hydrateFromStorage: () => Promise<void>;
  createConversation: (seedMessage?: string) => void;
  setActiveConversation: (id: string) => void;
  sendMessage: (input: {
    text: string;
    context: CoachContextSnapshot;
  }) => Promise<void>;
  deleteConversation: (id: string) => Promise<void>;
  clearNotice: () => void;
}

const MAX_MESSAGES = 30;
const MAX_CONVERSATIONS = 20;

interface PersistedCoachPayload {
  conversations: CoachConversation[];
  activeConversationId: string | null;
}

export const useCoachStore = create<CoachStoreState>((set, get) => ({
  status: 'idle',
  conversations: [],
  activeConversationId: null,
  isSending: false,
  notice: null,
  errorMessage: null,

  hydrateFromStorage: async () => {
    try {
      const payload = await loadPersistedDomainPayload<PersistedCoachPayload>('coach');
      if (payload) {
        set({ 
          conversations: Array.isArray(payload.conversations) ? payload.conversations : [],
          activeConversationId: payload.activeConversationId ?? null,
          status: 'ready'
        });
      } else {
        set({ status: 'ready' });
      }
    } catch (error) {
      set({ status: 'failed', errorMessage: 'Error hidratando coach.' });
    }
  },

  createConversation: (seedMessage) => {
    const id = `conv_${Date.now()}`;
    const now = new Date().toISOString();
    const newConv: CoachConversation = {
      id,
      title: seedMessage ? summarizeConversationTitle(seedMessage) : 'Nueva conversación',
      createdAt: now,
      updatedAt: now,
      messages: []
    };

    let nextConversations = [newConv, ...get().conversations];
    if (nextConversations.length > MAX_CONVERSATIONS) {
      nextConversations = nextConversations
        .sort((a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime())
        .slice(0, MAX_CONVERSATIONS);
    }

    set({ conversations: nextConversations, activeConversationId: id });
    void persistDomainPayload('coach', { 
      conversations: nextConversations, 
      activeConversationId: id 
    });
  },

  setActiveConversation: (id) => {
    set({ activeConversationId: id });
    void persistDomainPayload('coach', { 
      conversations: get().conversations, 
      activeConversationId: id 
    });
  },

  sendMessage: async ({ text, context }) => {
    const state = get();
    let currentConvId = state.activeConversationId;
    
    if (!currentConvId) {
      state.createConversation(text);
      currentConvId = get().activeConversationId!;
    }

    const conversations = [...get().conversations];
    const convIndex = conversations.findIndex(c => c.id === currentConvId);
    if (convIndex === -1) return;

    set({ isSending: true, errorMessage: null });

    try {
      const now = new Date().toISOString();
      const userMsg: CoachChatMessage = {
        id: `msg_u_${Date.now()}`,
        role: 'user',
        text,
        createdAt: now
      };

      const conversation = { ...conversations[convIndex] };
      let messages = [...conversation.messages, userMsg];

      // Generate AI Reply
      const { reply } = generateCoachReply({
        userText: text,
        context,
        recentMessages: messages
      });

      const assistantMsg: CoachChatMessage = {
        id: `msg_a_${Date.now() + 1}`,
        role: 'assistant',
        text: reply,
        createdAt: new Date().toISOString()
      };

      messages.push(assistantMsg);

      // Truncate messages
      if (messages.length > MAX_MESSAGES) {
        messages = messages.slice(-MAX_MESSAGES);
      }

      conversation.messages = messages;
      conversation.updatedAt = new Date().toISOString();
      
      // Update title if it was default
      if (conversation.title === 'Nueva conversación') {
        conversation.title = summarizeConversationTitle(text);
      }

      conversations[convIndex] = conversation;
      
      set({ conversations, isSending: false });
      void persistDomainPayload('coach', { 
        conversations, 
        activeConversationId: currentConvId 
      });
    } catch (error) {
      set({ isSending: false, errorMessage: 'Error al procesar mensaje.' });
    }
  },

  deleteConversation: async (id) => {
    const nextConversations = get().conversations.filter(c => c.id !== id);
    const nextActiveId = get().activeConversationId === id ? null : get().activeConversationId;
    
    set({ conversations: nextConversations, activeConversationId: nextActiveId });
    void persistDomainPayload('coach', { 
      conversations: nextConversations, 
      activeConversationId: nextActiveId 
    });
  },

  clearNotice: () => set({ notice: null })
}));
