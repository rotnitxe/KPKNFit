// IMPORTANTE: Para habilitar la sincronización con Google Drive,
// debes crear un proyecto en Google Cloud Console, habilitar la API de Google Drive,
// y crear credenciales de "ID de cliente OAuth 2.0" para una aplicación web.
// Luego, pega el Client ID aquí.
//
// Guía Rápida:
// 1. Ve a https://console.cloud.google.com/
// 2. Crea un nuevo proyecto.
// 3. En el menú, ve a "APIs y servicios" > "Biblioteca". Busca y habilita "Google Drive API".
// 4. Ve a "APIs y servicios" > "Credenciales".
// 5. Haz clic en "Crear credenciales" > "ID de cliente de OAuth".
// 6. Selecciona "Aplicación web" como tipo de aplicación.
// 7. En "Orígenes de JavaScript autorizados", añade las URLs donde se ejecutará tu app.
//    - Para desarrollo local, añade "http://localhost:PUERTO" (ej. http://localhost:8080).
//    - Para la app desplegada, añade su URL.
// 8. Copia el "ID de cliente" que se genera y pégalo abajo.

export const GOOGLE_DRIVE_CLIENT_ID = 'YOUR_CLIENT_ID.apps.googleusercontent.com';

// No necesitas cambiar esto.
export const GOOGLE_DRIVE_SCOPES = 'https://www.googleapis.com/auth/drive.file';
