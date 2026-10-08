"""Exercise the RELRO padding condition independently of library filenames."""
import unittest
from verify_native_packaging import relro_padding_conflicts


class RelroPaddingTest(unittest.TestCase):
    def test_a_separate_writable_segment_after_padding_is_safe(self):
        loads = [{'virtualAddress': 0x5c40, 'memoryBytes': 0x390, 'writable': True},
                 {'virtualAddress': 0x9fd0, 'memoryBytes': 0x10, 'writable': True}]
        relro = [{'virtualAddress': 0x5c40, 'memoryBytes': 0x3c0}]
        self.assertEqual([], relro_padding_conflicts(loads, relro))

    def test_writable_data_in_the_rounded_tail_is_rejected(self):
        loads = [{'virtualAddress': 0xdfaf0, 'memoryBytes': 0x3000, 'writable': True}]
        relro = [{'virtualAddress': 0xdfaf0, 'memoryBytes': 0x1510}]
        self.assertTrue(relro_padding_conflicts(loads, relro))

    def test_writable_data_before_a_relro_start_is_also_rejected(self):
        loads = [{'virtualAddress': 0x4000, 'memoryBytes': 0x2000, 'writable': True}]
        relro = [{'virtualAddress': 0x5000, 'memoryBytes': 0x1000}]
        self.assertTrue(relro_padding_conflicts(loads, relro))


if __name__ == '__main__':
    unittest.main()
