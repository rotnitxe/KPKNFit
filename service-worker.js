const CACHE_NAME = 'yourprime-v20';
const urlsToCache = [
  '/',
  '/index.html',
  '/index.js',
  '/manifest.json',
  '/favicon.svg',
  'widget-template.html',
  '/output.css',
  'https://fonts.googleapis.com/css2?family=Bebas+Neue&family=Oswald:wght@400;700&family=Roboto+Condensed:wght@400;700&family=Great+Vibes&family=Tahoma:wght@400;700&family=Courier+Prime:wght@400;700&display=swap',

  // Google Drive API scripts
  'https://apis.google.com/js/api.js',
  'https://accounts.google.com/gsi/client',

  // Audio files
  'https://actions.google.com/sounds/v1/alarms/digital_watch_alarm_long.ogg',
  'https://actions.google.com/sounds/v1/alarms/alarm_clock.ogg',
  'https://actions.google.com/sounds/v1/impacts/crash.ogg',
  'https://actions.google.com/sounds/v1/emergency/beeper_confirm.ogg',
  'https://actions.google.com/sounds/v1/alarms/bugle_tune.ogg',
  'https://actions.google.com/sounds/v1/switches/switch_toggle_on.ogg',
  'https://actions.google.com/sounds/v1/ui/ui_tap_forward.ogg',
  'https://actions.google.com/sounds/v1/ui/ui_tap_reverse.ogg'
];

self.addEventListener('install', event => {
  self.skipWaiting(); // OBLIGA A TOMAR EL CONTROL INMEDIATAMENTE AL ABRIR LA APP
  event.waitUntil(
    caches.open(CACHE_NAME)
      .then(cache => {
        console.log('Opened cache and caching essential assets');
        return cache.addAll(urlsToCache);
      })
      .catch(error => {
        console.error('Failed to cache essential assets during install:', error);
      })
  );
});

