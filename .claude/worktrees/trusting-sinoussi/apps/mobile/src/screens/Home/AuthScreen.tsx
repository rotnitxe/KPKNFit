import React, { useEffect, useState } from 'react';
import {
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { Button } from '@/components/ui/Button';
import { KpknLogoIcon } from '@/components/icons';
import { useAuthStore } from '@/stores/authStore';

type AuthMode = 'login' | 'signup';

export function AuthScreen() {
  const initialize = useAuthStore(state => state.initialize);
  const signIn = useAuthStore(state => state.signIn);
  const signUp = useAuthStore(state => state.signUp);
  const signInWithGoogle = useAuthStore(state => state.signInWithGoogle);
  const signInWithApple = useAuthStore(state => state.signInWithApple);
  const isLoading = useAuthStore(state => state.isLoading);

  const [mode, setMode] = useState<AuthMode>('login');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void initialize();
  }, [initialize]);

  const handleSubmit = async () => {
    setSubmitting(true);
    setMessage(null);
    try {
      const result =
        mode === 'login'
          ? await signIn(email.trim(), password)
          : await signUp(email.trim(), password);
      if (result.error) {
        setMessage(result.error.message);
      } else if (mode === 'signup') {
        setMessage('Registro iniciado. Verifica tu correo si el backend remoto esta habilitado.');
        setMode('login');
      }
    } finally {
      setSubmitting(false);
    }
  };

  const handleOAuth = async (provider: 'google' | 'apple') => {
    setMessage(null);
    try {
      if (provider === 'google') {
        await signInWithGoogle();
      } else {
        await signInWithApple();
      }
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'No se pudo conectar con el proveedor.');
    }
  };

  return (
    <SafeAreaView style={styles.safeArea}>
      <KeyboardAvoidingView
        style={styles.container}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      >
        <View style={styles.logoBlock}>
          <View style={styles.logoWrap}>
            <KpknLogoIcon size={84} color="#FFFFFF" />
          </View>
          <Text style={styles.brandTitle}>KPKN FIT</Text>
          <Text style={styles.brandSubtitle}>The Ultimate Training Engine</Text>
        </View>

        <View style={styles.card}>
          <View style={styles.cardHeader}>
            <Text style={styles.cardTitle}>
              {mode === 'login' ? 'Iniciar sesion' : 'Crear cuenta'}
            </Text>
            <Pressable onPress={() => setMode(mode === 'login' ? 'signup' : 'login')}>
              <Text style={styles.switchLink}>
                {mode === 'login' ? 'Registrarse' : 'Ya tengo cuenta'}
              </Text>
            </Pressable>
          </View>

          <View style={styles.form}>
            <Text style={styles.label}>Email</Text>
            <TextInput
              value={email}
              onChangeText={setEmail}
              autoCapitalize="none"
              keyboardType="email-address"
              placeholder="tu@email.com"
              placeholderTextColor="rgba(255,255,255,0.32)"
              style={styles.input}
            />

            <Text style={styles.label}>Contrasena</Text>
            <TextInput
              value={password}
              onChangeText={setPassword}
              secureTextEntry
              placeholder="••••••••"
              placeholderTextColor="rgba(255,255,255,0.32)"
              style={styles.input}
            />

            <Button
              onPress={() => void handleSubmit()}
              isLoading={submitting || isLoading}
              style={styles.primaryButton}
            >
              <Text style={styles.primaryButtonText}>
                {mode === 'login' ? 'ENTRAR' : 'REGISTRARME'}
              </Text>
            </Button>

            <View style={styles.dividerRow}>
              <View style={styles.divider} />
              <Text style={styles.dividerText}>O continuar con</Text>
              <View style={styles.divider} />
            </View>

            <View style={styles.oauthRow}>
              <Button variant="secondary" onPress={() => void handleOAuth('google')} style={styles.oauthButton}>
                <Text style={styles.oauthText}>Google</Text>
              </Button>
              <Button variant="secondary" onPress={() => void handleOAuth('apple')} style={styles.oauthButton}>
                <Text style={styles.oauthText}>Apple</Text>
              </Button>
            </View>

            {message ? <Text style={styles.message}>{message}</Text> : null}
          </View>
        </View>

        <Text style={styles.footer}>Propulsado por AUGE Adaptive Engine</Text>
      </KeyboardAvoidingView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  safeArea: {
    flex: 1,
    backgroundColor: '#040404',
  },
  container: {
    flex: 1,
    justifyContent: 'center',
    paddingHorizontal: 24,
    paddingVertical: 32,
    backgroundColor: '#040404',
  },
  logoBlock: {
    alignItems: 'center',
    marginBottom: 28,
  },
  logoWrap: {
    marginBottom: 18,
  },
  brandTitle: {
    color: '#FFFFFF',
    fontSize: 32,
    fontWeight: '900',
    letterSpacing: -1,
  },
  brandSubtitle: {
    color: 'rgba(255,255,255,0.36)',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 3,
    textTransform: 'uppercase',
    marginTop: 6,
  },
  card: {
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.10)',
    backgroundColor: 'rgba(255,255,255,0.04)',
    padding: 24,
  },
  cardHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 20,
  },
  cardTitle: {
    color: '#FFFFFF',
    fontSize: 22,
    fontWeight: '900',
    textTransform: 'uppercase',
  },
  switchLink: {
    color: 'rgba(255,255,255,0.56)',
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.4,
  },
  form: {
    gap: 10,
  },
  label: {
    color: 'rgba(255,255,255,0.42)',
    fontSize: 10,
    fontWeight: '900',
    textTransform: 'uppercase',
    letterSpacing: 1.6,
  },
  input: {
    backgroundColor: 'rgba(255,255,255,0.04)',
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.10)',
    color: '#FFFFFF',
    paddingHorizontal: 14,
    paddingVertical: 13,
    fontSize: 15,
  },
  primaryButton: {
    marginTop: 12,
    backgroundColor: '#FFFFFF',
  },
  primaryButtonText: {
    color: '#000000',
    fontSize: 14,
    fontWeight: '900',
    letterSpacing: 1.2,
    textTransform: 'uppercase',
  },
  dividerRow: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 10,
    marginTop: 12,
    marginBottom: 4,
  },
  divider: {
    flex: 1,
    height: 1,
    backgroundColor: 'rgba(255,255,255,0.10)',
  },
  dividerText: {
    color: 'rgba(255,255,255,0.24)',
    fontSize: 10,
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: 1.2,
  },
  oauthRow: {
    flexDirection: 'row',
    gap: 10,
  },
  oauthButton: {
    flex: 1,
    minWidth: 0,
    backgroundColor: 'rgba(255,255,255,0.06)',
  },
  oauthText: {
    color: '#FFFFFF',
    fontWeight: '800',
  },
  message: {
    marginTop: 10,
    color: '#F3B7B7',
    fontSize: 13,
    lineHeight: 18,
  },
  footer: {
    marginTop: 18,
    textAlign: 'center',
    color: 'rgba(255,255,255,0.18)',
    fontSize: 10,
    fontWeight: '800',
    letterSpacing: 2.4,
    textTransform: 'uppercase',
  },
});
