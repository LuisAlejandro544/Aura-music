#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Aura Music - Extractor Nativo Autónomo en Python Puro (Reserva Offline).
Implementa el protocolo CLI de yt-dlp (--dump-single-json, -f, -o, etc.) resolviendo flujos
de audio y video de YouTube y TikTok directamente con la librería estándar de CPython 3.
"""
import sys
import os
import json
import urllib.request
import urllib.parse
import ssl
import re

def extract_youtube_id(url):
    patterns = [
        r'music\.youtube\.com\/watch\?.*v=([0-9A-Za-z_-]{11})',
        r'(?:v=|\/)([0-9A-Za-z_-]{11}).*',
        r'(?:youtu\.be\/)([0-9A-Za-z_-]{11})',
        r'(?:shorts\/)([0-9A-Za-z_-]{11})'
    ]
    for p in patterns:
        m = re.search(p, url)
        if m:
            return m.group(1)
    return None

def create_ssl_context():
    cert_file = os.environ.get("SSL_CERT_FILE") or os.environ.get("REQUESTS_CA_BUNDLE")
    if cert_file and os.path.exists(cert_file):
        try:
            return ssl.create_default_context(cafile=cert_file)
        except Exception:
            pass
    try:
        return ssl.create_default_context()
    except Exception:
        return ssl._create_unverified_context()

def query_innertube_client(video_id, endpoint, client_payload, user_agent):
    payload = {
        "videoId": video_id,
        "contentCheckOk": True,
        "racyCheckOk": True,
        "context": {
            "client": client_payload
        }
    }
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(endpoint, data=data, headers={
        "Content-Type": "application/json",
        "User-Agent": user_agent
    })
    ctx = create_ssl_context()
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            body = resp.read().decode('utf-8')
            parsed = json.loads(body)
            status = parsed.get("playabilityStatus", {}).get("status", "")
            if status and status != "OK":
                return None

            details = parsed.get("videoDetails", {})
            title = details.get("title", f"Video {video_id}")
            author = details.get("author", "YouTube Artist")
            duration = int(details.get("lengthSeconds", 0) or 0)
            thumbnails = details.get("thumbnail", {}).get("thumbnails", [])
            thumb_url = thumbnails[-1]["url"] if thumbnails else f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"

            streaming = parsed.get("streamingData", {})
            formats = streaming.get("adaptiveFormats", []) + streaming.get("formats", [])

            audio_url = None
            video_url = None
            out_formats = []
            client_headers = {
                "User-Agent": user_agent,
                "Accept": "*/*"
            }

            for f in formats:
                mime = f.get("mimeType", "")
                f_url = f.get("url")
                if not f_url:
                    continue
                height = int(f.get("height", 0) or 0)
                abr = float(f.get("averageBitrate", 0) or f.get("bitrate", 0) or 0) / 1000.0
                if "audio" in mime:
                    if not audio_url or "mp4" in mime:
                        audio_url = f_url
                    out_formats.append({
                        "url": f_url,
                        "vcodec": "none",
                        "acodec": "mp4a" if "mp4" in mime else "opus",
                        "ext": "m4a" if "mp4" in mime else "webm",
                        "abr": abr,
                        "protocol": "https",
                        "http_headers": client_headers
                    })
                elif "video" in mime:
                    if not video_url or (height == 480 and "avc1" in mime):
                        video_url = f_url
                    out_formats.append({
                        "url": f_url,
                        "vcodec": "avc1" if "avc1" in mime else "vp9",
                        "acodec": "mp4a" if "mp4a" in mime else "none",
                        "height": height if height > 0 else 480,
                        "ext": "mp4" if "mp4" in mime else "webm",
                        "protocol": "https",
                        "http_headers": client_headers
                    })

            if not audio_url and not video_url:
                return None

            return {
                "id": video_id,
                "title": title,
                "uploader": author,
                "channel": author,
                "duration": duration,
                "thumbnail": thumb_url,
                "url": audio_url or video_url,
                "http_headers": client_headers,
                "formats": out_formats
            }
    except Exception as e:
        sys.stderr.write(f"Aura Python Extractor Notice ({client_payload.get('clientName')}): {e}\n")
        return None

def resolve_youtube_innertube(video_id):
    clients = [
        (
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            {
                "clientName": "ANDROID_VR",
                "clientVersion": "1.60.19",
                "deviceModel": "Quest 3",
                "osName": "Android",
                "osVersion": "12",
                "androidSdkVersion": 32,
                "hl": "es",
                "gl": "US"
            },
            "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip"
        ),
        (
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            {
                "clientName": "IOS",
                "clientVersion": "19.45.4",
                "deviceMake": "Apple",
                "deviceModel": "iPhone16,2",
                "osName": "iPhone",
                "osVersion": "18.1.0.22B83",
                "hl": "es",
                "gl": "US"
            },
            "com.google.ios.youtube/19.45.4 (iPhone16,2; U; CPU iOS 18_1_0 like Mac OS X;)"
        ),
        (
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            {
                "clientName": "VISIONOS",
                "clientVersion": "0.1",
                "deviceMake": "Apple",
                "deviceModel": "RealityDevice14,1",
                "osName": "visionOS",
                "osVersion": "1.3.21O771",
                "hl": "es",
                "gl": "US"
            },
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Safari/605.1.15"
        ),
        (
            "https://music.youtube.com/youtubei/v1/player?prettyPrint=false",
            {
                "clientName": "ANDROID_MUSIC",
                "clientVersion": "7.27.52",
                "androidSdkVersion": 34,
                "osName": "Android",
                "osVersion": "14",
                "hl": "es",
                "gl": "US"
            },
            "com.google.android.apps.youtube.music/7.27.52 (Linux; U; Android 14; es_US) gzip"
        )
    ]
    for endpoint, client_payload, ua in clients:
        res = query_innertube_client(video_id, endpoint, client_payload, ua)
        if res:
            return res
    return None

def download_stream_to_file(stream_url, headers, output_path):
    ctx = create_ssl_context()
    req = urllib.request.Request(stream_url, headers=headers)
    with urllib.request.urlopen(req, timeout=30, context=ctx) as resp, open(output_path, "wb") as out_f:
        while True:
            chunk = resp.read(65536)
            if not chunk:
                break
            out_f.write(chunk)

def main():
    target_url = None
    output_template = None
    args = sys.argv[1:]
    i = 0
    while i < len(args):
        arg = args[i]
        if arg in ("-o", "--output") and i + 1 < len(args):
            output_template = args[i + 1]
            i += 2
            continue
        if arg.startswith("http://") or arg.startswith("https://"):
            target_url = arg
        i += 1

    if not target_url:
        sys.exit(1)

    yt_id = extract_youtube_id(target_url)
    if yt_id:
        info = resolve_youtube_innertube(yt_id)
        if info:
            if output_template:
                out_path = output_template.replace("%(ext)s", "m4a")
                stream_url = info.get("url")
                headers = info.get("http_headers", {})
                if stream_url:
                    download_stream_to_file(stream_url, headers, out_path)
                    sys.exit(0)
            else:
                print(json.dumps(info))
                sys.exit(0)

    sys.stderr.write("No se pudo resolver el flujo desde el extractor de reserva.\n")
    sys.exit(1)

if __name__ == '__main__':
    main()

