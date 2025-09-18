# Google Photos Slideshow for Raspberry Pi Zero W

A Python application that creates a slideshow from a shared Google Photos album, designed specifically for Raspberry Pi Zero W. The app automatically syncs with your Google Photos album every 5 minutes and displays images in a continuous slideshow.

## Features

- 🔄 **Auto-sync**: Synchronizes with Google Photos album every 5 minutes
- 💾 **Smart caching**: Maintains local cache with size limits optimized for Pi Zero W
- 🖼️ **Slideshow display**: Full-screen slideshow with configurable intervals
- 🚀 **Auto-start**: Starts automatically on Pi boot
- ⚡ **Pi Zero W optimized**: Designed for limited resources (512MB RAM)
- 🎮 **Interactive controls**: Keyboard shortcuts for navigation

## Requirements

### Hardware
- Raspberry Pi Zero W (or any Raspberry Pi with WiFi)
- MicroSD card (16GB+ recommended)
- Display connected via HDMI
- Keyboard (for initial setup and controls)

### Software
- Raspberry Pi OS with desktop environment
- Python 3.7+
- Internet connection

## Installation

### 1. Prepare Google Photos API

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Create a new project or select an existing one
3. Enable the Photos Library API
4. Create credentials (OAuth 2.0 Client ID) for a desktop application
5. Download the credentials file as `credentials.json`

### 2. Get Your Album ID

1. Go to [photos.google.com](https://photos.google.com)
2. Open the shared album you want to use
3. Copy the album ID from the URL (the long string after `/album/`)

### 3. Install on Raspberry Pi

1. Clone or copy this project to your Raspberry Pi:
```bash
git clone <repository-url> /home/pi/google-photos-slideshow
# OR copy the files manually
```

2. Run the setup script:
```bash
cd /home/pi/google-photos-slideshow
chmod +x setup.sh
./setup.sh
```

3. Place your `credentials.json` file in the project directory:
```bash
cp /path/to/your/credentials.json /home/pi/google-photos-slideshow/
```

4. Configure the application:
```bash
cp .env.example .env
nano .env
```

Set your album ID in the `.env` file:
```
GOOGLE_PHOTOS_ALBUM_ID=your_album_id_here
```

### 4. First Run and Authentication

1. Test the application:
```bash
cd /home/pi/google-photos-slideshow
source venv/bin/activate
python main.py
```

2. On first run, you'll be prompted to authenticate with Google Photos
3. Follow the authentication flow in your web browser
4. The app will save authentication tokens for future use

## Configuration

Edit the `config.py` file or use environment variables in `.env`:

### Display Settings
- `SLIDESHOW_INTERVAL`: Seconds between images (default: 10)
- `FULLSCREEN`: Enable fullscreen mode (default: True)
- `DISPLAY_WIDTH/HEIGHT`: Display resolution (auto-detected if possible)

### Cache Settings
- `MAX_CACHE_SIZE_MB`: Maximum cache size in MB (default: 500)
- `CACHE_DIR`: Local cache directory (default: ./photo_cache)
- `IMAGE_QUALITY`: JPEG quality for cached images (default: 85)

### Sync Settings
- `SYNC_INTERVAL`: Seconds between sync operations (default: 300 = 5 minutes)
- `MAX_CONCURRENT_DOWNLOADS`: Concurrent downloads (default: 2 for Pi Zero W)

## Usage

### Manual Start
```bash
cd /home/pi/google-photos-slideshow
source venv/bin/activate
python main.py
```

### Service Management
```bash
# Start the service
sudo systemctl start google-photos-slideshow

# Stop the service
sudo systemctl stop google-photos-slideshow

# Check service status
sudo systemctl status google-photos-slideshow

# View logs
sudo journalctl -u google-photos-slideshow -f
```

### Keyboard Controls (during slideshow)
- `ESC`: Exit slideshow
- `SPACE`: Pause/resume
- `RIGHT ARROW`: Next image
- `LEFT ARROW`: Previous image

## File Structure

```
google-photos-slideshow/
├── main.py                    # Main application entry point
├── config.py                  # Configuration settings
├── google_photos_client.py    # Google Photos API client
├── cache_manager.py           # Local cache management
├── sync_service.py            # Background sync service
├── slideshow.py               # Slideshow display logic
├── requirements.txt           # Python dependencies
├── setup.sh                   # Installation script
├── google-photos-slideshow.service  # Systemd service file
├── .env.example               # Environment variables template
├── credentials.json           # Google API credentials (you provide)
├── token.json                 # OAuth tokens (auto-generated)
├── photo_cache/               # Local image cache directory
└── slideshow.log              # Application log file
```

## Troubleshooting

### Common Issues

1. **"No module named 'pygame'"**
   - Install pygame: `sudo apt-get install python3-pygame`
   - Or reinstall in venv: `pip install pygame`

2. **"Credentials file not found"**
   - Ensure `credentials.json` is in the project directory
   - Check file permissions: `chmod 644 credentials.json`

3. **"Album ID not configured"**
   - Set `GOOGLE_PHOTOS_ALBUM_ID` in your `.env` file
   - Make sure the album is shared and accessible

4. **Display issues on Pi Zero W**
   - Ensure X11 is running: `export DISPLAY=:0`
   - Check GPU memory split: `sudo raspi-config` → Advanced → Memory Split (set to 64 or 128)

5. **Service won't start on boot**
   - Check service status: `sudo systemctl status google-photos-slideshow`
   - Ensure graphical session is available
   - Check logs: `sudo journalctl -u google-photos-slideshow`

### Performance Tips for Pi Zero W

1. **Reduce cache size**: Set `MAX_CACHE_SIZE_MB` to 200-300
2. **Lower image quality**: Set `IMAGE_QUALITY` to 70-80
3. **Increase slideshow interval**: Set `SLIDESHOW_INTERVAL` to 15-30 seconds
4. **Reduce concurrent downloads**: Keep `MAX_CONCURRENT_DOWNLOADS` at 1-2

### Logs

- Application logs: `tail -f slideshow.log`
- System logs: `sudo journalctl -u google-photos-slideshow -f`

## Development

### Running in Development Mode

```bash
# Install development dependencies
pip install -r requirements.txt

# Run with debug logging
LOG_LEVEL=DEBUG python main.py
```

### Testing Components

```python
# Test Google Photos connection
from google_photos_client import GooglePhotosClient
client = GooglePhotosClient()

# Test cache management
from cache_manager import CacheManager
cache = CacheManager()

# Test sync service
from sync_service import SyncService
sync = SyncService()
```

## License

This project is open source. Feel free to modify and distribute according to your needs.

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review the logs for error messages
3. Ensure all dependencies are properly installed
4. Verify Google Photos API credentials and permissions
