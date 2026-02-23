/** @type {import('tailwindcss').Config} */
export default {
  content: [
    './index.html',
    './index.tsx',
    './components/**/*.{tsx,ts,jsx,js}',
  ],
  theme: {
    extend: {
      colors: {
        primary: 'var(--primary-color)',
        cyber: {
          canvas: '#0A0B0E',
          card: '#15171E',
          border: '#2A2D38',
          cyan: '#00F0FF',
          copper: '#FF7B00',
          success: '#00FF9D',
          warning: '#FFD600',
          danger: '#FF2E43',
        },
      },
      fontFamily: {
        mono: ['var(--font-family-data)', 'monospace'],
      },
    },
  },
  plugins: [],
};
