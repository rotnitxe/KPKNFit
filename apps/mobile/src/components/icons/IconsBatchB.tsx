import React from 'react';
import Svg, { Path, Circle, Rect, Line, Polyline, Polygon, Ellipse, G, Text as SvgText } from 'react-native-svg';
import type { IconProps } from './types';

export const KeyIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21 2l-2 2m-7.61 7.61a5.5 5.5 0 1 1-7.778 7.778 5.5 5.5 0 0 1 7.777-7.777zm0 0L15.5 7.5m0 0l3 3L22 7l-3-3m-3.5 3.5L19 4" />
  </Svg>
);

export const PaletteIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="13.5" cy="6.5" r=".5" fill={color} />
    <Circle cx="17.5" cy="10.5" r=".5" fill={color} />
    <Circle cx="8.5" cy="7.5" r=".5" fill={color} />
    <Circle cx="6.5" cy="12.5" r=".5" fill={color} />
    <Path d="M12 2C6.5 2 2 6.5 2 12s4.5 10 10 10c.926 0 1.648-.746 1.648-1.667 0-.424-.163-.82-.437-1.125-.29-.32-1.04-.63-1.04-.63s.424-1.22.424-1.375c0-.926-.746-1.667-1.667-1.667h-.167c-.926 0-1.667.746-1.667 1.667 0 .156.424 1.375.424 1.375s-.75.31-1.04.63c-.274.305-.437.701-.437 1.125C7.352 21.254 8.074 22 9 22c5.5 0 10-4.5 10-10S17.5 2 12 2Z" />
  </Svg>
);

export const BellIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
    <Path d="M13.73 21a2 2 0 0 1-3.46 0" />
  </Svg>
);

export const Wand2Icon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m5 21 6-6m-4-4 6-6m2 2-6 6" />
    <Path d="M18 8a2.828 2.828 0 1 1-4 4 2.828 2.828 0 1 1 4-4Z" />
  </Svg>
);

export const CheckIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="20 6 9 17 4 12" />
  </Svg>
);

export const BodyIcon: React.FC<IconProps> = ({
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

export const UtensilsIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M3 2v7c0 1.1.9 2 2 2h4a2 2 0 0 0 2-2V2" />
    <Path d="M7 2v20" />
    <Path d="M21 15V2v0a5 5 0 0 0-5 5v6c0 1.1.9 2 2 2h3Z" />
  </Svg>
);

export const PlateIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="16" r="4" />
    <Path d="M12 12V4" />
    <Path d="M9 4h6" />
  </Svg>
);

export const WikiLabIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" {...props}>
    <SvgText
      x="12"
      y="17"
      textAnchor="middle"
      fontFamily="serif"
      fontWeight="bold"
      fontSize="18"
      fill={color}
    >
      W
    </SvgText>
  </Svg>
);

export const SearchIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="11" cy="11" r="8" />
    <Line x1="21" y1="21" x2="16.65" y2="16.65" />
  </Svg>
);

export const ClipboardListIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="8" y="2" width="8" height="4" rx="1" ry="1" />
    <Path d="M16 4h2a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h2" />
    <Path d="M12 11h4" />
    <Path d="M12 16h4" />
    <Path d="M8 11h.01" />
    <Path d="M8 16h.01" />
  </Svg>
);

export const ClipboardPlusIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M16 2.5a2.5 2.5 0 0 1 3 3L11 14l-4 1 1-4Z" />
    <Path d="M15 5.5 18.5 9" />
    <Path d="M12.5 11.5 8 7" />
    <Path d="M9 11a7 7 0 0 0 0 10h6a7 7 0 0 0 7-7V9" />
    <Path d="M18 15v6" />
    <Path d="M15 18h6" />
  </Svg>
);

export const ActivityIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="22 12 18 12 15 21 9 3 6 12 2 12" />
  </Svg>
);

