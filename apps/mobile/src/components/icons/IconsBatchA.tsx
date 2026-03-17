import React from 'react';
import Svg, { Path, Circle, Rect, Line, Polyline, Polygon, Ellipse, G } from 'react-native-svg';
import type { IconProps } from './types';

export const UserBadgeIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2" />
    <Circle cx="12" cy="7" r="4" />
  </Svg>
);

export const IdCardIcon = UserBadgeIcon;

export const EditIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
  </Svg>
);

export const CameraIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M23 19a2 2 0 0 1-2 2H3a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h4l2-3h6l2 3h4a2 2 0 0 1 2 2z" />
    <Circle cx="12" cy="13" r="4" />
  </Svg>
);

export const TagIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M12 2H2v10l9.29 9.29c.94.94 2.48.94 3.42 0l6.58-6.58c.94-.94.94-2.48 0-3.42L12 2Z" />
    <Path d="M7 7h.01" />
  </Svg>
);

export const BedIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M2 4v16h20V4" />
    <Path d="M2 10h20" />
    <Path d="M6 8v-2a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2v2" />
  </Svg>
);

export const SunIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="4" />
    <Path d="M12 2v2" />
    <Path d="M12 20v2" />
    <Path d="m4.93 4.93 1.41 1.41" />
    <Path d="m17.66 17.66 1.41 1.41" />
    <Path d="M2 12h2" />
    <Path d="M20 12h2" />
    <Path d="m6.34 17.66-1.41 1.41" />
    <Path d="m19.07 4.93-1.41 1.41" />
  </Svg>
);

export const MoonIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
  </Svg>
);

export const ChevronDownIcon: React.FC<IconProps> = ({
  size = 24,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="6 9 12 15 18 9" />
  </Svg>
);

export const MicIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M12 1a3 3 0 0 0-3 3v8a3 3 0 0 0 6 0V4a3 3 0 0 0-3-3z" />
    <Path d="M19 10v2a7 7 0 0 1-14 0v-2" />
    <Line x1="12" y1="19" x2="12" y2="23" />
  </Svg>
);

export const MicOffIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="1" y1="1" x2="23" y2="23" />
    <Path d="M9 9v3a3 3 0 0 0 5.12 2.12M15 9.34V4a3 3 0 0 0-5.94-.6" />
    <Path d="M17 16.95A7 7 0 0 1 5 12v-2m14 0v2a7 7 0 0 1-.11 1.23" />
    <Line x1="12" y1="19" x2="12" y2="23" />
  </Svg>
);

export const PauseIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="6" y="4" width="4" height="16" />
    <Rect x="14" y="4" width="4" height="16" />
  </Svg>
);

export const PlusCircleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="9" />
    <Line x1="12" y1="8" x2="12" y2="16" />
    <Line x1="8" y1="12" x2="16" y2="12" />
  </Svg>
);

export const HomeIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m3 9 9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z" />
    <Polyline points="9 22 9 12 15 12 15 22" />
  </Svg>
);

export const RingIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="9" />
  </Svg>
);

export const TripleRingsIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 1.8,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="9" />
  </Svg>
);

export const SettingsIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
    <Circle cx="12" cy="12" r="4" />
    <Circle cx="12" cy="12" r="1.5" />
  </Svg>
);

export const CoachIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m16 2 3 3-1.33 1.33a4 4 0 0 0-1.2 1.2L13 11l5 5 2.4-2.4c.55-.55.55-1.44 0-1.98l-5-5a1.4 1.4 0 0 0-1.98 0Z" />
    <Path d="m2 16 3 3 1.33-1.33a4 4 0 0 0 1.2-1.2L11 13l-5-5-2.4 2.4C3.05 10.95 2.5 11.5 2 12Z" />
    <Path d="m11 13 2.5-2.5" />
    <Path d="m13 11 2.5-2.5" />
    <Path d="m10 14 1 1" />
    <Path d="M14 10l-1-1" />
    <Path d="m18 6-1-1" />
    <Path d="m6 18-1-1" />
  </Svg>
);

