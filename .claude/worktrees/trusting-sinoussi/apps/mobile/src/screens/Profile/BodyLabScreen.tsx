import React from 'react';
import { ScreenShell } from '@/components/ScreenShell';
import { BodyLabView } from '@/components/body/BodyLabView';

export function BodyLabScreen() {
  return (
    <ScreenShell title="Body Lab" subtitle="Composición, FFMI, TDEE y proyección corporal.">
      <BodyLabView />
    </ScreenShell>
  );
}
