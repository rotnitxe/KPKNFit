/**
 * KPKN Fit - Antigravity CLI Telegram Bot Bridge (REMOTE SHELL & TERMINAL)
 * 
 * Un bot de Telegram nativo (cero dependencias externas) que actúa como una 
 * TERMINAL REMOTA (Shell) interactiva para controlar el CLI y tu PC de forma 100% directa.
 * 
 * ❌ NO REQUIERE NINGUNA API KEY DE IA (Gemini, OpenAI, etc.).
 * 
 * Cómo funciona:
 * 1. Cualquier mensaje de texto que envíes al bot (que no sea un botón) se ejecutará 
 *    directamente como un comando en el terminal de tu PC (PowerShell/CMD).
 * 2. La salida (stdout/stderr) se captura en tiempo real y se envía a tu chat de Telegram.
 * 3. Incorpora botones táctiles nativos para acciones rápidas sin tener que escribir.
 * 
 * Botones Táctiles de Telegram:
 * - [🖥️ Estado PC]      : CPU, Memoria RAM y tareas activas en segundo plano.
 * - [📸 Pantalla]       : Captura de pantalla visual de tu PC en tiempo real.
 * - [📁 Carpeta Raíz]   : Cambia la carpeta de trabajo a la raíz del proyecto.
 * - [📁 Carpeta KOTLIN] : Cambia la carpeta de trabajo a 'android-native/' (Kotlin).
 * - [🛠️ Compilar Debug] : Compila el proyecto en la carpeta seleccionada.
 * - [⚡ Interrumpir]    : Mata/Detiene inmediatamente cualquier comando que se haya colgado.
 */

import https from 'https';
import fs from 'fs';
import path from 'path';
import { exec, spawn } from 'child_process';
import os from 'os';

// Cargar variables de entorno del archivo .env
try {
    const envPath = path.resolve(process.cwd(), '.env');
    if (fs.existsSync(envPath)) {
        const envContent = fs.readFileSync(envPath, 'utf8');
        envContent.split('\n').forEach(line => {
            const trimmed = line.trim();
            if (trimmed && !trimmed.startsWith('#')) {
                const parts = trimmed.split('=');
                if (parts.length >= 2) {
                    const key = parts[0].trim();
                    const value = parts.slice(1).join('=').trim();
                    process.env[key] = value;
                }
            }
        });
    }
} catch (e) {
    console.error("Error al cargar archivo .env:", e.message);
}

const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const AUTHORIZED_USER_ID = process.env.TELEGRAM_USER_ID;

if (!BOT_TOKEN) {
    console.error("\n❌ ERROR: La variable de entorno TELEGRAM_BOT_TOKEN no está configurada.\n");
    process.exit(1);
}

const BASE_URL = `https://api.telegram.org/bot${BOT_TOKEN}`;
let lastUpdateId = 0;

// Estado del Terminal Remoto
const ROOT_DIR = process.cwd();
const KOTLIN_DIR = path.resolve(ROOT_DIR, 'android-native');
let currentWorkDir = KOTLIN_DIR; // Carpeta de ejecución por defecto (Kotlin)

let activeChildProcess = null;
let activeCommandName = "";
let outputBuffer = "";
let debounceTimeout = null;

// Teclado Interactivo de Botones Nativos de Telegram
const KEYBOARD_MARKUP = {
    keyboard: [
        [{ text: '🖥️ Estado PC' }, { text: '📸 Pantalla' }],
        [{ text: '📁 Carpeta Raíz' }, { text: '📁 Carpeta KOTLIN' }],
        [{ text: '🛠️ Compilar Debug' }, { text: '⚡ Interrumpir' }]
    ],
    resize_keyboard: true,
    one_time_keyboard: false
};

// Helper para limpiar códigos de escape ANSI
function stripAnsiCodes(text) {
    return text.replace(/[\u001b\u009b][[()#;?]*(?:[0-9]{1,4}(?:;[0-9]{0,4})*)?[0-9A-ORZcf-nqry=><]/g, '');
}

// Helper de Telegram API
function apiRequest(methodName, params = {}, callback = null) {
    const url = `${BASE_URL}/${methodName}`;
    const payload = JSON.stringify(params);

    const req = https.request(url, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Content-Length': Buffer.byteLength(payload)
        }
    }, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
            try {
                const parsed = JSON.parse(body);
                if (callback) callback(null, parsed);
            } catch (err) {
                if (callback) callback(err, null);
            }
        });
    });

    req.on('error', err => {
        if (callback) callback(err, null);
    });

    req.write(payload);
    req.end();
}

