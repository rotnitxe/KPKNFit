import React from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from '@tanstack/react-router';
import { router } from './routes/router';
import { App } from './App';
import { AppProvider } from './contexts/AppContext';
import ErrorBoundary from './components/ui/ErrorBoundary';
import { initSentry } from './services/sentryService';

initSentry();

const rootElement = document.getElementById('root');
if (!rootElement) {
  throw new Error("Could not find root element to mount to");
}

const root = createRoot(rootElement);
root.render(
  <React.StrictMode>
    <ErrorBoundary>
      <AppProvider>
        <RouterProvider router={router} />
        <App />
      </AppProvider>
    </ErrorBoundary>
  </React.StrictMode>
);

export default React;
