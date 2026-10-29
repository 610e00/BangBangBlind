import io
import json
import unittest
from unittest.mock import Mock, patch

import numpy as np
from PIL import Image
from fastapi import HTTPException
from traffic.demo_app import app
from traffic.routes import decode_image
from traffic.schemas import build_success
from traffic.service import TrafficBusyError, interpret_detections


def image_bytes():
    output_buffer = io.BytesIO()
    Image.new('RGB', (32, 32), 'green').save(output_buffer, format='PNG')
    return output_buffer.getvalue()


async def request_api(image_content=None, field_name='file', path='/traffic/detect'):
    payload = b''
    request_headers = []
    if image_content is not None:
        boundary = 'bangbangblind-contract-boundary'
        payload = (f'--{boundary}\r\nContent-Disposition: form-data; name="{field_name}"; filename="signal.png"\r\n'
                   'Content-Type: image/png\r\n\r\n').encode() + image_content + f'\r\n--{boundary}--\r\n'.encode()
        request_headers = [(b'content-type', f'multipart/form-data; boundary={boundary}'.encode())]
    request_headers.append((b'content-length', str(len(payload)).encode()))
    messages = []
    received = False
    async def receive():
        nonlocal received
        if not received:
            received = True
            return {'type': 'http.request', 'body': payload, 'more_body': False}
        return {'type': 'http.disconnect'}
    async def send(message):
        messages.append(message)
    scope = {'type': 'http', 'asgi': {'version': '3.0'}, 'http_version': '1.1',
             'method': 'POST', 'scheme': 'http', 'path': path, 'raw_path': path.encode(),
             'query_string': b'', 'root_path': '', 'headers': request_headers,
             'client': ('127.0.0.1', 50000), 'server': ('localhost', 8000)}
    await app(scope, receive, send)
    status_code = next(item['status'] for item in messages if item['type'] == 'http.response.start')
    response_bytes = b''.join(item.get('body', b'') for item in messages if item['type'] == 'http.response.body')
    return status_code, json.loads(response_bytes)


class ContractChecks(unittest.TestCase):
    def setUp(self):
        self.image_bgr = np.zeros((200, 200, 3), np.uint8)
        self.green_signal = dict(class_name='pedestrian_green', confidence=.9, xyxy=[50, 80, 80, 120])
        self.timer_box = dict(class_name='countdown_timer', confidence=.9, xyxy=[50, 30, 80, 65])

    def test_exact_green_contract_and_threshold(self):
        for seconds_remaining, crossing_flag in [(0, False), (14, False), (15, True), (18, True), (99, True)]:
            response = build_success('green', seconds_remaining).model_dump(by_alias=True)
            self.assertEqual(set(response), {'status', 'message', 'data'})
            self.assertEqual(response['data'], dict(light_detected=True, light_color='green',
                             seconds_remaining=seconds_remaining, is_safe_to_cross=crossing_flag))
            self.assertIs(type(response['data']['seconds_remaining']), int)
            self.assertIs(type(response['data']['is_safe_to_cross']), bool)

    def test_no_detection(self):
        response = build_success().model_dump(by_alias=True)
        self.assertEqual(response['message'], '未偵測到紅綠燈')
        self.assertEqual(response['data'], dict(light_detected=False, light_color=None,
                         seconds_remaining=None, is_safe_to_cross=False))

    def test_red_never_reports_seconds(self):
        response = build_success('red', 20)
        self.assertIsNone(response.traffic_reading.seconds_remaining)
        self.assertFalse(response.traffic_reading.is_safe_to_cross)

    def test_unreadable_and_invalid_seconds(self):
        for seconds_remaining in [None, True, '18', 18.0, -1, 100]:
            response = build_success('green', seconds_remaining)
            self.assertIsNone(response.traffic_reading.seconds_remaining)
            self.assertFalse(response.traffic_reading.is_safe_to_cross)

    def test_ambiguous_directions(self):
        response = interpret_detections([self.green_signal, self.green_signal], self.image_bgr, Mock())
        self.assertFalse(response.traffic_reading.light_detected)

    def test_unpaired_timer(self):
        unrelated_timer = dict(self.timer_box, xyxy=[150, 30, 180, 65])
        response = interpret_detections([self.green_signal, unrelated_timer], self.image_bgr, Mock())
        self.assertEqual(response.traffic_reading.light_color, 'green')
        self.assertIsNone(response.traffic_reading.seconds_remaining)

    def test_cnn_confidence_and_segmentation_failure(self):
        recognizer = Mock()
        for confidence, expected_seconds in [(.79, None), (.8, 18), (.99, 18)]:
            recognizer.recognize_countdown_crops.return_value = dict(seconds_remaining=18, confidence=confidence)
            with patch('traffic.service.segment_countdown_digits', return_value={'crops_bgr': [self.image_bgr]}):
                response = interpret_detections([self.green_signal, self.timer_box], self.image_bgr, recognizer)
            self.assertEqual(response.traffic_reading.seconds_remaining, expected_seconds)
        with patch('traffic.service.segment_countdown_digits', side_effect=ValueError('unreadable')):
            response = interpret_detections([self.green_signal, self.timer_box], self.image_bgr, recognizer)
        self.assertIsNone(response.traffic_reading.seconds_remaining)

    def test_red_does_not_call_cnn(self):
        recognizer = Mock()
        response = interpret_detections([dict(self.green_signal, class_name='pedestrian_red')], self.image_bgr, recognizer)
        self.assertEqual(response.traffic_reading.light_color, 'red')
        recognizer.recognize_countdown_crops.assert_not_called()


