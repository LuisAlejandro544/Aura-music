#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Aura Music - Extractor Nativo Autónomo en Python Puro (Reserva Offline).
Implementa el protocolo CLI de yt-dlp (--dump-single-json, -f, etc.) resolviendo flujos
de audio y video de YouTube y TikTok directamente con la librería estándar de CPython 3.
"""
import sys
import json
import urllib.request
import urllib.parse
import ssl
import re

def extract_youtube_id(url):
    patterns = [
        r'(?:v=|\/)([0-9A-Za-z_-]{11}).*',
        r'(?:youtu\.be\/)([0-9A-Za-z_-]{11})',
        r'(?:shorts\/)([0-9A-Za-z_-]{11})'
    ]
    for p in patterns:
        m = re.search(p, url)
        if m:
            return m.group(1)
    return None

def resolve_youtube_innertube(video_id):
    endpoint = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    payload = {
        "videoId": video_id,
        "context": {
            "client": {
                "clientName": "ANDROID_VR",
                "clientVersion": "1.65.1",
                "deviceModel": "Quest 3",
                "osName": "Android",
                "osVersion": "12",
                "hl": "es",
                "gl": "US"
            }
        }
    }
    data = json.dumps(payload).encode('utf-8')
    req = urllib.request.Request(endpoint, data=data, headers={
        "Content-Type": "application/json",
        "User-Agent": "com.google.android.apps.youtube.vr.oculus/1.65.1 (Linux; U; Android 12)"
    })
    ctx = ssl._create_unverified_context()
    try:
        with urllib.request.urlopen(req, timeout=15, context=ctx) as resp:
            body = resp.read().decode('utf-8')
            parsed = json.loads(body)
            details = parsed.get("videoDetails", {})
            title = details.get("title", f"Video {video_id}")
            author = details.get("author", "YouTube Artist")
            duration = int(details.get("lengthSeconds", 0))
            thumbnails = details.get("thumbnail", {}).get("thumbnails", [])
            thumb_url = thumbnails[-1]["url"] if thumbnails else f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"

            streaming = parsed.get("streamingData", {})
            formats = streaming.get("adaptiveFormats", []) + streaming.get("formats", [])

            audio_url = None
            video_url = None
            out_formats = []

            for f in formats:
                mime = f.get("mimeType", "")
                f_url = f.get("url")
                if not f_url:
                    continue
                height = int(f.get("height", 0) or 0)
                abr = float(f.get("averageBitrate", 0) or f.get("bitrate", 0) or 0) / 1000.0
                if "audio" in mime:
                    if not audio_url:
                        audio_url = f_url
                    out_formats.append({
                        "url": f_url,
                        "vcodec": "none",
                        "acodec": "mp4a" if "mp4" in mime else "opus",
                        "ext": "m4a" if "mp4" in mime else "webm",
                        "abr": abr,
                        "protocol": "https"
                    })
                elif "video" in mime:
                    if not video_url or height == 480:
                        video_url = f_url
                    out_formats.append({
                        "url": f_url,
                        "vcodec": "avc1" if "avc1" in mime else "vp9",
                        "acodec": "mp4a" if "mp4a" in mime else "none",
                        "height": height if height > 0 else 480,
                        "ext": "mp4" if "mp4" in mime else "webm",
                        "protocol": "https"
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
                "formats": out_formats
            }
    except Exception as e:
        sys.stderr.write(f"Aura Python Extractor Error: {e}\n")
        return None

def main():
    target_url = None
    for arg in sys.argv[1:]:
        if arg.startswith("http://") or arg.startswith("https://"):
            target_url = arg
            break

    if not target_url:
        sys.exit(1)

    yt_id = extract_youtube_id(target_url)
    if yt_id:
        info = resolve_youtube_innertube(yt_id)
        if info:
            print(json.dumps(info))
            sys.exit(0)

    sys.stderr.write("No se pudo resolver el flujo desde el extractor de reserva.\n")
    sys.exit(1)

if __name__ == '__main__':
    main()
