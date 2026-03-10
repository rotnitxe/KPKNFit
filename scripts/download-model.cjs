#!/usr/bin/env node

/**
 * Script de descarga automática del modelo Qwen2.5-1.5B
 * 
 * Se ejecuta durante el build para descargar el modelo desde HuggingFace
 * sin incluirlo en el repositorio de Git.
 */

const https = require('https');
const fs = require('fs');
const path = require('path');

const MODEL_URL = 'https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf';
const MODEL_PATH = path.join(__dirname, '..', 'public', 'models', 'qwen2.5-1.5b-instruct-q4_k_m.gguf');
const WWW_MODEL_PATH = path.join(__dirname, '..', 'www', 'models', 'qwen2.5-1.5b-instruct-q4_k_m.gguf');

// Tamaño esperado: ~1.1GB
const EXPECTED_SIZE_MIN = 1000000000;
const EXPECTED_SIZE_MAX = 1300000000;

function log(message) {
    console.log(`[Model Download] ${message}`);
}

function ensureDirectoryExists(filePath) {
    const dir = path.dirname(filePath);
    if (!fs.existsSync(dir)) {
        fs.mkdirSync(dir, { recursive: true });
        log(`Directorio creado: ${dir}`);
    }
}

function downloadFile(url, dest) {
    return new Promise((resolve, reject) => {
        ensureDirectoryExists(dest);
        
        log(`Iniciando descarga desde HuggingFace...`);
        log(`URL: ${url}`);
        log(`Destino: ${dest}`);
        
        const file = fs.createWriteStream(dest);
        let downloadedBytes = 0;
        let totalBytes = 0;
        let lastProgress = 0;
        
        https.get(url, (response) => {
            if (response.statusCode !== 200) {
                reject(new Error(`Error HTTP: ${response.statusCode}`));
                return;
            }
            
            totalBytes = parseInt(response.headers['content-length'] || '0', 10);
            log(`Tamaño total: ${(totalBytes / 1024 / 1024 / 1024).toFixed(2)} GB`);
            
            response.on('data', (chunk) => {
                downloadedBytes += chunk.length;
                
                // Mostrar progreso cada 5%
                const progress = Math.round((downloadedBytes / totalBytes) * 100);
                if (progress >= lastProgress + 5) {
                    log(`Progreso: ${progress}% (${(downloadedBytes / 1024 / 1024 / 1024).toFixed(2)} GB / ${(totalBytes / 1024 / 1024 / 1024).toFixed(2)} GB)`);
                    lastProgress = progress;
                }
            });
            
            response.pipe(file);
            
            file.on('finish', () => {
                file.close();
                
                // Verificar tamaño
                const stats = fs.statSync(dest);
                log(`Descarga completada: ${(stats.size / 1024 / 1024 / 1024).toFixed(2)} GB`);
                
                if (stats.size < EXPECTED_SIZE_MIN || stats.size > EXPECTED_SIZE_MAX) {
                    reject(new Error(`Tamaño inválido: ${stats.size} bytes (esperado: ${EXPECTED_SIZE_MIN}-${EXPECTED_SIZE_MAX})`));
                    return;
                }
                
                log('✅ Verificación de integridad: OK');
                resolve(dest);
            });
        }).on('error', (err) => {
            fs.unlink(dest, () => {}); // Eliminar archivo incompleto
            reject(err);
        });
    });
}

async function copyToWww() {
    const wwwDir = path.dirname(WWW_MODEL_PATH);
    ensureDirectoryExists(WWW_MODEL_PATH);
    
    // Copiar de public/models a www/models
    if (fs.existsSync(MODEL_PATH)) {
        fs.copyFileSync(MODEL_PATH, WWW_MODEL_PATH);
        log(`✅ Modelo copiado a www/models/`);
    } else {
        log(`⚠️ Modelo no existe en public/models/, saltando copia`);
    }
}

async function main() {
    try {
        // Verificar si ya existe
        if (fs.existsSync(MODEL_PATH)) {
            const stats = fs.statSync(MODEL_PATH);
            const sizeGB = (stats.size / 1024 / 1024 / 1024).toFixed(2);
            
            if (stats.size >= EXPECTED_SIZE_MIN && stats.size <= EXPECTED_SIZE_MAX) {
                log(`✅ Modelo ya existe (${sizeGB} GB), saltando descarga`);
                
                // Siempre copiar a www/models
                await copyToWww();
                
                log('✅ ¡Modelo listo para usar!');
                return;
            } else {
                log(`⚠️ Modelo existe pero tiene tamaño inválido (${sizeGB} GB), re-descargando...`);
                fs.unlinkSync(MODEL_PATH);
            }
        }
        
        // Descargar modelo
        await downloadFile(MODEL_URL, MODEL_PATH);
        
        // Copiar a www/models
        await copyToWww();
        
        log('✅ ¡Modelo listo para usar!');
        
    } catch (error) {
        log(`❌ Error: ${error.message}`);
        log('El build continuará sin el modelo. La IA no estará disponible hasta que se descargue manualmente.');
        
        // No fallar el build, solo advertir
        // process.exit(0);
    }
}

main();