function sendMessage(chatId, text, showKeyboard = true) {
    const cleanText = stripAnsiCodes(text);
    
    // Dividir mensajes extremadamente largos para evitar límites de Telegram (4096 caracteres)
    if (cleanText.length > 4000) {
        const chunks = [];
        let remaining = cleanText;
        while (remaining.length > 0) {
            chunks.push(remaining.substring(0, 3900));
            remaining = remaining.substring(3900);
        }
        
        chunks.forEach((chunk, index) => {
            const pageInfo = `\n\n[Parte ${index + 1} de ${chunks.length}]`;
            const params = {
                chat_id: chatId,
                text: `<pre>${chunk}${pageInfo}</pre>`,
                parse_mode: 'HTML'
            };
            if (showKeyboard && index === chunks.length - 1) {
                params.reply_markup = KEYBOARD_MARKUP;
            }
            apiRequest('sendMessage', params);
        });
    } else {
        const params = {
            chat_id: chatId,
            text: cleanText.startsWith('<') ? cleanText : `<pre>${cleanText}</pre>`,
            parse_mode: 'HTML'
        };
        if (showKeyboard) {
            params.reply_markup = KEYBOARD_MARKUP;
        }
        apiRequest('sendMessage', params);
    }
}

function sendPhoto(chatId, photoPath, caption = '') {
    if (!fs.existsSync(photoPath)) {
        sendMessage(chatId, `❌ Error: No se encontró la imagen en ${photoPath}`);
        return;
    }

    const photoBuffer = fs.readFileSync(photoPath);
    const boundary = '----TelegramBotBoundary' + Math.random().toString(36).substring(2);
    const filename = path.basename(photoPath);

    const payloadHeader = 
        `--${boundary}\r\n` +
        `Content-Disposition: form-data; name="chat_id"\r\n\r\n${chatId}\r\n` +
        `--${boundary}\r\n` +
        `Content-Disposition: form-data; name="caption"\r\n\r\n${caption}\r\n` +
        `--${boundary}\r\n` +
        `Content-Disposition: form-data; name="photo"; filename="${filename}"\r\n` +
        `Content-Type: image/png\r\n\r\n`;
    
    const payloadFooter = `\r\n--${boundary}--\r\n`;

    const requestBody = Buffer.concat([
        Buffer.from(payloadHeader, 'utf8'),
        photoBuffer,
        Buffer.from(payloadFooter, 'utf8')
    ]);

    const req = https.request(`${BASE_URL}/sendPhoto`, {
        method: 'POST',
        headers: {
            'Content-Type': `multipart/form-data; boundary=${boundary}`,
            'Content-Length': requestBody.length
        }
    }, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
            try { fs.unlinkSync(photoPath); } catch(e){}
        });
    });

    req.write(requestBody);
    req.end();
}

// Tomar captura de pantalla nativa
function takeScreenshot(outputPath, callback) {
    const psScript = `
Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing
$screen = [System.Windows.Forms.Screen]::PrimaryScreen
$bounds = $screen.Bounds
$bitmap = New-Object System.Drawing.Bitmap $bounds.Width, $bounds.Height
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.CopyFromScreen($bounds.Location, [System.Drawing.Point]::Empty, $bounds.Size)
$bitmap.Save("${outputPath.replace(/\\/g, '\\\\')}", [System.Drawing.Imaging.ImageFormat]::Png)
$graphics.Dispose()
$bitmap.Dispose()
`;

    const tempPsFile = path.resolve(os.tmpdir(), 'take_screenshot.ps1');
    fs.writeFileSync(tempPsFile, psScript, 'utf8');

    exec(`powershell -NoProfile -ExecutionPolicy Bypass -File "${tempPsFile}"`, (error) => {
        try { fs.unlinkSync(tempPsFile); } catch (e) {}
        callback(error);
    });
}

