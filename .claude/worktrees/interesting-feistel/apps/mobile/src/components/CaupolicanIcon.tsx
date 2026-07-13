import React from 'react';
import { View, ViewStyle } from 'react-native';
import Svg, { Path, G } from 'react-native-svg';

interface IconProps {
  color?: string;
  size?: number;
  style?: ViewStyle;
}

export const CaupolicanIcon: React.FC<IconProps> = ({ color = '#000', size = 48, style }) => {
  return (
    <View style={[{ width: size, height: size }, style]}>
      <Svg
        width={size}
        height={size}
        viewBox="0 0 1000 1000"
        preserveAspectRatio="xMidYMid meet"
      >
        <G transform="translate(0, 1000) scale(0.1, -0.1)" fill={color}>
          <Path d="M1170 7110 l0 -910 3830 0 3830 0 0 910 0 910 -3830 0 -3830 0 0 -910z" />
          <Path d="M2659 4889 c-805 -583 -1470 -1066 -1477 -1072 -9 -10 -12 -270 -10 -1259 l3 -1246 1910 1350 c1051 742 1915 1352 1922 1355 7 3 867 -572 1913 -1276 1045 -705 1902 -1281 1905 -1281 3 0 4 530 3 1177 l-3 1178 -1309 1067 -1309 1068 -1041 0 -1042 0 -1465 -1061z" />
        </G>
      </Svg>
    </View>
  );
};