export const UsersIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M17 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2" />
    <Circle cx="9" cy="7" r="4" />
    <Path d="M23 21v-2a4 4 0 0 0-3-3.87" />
    <Path d="M16 3.13a4 4 0 0 1 0 7.75" />
  </Svg>
);

export const ArrowUpIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="12" y1="19" x2="12" y2="5" />
    <Polyline points="5 12 12 5 19 12" />
  </Svg>
);

export const ArrowDownIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="12" y1="5" x2="12" y2="19" />
    <Polyline points="19 12 12 19 5 12" />
  </Svg>
);

export const BrainIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M15.5 13a3.5 3.5 0 0 0-3.5 3.5v1a3.5 3.5 0 0 0 7 0v-1.8" />
    <Path d="M8.5 13a3.5 3.5 0 0 1 3.5 3.5v1a3.5 3.5 0 0 1-7 0v-1.8" />
    <Path d="M17.5 16a3.5 3.5 0 0 0 0-7h-.5" />
    <Path d="M19 9.3v-2.8a3.5 3.5 0 0 0-7 0" />
    <Path d="M6.5 16a3.5 3.5 0 0 1 0-7h.5" />
    <Path d="M5 9.3v-2.8a3.5 3.5 0 0 1 7 0" />
  </Svg>
);

export const ChevronUpIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="18 15 12 9 6 15" />
  </Svg>
);

export const FlaskConical: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M4.5 3h15" />
    <Path d="M6 3v12c0 3.3 2.7 6 6 6s6-2.7 6-6V3" />
    <Path d="M6 11h12" />
  </Svg>
);

export const FeedIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M4 11a9 9 0 0 1 9 9" />
    <Path d="M4 4a16 16 0 0 1 16 16" />
    <Circle cx="5" cy="19" r="1" />
  </Svg>
);

export const InfoIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
    <Path d="M12 16v-4" />
    <Path d="M12 8h.01" />
  </Svg>
);

export const LightbulbIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M15 14c.2-1 .7-1.7 1.5-2.5C17.7 10.2 18 9 18 7c0-2.2-1.8-4-4-4S10 4.8 10 7c0 2 .3 3.2 1.5 4.5.8.8 1.3 1.5 1.5 2.5" />
    <Path d="M9 18h6" />
    <Path d="M10 22h4" />
  </Svg>
);

export const MapPinIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
    <Circle cx="12" cy="10" r="3" />
  </Svg>
);

export const DragHandleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="5" r="1" />
    <Circle cx="12" cy="12" r="1" />
    <Circle cx="12" cy="19" r="1" />
  </Svg>
);

export const AlertTriangleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m21.73 18-8-14a2 2 0 0 0-3.46 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3Z" />
    <Line x1="12" y1="9" x2="12" y2="13" />
    <Line x1="12" y1="17" x2="12.01" y2="17" />
  </Svg>
);

export const CalculatorIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="4" y="2" width="16" height="20" rx="2" />
    <Line x1="8" y1="6" x2="16" y2="6" />
    <Line x1="16" y1="14" x2="16" y2="18" />
    <Path d="M16 10h.01" />
    <Path d="M12 10h.01" />
    <Path d="M8 10h.01" />
    <Path d="M12 14h.01" />
    <Path d="M8 14h.01" />
    <Path d="M12 18h.01" />
    <Path d="M8 18h.01" />
  </Svg>
);

export const FocusIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="3" />
    <Path d="M3 7V5a2 2 0 0 1 2-2h2" />
    <Path d="M17 3h2a2 2 0 0 1 2 2v2" />
    <Path d="M21 17v2a2 2 0 0 1-2 2h-2" />
    <Path d="M7 21H5a2 2 0 0 1-2-2v-2" />
  </Svg>
);

export const MoreVerticalIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="1" />
    <Circle cx="12" cy="5" r="1" />
    <Circle cx="12" cy="19" r="1" />
  </Svg>
);

