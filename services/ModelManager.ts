/**
 * ModelManager - Gestor de Modelos de IA Local
 * 
 * Funcionalidades:
 * - Verificar existencia del modelo en public/models/
 * - Verificar integridad por tamaño (±5% tolerancia)
 * - Reportar estado de carga para UI
 * 
 * NOTA: El modelo está incluido en el bundle desde el inicio,
 * no hay descarga externa.
 */

export class ModelManager {
    private readonly MODEL_PATH = './models/qwen2.5-1.5b-instruct-q4_k_m.gguf';
    private readonly EXPECTED_SIZE_MIN = 1000000000; // ~1GB (mínimo)
    private readonly EXPECTED_SIZE_MAX = 1300000000; // ~1.3GB (máximo)

    /**
     * Verificar que el modelo existe y tiene tamaño válido
     */
    async verifyModelExists(): Promise<boolean> {
        try {
            const response = await fetch(this.MODEL_PATH, { method: 'HEAD' });
            
            if (!response.ok) {
                console.error('❌ Modelo no encontrado:', this.MODEL_PATH);
                return false;
            }

            const size = parseInt(response.headers.get('content-length') || '0');
            
            if (size === 0) {
                console.error('❌ Modelo tiene tamaño 0');
                return false;
            }

            // Verificar tamaño dentro del rango esperado
            if (size < this.EXPECTED_SIZE_MIN || size > this.EXPECTED_SIZE_MAX) {
                console.error(
                    `❌ Tamaño incorrecto: ${this.formatBytes(size)} (esperado: ${this.formatBytes(this.EXPECTED_SIZE_MIN)} - ${this.formatBytes(this.EXPECTED_SIZE_MAX)})`
                );
                return false;
            }

            console.log(
                `✅ Modelo verificado: ${this.formatBytes(size)} (${this.MODEL_PATH})`
            );
            return true;

        } catch (error) {
            console.error('❌ Error verificando modelo:', error);
            return false;
        }
    }

    /**
     * Obtener información del modelo (tamaño, path, etc.)
     */
    async getModelInfo(): Promise<{ exists: boolean; size?: string; path: string }> {
        try {
            const response = await fetch(this.MODEL_PATH, { method: 'HEAD' });
            
            if (!response.ok) {
                return { exists: false, path: this.MODEL_PATH };
            }

            const size = parseInt(response.headers.get('content-length') || '0');
            
            return {
                exists: true,
                size: this.formatBytes(size),
                path: this.MODEL_PATH,
            };
        } catch {
            return { exists: false, path: this.MODEL_PATH };
        }
    }

    /**
     * Formatear bytes a MB/GB legible
     */
    private formatBytes(bytes: number): string {
        const mb = bytes / (1024 * 1024);
        const gb = mb / 1024;
        
        if (gb >= 1) {
            return `${gb.toFixed(2)} GB`;
        }
        return `${mb.toFixed(0)} MB`;
    }

    /**
     * Verificar espacio disponible en disco (si el navegador lo permite)
     */
    async checkStorage(): Promise<{ available?: number; usage?: number }> {
        if ('storage' in navigator && 'estimate' in navigator.storage) {
            try {
                const estimate = await navigator.storage.estimate();
                const usage = estimate.usage || 0;
                const quota = estimate.quota || 0;
                const available = quota - usage;

                return {
                    available,
                    usage,
                };
            } catch {
                return {};
            }
        }
        return {};
    }

    /**
     * Verificar si hay suficiente espacio para el modelo
     */
    async hasEnoughSpace(): Promise<boolean> {
        const storage = await this.checkStorage();
        
        if (storage.available !== undefined) {
            // Necesitamos al menos 1.5GB libre (modelo + overhead)
            const requiredSpace = 1.5 * 1024 * 1024 * 1024;
            return storage.available >= requiredSpace;
        }

        // Si no podemos verificar, asumir que sí hay espacio
        return true;
    }

    /**
     * Reportar estado completo para UI
     */
    async getFullStatus(): Promise<{
        exists: boolean;
        size?: string;
        enoughSpace: boolean;
        hasWebGPU: boolean;
        ready: boolean;
    }> {
        const [modelInfo, hasEnoughSpace, hasWebGPU] = await Promise.all([
            this.getModelInfo(),
            this.hasEnoughSpace(),
            this.checkWebGPU(),
        ]);

        return {
            exists: modelInfo.exists,
            size: modelInfo.size,
            enoughSpace: hasEnoughSpace,
            hasWebGPU,
            ready: modelInfo.exists && hasEnoughSpace && hasWebGPU,
        };
    }

    /**
     * Verificar soporte WebGPU
     */
    private async checkWebGPU(): Promise<boolean> {
        try {
            if (!navigator.gpu) return false;
            const adapter = await navigator.gpu.requestAdapter();
            return !!adapter;
        } catch {
            return false;
        }
    }
}

// Exportar instancia única
export const modelManager = new ModelManager();
