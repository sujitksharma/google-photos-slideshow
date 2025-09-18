# Manual Installation Guide for Raspberry Pi Zero W

If the automated `setup.sh` script fails due to network timeouts, follow these manual steps:

## Step 1: Install System Dependencies

```bash
sudo apt-get update
sudo apt-get install -y python3-pip python3-venv python3-pygame python3-pil python3-requests git
```

## Step 2: Create Project Directory

```bash
mkdir -p /home/pi/google-photos-slideshow
cd /home/pi/google-photos-slideshow
```

## Step 3: Copy Project Files

Copy all the project files to `/home/pi/google-photos-slideshow/`

## Step 4: Create Virtual Environment

```bash
python3 -m venv venv
source venv/bin/activate
```

## Step 5: Install Python Packages (Alternative Methods)

### Method A: Use System Packages (Recommended for Pi Zero W)

```bash
# Use system packages for heavy dependencies
sudo apt-get install -y python3-pygame python3-pil python3-requests

# Install only Google API packages via pip
pip install --timeout 600 --retries 5 google-auth
pip install --timeout 600 --retries 5 google-auth-oauthlib  
pip install --timeout 600 --retries 5 google-auth-httplib2
pip install --timeout 600 --retries 5 google-api-python-client
pip install --timeout 600 --retries 5 schedule
pip install --timeout 600 --retries 5 python-dotenv
```

### Method B: Install from PyPI with Timeouts

```bash
pip install --timeout 600 --retries 5 --default-timeout 600 -r requirements.txt
```

### Method C: Install Individual Packages

If bulk installation fails, install one by one:

```bash
pip install --timeout 600 google-auth==2.23.4
pip install --timeout 600 google-auth-oauthlib==1.1.0  
pip install --timeout 600 google-auth-httplib2==0.1.1
pip install --timeout 600 google-api-python-client==2.108.0
pip install --timeout 600 requests==2.31.0
pip install --timeout 600 Pillow==10.0.1
pip install --timeout 600 pygame==2.5.2
pip install --timeout 600 schedule==1.2.0
pip install --timeout 600 python-dotenv==1.0.0
```

## Step 6: Configure Environment

```bash
cp .env.example .env
nano .env
```

Set your Google Photos album ID:
```
GOOGLE_PHOTOS_ALBUM_ID=your_album_id_here
```

## Step 7: Set Up Systemd Service

```bash
sudo cp google-photos-slideshow.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable google-photos-slideshow
```

## Step 8: Test Installation

```bash
cd /home/pi/google-photos-slideshow
source venv/bin/activate
python main.py
```

## Troubleshooting Network Issues

### If pip keeps timing out:

1. **Increase swap space** (helps with memory during compilation):
```bash
sudo dphys-swapfile swapoff
sudo nano /etc/dphys-swapfile
# Change CONF_SWAPSIZE=100 to CONF_SWAPSIZE=512
sudo dphys-swapfile setup
sudo dphys-swapfile swapon
```

2. **Use alternative package index**:
```bash
pip install --index-url https://pypi.org/simple/ --timeout 600 package_name
```

3. **Install from wheel files** (if available):
```bash
pip install --only-binary=all --timeout 600 package_name
```

4. **Use system packages when possible**:
```bash
sudo apt-get install python3-package-name
```

### Network optimization for Pi Zero W:

```bash
# Add to /etc/dhcpcd.conf for better WiFi stability
echo "interface wlan0" | sudo tee -a /etc/dhcpcd.conf
echo "static domain_name_servers=8.8.8.8 8.8.4.4" | sudo tee -a /etc/dhcpcd.conf
```

## Alternative: Pre-built Package Installation

If all else fails, you can install packages using the system package manager:

```bash
sudo apt-get install -y python3-google-auth python3-google-auth-oauthlib python3-googleapi
```

Note: System packages might be older versions but should work for basic functionality.
