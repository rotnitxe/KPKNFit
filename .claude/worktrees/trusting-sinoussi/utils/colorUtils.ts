// utils/colorUtils.ts
import React, { useState, useEffect } from 'react';

// A robust function for color manipulation (shading, blending).
// Source: https://github.com/PimpTrizkit/PJs/wiki/12.-Shade,-Blend-and-Convert-a-Web-Color-(pSBC.js)
// p: percentage (0 to 1 for blend, -1 to 1 for shade)
// from: color string (hex, rgb, etc.)
// to: color string to blend with (optional)
// useLinear: use linear blending (optional)
export const pSBC = (p: number, from: string, to?: string | null, useLinear?: boolean): string | null => {
    let r: number, g: number, b: number;
    let P: boolean, f: any, t: any, h: boolean;
    const pInt = parseInt;
    const m = Math.round;
    const a = typeof (to) === "string";

    if (typeof (p) !== "number" || p < -1 || p > 1 || typeof (from) !== "string" || (from[0] !== 'r' && from[0] !== '#') || (a && to && to[0] !== 'r' && to[0] !== '#')) return null;
    
    const pSBCr = (d: string): { [key: number]: number } | null => {
        const l = d.length;
        const RGB: { [key: number]: number } = {};
        if (l > 9) {
            const dArr = d.split(",");
            if (dArr.length < 3 || dArr.length > 4) return null;
            RGB[0] = pInt(dArr[0].slice(4));
            RGB[1] = pInt(dArr[1]);
            RGB[2] = pInt(dArr[2]);
            RGB[3] = dArr[3] ? parseFloat(dArr[3]) : -1;
        } else {
            let dStr = d;
            if (l === 8 || l === 6 || l < 4) return null;
            if (l < 6) dStr = "#" + dStr[1] + dStr[1] + dStr[2] + dStr[2] + dStr[3] + dStr[3] + (l > 4 ? dStr[4] + dStr[4] : "");
            const numD = pInt(dStr.slice(1), 16);
            if (l === 9 || l === 5) {
                RGB[0] = numD >> 24 & 255;
                RGB[1] = numD >> 16 & 255;
                RGB[2] = numD >> 8 & 255;
                RGB[3] = m(((numD & 255) / 0.255)) / 1000;
            } else {
                RGB[0] = numD >> 16;
                RGB[1] = (numD >> 8) & 255;
                RGB[2] = numD & 255;
                RGB[3] = -1;
            }
        }
        return RGB;
    };

    let h1 = from.length > 9;
    h1 = a ? (to && to.length > 9 ? true : to && to.length > 7 ? false : true) : h1;
    h = h1;
    const rgbType = h ? "rgba" : "rgb";
    f = pSBCr(from);
    P = p < 0;
    t = to ? pSBCr(to) : (P ? {0:0,1:0,2:0,3:-1} : {0:255,1:255,2:255,3:-1});
    const pAbs = P ? p * -1 : p;
    
    if (!f || !t) return null;

    if (useLinear) {
        r = m(f[0] + (t[0] - f[0]) * pAbs);
        g = m(f[1] + (t[1] - f[1]) * pAbs);
        b = m(f[2] + (t[2] - f[2]) * pAbs);
    } else {
        r = m(Math.sqrt(f[0] * f[0] * (1 - pAbs) + t[0] * t[0] * pAbs));
        g = m(Math.sqrt(f[1] * f[1] * (1 - pAbs) + t[1] * t[1] * pAbs));
        b = m(Math.sqrt(f[2] * f[2] * (1 - pAbs) + t[2] * t[2] * pAbs));
    }
    
    const alpha = (f[3] < 0 && t[3] < 0) ? null : f[3] < 0 ? t[3] : t[3] < 0 ? f[3] : f[3] + (t[3] - f[3]) * pAbs;

    if (h && alpha !== null) return `${rgbType}(${r},${g},${b},${m(alpha * 1000) / 1000})`;
    else return `${rgbType}(${r},${g},${b})`;
};

export const useImageGradient = (imageUrl?: string): { gradient: string; isLoading: boolean } => {
    const [gradient, setGradient] = useState<string>('linear-gradient(135deg, #1e293b, #0f172a)');
    const [isLoading, setIsLoading] = useState(false);

    useEffect(() => {
        if (!imageUrl) {
            setGradient('linear-gradient(135deg, #1e293b, #0f172a)');
            return;
        }

        let isMounted = true;
        setIsLoading(true);

        const img = new Image();
        img.crossOrigin = 'Anonymous';
        img.src = imageUrl;

        img.onload = () => {
            if (!isMounted) return;

            const canvas = document.createElement('canvas');
            const size = 10;
            canvas.width = size;
            canvas.height = size;
            const ctx = canvas.getContext('2d', { willReadFrequently: true });
            if (!ctx) {
                 setIsLoading(false);
                 setGradient('linear-gradient(135deg, #1e293b, #0f172a)');
                 return;
            }

            ctx.drawImage(img, 0, 0, size, size);
            
            const toHex = (r: number, g: number, b: number) => `#${[r, g, b].map(x => x.toString(16).padStart(2, '0')).join('')}`;
            
            const center = ctx.getImageData(size / 2, size / 2, 1, 1).data;
            const topLeft = ctx.getImageData(1, 1, 1, 1).data;
            const bottomRight = ctx.getImageData(size - 2, size - 2, 1, 1).data;

            const color1 = toHex(center[0], center[1], center[2]);
            const color2 = toHex(topLeft[0], topLeft[1], topLeft[2]);
            const color3 = toHex(bottomRight[0], bottomRight[1], bottomRight[2]);
            
            const darkColor1 = pSBC(-0.6, color1) || '#111827'; // Darken by 60%, fallback to dark gray
            const darkColor2 = pSBC(-0.7, color2) || '#000000'; // Darken by 70%, fallback to black
            const darkColor3 = pSBC(-0.5, color3) || '#1f2937'; // Darken by 50%

            const newGradient = `radial-gradient(circle at top left, ${darkColor2} 0%, transparent 50%), radial-gradient(circle at bottom right, ${darkColor3} 0%, transparent 40%), linear-gradient(160deg, ${darkColor1}, #000000)`;
            
            setGradient(newGradient);
            setIsLoading(false);
        };

        img.onerror = () => {
            if (!isMounted) return;
            setGradient('linear-gradient(135deg, #1e293b, #0f172a)');
            setIsLoading(false);
        };

        return () => { isMounted = false; };
    }, [imageUrl]);

    return { gradient, isLoading };
};