export const LayersIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polygon points="12 2 2 7 12 12 22 7 12 2" />
    <Polyline points="2 17 12 22 22 17" />
    <Polyline points="2 12 12 17 22 12" />
  </Svg>
);

export const CircleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="10" />
  </Svg>
);

export const RefreshCwIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8" />
    <Path d="M21 3v5h-5" />
    <Path d="M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16" />
    <Path d="M3 21v-5h5" />
  </Svg>
);

export const BriefcaseIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect width="20" height="14" x="2" y="7" rx="2" ry="2" />
    <Path d="M16 7V5a2 2 0 0 0-2-2h-4a2 2 0 0 0-2 2v2" />
  </Svg>
);

export const RulerIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M21.3 15.3a2.4 2.4 0 0 1 0 3.4l-2.6 2.6a2.4 2.4 0 0 1-3.4 0L2.7 8.7a2.41 2.41 0 0 1 0-3.4l2.6-2.6a2.41 2.41 0 0 1 3.4 0Z" />
    <Path d="m14.5 12.5 2-2" />
    <Path d="m11.5 9.5 2-2" />
    <Path d="m8.5 6.5 2-2" />
    <Path d="m17.5 15.5 2-2" />
  </Svg>
);

export const GridIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="3" y="3" width="7" height="7" />
    <Rect x="14" y="3" width="7" height="7" />
    <Rect x="14" y="14" width="7" height="7" />
    <Rect x="3" y="14" width="7" height="7" />
  </Svg>
);

export const MoveIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="5 9 2 12 5 15" />
    <Polyline points="9 5 12 2 15 5" />
    <Polyline points="15 19 12 22 9 19" />
    <Polyline points="19 9 22 12 19 15" />
    <Line x1="2" y1="12" x2="22" y2="12" />
    <Line x1="12" y1="2" x2="12" y2="22" />
  </Svg>
);

export const LayoutGridIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="3" y="3" width="7" height="7" />
    <Rect x="14" y="3" width="7" height="7" />
    <Rect x="14" y="14" width="7" height="7" />
    <Rect x="3" y="14" width="7" height="7" />
  </Svg>
);

export const ListIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="8" y1="6" x2="21" y2="6" />
    <Line x1="8" y1="12" x2="21" y2="12" />
    <Line x1="8" y1="18" x2="21" y2="18" />
    <Line x1="3" y1="6" x2="3.01" y2="6" />
    <Line x1="3" y1="12" x2="3.01" y2="12" />
    <Line x1="3" y1="18" x2="3.01" y2="18" />
  </Svg>
);

export const MenuIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="4" y1="12" x2="20" y2="12" />
    <Line x1="4" y1="6" x2="20" y2="6" />
    <Line x1="4" y1="18" x2="20" y2="18" />
  </Svg>
);

export const ChevronLeftIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Polyline points="15 18 9 12 15 6" />
  </Svg>
);

export const ScaleIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m16 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
    <Path d="m2 16 3-8 3 8c-.87.65-1.92 1-3 1s-2.13-.35-3-1Z" />
    <Path d="M7 21h10" />
    <Path d="M12 3v18" />
    <Path d="M3 7h2c2 0 5-1 7-2 2 1 5 2 7 2h2" />
  </Svg>
);

export const BarChart2Icon: React.FC<IconProps> = ({
  size = 24,
  color = 'currentColor',
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="18" y1="20" x2="18" y2="10" />
    <Line x1="12" y1="20" x2="12" y2="4" />
    <Line x1="6" y1="20" x2="6" y2="14" />
  </Svg>
);

export const MaximizeIcon: React.FC<IconProps> = ({
  size = 24,
  color = 'currentColor',
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={2} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M8 3H5a2 2 0 0 0-2 2v3m18 0V5a2 2 0 0 0-2-2h-3m0 18h3a2 2 0 0 0 2-2v-3M3 16v3a2 2 0 0 0 2 2h3" />
  </Svg>
);

export const BatteryIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect x="2" y="7" width="16" height="10" rx="2" ry="2" />
    <Line x1="22" x2="22" y1="11" y2="13" />
  </Svg>
);

export const WorkoutIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M18 20V6a2 2 0 0 0-2-2H8a2 2 0 0 0-2 2v14" />
    <Path d="M2 20h20" />
    <Path d="M14 12h.01" />
  </Svg>
);

