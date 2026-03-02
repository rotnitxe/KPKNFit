
import React from 'react';

interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  children: React.ReactNode;
  isLoading?: boolean;
  variant?: 'primary' | 'secondary' | 'danger';
}

const Button: React.FC<ButtonProps> = ({ children, isLoading = false, variant = 'primary', className = '', ...props }) => {
  // Base: bordes rectos (rounded-none), diseño global
  const baseStyles = 'inline-flex items-center justify-center gap-2 font-bold py-2.5 px-5 text-sm rounded-none transition-all duration-300 focus:outline-none disabled:opacity-50 disabled:cursor-not-allowed active:scale-95';
  
  let variantStyles = '';

  switch (variant) {
    case 'primary':
      // Blanco con texto negro, bordes rectos (plan global)
      variantStyles = 'bg-white text-black border border-white hover:bg-white/90';
      break;
    case 'secondary':
      // Secundario: bordes rectos
      variantStyles = 'bg-white/10 hover:bg-white/20 border border-white/20 text-white';
      break;
    case 'danger':
      variantStyles = 'bg-red-500/10 hover:bg-red-500/20 border border-red-500/30 text-red-400 hover:text-red-300 rounded-none';
      break;
  }

  return (
    <button
      className={`${baseStyles} ${variantStyles} ${className}`}
      disabled={isLoading || props.disabled}
      {...props}
    >
      {isLoading ? (
        <svg className="animate-spin h-5 w-5 text-current" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
          <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
          <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
        </svg>
      ) : (
        children
      )}
    </button>
  );
};

export default Button;
