const React = require('react');

module.exports = {
  Canvas: ({ children }) => React.createElement(React.Fragment, null, children),
  Circle: () => null,
  Rect: () => null,
  Path: () => null,
  Line: () => null,
  Group: ({ children }) => React.createElement(React.Fragment, null, children),
  Blur: () => null,
  BlurMask: () => null,
  LinearGradient: () => null,
  RadialGradient: () => null,
  vec: (x, y) => ({ x, y }),
  Skia: {},
};