export const TrendingUpIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="22 7 13.5 15.5 8.5 10.5 2 17" />
    <Polyline points="16 7 22 7 22 13" />
  </Svg>
);

export const BookOpenIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z" />
    <Path d="M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z" />
  </Svg>
);

export const ArrowLeftIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m12 19-7-7 7-7" />
    <Path d="M19 12H5" />
  </Svg>
);

export const PencilIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
    <Path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
  </Svg>
);

export const PlayIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  fill = 'none',
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill={fill === 'currentColor' ? color : fill} stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="5 3 19 12 5 21 5 3" />
  </Svg>
);

export const ClockIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
    <Polyline points="12 6 12 12 16 14" />
  </Svg>
);

export const CalendarIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect width="18" height="18" x="3" y="4" rx="2" ry="2" />
    <Line x1="16" x2="16" y1="2" y2="6" />
    <Line x1="8" x2="8" y1="2" y2="6" />
    <Line x1="3" x2="21" y1="10" y2="10" />
  </Svg>
);

export const CheckCircleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M9 12l2 2 4-4" />
    <Circle cx="12" cy="12" r="10" />
  </Svg>
);

export const FlameIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z" />
  </Svg>
);

export const XCircleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
    <Line x1="15" y1="9" x2="9" y2="15" />
    <Line x1="9" y1="9" x2="15" y2="15" />
  </Svg>
);

export const PlusIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="12" y1="5" x2="12" y2="19" />
    <Line x1="5" y1="12" x2="19" y2="12" />
  </Svg>
);

export const ChevronRightIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="9 18 15 12 9 6" />
  </Svg>
);

export const ReplaceIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M14 22v-4a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2v4" />
    <Path d="M18 16.5V22" />
    <Path d="m22 20-4-4-4 4" />
    <Path d="M8 2v4a2 2 0 0 0 2 2h8a2 2 0 0 0 2-2V2" />
    <Path d="M6 7.5V2" />
    <Path d="m2 4 4-4 4 4" />
  </Svg>
);

export const SwapIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M20 18v-4h-4" />
    <Path d="M4 6v4h4" />
    <Path d="M16 17.5A6 6 0 0 0 6.5 8" />
    <Path d="M8 6.5A6 6 0 0 0 17.5 16" />
  </Svg>
);

export const MinusIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="5" y1="12" x2="19" y2="12" />
  </Svg>
);

export const GripVerticalIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="9" cy="12" r="1" />
    <Circle cx="9" cy="5" r="1" />
    <Circle cx="9" cy="19" r="1" />
    <Circle cx="15" cy="12" r="1" />
    <Circle cx="15" cy="5" r="1" />
    <Circle cx="15" cy="19" r="1" />
  </Svg>
);

export const ZapIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2" />
  </Svg>
);

export const TrophyIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M6 9H4.5a2.5 2.5 0 0 1 0-5H6" />
    <Path d="M18 9h1.5a2.5 2.5 0 0 0 0-5H18" />
    <Path d="M4 22h16" />
    <Path d="M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22" />
    <Path d="M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22" />
    <Path d="M18 2H6v7a6 6 0 0 0 12 0V2Z" />
  </Svg>
);

export const TargetIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
    <Circle cx="12" cy="12" r="6" />
    <Circle cx="12" cy="12" r="2" />
  </Svg>
);

export const SparklesIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m12 3-1.912 5.813a2 2 0 0 1-1.275 1.275L3 12l5.813 1.912a2 2 0 0 1 1.275 1.275L12 21l1.912-5.813a2 2 0 0 1 1.275-1.275L21 12l-5.813-1.912a2 2 0 0 1-1.275-1.275L12 3Z" />
    <Path d="M5 3v4" />
    <Path d="M19 17v4" />
    <Path d="M3 5h4" />
    <Path d="M17 19h4" />
  </Svg>
);

