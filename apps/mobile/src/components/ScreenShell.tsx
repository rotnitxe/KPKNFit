import React from 'react';
import { ScrollView, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

interface ScreenShellProps {
  title: string;
  subtitle?: string;
  children: React.ReactNode;
}

export function ScreenShell({ title, subtitle, children }: ScreenShellProps) {
  return (
    <SafeAreaView className="flex-1 bg-kpkn-bg">
      <ScrollView
        className="flex-1"
        contentContainerStyle={{ padding: 20, paddingBottom: 40 }}
        keyboardShouldPersistTaps="handled"
      >
        <View className="mb-6 gap-2">
          <Text className="text-3xl font-bold text-kpkn-text">{title}</Text>
          {subtitle ? (
            <Text className="text-base leading-6 text-kpkn-muted">{subtitle}</Text>
          ) : null}
        </View>
        {children}
      </ScrollView>
    </SafeAreaView>
  );
}
