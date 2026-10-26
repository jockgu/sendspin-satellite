# Sandpiper artwork

`sandpiper-mark.svg` is the launcher and splash mark. Its artwork is kept
within the adaptive icon's 72 dp safe circle on a 108 dp canvas.
`sandpiper-notification.svg` is the simplified, white 24 dp notification mark.

After editing either SVG, run `python3 artwork/export_android_icons.py` from
the repository root. The exporter requires Pillow and FFmpeg with librsvg
support. It generates the Android vector drawables, including the light and
dark splash variants, and the square and round legacy WebP icons at all five
densities. The adaptive launcher background and splash backgrounds are defined
in Android resources.
