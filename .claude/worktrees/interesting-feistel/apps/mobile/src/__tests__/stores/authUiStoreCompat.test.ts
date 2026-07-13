import { useAuthStore } from '@/stores/authStore';
import { useUIStore } from '@/stores/uiStore';

describe('auth/ui compatibility stores', () => {
  beforeEach(() => {
    useAuthStore.setState({
      user: null,
      session: null,
      isLoading: true,
      isAuthenticated: false,
    });
    useUIStore.setState({
      view: 'home',
      historyStack: [{ view: 'home' }],
      isMenuOpen: false,
      isTimersModalOpen: false,
      isReadinessModalOpen: false,
      activeProgramId: null,
      toasts: [],
      toastQueue: [],
    });
  });

  it('initializes auth store in offline mode', async () => {
    await useAuthStore.getState().initialize();
    expect(useAuthStore.getState().isLoading).toBe(false);
    expect(useAuthStore.getState().isAuthenticated).toBe(false);
  });

  it('returns unsupported errors for remote auth actions', async () => {
    const login = await useAuthStore.getState().signIn('a@b.com', '123456');
    const signup = await useAuthStore.getState().signUp('a@b.com', '123456');
    expect(login.error?.message).toContain('Autenticacion remota');
    expect(signup.error?.message).toContain('Autenticacion remota');
  });

  it('keeps toast queue semantics', () => {
    useUIStore.getState().addToast('one');
    useUIStore.getState().addToast('two');
    useUIStore.getState().addToast('three');
    useUIStore.getState().addToast('four');
    expect(useUIStore.getState().toasts).toHaveLength(3);
    expect(useUIStore.getState().toastQueue).toHaveLength(1);
  });
});

