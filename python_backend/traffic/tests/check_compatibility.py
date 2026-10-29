import tempfile
import unittest
from pathlib import Path
from unittest.mock import Mock, patch
from traffic.schemas import build_success
from traffic.traffic_detector import TrafficDetector
from traffic.tests.check_contract import image_bytes


class CompatibilityChecks(unittest.TestCase):
    def test_legacy_constructor_and_detect(self):
        service = Mock()
        service.detect.return_value = build_success('green', 18)
        with patch('traffic.traffic_detector.TrafficService', return_value=service) as service_factory:
            detector = TrafficDetector('chosen-yolo.pt')
        self.assertEqual(service_factory.call_args.kwargs['yolo_model_path'], 'chosen-yolo.pt')
        with tempfile.TemporaryDirectory() as sample_directory:
            source_path = Path(sample_directory) / 'signal.png'
            source_path.write_bytes(image_bytes())
            response = detector.detect(source_path, yolo_conf=.3, signal_conf_threshold=.6)
        self.assertEqual(response['data']['seconds_remaining'], 18)
        self.assertEqual(service.detect.call_args.kwargs, dict(yolo_conf=.3, signal_conf_threshold=.6))

    def test_missing_image_and_invalid_thresholds(self):
        with patch('traffic.traffic_detector.TrafficService'):
            detector = TrafficDetector()
        for kwargs in [{}, {'yolo_conf': -1}]:
            response = detector.detect('/not-present/signal.png', **kwargs)
            self.assertEqual(response['status'], 'error')
            self.assertIsNone(response['data'])

if __name__ == '__main__':
    unittest.main()
