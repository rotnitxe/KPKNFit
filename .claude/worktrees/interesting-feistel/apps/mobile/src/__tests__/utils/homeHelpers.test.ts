
import { getGreeting, calculateWeightDelta } from '../../utils/homeHelpers';

describe('homeHelpers', () => {
  describe('getGreeting', () => {
    it('returns "Buenos días" in the morning', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-01-01T08:00:00'));
      expect(getGreeting()).toBe('Buenos días');
      jest.useRealTimers();
    });

    it('returns "Buenas tardes" in the afternoon', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-01-01T15:00:00'));
      expect(getGreeting()).toBe('Buenas tardes');
      jest.useRealTimers();
    });

    it('returns "Buenas noches" at night', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-01-01T22:00:00'));
      expect(getGreeting()).toBe('Buenas noches');
      jest.useRealTimers();
    });

    it('includes userName if provided and not "Usuario"', () => {
      jest.useFakeTimers().setSystemTime(new Date('2024-01-01T08:00:00'));
      expect(getGreeting('Valen')).toBe('Buenos días, Valen');
      expect(getGreeting('Usuario')).toBe('Buenos días');
      jest.useRealTimers();
    });
  });

  describe('calculateWeightDelta', () => {
    it('returns null if less than 2 entries', () => {
      expect(calculateWeightDelta([])).toBeNull();
      expect(calculateWeightDelta([{ id: '1', date: '2024-01-01', weight: 80 }])).toBeNull();
    });

    it('calculates delta and days correctly', () => {
      const bodyProgress = [
        { id: '1', date: '2024-01-10', weight: 81.2 }, // latest
        { id: '2', date: '2024-01-01', weight: 80.0 }, // previous
      ];
      const result = calculateWeightDelta(bodyProgress);
      expect(result).toEqual({
        delta: 1.2,
        days: 9
      });
    });

    it('handles negative delta (weight loss)', () => {
      const bodyProgress = [
        { id: '1', date: '2024-01-10', weight: 79.5 },
        { id: '2', date: '2024-01-01', weight: 80.0 },
      ];
      const result = calculateWeightDelta(bodyProgress);
      expect(result?.delta).toBeCloseTo(-0.5);
    });

    it('works with unsorted entries', () => {
      const bodyProgress = [
        { id: '2', date: '2024-01-01', weight: 80.0 },
        { id: '1', date: '2024-01-10', weight: 81.2 },
      ];
      const result = calculateWeightDelta(bodyProgress);
      expect(result).toEqual({
        delta: 1.2,
        days: 9
      });
    });
  });
});