export const XIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="18" y1="6" x2="6" y2="18" />
    <Line x1="6" y1="6" x2="18" y2="18" />
  </Svg>
);

export const UploadIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <Polyline points="17 8 12 3 7 8" />
    <Line x1="12" y1="3" x2="12" y2="15" />
  </Svg>
);

export const ImageIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="3" y="3" width="18" height="18" rx="2" ry="2" />
    <Circle cx="8.5" cy="8.5" r="1.5" />
    <Polyline points="21 15 16 10 5 21" />
  </Svg>
);

export const TrashIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="3 6 5 6 21 6" />
    <Path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2" />
    <Line x1="10" y1="11" x2="10" y2="17" />
    <Line x1="14" y1="11" x2="14" y2="17" />
  </Svg>
);

export const LinkIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.72" />
    <Path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.72-1.72" />
  </Svg>
);

export const VideoIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="23 7 16 12 23 17 23 7" />
    <Rect x="1" y="5" width="15" height="14" rx="2" ry="2" />
  </Svg>
);

export const StarIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  filled = false,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24"
    fill={filled ? color : 'none'} stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
  </Svg>
);

export const SaveIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
    <Polyline points="17 21 17 13 7 13 7 21" />
    <Polyline points="7 3 7 8 15 8" />
  </Svg>
);

export const BugIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M20 9V7a2 2 0 0 0-2-2H6a2 2 0 0 0-2 2v2" />
    <Path d="M12 13h0" />
    <Path d="M8 13h0" />
    <Path d="M16 13h0" />
    <Path d="M20 17v-4a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v4" />
    <Path d="M4 17a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-2H4Z" />
    <Path d="M6 5V2" />
    <Path d="M18 5V2" />
  </Svg>
);

export const DatabaseIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Ellipse cx="12" cy="5" rx="9" ry="3" />
    <Path d="M21 12c0 1.66-4 3-9 3s-9-1.34-9-3" />
    <Path d="M3 5v14c0 1.66 4 3 9 3s9-1.34 9-3V5" />
  </Svg>
);

export const BarChartIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="12" y1="20" x2="12" y2="10" />
    <Line x1="18" y1="20" x2="18" y2="4" />
    <Line x1="6" y1="20" x2="6" y2="16" />
  </Svg>
);

export const DumbbellIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m6.5 6.5 11 11" />
    <Path d="m21 21-1-1" />
    <Path d="m3 3 1 1" />
    <Path d="m18 22 4-4" />
    <Path d="m6 2-4 4" />
    <Path d="m3 10 7-7" />
    <Path d="m14 21 7-7" />
  </Svg>
);

export const Volume2Icon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
    <Path d="M19.07 4.93a10 10 0 0 1 0 14.14M15.54 8.46a5 5 0 0 1 0 7.07" />
  </Svg>
);

export const VolumeXIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="11 5 6 9 2 9 2 15 6 15 11 19 11 5" />
    <Line x1="23" y1="9" x2="17" y2="15" />
    <Line x1="17" y1="9" x2="23" y2="15" />
  </Svg>
);

export const DownloadIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <Polyline points="7 10 12 15 17 10" />
    <Line x1="12" y1="15" x2="12" y2="3" />
  </Svg>
);

export const TypeIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="4 7 4 4 20 4 20 7" />
    <Line x1="9" y1="20" x2="15" y2="20" />
    <Line x1="12" y1="4" x2="12" y2="20" />
  </Svg>
);

export const CloudIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M18 10h-1.26A8 8 0 1 0 9 20h9a5 5 0 0 0 0-10z" />
  </Svg>
);

export const UploadCloudIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4" />
    <Polyline points="17 8 12 3 7 8" />
    <Line x1="12" y1="3" x2="12" y2="15" />
  </Svg>
);

export const DownloadCloudIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="8 17 12 21 16 17" />
    <Line x1="12" y1="12" x2="12" y2="21" />
    <Path d="M20.88 18.09A5 5 0 0 0 18 9h-1.26A8 8 0 1 0 3 16.29" />
  </Svg>
);