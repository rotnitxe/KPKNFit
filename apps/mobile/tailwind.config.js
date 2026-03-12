/** @type {import('tailwindcss').Config} */
module.exports = {
  presets: [require('nativewind/preset')],
  content: ['./App.tsx', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        kpkn: {
          bg: '#090B12',
          surface: '#121622',
          elevated: '#1A2030',
          border: 'rgba(255,255,255,0.08)',
          brand: '#00F0FF',
          text: '#F5F7FF',
          muted: '#A7B0C3',
          success: '#32D583',
          warning: '#F5B942',
        },
      },
      borderRadius: {
        card: '24px',
      },
    },
  },
  plugins: [],
};