// Interrumpir cualquier proceso de consola colgado en el PC
function interruptConsoleProcess(chatId) {
    if (!activeChildProcess) {
        sendMessage(chatId, "ℹ️ No hay ningún comando ejecutándose para interrumpir.");
        return;
    }

    const pid = activeChildProcess.pid;
    sendMessage(chatId, `⚡ <b>Interrumpiendo proceso:</b> <code>${activeCommandName}</code> (PID: ${pid})...`);

    exec(`taskkill /F /T /PID ${pid}`, (err) => {
        if (err) {
            try {
                activeChildProcess.kill('SIGKILL');
                sendMessage(chatId, "✅ Proceso forzado a terminar mediante SIGKILL.");
            } catch (e) {
                sendMessage(chatId, `❌ Fallo al terminar: ${e.message}`);
            }
        } else {
            sendMessage(chatId, `✅ <b>Comando de consola terminado con éxito.</b>`);
        }
        activeChildProcess = null;
        activeCommandName = "";
        outputBuffer = "";
    });
}

// Ejecutar comandos de consola de forma asíncrona e interactiva
function runConsoleCommand(chatId, fullCommand) {
    if (activeChildProcess) {
        sendMessage(chatId, `⚠️ Ya hay un comando ejecutándose: <code>${activeCommandName}</code>.\nUsa [⚡ Interrumpir] para cancelarlo primero.`);
        return;
    }

    sendMessage(chatId, `⏳ <b>Ejecutando en PC:</b> <code>${fullCommand}</code>\n📂 Carpeta: <code>${path.relative(ROOT_DIR, currentWorkDir) || '[Raíz]'}</code>`);
    
    activeCommandName = fullCommand;
    outputBuffer = "";

    // Adaptar comando de Gradlew en Windows
    let cmd = fullCommand;
    if (os.platform() === 'win32' && fullCommand.startsWith('.\\gradlew')) {
        cmd = `powershell.exe -NoProfile -ExecutionPolicy Bypass -Command "${fullCommand}"`;
    }

    try {
        const child = exec(cmd, { cwd: currentWorkDir });
        activeChildProcess = child;

        const handleData = (data) => {
            const str = data.toString();
            
            // Ocultar líneas de advertencia de npm repetitivas para mantener el chat limpio
            const filteredLines = str.split('\n').filter(line => {
                const lower = line.toLowerCase();
                return !(lower.includes('npm warn') || lower.includes('unknown project config') || lower.includes('sharp_binary_host'));
            });

            const cleanStr = filteredLines.join('\n');
            if (cleanStr.trim()) {
                outputBuffer += cleanStr + '\n';

                // Debounce de 1.8 segundos para enviar resultados de consola en bloques limpios
                if (debounceTimeout) clearTimeout(debounceTimeout);
                debounceTimeout = setTimeout(() => {
                    if (outputBuffer.trim()) {
                        sendMessage(chatId, outputBuffer);
                        outputBuffer = "";
                    }
                }, 1800);
            }
        };

        child.stdout.on('data', handleData);
        child.stderr.on('data', handleData);

        child.on('close', (code) => {
            if (activeChildProcess === child) {
                if (debounceTimeout) clearTimeout(debounceTimeout);
                
                let finalOut = outputBuffer.trim();
                if (code === 0) {
                    sendMessage(chatId, `✅ <b>Comando Completado (Código 0):</b>\n<code>${activeCommandName}</code>\n\n${finalOut ? `<pre>${finalOut}</pre>` : '[Sin salida]'}`);
                } else {
                    sendMessage(chatId, `❌ <b>Comando Fallido (Código ${code}):</b>\n<code>${activeCommandName}</code>\n\n${finalOut ? `<pre>${finalOut}</pre>` : '[Sin salida]'}`);
                }
                activeChildProcess = null;
                activeCommandName = "";
                outputBuffer = "";
            }
        });

        child.on('error', (err) => {
            sendMessage(chatId, `❌ <b>Error al lanzar comando:</b> ${err.message}`);
            activeChildProcess = null;
            activeCommandName = "";
            outputBuffer = "";
        });

    } catch (e) {
        sendMessage(chatId, `❌ Error excepcional en terminal: ${e.message}`);
        activeChildProcess = null;
        activeCommandName = "";
        outputBuffer = "";
    }
}