class HttpChecks(unittest.IsolatedAsyncioTestCase):
    async def test_multipart_success(self):
        service = Mock()
        service.detect.return_value = build_success('green', 18)
        with patch('traffic.routes.get_traffic_service', return_value=service):
            status_code, response = await request_api(image_content=image_bytes())
        self.assertEqual(status_code, 200)
        self.assertEqual(response['data']['seconds_remaining'], 18)
        self.assertTrue(response['data']['is_safe_to_cross'])
        self.assertEqual(service.detect.call_args.args[0].shape, (32, 32, 3))

    async def test_missing_and_wrong_field(self):
        for keyword_arguments in [{}, {'image_content': image_bytes(), 'field_name': 'picture'}]:
            status_code, response = await request_api(**keyword_arguments)
            self.assertEqual(status_code, 422)
            self.assertEqual(set(response), {'status', 'message', 'data'})
            self.assertEqual(response['status'], 'error')
            self.assertIsNone(response['data'])

    async def test_image_error_contract(self):
        for payload, expected_status in [(b'', 400), (b'bad image', 415), (b'x' * (8 * 1024 * 1024 + 1), 413)]:
            status_code, response = await request_api(image_content=payload)
            self.assertEqual(status_code, expected_status)
            self.assertEqual(response['status'], 'error')
            self.assertIsNone(response['data'])

    async def test_busy_and_unavailable_contract(self):
        for exception, expected_status in [(TrafficBusyError('忙碌中'), 429), (RuntimeError('private path'), 503)]:
            service = Mock()
            service.detect.side_effect = exception
            with patch('traffic.routes.get_traffic_service', return_value=service), self.assertLogs('traffic.routes', level='ERROR') if expected_status == 503 else patch('traffic.routes.LOGGER'):
                status_code, response = await request_api(image_content=image_bytes())
            self.assertEqual(status_code, expected_status)
            self.assertIsNone(response['data'])
            self.assertNotIn('private path', response['message'])

    async def test_undocumented_path(self):
        status_code, response = await request_api(path='/frame')
        self.assertEqual(status_code, 404)
        self.assertEqual(response['status'], 'error')
        self.assertIsNone(response['data'])


if __name__ == '__main__':
    unittest.main()