export const KpknLogoIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 1000 1000"
    preserveAspectRatio="xMidYMid meet" {...props}>
    <G transform="translate(0,1050) scale(0.1,-0.1)" fill={color} stroke="none">
      <Path d="M1170 8000 l0 -910 3830 0 3830 0 0 910 0 910 -3830 0 -3830 0 0 -910z" />
      <Path d="M2659 5889 c-805 -583 -1470 -1066 -1477 -1072 -9 -10 -12 -270 -10 -1259 l3 -1246 1910 1350 c1051 742 1915 1352 1922 1355 7 3 867 -572 1913 -1276 1045 -705 1902 -1281 1905 -1281 3 0 4 530 3 1177 l-3 1178 -1309 1067 -1309 1068 -1041 0 -1042 0 -1465 -1061z" />
    </G>
  </Svg>
);

export const IntertwinedRingsIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="8" cy="11" r="5" />
    <Circle cx="16" cy="11" r="5" />
    <Circle cx="12" cy="16" r="5" />
  </Svg>
);

export const SingleRingIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Circle cx="12" cy="12" r="8" />
  </Svg>
);

export const HistoryIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M3 12a9 9 0 1 0 9-9 9.75 9.75 0 0 0-6.74 2.74L3 8" />
    <Path d="M3 3v5h5" />
    <Path d="M12 7v5l4 2" />
  </Svg>
);

export const DropletsIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M7 16.3c2.2 0 4-1.8 4-4 0-3.3-4-6-4-6s-4 2.7-4 6c0 2.2 1.8 4 4 4z" />
    <Path d="M17 16.3c2.2 0 4-1.8 4-4 0-3.3-4-6-4-6s-4 2.7-4 6c0 2.2 1.8 4 4 4z" />
  </Svg>
);

export const UserIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2" />
    <Circle cx="12" cy="7" r="4" />
  </Svg>
);

export const FoodIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M18 8c0-2.5-2-4-4-4s-4 1.5-4 4c0 1.5.5 2.5 1 3.5V20h6v-8.5c.5-1 1-2 1-3.5z" />
    <Path d="M8 20v-4" />
    <Path d="M4 20v-8" />
  </Svg>
);

export const LayoutIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect width="18" height="18" x="3" y="3" rx="2" ry="2" />
    <Line x1="3" x2="21" y1="9" y2="9" />
    <Line x1="9" x2="9" y1="21" y2="9" />
  </Svg>
);

export const TimerIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Line x1="10" x2="14" y1="2" y2="2" />
    <Line x1="12" x2="15" y1="14" y2="11" />
    <Circle cx="12" cy="14" r="8" />
  </Svg>
);

export const FlagIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z" />
    <Line x1="4" x2="4" y1="22" y2="15" />
  </Svg>
);

export const ShieldIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z" />
  </Svg>
);

export const MergeIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Path d="m8 6 4-4 4 4" />
    <Path d="M12 2v10.3a4 4 0 0 1-1.172 2.872L4 22" />
    <Path d="m20 22-5-5" />
  </Svg>
);

export const CopyIcon: React.FC<IconProps> = ({
  size = 20,
  color = 'currentColor',
  strokeWidth = 2,
  ...props
}) => (
  <Svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke={color}
    strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" {...props}>
    <Rect width="14" height="14" x="8" y="8" rx="2" ry="2" />
    <Path d="M4 16c-1.1 0-2-.9-2-2V4c0-1.1.9-2 2-2h10c1.1 0 2 .9 2 2" />
  </Svg>
);
