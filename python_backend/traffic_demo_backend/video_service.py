"""Local demo worker. Predictions are never interpolated or held across frames."""
import json, os, subprocess, threading
from pathlib import Path
import cv2
import numpy as np
import torch
from PIL import Image, ImageDraw, ImageFont
from traffic.traffic_detector import TrafficDetector
from traffic.countdown_segmenter_v4 import segment_countdown_digits

ROOT = Path(__file__).parent
FFMPEG = os.environ.get('FFMPEG_PATH', str(ROOT / 'tools/ffmpeg'))
FFPROBE = str(Path(FFMPEG).with_name('ffprobe'))
LOCK = threading.Lock()
_detector = None

def detector():
    global _detector
    if _detector is None:
        torch.set_num_threads(4)
        _detector = TrafficDetector(ROOT / 'best.pt', ROOT / 'models/final_v3_recognizer_9I2D_E3.pt')
    return _detector

def decision(light, seconds):
    if light == 'red': return 'red', '紅燈，請等待'
    if light == 'green' and seconds is not None:
        if seconds < 15: return 'short', '剩餘時間不足，請等待下一輪'
        return 'green', f'綠燈，剩餘 {seconds} 秒'
    return 'unknown', '無法確認，請等待'

def inspect_video(path):
    data = json.loads(subprocess.check_output([FFPROBE, '-v', 'error', '-show_streams', '-show_format', '-of', 'json', str(path)], timeout=30))
    s = next(s for s in data['streams'] if s['codec_type'] == 'video')
    w, h = s['width'], s['height']
    if any(abs(x.get('rotation', 0)) % 180 == 90 for x in s.get('side_data_list', [])): w, h = h, w
    duration = float(s.get('duration', data['format'].get('duration', 0)))
    if not 0 < duration <= 120: raise ValueError('請選擇 120 秒以內的影片')
    if w * h > 3840 * 2160: raise ValueError('請選擇 4K 以下的影片')
    return w, h, duration

def predict_frame(im, roi=None):
    d = detector(); h, w = im.shape[:2]
    x, y, ex, ey = roi or (0, 0, w, h)
    result = d.yolo_model.predict(im[y:ey, x:ex], imgsz=640, conf=.25, iou=.5, device='cpu', verbose=False)[0]
    boxes = []
    for b in result.boxes:
        z = b.xyxy[0].tolist()
        boxes.append({'name': d.yolo_model.names[int(b.cls[0])], 'confidence': float(b.conf[0]), 'xyxy': [z[0]+x,z[1]+y,z[2]+x,z[3]+y]})
    red = max([b['confidence'] for b in boxes if b['name']=='pedestrian_red'] or [0])
    green = max([b['confidence'] for b in boxes if b['name']=='pedestrian_green'] or [0])
    light = 'red' if red >= .5 else 'green' if green >= .5 else None
    timers = [b for b in boxes if b['name']=='countdown_timer']; seconds = None; cnn_confidence = None
    if timers and light != 'red':
        b = max(timers, key=lambda b:b['confidence']); x1,y1,x2,y2 = map(int,b['xyxy']); px=int((x2-x1)*.1);py=int((y2-y1)*.1)
        crop=im[max(0,y1-py):min(h,y2+py),max(0,x1-px):min(w,x2+px)]
        if crop.size:
            cr = d.digit_recognizer.recognize_countdown_crops(segment_countdown_digits(crop)['crops_bgr'])
            seconds=cr['seconds_remaining']; cnn_confidence=cr['confidence']
    confirmed = seconds if light=='green' else None
    state, message = decision(light, confirmed)
    return {'detections':boxes,'light':light,'raw_seconds':seconds,'seconds':confirmed,'cnn_confidence':cnn_confidence,'state':state,'message':message}

