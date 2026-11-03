from fastapi import FastAPI, UploadFile, File, Form, HTTPException
from fastapi.responses import FileResponse
from PIL import Image
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
import io, uuid, json
import numpy as np
from video_service import ROOT, LOCK, detector, process, predict_frame

app = FastAPI()
JOBS = {}; POOL = ThreadPoolExecutor(max_workers=1)
STORE = ROOT / 'video_jobs'; STORE.mkdir(exist_ok=True)

@app.get('/')
def root(): return {'status':'ok','video_enabled':True}

@app.post('/detect')
def detect(file: UploadFile = File(...)):
    image = Image.open(io.BytesIO(file.file.read())).convert('RGB')
    with LOCK:
        model = detector().yolo_model
        result = model.predict(image,imgsz=640,conf=.25,verbose=False)[0]
        detections=[{'class_id':int(b.cls[0]),'class_name':model.names[int(b.cls[0])],'confidence':float(b.conf[0])} for b in result.boxes]
    return {'status':'success','detections':detections}


@app.post('/traffic/detect')
def traffic_detect(file: UploadFile = File(...)):
    """單張影格：YOLO + Segmenter v4 + CNN E3。"""

    # 限制上傳大小，避免即時影格占用過多記憶體
    max_bytes = 8 * 1024 * 1024
    content = file.file.read(max_bytes + 1)

    if not content:
        raise HTTPException(400, '沒有收到圖片')

    if len(content) > max_bytes:
        raise HTTPException(413, '圖片超過 8 MB')

    array = np.frombuffer(content, dtype=np.uint8)
    image = __import__('cv2').imdecode(
        array,
        __import__('cv2').IMREAD_COLOR
    )

    if image is None:
        raise HTTPException(400, '無法解碼圖片')

    h, w = image.shape[:2]

    if w * h > 3840 * 2160:
        raise HTTPException(413, '圖片解析度過高')

    try:
        # 和影片處理共用同一套模型及辨識流程
        with LOCK:
            result = predict_frame(image)

    except Exception as error:
        raise HTTPException(
            500,
            f'即時影格辨識失敗：{type(error).__name__}'
        )

    light = result['light']
    seconds = result['seconds']
    raw_seconds = result['raw_seconds']
    confidence = result['cnn_confidence']

    # 尚未完成跨影格驗證與多組號誌配對驗證
    # 不以單張影像授權通行
    threshold_met = (
        light == 'green'
        and type(seconds) is int
        and seconds >= 15
    )

    return {
        'status': 'success',
        'message': '即時影格辨識完成',
        'data': {
            'light_detected': light is not None,
            'light_color': light,
            'seconds_remaining': seconds,
            'raw_seconds': raw_seconds,
            'raw_seconds_unverified': raw_seconds is not None,
            'cnn_confidence': confidence,
            'countdown_detected': any(
                b['name'] == 'countdown_timer'
                for b in result['detections']
            ),
            'threshold_met_15s': threshold_met,
            'is_safe_to_cross': False,
            'detections': result['detections'],
            'demo_only': True
        }
    }


@app.post('/videos',status_code=202)
async def upload_video(file: UploadFile = File(...), center_x: float = Form(None), center_y: float = Form(None), crop_size: int = Form(400), track: bool = Form(False)):
    if (center_x is None)!=(center_y is None) or (center_x is not None and not(0<=center_x<=1 and 0<=center_y<=1)):raise HTTPException(422,'號誌位置不正確')
    if crop_size not in [320,400,500,640]:raise HTTPException(422,'裁切尺寸不正確')
    if len([j for j in JOBS.values() if j['status'] in ['queued','processing']])>=3:raise HTTPException(429,'處理中，請稍後再試')
    suffix=Path(file.filename or '').suffix.lower()
    if suffix not in ['.mp4','.mov','.m4v']:raise HTTPException(415,'請選擇 MP4 或 MOV 影片')
    jid=uuid.uuid4().hex;folder=STORE/jid;folder.mkdir();path=folder/('input'+suffix);size=0
    try:
        with path.open('wb') as f:
            while True:
                chunk=await file.read(1024*1024)
                if not chunk:break
                size+=len(chunk)
                if size>300*1024*1024:raise HTTPException(413,'影片請小於 300 MB')
                f.write(chunk)
        if size==0:raise HTTPException(400,'影片是空的')
    except Exception:
        path.unlink(missing_ok=True);folder.rmdir();raise
    finally:await file.close()
    job={'id':jid,'status':'queued','progress':0};JOBS[jid]=job
    POOL.submit(process,job,path,(center_x,center_y) if center_x is not None else None,crop_size,track)
    return dict(job)

def job_for(jid):
    if jid not in JOBS:raise HTTPException(404,'找不到處理工作；後端重啟後請重新上傳')
    return JOBS[jid]

@app.get('/videos/{jid}')
def status(jid:str):return dict(job_for(jid))

@app.get('/videos/{jid}/result')
def result(jid:str):
    if job_for(jid)['status']!='done':raise HTTPException(409,'影片尚未處理完成')
    return FileResponse(STORE/jid/'annotated.mp4',media_type='video/mp4')

@app.get('/videos/{jid}/results')
def results(jid:str):
    if job_for(jid)['status']!='done':raise HTTPException(409,'影片尚未處理完成')
    return FileResponse(STORE/jid/'results.json',media_type='application/json')

@app.post('/video-preview')
def video_preview(file: UploadFile = File(...)):
    """Decode HDR on the server; original upload remains untouched."""
    import tempfile, subprocess
    from fastapi.responses import Response
    from video_service import FFMPEG
    suffix = Path(file.filename or '').suffix.lower()
    if suffix not in ['.mov','.mp4','.m4v']:raise HTTPException(415,'請選擇 MP4 或 MOV 影片')
    try:
        with tempfile.TemporaryDirectory(dir=STORE) as folder:
            source=Path(folder)/('input'+suffix);target=Path(folder)/'preview.jpg';total=0
            with source.open('wb') as f:
                while True:
                    chunk=file.file.read(1024*1024)
                    if not chunk:break
                    total+=len(chunk)
                    if total>300*1024*1024:raise HTTPException(413,'影片請小於 300 MB')
                    f.write(chunk)
            if not total:raise HTTPException(400,'影片是空的')
            subprocess.run([FFMPEG,'-v','error','-y','-i',str(source),'-frames:v','1','-vf','scale=540:-2','-q:v','2',str(target)],check=True,timeout=45,capture_output=True)
            from video_service import inspect_video
            width,height,_=inspect_video(source)
            return Response(target.read_bytes(),media_type='image/jpeg',headers={'X-Video-Width':str(width),'X-Video-Height':str(height)})
    except (subprocess.CalledProcessError,subprocess.TimeoutExpired,FileNotFoundError):
        raise HTTPException(422,'無法產生預覽，但仍可嘗試上傳辨識')
    finally:file.file.close()
