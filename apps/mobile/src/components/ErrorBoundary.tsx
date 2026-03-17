import React from 'react';
import {
  View,
  Text,
  TouchableOpacity,
  StyleSheet,
  Share,
  Platform,
} from 'react-native';
import { AlertTriangleIcon, RefreshCwIcon, CopyIcon } from './icons';
import { useColors } from '../theme';

interface Props {
  children: React.ReactNode;
  mode?: 'view' | 'app';
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends React.Component<Props, State> {
  state: State = { hasError: false, error: null };

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, info: React.ErrorInfo) {
    console.error('[ErrorBoundary]', error, info.componentStack);
  }

  private handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  private handleShareError = async () => {
    try {
      await Share.share({
        message: `KPKN Fit Error Report\n\nError: ${this.state.error?.message}\n\nStack: ${this.state.error?.stack}`,
      });
    } catch (error) {
      console.error('Error sharing', error);
    }
  };

  render() {
    if (this.state.hasError) {
      return <ErrorFallback 
        error={this.state.error} 
        mode={this.props.mode} 
        onRetry={this.handleRetry} 
        onShare={this.handleShareError}
      />;
    }
    return this.props.children;
  }
}

// Functional component for hooks usage
function ErrorFallback({ 
  error, 
  mode = 'view', 
  onRetry, 
  onShare 
}: { 
  error: Error | null; 
  mode?: 'view' | 'app'; 
  onRetry: () => void;
  onShare: () => void;
}) {
  const colors = useColors();
  
  return (
    <View style={[styles.container, { backgroundColor: colors.background }]}>
      <AlertTriangleIcon size={48} color={colors.error} />
      
      <Text style={[styles.title, { color: colors.onSurface }]}>
        Algo salió mal
      </Text>
      
      <View style={[styles.errorBox, { backgroundColor: colors.surfaceContainer, borderColor: colors.outlineVariant }]}>
        <Text style={[styles.message, { color: colors.error }]}>
          {error?.message ?? 'Error inesperado en la interfaz.'}
        </Text>
      </View>

      <View style={styles.buttonRow}>
        <TouchableOpacity 
          style={[styles.button, { backgroundColor: colors.primary }]} 
          onPress={onRetry}
        >
          <RefreshCwIcon size={20} color={colors.onPrimary} />
          <Text style={[styles.buttonText, { color: colors.onPrimary }]}>
            {mode === 'app' ? 'Reiniciar' : 'Reintentar'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity 
          style={[styles.secondaryButton, { borderColor: colors.outline }]} 
          onPress={onShare}
        >
          <CopyIcon size={20} color={colors.onSurface} />
          <Text style={[styles.secondaryButtonText, { color: colors.onSurface }]}>
            Reportar
          </Text>
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: 24,
    gap: 16,
  },
  title: {
    fontSize: 22,
    fontWeight: '800',
    textAlign: 'center',
    marginTop: 8,
  },
  errorBox: {
    width: '100%',
    padding: 16,
    borderRadius: 8,
    borderWidth: 1,
    maxHeight: 200,
  },
  message: {
    fontSize: 14,
    textAlign: 'center',
    lineHeight: 20,
    fontFamily: Platform.OS === 'ios' ? 'Courier' : 'monospace',
  },
  buttonRow: {
    flexDirection: 'row',
    gap: 12,
    marginTop: 16,
  },
  button: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
  },
  secondaryButton: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 8,
    borderWidth: 1,
  },
  buttonText: {
    fontSize: 15,
    fontWeight: '700',
  },
  secondaryButtonText: {
    fontSize: 15,
    fontWeight: '700',
  },
});
