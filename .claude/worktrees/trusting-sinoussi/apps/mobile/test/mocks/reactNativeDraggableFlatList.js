const React = require('react');
const { FlatList } = require('react-native');

const MockDraggableFlatList = React.forwardRef((props, ref) =>
  React.createElement(FlatList, { ...props, ref })
);

module.exports = {
  __esModule: true,
  default: MockDraggableFlatList,
  NestableDraggableFlatList: MockDraggableFlatList,
  NestableScrollContainer: ({ children }) => children,
  ScaleDecorator: ({ children }) => children,
  ShadowDecorator: ({ children }) => children,
  OpacityDecorator: ({ children }) => children,
};