self.addEventListener('activate', event => {
  const cacheWhitelist = [CACHE_NAME];
  event.waitUntil(
    caches.keys().then(cacheNames => {
      return Promise.all(
        cacheNames.map(cacheName => {
          if (cacheWhitelist.indexOf(cacheName) === -1) {
            console.log('Deleting old cache:', cacheName);
            return caches.delete(cacheName);
          }
        })
      );
    }).then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', event => {
  if (event.request.method !== 'GET' || !event.request.url.startsWith('http')) {
    return;
  }

  event.respondWith(
    caches.open(CACHE_NAME).then(cache => {
      return cache.match(event.request).then(cachedResponse => {
        const fetchPromise = fetch(event.request).then(networkResponse => {
          if (networkResponse && networkResponse.ok) {
            cache.put(event.request, networkResponse.clone());
          }
          return networkResponse;
        });

        return cachedResponse || fetchPromise;
      });
    })
  );
});

// --- LÓGICA DE WIDGET DE PWA ---

// Se dispara cuando el widget se instala por primera vez
self.addEventListener('widgetinstall', event => {
    console.log('Widget instalado:', event.widget.definition.tag);
    // Fuerza una actualización inmediata al instalar
    event.waitUntil(updateWidget(event.widget.instanceId));
});

// Se dispara cuando el usuario hace clic en el widget
self.addEventListener('widgetclick', event => {
    console.log('Widget clickeado:', event.widget.instanceId);
    // Asumimos que todos los widgets abren la app
    event.waitUntil(clients.openWindow('/'));
});

// Se dispara cuando el widget es eliminado
self.addEventListener('widgetuninstall', event => {
    console.log('Widget desinstalado:', event.widget.instanceId);
});

// Función para actualizar los datos del widget
// En un escenario real, aquí harías fetch a tu API o IndexedDB para obtener datos reales.
async function updateWidget(instanceId) {
    // Obtener la definición del widget para saber qué tipo es
    const widget = await self.widgets.getByInstanceId(instanceId);
    if (!widget) {
        return;
    }
    const tag = widget.definition.tag;

    let template = '';
    let data = {};

    // Estilos comunes - Hevy-inspired: blanco, limpio, sin bordes llamativos
    const commonStyles = `
        <style>
            body {
                font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
                margin: 0; padding: 14px;
                background: #ffffff;
                color: #18181b;
                border-radius: 12px;
                height: 100%;
                box-sizing: border-box;
                display: flex;
                flex-direction: column;
                box-shadow: 0 1px 3px rgba(0,0,0,0.08);
            }
            h1, h2, h3, p { margin: 0; }
            .widget-title { font-size: 11px; color: #71717a; font-weight: 700; letter-spacing: 0.05em; text-transform: uppercase; margin-bottom: 8px; flex-shrink: 0; }
        </style>
    `;

    switch (tag) {
        case 'next_workout':
            data = {
                title: "Próxima Sesión (Hoy)",
                program: "Tu Programa de Fuerza",
                session: "Pecho y Tríceps (Intenso)"
            };
            template = `
                ${commonStyles}
                <style>
                    #widget-session-name { font-size: 17px; font-weight: 600; margin-top: 4px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; color: #18181b; }
                    #widget-program-name { font-size: 11px; color: #71717a; margin-top: 2px; }
                </style>
                <h1 class="widget-title">${data.title}</h1>
                <h2 id="widget-session-name">${data.session}</h2>
                <p id="widget-program-name">${data.program}</p>
            `;
            break;
        
        case 'macros_today':
            data = { calories: "1850", protein: "140g", carbs: "180g", fats: "60g" };
            template = `
                ${commonStyles}
                <style>
                    .macro-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; flex-grow: 1;}
                    .macro-item { text-align: center; }
                    .macro-value { font-size: 17px; font-weight: 600; color: #18181b; }
                    .macro-label { font-size: 10px; color: #71717a; text-transform: uppercase; letter-spacing: 0.03em; }
                    .calories { grid-column: 1 / -1; margin-bottom: 8px; text-align: center; }
                    .calories .macro-value { color: #18181b; font-size: 22px; }
                </style>
                <h1 class="widget-title">Macros de Hoy</h1>
                <div class="macro-grid">
                    <div class="macro-item calories">
                        <p class="macro-value">${data.calories}</p>
                        <p class="macro-label">KCAL</p>
                    </div>
                    <div class="macro-item">
                        <p class="macro-value">${data.protein}</p>
                        <p class="macro-label">Proteína</p>
                    </div>
                    <div class="macro-item">
                        <p class="macro-value">${data.carbs}</p>
                        <p class="macro-label">Carbs</p>
                    </div>
                    <div class="macro-item">
                        <p class="macro-value">${data.fats}</p>
                        <p class="macro-label">Grasas</p>
                    </div>
                </div>
            `;
            break;
            
        case 'muscle_battery':
            data = {
                muscles: [
                    { name: "Pectoral", score: 35 },
                    { name: "Cuádriceps", score: 60 },
                    { name: "Dorsales", score: 85 }
                ]
            };
            template = `
                ${commonStyles}
                <style>
                    .muscle-list { display: flex; flex-direction: column; gap: 6px; flex-grow: 1; justify-content: center; }
                    .muscle-item { display: flex; align-items: center; justify-content: space-between; padding: 4px 0; }
                    .muscle-name { font-size: 13px; font-weight: 500; color: #18181b; }
                    .muscle-score { font-size: 14px; font-weight: 600; }
                    .score-red { color: #dc2626; }
                    .score-yellow { color: #ca8a04; }
                    .score-green { color: #16a34a; }
                </style>
                <h1 class="widget-title">Batería Muscular</h1>
                <div class="muscle-list">
                    ${data.muscles.map(m => `
                        <div class="muscle-item">
                            <span class="muscle-name">${m.name}</span>
                            <span class="muscle-score ${m.score < 40 ? 'score-red' : m.score < 75 ? 'score-yellow' : 'score-green'}">${m.score}%</span>
                        </div>
                    `).join('')}
                </div>
            `;
            break;

        case 'effective_volume':
            data = { completed: 12, planned: 18 };
            const percentage = data.planned > 0 ? (data.completed / data.planned) * 100 : 0;
            template = `
                ${commonStyles}
                <style>
                    .volume-container { flex-grow: 1; display: flex; flex-direction: column; justify-content: center; }
                    .volume-summary { font-size: 24px; font-weight: 600; text-align: center; margin: 4px 0; color: #18181b; }
                    .volume-label { font-size: 11px; color: #71717a; text-align: center; }
                    .progress-bar-bg { background: #e4e4e7; border-radius: 6px; height: 6px; margin-top: 8px; overflow: hidden; }
                    .progress-bar-fg { background: #3b82f6; border-radius: 6px; height: 100%; width: ${percentage}%; transition: width 0.3s ease; }
                </style>
                <h1 class="widget-title">Volumen Efectivo (Semanal)</h1>
                <div class="volume-container">
                    <p class="volume-summary">${data.completed} <span style="font-size: 16px; color: #71717a;">/ ${data.planned}</span></p>
                    <p class="volume-label">Series Efectivas</p>
                    <div class="progress-bar-bg">
                        <div class="progress-bar-fg"></div>
                    </div>
                </div>
            `;
            break;

        case 'volume_by_muscle':
            data = {
                muscles: [
                    { name: "Pectoral", volume: 24 },
                    { name: "Cuádriceps", volume: 18 },
                    { name: "Dorsales", volume: 12 }
                ]
            };
            const maxVol = Math.max(...data.muscles.map(m => m.volume), 1);
            template = `
                ${commonStyles}
                <style>
                    .muscle-volume-list { display: flex; flex-direction: column; gap: 6px; flex-grow: 1; justify-content: center; }
                    .muscle-volume-item { display: flex; align-items: center; gap: 8px; }
                    .muscle-volume-name { font-size: 12px; font-weight: 500; color: #18181b; width: 70px; flex-shrink: 0; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
                    .muscle-volume-bar-bg { flex: 1; height: 6px; background: #e4e4e7; border-radius: 6px; overflow: hidden; min-width: 0; }
                    .muscle-volume-bar-fg { height: 100%; background: linear-gradient(90deg, #0891b2, #06b6d4); border-radius: 6px; min-width: 4px; }
                    .muscle-volume-value { font-size: 11px; font-weight: 600; color: #0891b2; width: 24px; text-align: right; flex-shrink: 0; }
                </style>
                <h1 class="widget-title">Volumen por Músculo</h1>
                <div class="muscle-volume-list">
                    ${data.muscles.map(m => {
                        const pct = Math.max((m.volume / maxVol) * 100, 4);
                        return `
                        <div class="muscle-volume-item">
                            <span class="muscle-volume-name">${m.name}</span>
                            <div class="muscle-volume-bar-bg"><div class="muscle-volume-bar-fg" style="width:${pct}%"></div></div>
                            <span class="muscle-volume-value">${m.volume}</span>
                        </div>
                    `}).join('')}
                </div>
            `;
            break;
            
        default:
            console.log('Widget desconocido:', tag);
            return;
    }

    // Actualiza la instancia del widget con el nuevo HTML y datos
    return self.widgets.updateByInstanceId(instanceId, {
        template,
        data
    });
}