// Procesador de mensajería entrante
function processMessage(msg) {
    const chatId = msg.chat.id;
    const userId = msg.from.id.toString();
    const text = msg.text ? msg.text.trim() : '';

    if (AUTHORIZED_USER_ID && userId !== AUTHORIZED_USER_ID) {
        sendMessage(chatId, `🛑 <b>Acceso denegado.</b> Tu ID no está autorizado.`);
        return;
    }

    if (!text) return;

    // Menú de Botones y Comandos del Terminal
    switch (text) {
        case '/start':
            sendMessage(chatId, `
👋 <b>¡Bienvenido al Terminal Remoto de tu PC!</b>

Cualquier mensaje que escribas aquí se ejecutará directamente como un comando de consola en tu computadora en la carpeta seleccionada.

📂 <b>Carpeta de Trabajo Actual:</b> <code>${path.relative(ROOT_DIR, currentWorkDir) || '[Raíz del Proyecto]'}</code>

Usa los botones inferiores para diagnósticos y accesos rápidos sin escribir.
`);
            break;

        case '🖥️ Estado PC':
            const totalMem = Math.round(os.totalmem() / 1024 / 1024 / 1024);
            const freeMem = Math.round(os.freemem() / 1024 / 1024 / 1024);
            sendMessage(chatId, `
🖥️ <b>PC de Desarrollo:</b>
• <b>Sistema:</b> ${os.type()} (${os.arch()})
• <b>RAM:</b> ${freeMem}GB libres de ${totalMem}GB
• <b>Carpeta Activa:</b> <code>${path.relative(ROOT_DIR, currentWorkDir) || '[Raíz]'}</code>
• <b>Tarea Activa en Consola:</b> ${activeChildProcess ? `🟢 corriendo <code>${activeCommandName}</code>` : '⚪ Ninguna'}
`);
            break;

        case '📸 Pantalla':
            sendMessage(chatId, "📸 Capturando pantalla...");
            const photoPath = path.resolve(os.tmpdir(), 'kpkn_screenshot.png');
            takeScreenshot(photoPath, (err) => {
                if (err) {
                    sendMessage(chatId, `❌ Error de captura: ${err.message}`);
                } else {
                    sendPhoto(chatId, photoPath, `🖥️ PC - ${new Date().toLocaleTimeString()}`);
                }
            });
            break;

        case '📁 Carpeta Raíz':
            currentWorkDir = ROOT_DIR;
            sendMessage(chatId, `📂 <b>Carpeta de trabajo cambiada a Raíz del Proyecto:</b>\n<code>${currentWorkDir}</code>`);
            break;

        case '📁 Carpeta KOTLIN':
            currentWorkDir = KOTLIN_DIR;
            sendMessage(chatId, `📂 <b>Carpeta de trabajo cambiada a Kotlin (android-native):</b>\n<code>${currentWorkDir}</code>`);
            break;

        case '🛠️ Compilar Debug':
            if (currentWorkDir === KOTLIN_DIR) {
                runConsoleCommand(chatId, '.\\gradlew.bat :app:bundleDebug');
            } else {
                runConsoleCommand(chatId, 'npm run build');
            }
            break;

        case '⚡ Interrumpir':
            interruptConsoleProcess(chatId);
            break;

        default:
            // Cualquier texto libre enviado por el usuario se ejecuta directamente como comando de consola en el PC
            runConsoleCommand(chatId, text);
            break;
    }
}

function pollUpdates() {
    const url = `${BASE_URL}/getUpdates?offset=${lastUpdateId + 1}&timeout=30`;

    https.get(url, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
            try {
                const data = JSON.parse(body);
                if (data.ok && data.result.length > 0) {
                    data.result.forEach(update => {
                        lastUpdateId = Math.max(lastUpdateId, update.update_id);
                        if (update.message) {
                            processMessage(update.message);
                        }
                    });
                }
            } catch (err) {
                console.error("Error updates:", err.message);
            }
            setTimeout(pollUpdates, 100);
        });
    }).on('error', err => {
        setTimeout(pollUpdates, 5000);
    });
}

// Inicio
console.log("====================================================");
console.log("🤖 Iniciando Terminal Remoto Interactivo (Shell Bridge)...");
console.log(`📂 Carpeta por defecto: ${currentWorkDir}`);
console.log("====================================================");

apiRequest('getMe', {}, (err, data) => {
    if (err) {
        console.error("❌ Fallo en conexión:", err.message);
        process.exit(1);
    }
    if (data.ok) {
        console.log(`✅ Terminal Remoto Conectado a Telegram. Bot: @${data.result.username}`);
        pollUpdates();
    } else {
        console.error("❌ Token inválido:", data.description);
        process.exit(1);
    }
});
