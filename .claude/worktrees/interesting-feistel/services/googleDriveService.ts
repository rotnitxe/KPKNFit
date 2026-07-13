
// --- Type Definitions for Google APIs ---
declare global {
  interface Window {
    gapi: any;
    google: any;
  }
}

// --- Constants ---
const DISCOVERY_DOCS = ["https://www.googleapis.com/discovery/v1/apis/drive/v3/rest"];
const DATA_FILE_NAME = 'kpkn.data.json';
export const GOOGLE_DRIVE_SCOPES = 'https://www.googleapis.com/auth/drive.file';


// --- Module State ---
let gapiInited = false;
let gisInited = false;
let tokenClient: any = null;

// --- Helper Promises for Script Loading ---
const gapiLoaded = new Promise<void>((resolve) => {
  const checkGapi = () => {
    if (window.gapi && window.gapi.client) {
      resolve();
    } else {
      setTimeout(checkGapi, 100);
    }
  };
  checkGapi();
});

const gisLoaded = new Promise<void>((resolve) => {
  const checkGis = () => {
    if (window.google && window.google.accounts) {
      resolve();
    } else {
      setTimeout(checkGis, 100);
    }
  };
  checkGis();
});

/**
 * Initializes the GAPI client.
 */
async function initializeGapiClient(): Promise<void> {
  await gapiLoaded;
  await window.gapi.client.init({
    discoveryDocs: DISCOVERY_DOCS,
  });
  gapiInited = true;
}

/**
 * Initializes the GIS client.
 */
export async function initializeGisClient(callback: (tokenResponse: any) => void, clientId: string | undefined): Promise<void> {
  if (!clientId || clientId.startsWith('YOUR')) return;
  await gisLoaded;
  tokenClient = window.google.accounts.oauth2.initTokenClient({
    client_id: clientId,
    scope: GOOGLE_DRIVE_SCOPES,
    callback: callback,
  });
  gisInited = true;
}

/**
 * Handles the sign-in process.
 */
export async function handleSignIn() {
  if (!gapiInited || !gisInited) {
    throw new Error("Cliente de Google no inicializado. Asegúrate de que el Client ID es correcto en Ajustes.");
  }
  if (window.gapi.client.getToken() === null) {
    tokenClient.requestAccessToken({ prompt: 'consent' });
  } else {
    tokenClient.requestAccessToken({ prompt: '' });
  }
}

/**
 * Handles the sign-out process.
 */
export function handleSignOut() {
  const token = window.gapi.client.getToken();
  if (token !== null) {
    window.google.accounts.oauth2.revoke(token.access_token, () => {
      window.gapi.client.setToken('');
    });
  }
}

/**
 * Fetches the user's profile information.
 */
export async function getProfile() {
    try {
        const response = await window.gapi.client.oauth2.userinfo.get();
        return response.result;
    } catch (e) {
        console.error("Error fetching profile", e);
        return null;
    }
}

/**
 * Searches for the app's data file in the appDataFolder.
 * @returns The file ID if found, otherwise undefined.
 */
async function searchFile(): Promise<string | undefined> {
  try {
    const response = await window.gapi.client.drive.files.list({
      q: `name='${DATA_FILE_NAME}' and trashed=false`,
      spaces: 'appDataFolder',
      fields: 'files(id, name, modifiedTime)',
    });
    if (response.result.files && response.result.files.length > 0) {
      return response.result.files[0].id;
    }
    return undefined;
  } catch (e) {
    console.error("Error searching for file:", e);
    throw new Error("No se pudo buscar el archivo en Google Drive.");
  }
}


/**
 * Uploads or updates the data file in Google Drive.
 * @param data The stringified JSON data to upload.
 */
export async function uploadData(data: string) {
    const fileId = await searchFile();
    const boundary = '-------314159265358979323846';
    const delimiter = "\r\n--" + boundary + "\r\n";
    const close_delim = "\r\n--" + boundary + "--";
    
    const metadata = {
        name: DATA_FILE_NAME,
        mimeType: 'application/json',
    };

    const multipartRequestBody =
        delimiter +
        'Content-Type: application/json\r\n\r\n' +
        JSON.stringify(metadata) +
        delimiter +
        'Content-Type: application/json\r\n\r\n' +
        data +
        close_delim;

    const path = fileId 
        ? `/upload/drive/v3/files/${fileId}?uploadType=multipart`
        : '/upload/drive/v3/files?uploadType=multipart&fields=id';

    const method = fileId ? 'PATCH' : 'POST';

    const request = window.gapi.client.request({
        path,
        method,
        params: { uploadType: 'multipart' },
        headers: {
            'Content-Type': 'multipart/related; boundary="' + boundary + '"'
        },
        body: multipartRequestBody
    });
    
    try {
        await request;
    } catch (e) {
        console.error("Error al subir los datos:", e);
        throw new Error("No se pudieron subir los datos a Google Drive.");
    }
}


/**
 * Downloads the data file from Google Drive.
 * @returns The parsed JSON data from the file.
 */
export async function downloadData() {
    const fileId = await searchFile();
    if (!fileId) {
        throw new Error("No se encontró ningún archivo de datos en Google Drive.");
    }

    try {
        const response = await window.gapi.client.drive.files.get({
            fileId: fileId,
            alt: 'media',
        });
        return JSON.parse(response.body);
    } catch (e) {
        console.error("Error al descargar los datos:", e);
        throw new Error("No se pudieron descargar los datos desde Google Drive.");
    }
}

/**
 * Gets the last modified time of the data file.
 * @returns ISO string of the last modified date, or null if not found.
 */
export async function getLastSyncDate(): Promise<string | null> {
  try {
    const response = await window.gapi.client.drive.files.list({
      q: `name='${DATA_FILE_NAME}' and trashed=false`,
      spaces: 'appDataFolder',
      fields: 'files(id, modifiedTime)',
    });
    if (response.result.files && response.result.files.length > 0) {
      return response.result.files[0].modifiedTime;
    }
    return null;
  } catch (e) {
    console.error("Error getting last sync date:", e);
    return null;
  }
}

// --- Initialization on Load ---
(async () => {
    await initializeGapiClient();
})();