def locate_region(im, crop_size=400):
    """Find a complete signal/timer pair, then compare local crop contexts."""
    d=detector();h,w=im.shape[:2];tile=min(400,w,h)
    xs=sorted(set(list(range(0,w-tile+1,max(1,tile//2)))+[w-tile]))
    ys=sorted(set(list(range(0,h-tile+1,max(1,tile//2)))+[h-tile]))
    offsets=[(x,y) for y in ys for x in xs]
    def boxes_for(regions, offsets):
        rs=d.yolo_model.predict(regions,imgsz=640,conf=.25,iou=.5,verbose=False,device='cpu',batch=8)
        groups=[]
        for r,(x,y) in zip(rs,offsets):
            bs=[]
            for b in r.boxes:
                z=b.xyxy[0].tolist()
                if min(z[0],z[1])<2 or z[2]>=r.orig_shape[1]-2 or z[3]>=r.orig_shape[0]-2:continue
                bs.append({'name':d.yolo_model.names[int(b.cls[0])],'confidence':float(b.conf[0]),'xyxy':[v+(x if j%2==0 else y) for j,v in enumerate(z)]})
            groups.append(bs)
        return groups
    def score(bs):
        pairs=[]
        for a in bs:
            if not a['name'].startswith('pedestrian'):continue
            ax,ay,ex,ey=a['xyxy'];cx=(ax+ex)/2;cy=(ay+ey)/2
            for b in bs:
                if b['name']!='countdown_timer':continue
                bx,by,fx,fy=b['xyxy'];tx=(bx+fx)/2;ty=(by+fy)/2
                if abs(tx-cx)<ex-ax and 0<cy-ty<2*(ey-ay):
                    pairs.append((a['confidence']+b['confidence'],(cx+tx)/2,(cy+ty)/2))
        return max(pairs) if pairs else None
    groups=boxes_for([im[y:y+tile,x:x+tile] for x,y in offsets],offsets)
    found=[score(bs) for bs in groups];found=[f for f in found if f]
    if not found:return None
    _,cx,cy=max(found);crops=[];offsets=[]
    for size in sorted(set([min(320,w,h),min(crop_size,w,h)])):
        for dx in [-32,0,32]:
            for dy in [-32,0,32]:
                x=max(0,min(w-size,round(cx+dx-size/2)));y=max(0,min(h-size,round(cy+dy-size/2)))
                crops.append((x,y,x+size,y+size));offsets.append((x,y))
    groups=boxes_for([im[y:ey,x:ex] for x,y,ex,ey in crops],offsets)
    ranked=[(score(bs)[0],roi) for bs,roi in zip(groups,crops) if score(bs)]
    return max(ranked)[1] if ranked else None

def render(im, result, t, roi, font, small):
    h,w=im.shape[:2]; ow,oh=(540,round(h*540/w/2)*2) if h>=w else (960,round(h*960/w/2)*2)
    p=Image.fromarray(cv2.cvtColor(cv2.resize(im,(ow,oh)),cv2.COLOR_BGR2RGB)); draw=ImageDraw.Draw(p); sx=ow/w;sy=oh/h;occupied=[]
    for b in sorted(result['detections'],key=lambda b:b['xyxy'][1]):
        x1,y1,x2,y2=[int(v*(sx if i%2==0 else sy)) for i,v in enumerate(b['xyxy'])]; name=b['name'];color={'countdown_timer':(255,215,30),'pedestrian_green':(35,230,100),'pedestrian_red':(255,70,70)}[name];label={'countdown_timer':'倒數','pedestrian_green':'綠燈','pedestrian_red':'紅燈'}[name]
        if name=='countdown_timer': label+=' '+(str(result['raw_seconds'])+' 秒' if result['raw_seconds'] is not None else '未辨識')
        label+=f" | YOLO {b['confidence']:.2f}";tw=draw.textbbox((0,0),label,font=font)[2]+12;th=28;lx=max(0,min(ow-tw,x1));ly=max(66,y1-th-3)
        for _ in range(8):
            if not any(not(lx+tw<=ox or lx>=ox+ww or ly+th<=oy or ly>=oy+hh) for ox,oy,ww,hh in occupied):break
            ly=ly-th-3 if ly>100 else ly+th+3
        occupied.append((lx,ly,tw,th));draw.rectangle((x1,y1,x2,y2),outline=color,width=3);draw.line((x1,y1,lx+10,ly+th),fill=color,width=2);draw.rectangle((lx,ly,lx+tw,ly+th),fill=color);draw.text((lx+6,ly),label,font=font,fill=(10,18,20))
    draw.rectangle((0,0,ow,62),fill=(7,22,34));draw.text((12,6),'AI 之眼｜行人號誌辨識',font=font,fill='white');draw.text((12,34),f'{t:.2f} 秒｜YOLO＋CNN｜'+('號誌區域辨識' if roi else '全畫面'),font=small,fill='white')
    color={'red':(135,25,25),'short':(140,90,0),'green':(10,85,45),'unknown':(35,45,55)}[result['state']]
    draw.rectangle((0,oh-58,ow,oh),fill=color);draw.text((12,oh-50),result['message'],font=font,fill='white');draw.text((12,oh-24),'15 秒以上｜14～1 秒警示｜未確認時不補猜',font=small,fill='white')
    return np.array(p),ow,oh

def process(job, path, center=None, crop_size=400, track=False):
    reader=writer=None
    try:
        with LOCK:
            job.update(status='processing', progress=0)
            w,h,duration=inspect_video(path);rate=15;expected=round(duration*rate)
            font=ImageFont.truetype('/System/Library/Fonts/STHeiti Medium.ttc',19);small=ImageFont.truetype('/System/Library/Fonts/STHeiti Medium.ttc',15)
            reader=subprocess.Popen([FFMPEG,'-v','error','-i',str(path),'-an','-vf',f'fps={rate}','-f','rawvideo','-pix_fmt','bgr24','-'],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL)
            rows=[];idx=0;cx,cy=(center[0]*w,center[1]*h) if center else (0,0);size=min(crop_size,w,h)
            output=path.parent/'annotated.mp4'; counts={k:0 for k in ['red','short','green','unknown']}
            while True:
                raw=reader.stdout.read(w*h*3)
                if not raw:break
                if len(raw)!=w*h*3:raise ValueError('影片解碼不完整')
                im=np.frombuffer(raw,np.uint8).reshape(h,w,3);roi=None
                if center:
                    x=max(0,min(w-size,round(cx-size/2)));y=max(0,min(h-size,round(cy-size/2)));roi=(x,y,x+size,y+size)
                r=predict_frame(im,roi)
                if idx == 0 and (roi is None or not r['detections']):
                    located=locate_region(im,crop_size)
                    if located:
                        roi=located;size=roi[2]-roi[0];cx=(roi[0]+roi[2])/2;cy=(roi[1]+roi[3])/2;center=(cx/w,cy/h)
                        job['region_message']='已自動定位號誌區域' if job.get('region_message') is None else job['region_message']
                        r=predict_frame(im,roi)
                r.update(time_s=idx/rate,roi=roi);counts[r['state']]+=1;rows.append(r)
                if center and track:
                    candidates=r['detections']
                    # Move only the region, never reuse light or digit predictions.
                    if candidates:
                        points=[((b['xyxy'][0]+b['xyxy'][2])/2,(b['xyxy'][1]+b['xyxy'][3])/2) for b in candidates];tx=sum(p[0] for p in points)/len(points);ty=sum(p[1] for p in points)/len(points);cx=.6*cx+.4*tx;cy=.6*cy+.4*ty
                frame,ow,oh=render(im,r,idx/rate,roi,font,small)
                if writer is None:writer=subprocess.Popen([FFMPEG,'-v','error','-y','-f','rawvideo','-pix_fmt','rgb24','-s',f'{ow}x{oh}','-r',str(rate),'-i','-','-an','-c:v','libopenh264','-b:v','2500k','-pix_fmt','yuv420p','-movflags','+faststart',str(output)],stdin=subprocess.PIPE,stderr=subprocess.DEVNULL)
                writer.stdin.write(frame.tobytes());idx+=1;job['progress']=min(99,round(idx/max(1,expected)*100))
            if not idx:raise ValueError('影片沒有可讀取的影格')
            reader.stdout.close();rc=reader.wait();writer.stdin.close();wc=writer.wait()
            if rc or wc:raise ValueError('影片轉檔失敗')
            (path.parent/'results.json').write_text(json.dumps(rows,ensure_ascii=False))
            job.update(status='done',progress=100,summary=counts,frames=idx,video_url=f"/videos/{job['id']}/result",results_url=f"/videos/{job['id']}/results")
    except Exception as e:
        job.update(status='error',message=str(e))
    finally:
        for p in [reader,writer]:
            if p and p.poll() is None:p.terminate();p.wait()
