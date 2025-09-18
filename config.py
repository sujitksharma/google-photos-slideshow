"""
Configuration settings for the Google Photos Slideshow
"""
import os
from pathlib import Path

# Base directory
BASE_DIR = Path(__file__).parent

# Google Photos API settings
GOOGLE_PHOTOS_SCOPES = ['https://www.googleapis.com/auth/photoslibrary.readonly']
CREDENTIALS_FILE = BASE_DIR / 'credentials.json'
TOKEN_FILE = BASE_DIR / 'token.json'

# Local cache settings
CACHE_DIR = BASE_DIR / 'photo_cache'
MAX_CACHE_SIZE_MB = 500  # Maximum cache size in MB (adjust for Pi Zero W)
IMAGE_QUALITY = 85  # JPEG quality for cached images

# Slideshow settings
SLIDESHOW_INTERVAL = 10  # seconds between images
SYNC_INTERVAL = 300  # seconds (5 minutes) between sync operations
FULLSCREEN = True
DISPLAY_WIDTH = 1920  # Will be auto-detected if possible
DISPLAY_HEIGHT = 1080

# Album settings (to be configured by user)
SHARED_ALBUM_ID = os.getenv('GOOGLE_PHOTOS_ALBUM_ID', '')

# Logging
LOG_LEVEL = 'INFO'
LOG_FILE = BASE_DIR / 'slideshow.log'

# Performance settings for Raspberry Pi Zero W
MAX_CONCURRENT_DOWNLOADS = 2
IMAGE_RESIZE_QUALITY = 'LANCZOS'
PRELOAD_IMAGES = 3  # Number of images to preload in memory
