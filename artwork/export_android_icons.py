#!/usr/bin/env python3
"""Export the Sandpiper SVG artwork to Android vectors and legacy launcher icons."""

import io
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

from PIL import Image, ImageChops, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
ARTWORK = ROOT / "artwork"
RES = ROOT / "app/src/main/res"
ANDROID = "http://schemas.android.com/apk/res/android"
SVG = "{http://www.w3.org/2000/svg}"
ET.register_namespace("android", ANDROID)


def android_attr(name):
    return f"{{{ANDROID}}}{name}"


def export_vector(source, destination, colors=None):
    svg = ET.parse(source).getroot()
    x, y, width, height = map(float, svg.attrib["viewBox"].split())
    assert (x, y) == (0, 0) and width == height
    size = str(int(width))
    vector = ET.Element("vector", {
        android_attr("width"): f"{size}dp", android_attr("height"): f"{size}dp",
        android_attr("viewportWidth"): size, android_attr("viewportHeight"): size,
    })
    group = svg.find(f"{SVG}g")
    transform = re.fullmatch(r"translate\(([-.\d]+) ([-.\d]+)\) scale\(([-.\d]+)\)", group.attrib["transform"])
    assert transform is not None
    dx, dy, scale = transform.groups()
    target = ET.SubElement(vector, "group", {
        android_attr("translateX"): dx, android_attr("translateY"): dy,
        android_attr("scaleX"): scale, android_attr("scaleY"): scale,
    })
    for path in group.findall(f"{SVG}path"):
        attrs = {android_attr("pathData"): path.attrib["d"]}
        fill = path.attrib["fill"]
        attrs[android_attr("fillColor")] = "#00000000" if fill == "none" else (colors or {}).get(fill, fill)
        if "stroke" in path.attrib:
            stroke = path.attrib["stroke"]
            attrs[android_attr("strokeColor")] = (colors or {}).get(stroke, stroke)
            attrs[android_attr("strokeWidth")] = path.attrib["stroke-width"]
            attrs[android_attr("strokeLineCap")] = path.attrib["stroke-linecap"]
        ET.SubElement(target, "path", attrs)
    ET.indent(vector, space="    ")
    destination.parent.mkdir(parents=True, exist_ok=True)
    ET.ElementTree(vector).write(destination, encoding="utf-8", xml_declaration=True)


def export_launcher_images(source):
    size = 864
    png = subprocess.check_output([
        "ffmpeg", "-loglevel", "error", "-width", str(size), "-height", str(size),
        "-i", str(source), "-frames:v", "1", "-f", "image2pipe", "-c:v", "png", "pipe:1",
    ])
    mark = Image.open(io.BytesIO(png)).convert("RGBA")
    safe = Image.new("L", mark.size)
    ImageDraw.Draw(safe).ellipse((140, 140, 724, 724), fill=255)
    assert ImageChops.subtract(mark.getchannel("A"), safe).getbbox() is None, "Mark exceeds the adaptive icon safe circle"
    square = Image.new("RGBA", mark.size, "#ECFEE8")
    square.alpha_composite(mark)
    circle = Image.new("L", mark.size)
    ImageDraw.Draw(circle).ellipse((0, 0, size - 1, size - 1), fill=255)
    round_icon = square.copy()
    round_icon.putalpha(circle)
    for density, pixels in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
        for name, image in (("ic_launcher", square), ("ic_launcher_round", round_icon)):
            image.resize((pixels, pixels), Image.Resampling.LANCZOS).save(
                RES / f"mipmap-{density}" / f"{name}.webp", "WEBP", lossless=True, exact=True,
            )


if __name__ == "__main__":
    mark = ARTWORK / "sandpiper-mark.svg"
    export_vector(mark, RES / "drawable/sandpiper_mark.xml")
    export_vector(mark, RES / "drawable/sandpiper_splash_mark.xml")
    export_vector(mark, RES / "drawable-night/sandpiper_splash_mark.xml", {"#41337A": "#ECFEE8"})
    export_vector(ARTWORK / "sandpiper-notification.svg", RES / "drawable/ic_stat_sendspin.xml")
    export_launcher_images(mark)
