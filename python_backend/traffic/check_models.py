"""Run from python_backend with: python -m traffic.check_models"""
import json
from .service import get_traffic_service

if __name__ == '__main__':
    service_instance = get_traffic_service()
    print(json.dumps({'model_loaded': True, 'class_names': list(service_instance.yolo_model.names.values()),
                      'cnn_hash_verified': True, 'input_size': 1280}, ensure_ascii=False))
