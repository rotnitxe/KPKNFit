// Polyfill para módulos de Node.js que WebLLM intenta importar
// Esto evita errores de build en el navegador

export const pathToFileURL = (path: string) => ({
    href: path,
    toString: () => path,
});

export default {
    pathToFileURL,
};
