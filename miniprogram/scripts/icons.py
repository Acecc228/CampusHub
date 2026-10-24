"""Render repository-owned line icons for the native tabBar (no remote assets)."""
from pathlib import Path
from PIL import Image, ImageDraw
root = Path(__file__).resolve().parents[1] / 'src' / 'assets'
root.mkdir(exist_ok=True)
for active, color in [(False, '#606b80'), (True, '#4f46e5')]:
    for name in ['home', 'activities', 'ticket', 'profile']:
        im = Image.new('RGBA', (96, 96))
        d = ImageDraw.Draw(im)
        if name == 'home':
            d.line([(17,44),(48,18),(79,44)], fill=color, width=6)
            d.line([(24,40),(24,77),(41,77),(41,56),(55,56),(55,77),(72,77),(72,40)],fill=color,width=6)
        elif name == 'activities':
            d.rounded_rectangle((20,26,76,78),radius=7,outline=color,width=6)
            d.line((20,42,76,42),fill=color,width=5)
            d.line((34,17,34,32),fill=color,width=6);d.line((62,17,62,32),fill=color,width=6)
            for x,y in [(35,55),(58,55),(35,67)]:d.ellipse((x-3,y-3,x+3,y+3),fill=color)
        elif name == 'ticket':
            d.rounded_rectangle((16,27,80,71),radius=6,outline=color,width=6)
            for y in [35,47,59]: d.line((57,y,57,y+5),fill=color,width=4)
            d.line([(28,48),(36,55),(47,41)],fill=color,width=5)
        else:
            d.ellipse((34,17,62,45),outline=color,width=6)
            d.arc((20,49,76,96),180,360,fill=color,width=6)
            d.line((21,74,75,74),fill=color,width=6)
        im.resize((48,48),Image.Resampling.LANCZOS).save(root/(name+('-active' if active else '')+'.png'))
