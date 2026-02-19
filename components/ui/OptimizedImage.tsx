// components/ui/OptimizedImage.tsx
import React, { useState, useEffect } from 'react';
import { Filesystem, Directory, Encoding } from '@capacitor/filesystem';

interface OptimizedImageProps extends React.ImgHTMLAttributes<HTMLImageElement> {
    src?: string; // Expects a file URI: 'file://...'
}

const OptimizedImage: React.FC<OptimizedImageProps> = ({ src, ...props }) => {
    const [imageData, setImageData] = useState<string | undefined>(undefined);

    useEffect(() => {
        const loadImage = async () => {
            if (src && src.startsWith('file://')) {
                try {
                    const { data } = await Filesystem.readFile({
                        path: src.split('/').pop()!, // Get filename from URI
                        directory: Directory.Data,
                    });
                    
                    // The data is Base64, but needs the data URI prefix
                    if (typeof data === 'string') {
                        setImageData(`data:image/jpeg;base64,${data}`);
                    }
                } catch (error) {
                    console.error(`Failed to load image from filesystem: ${src}`, error);
                    // Optionally set a fallback image source
                    setImageData(undefined);
                }
            } else {
                // If it's a regular URL or data URI, use it directly
                setImageData(src);
            }
        };

        loadImage();
    }, [src]);

    if (!imageData) {
        // Render a placeholder or nothing while loading
        return <div className="w-full h-full bg-slate-800 animate-pulse" />;
    }

    return <img src={imageData} {...props} />;
};

export default OptimizedImage;