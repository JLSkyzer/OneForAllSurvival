"""Socle partagé : chemins, jar vanilla, caméra, bruit, conversions de couleur."""
import io
import json
import math
import os
import tempfile
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "assets"
PREVIEW = ROOT / "preview"
CACHE = Path(os.environ.get("LI_CACHE", Path(tempfile.gettempdir()) / "lost_island_cache"))
JAR = Path.home() / ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client-extra.jar"
FFMPEG = Path(os.environ["LOCALAPPDATA"]) / "Programs/Ultimate Vocal Remover/ffmpeg.exe"

# Résolution de sortie, et marge des calques pour le zoom et la parallaxe.
OUT_W, OUT_H = 1920, 1080
PLATE_W, PLATE_H = 2112, 1188

for directory in (ASSETS, PREVIEW, CACHE):
    directory.mkdir(parents=True, exist_ok=True)


def jar_bytes(name):
    with zipfile.ZipFile(JAR) as archive:
        return archive.read(f"assets/minecraft/{name}")


def jar_image(name):
    """Charge une image du jar vanilla en RGBA flottant 0..1."""
    image = Image.open(io.BytesIO(jar_bytes(f"textures/{name}.png"))).convert("RGBA")
    return np.asarray(image, dtype=np.float32) / 255.0


def jar_json(name):
    return json.loads(jar_bytes(name))


def srgb_to_linear(c):
    c = np.asarray(c, dtype=np.float32)
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def linear_to_srgb(c):
    c = np.clip(c, 0.0, None)
    return np.where(c <= 0.0031308, c * 12.92, 1.055 * np.power(c, 1 / 2.4) - 0.055)


def tonemap(c):
    """Courbe filmique douce : garde des hautes lumières lisibles près des feux."""
    c = np.maximum(c, 0)
    return np.clip((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14), 0, 1)


def smoothstep(a, b, x):
    t = np.clip((np.asarray(x, dtype=np.float32) - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


def fbm(shape, scale, seed, octaves=4, wrap=False):
    """Bruit fractal centré, amplitude environ -1..1."""
    rng = np.random.default_rng(seed)
    out = np.zeros(shape, dtype=np.float32)
    amp, total = 1.0, 0.0
    mode = "wrap" if wrap else "reflect"
    for i in range(octaves):
        layer = gaussian_filter(rng.standard_normal(shape).astype(np.float32), scale / 2 ** i, mode=mode)
        layer /= layer.std() + 1e-6
        out += layer * amp
        total += amp
        amp *= 0.5
    return out / total / 1.5


def hex_rgb(value):
    value = value.lstrip("#")
    return np.array([int(value[i:i + 2], 16) / 255 for i in (0, 2, 4)], dtype=np.float32)


class Camera:
    """Caméra de la scène, partagée entre le rendu des calques et l'animation."""

    def __init__(self, width=PLATE_W, height=PLATE_H):
        # Caméra relevée sur la bannière : horizon incliné de 9 degrés, 2,5 blocs au-dessus des survivants.
        self.pos = np.array([250.0, 93.5, 0.0])
        yaw, pitch, roll = math.radians(0.0), math.radians(-4.6), math.radians(-9.0)
        self.forward = np.array([math.sin(yaw) * math.cos(pitch), math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
        right = np.array([math.cos(yaw), 0.0, -math.sin(yaw)])
        up = np.cross(self.forward, right)
        self.right = right * math.cos(roll) + up * math.sin(roll)
        self.up = up * math.cos(roll) - right * math.sin(roll)
        self.width, self.height = width, height
        # Champ horizontal défini sur l'image finale ; la marge du calque l'élargit d'autant.
        self.focal = (OUT_W / 2) / math.tan(math.radians(37.5)) * (width / PLATE_W)

    def rays(self, px, py):
        """Directions unitaires pour des coordonnées de pixels dans le calque."""
        dx = (np.asarray(px) - self.width / 2) / self.focal
        dy = (self.height / 2 - np.asarray(py)) / self.focal
        d = self.forward[None] + dx[..., None] * self.right[None] + dy[..., None] * self.up[None]
        return d / np.linalg.norm(d, axis=-1, keepdims=True)

    def project(self, point):
        """Monde vers pixels du calque ; renvoie (x, y, profondeur)."""
        v = np.asarray(point, dtype=np.float64) - self.pos
        depth = v @ self.forward
        x = self.width / 2 + (v @ self.right) / depth * self.focal
        y = self.height / 2 - (v @ self.up) / depth * self.focal
        return x, y, depth

    def direction_at(self, u, v):
        """Direction du monde pour une position relative de l'image finale (0..1)."""
        px = self.width / 2 + (u - 0.5) * OUT_W * (self.width / PLATE_W)
        py = self.height / 2 + (v - 0.5) * OUT_H * (self.width / PLATE_W)
        return self.rays(np.array([px]), np.array([py]))[0]


def save_rgba(path, array):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(array, 0, 1) * 255 + 0.5).astype(np.uint8)).save(path, optimize=False, compress_level=6)
