const ReactTestRenderer = require('react-test-renderer');

const trackedRenderers = new Set();

if (!ReactTestRenderer.__kpknRendererPatched) {
  const originalCreate = ReactTestRenderer.create.bind(ReactTestRenderer);

  ReactTestRenderer.create = (...args) => {
    const renderer = originalCreate(...args);
    trackedRenderers.add(renderer);

    const originalUnmount = renderer.unmount.bind(renderer);
    renderer.unmount = () => {
      trackedRenderers.delete(renderer);
      return originalUnmount();
    };

    return renderer;
  };

  ReactTestRenderer.__kpknRendererPatched = true;
}

afterEach(() => {
  for (const renderer of Array.from(trackedRenderers)) {
    try {
      ReactTestRenderer.act(() => {
        renderer.unmount();
      });
    } catch (_error) {
      try {
        renderer.unmount();
      } catch (_nestedError) {
        // Ignore cleanup failures in tests; we only want best-effort teardown.
      }
    } finally {
      trackedRenderers.delete(renderer);
    }
  }

  try {
    jest.clearAllTimers();
  } catch (_error) {
    // Ignore when timers are not mocked.
  }

  try {
    jest.useRealTimers();
  } catch (_error) {
    // Ignore when test does not switch timer implementation.
  }
});
