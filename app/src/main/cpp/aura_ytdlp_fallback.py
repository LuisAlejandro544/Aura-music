#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Aura Music - Extractor Nativo Autónomo en Python Puro (Reserva Offline / Fallback Directo).
Implementa el protocolo CLI de yt-dlp (--dump-single-json, --cookies, -f, etc.) resolviendo flujos
de audio y video de YouTube, YouTube Music y TikTok usando:
1) Cookies reales del navegador WebView (cookies.txt en formato Netscape si se pasa --cookies),
2) Clientes InnerTube actualizados (ANDROID_VR Quest 3, IOS, TVHTML5_SIMPLY_EMBEDDED_PLAYER con visitorData),
3) Red federada de Piped API (/streams/{videoId}) con proxy anti-403,
4) Instancias públicas de Invidious API v1 (?local=true /latest_version).
"""
import sys
import json
import urllib.request
import urllib.parse
import http.cookiejar
import ssl
import re

DEFAULT_UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

PIPED_INSTANCES = [
    "https://pipedapi.kavin.rocks",
    "https://pipedapi.tokhmi.xyz",
    "https://pipedapi.moomoo.me",
    "https://pipedapi.syncpundit.io",
    "https://api-piped.mha.fi",
    "https://piped-api.garudalinux.org",
    "https://pipedapi.r4fo.com",
    "https://pipedapi.leptons.xyz"
]

INVIDIOUS_INSTANCES = [
    "https://inv.nadeko.net",
    "https://invidious.nerdvpn.de",
    "https://invidious.f5.si",
    "https://iv.ggtyler.dev",
    "https://inv.tux.pizza"
]

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

def load_cookie_header(cookies_path):
    if not cookies_path:
        return "CONSENT=YES+cb.20240720-07-p0.es+FX+999; SOCS=CAI"
    try:
        cj = http.cookiejar.MozillaCookieJar(cookies_path)
        cj.load(ignore_discard=True, ignore_expires=True)
        pairs = []
        for c in cj:
            if "youtube.com" in c.domain or "google.com" in c.domain:
                pairs.append(f"{c.name}={c.value}")
        if pairs:
            return "; ".join(pairs)
    except Exception:
        pass
    return "CONSENT=YES+cb.20240720-07-p0.es+FX+999; SOCS=CAI"

def fetch_visitor_data(ctx, cookie_header):
    try:
        req = urllib.request.Request(
            "https://www.youtube.com/sw.js_data",
            headers={"User-Agent": DEFAULT_UA, "Cookie": cookie_header}
        )
        with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
            raw = resp.read().decode('utf-8', errors='ignore')
            m = re.search(r'"(Cgt[a-zA-Z0-9_\-%=]+)"', raw)
            if m:
                return m.group(1)
    except Exception:
        pass
    return None

def parse_innertube_response(video_id, parsed):
    status = parsed.get("playabilityStatus", {}).get("status", "")
    if status and status != "OK":
        return None
    details = parsed.get("videoDetails", {})
    title = details.get("title", f"Video {video_id}")
    author = details.get("author", "YouTube Artist").replace(" - Topic", "")
    duration = int(details.get("lengthSeconds", 0) or 0)
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
        if not f_url or ".m3u8" in f_url:
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

def resolve_youtube_innertube(video_id, cookies_path=None):
    ctx = ssl._create_unverified_context()
    cookie_header = load_cookie_header(cookies_path)
    visitor_data = fetch_visitor_data(ctx, cookie_header)

    clients = [
        {
            "name": "ANDROID_VR",
            "version": "1.61.48",
            "id": "28",
            "ua": "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
            "extra": {"deviceMake": "Oculus", "deviceModel": "Quest 3", "androidSdkVersion": 32, "osName": "Android", "osVersion": "12"},
            "embed": False
        },
        {
            "name": "IOS",
            "version": "20.10.4",
            "id": "5",
            "ua": "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
            "extra": {"deviceMake": "Apple", "deviceModel": "iPhone16,2", "osName": "iPhone", "osVersion": "18.3.2.22D82"},
            "embed": False
        },
        {
            "name": "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
            "version": "2.0",
            "id": "85",
            "ua": "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
            "extra": {},
            "embed": True
        }
    ]

    endpoint = "https://www.youtube.com/youtubei/v1/player?prettyPrint=false"
    for c in clients:
        try:
            client_dict = {
                "clientName": c["name"],
                "clientVersion": c["version"],
                "hl": "es",
                "gl": "US"
            }
            if visitor_data:
                client_dict["visitorData"] = visitor_data
            client_dict.update(c["extra"])

            context_dict = {"client": client_dict}
            if c["embed"]:
                context_dict["thirdParty"] = {"embedUrl": "https://www.youtube.com/"}

            payload = {
                "videoId": video_id,
                "context": context_dict,
                "contentCheckOk": True,
                "racyCheckOk": True
            }
            data = json.dumps(payload).encode('utf-8')
            headers = {
                "Content-Type": "application/json",
                "User-Agent": c["ua"],
                "X-YouTube-Client-Name": c["id"],
                "X-YouTube-Client-Version": c["version"],
                "Cookie": cookie_header
            }
            if visitor_data:
                headers["X-Goog-Visitor-Id"] = visitor_data

            req = urllib.request.Request(endpoint, data=data, headers=headers)
            with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
                parsed = json.loads(resp.read().decode('utf-8'))
                res = parse_innertube_response(video_id, parsed)
                if res:
                    return res
        except Exception:
            continue
    return None

def resolve_piped_api(video_id):
    ctx = ssl._create_unverified_context()
    for inst in PIPED_INSTANCES:
        try:
            req = urllib.request.Request(
                f"{inst}/streams/{video_id}",
                headers={"User-Agent": DEFAULT_UA, "Accept": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
                parsed = json.loads(resp.read().decode('utf-8'))
                if "audioStreams" not in parsed:
                    continue
                title = parsed.get("title", f"Video {video_id}")
                uploader = parsed.get("uploader", "YouTube Music").replace(" - Topic", "")
                duration = int(parsed.get("duration", 0) or 0)
                thumb = parsed.get("thumbnailUrl") or f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg"
                out_formats = []
                audio_url = None
                for a in parsed.get("audioStreams", []):
                    u = a.get("url")
                    if not u or ".m3u8" in u:
                        continue
                    mime = a.get("mimeType", "")
                    if not audio_url or "mp4" in mime:
                        audio_url = u
                    out_formats.append({
                        "url": u,
                        "vcodec": "none",
                        "acodec": "mp4a" if "mp4" in mime else "opus",
                        "ext": "m4a" if "mp4" in mime else "webm",
                        "abr": float(a.get("bitrate", 128000) or 128000) / 1000.0,
                        "protocol": "https"
                    })
                for v in parsed.get("videoStreams", []):
                    u = v.get("url")
                    if not u or ".m3u8" in u:
                        continue
                    h = int(v.get("height", 0) or 480)
                    out_formats.append({
                        "url": u,
                        "vcodec": "avc1",
                        "acodec": "none",
                        "height": h,
                        "ext": "mp4",
                        "protocol": "https"
                    })
                if audio_url:
                    return {
                        "id": video_id,
                        "title": title,
                        "uploader": uploader,
                        "channel": uploader,
                        "duration": duration,
                        "thumbnail": thumb,
                        "url": audio_url,
                        "formats": out_formats
                    }
        except Exception:
            continue
    return None

def resolve_invidious_api(video_id):
    ctx = ssl._create_unverified_context()
    for inst in INVIDIOUS_INSTANCES:
        try:
            req = urllib.request.Request(
                f"{inst}/api/v1/videos/{video_id}?local=true",
                headers={"User-Agent": DEFAULT_UA, "Accept": "application/json"}
            )
            with urllib.request.urlopen(req, timeout=8, context=ctx) as resp:
                parsed = json.loads(resp.read().decode('utf-8'))
                title = parsed.get("title", f"Video {video_id}")
                author = parsed.get("author", "YouTube").replace(" - Topic", "")
                duration = int(parsed.get("lengthSeconds", 0) or 0)
                out_formats = []
                audio_url = None
                for f in parsed.get("adaptiveFormats", []):
                    itag = str(f.get("itag", ""))
                    mime = f.get("type", "")
                    if not itag.isdigit():
                        continue
                    proxied_url = f"{inst}/latest_version?id={video_id}&itag={itag}&local=true"
                    if "audio" in mime:
                        if not audio_url or "mp4" in mime:
                            audio_url = proxied_url
                        out_formats.append({
                            "url": proxied_url,
                            "vcodec": "none",
                            "acodec": "mp4a" if "mp4" in mime else "opus",
                            "ext": "m4a" if "mp4" in mime else "webm",
                            "abr": float(f.get("bitrate", 128000) or 128000) / 1000.0,
                            "protocol": "https"
                        })
                    elif "video" in mime:
                        out_formats.append({
                            "url": proxied_url,
                            "vcodec": "avc1" if "avc1" in mime else "vp9",
                            "acodec": "none",
                            "height": 480,
                            "ext": "mp4",
                            "protocol": "https"
                        })
                if audio_url:
                    return {
                        "id": video_id,
                        "title": title,
                        "uploader": author,
                        "channel": author,
                        "duration": duration,
                        "thumbnail": f"https://i.ytimg.com/vi/{video_id}/hqdefault.jpg",
                        "url": audio_url,
                        "formats": out_formats
                    }
        except Exception:
            continue
    return None

def main():
    target_url = None
    cookies_path = None
    args = sys.argv[1:]
    for i, arg in enumerate(args):
        if arg == "--cookies" and i + 1 < len(args):
            cookies_path = args[i + 1]
        elif arg.startswith("http://") or arg.startswith("https://"):
            target_url = arg

    if not target_url:
        sys.exit(1)

    yt_id = extract_youtube_id(target_url)
    if yt_id:
        info = (
            resolve_youtube_innertube(yt_id, cookies_path)
            or resolve_piped_api(yt_id)
            or resolve_invidious_api(yt_id)
        )
        if info:
            print(json.dumps(info))
            sys.exit(0)

    sys.stderr.write("No se pudo resolver el flujo desde el extractor de reserva.\n")
    sys.exit(1)

if __name__ == '__main__':
    main()
