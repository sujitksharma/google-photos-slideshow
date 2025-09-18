#!/bin/bash

# Google Photos Slideshow Setup Script for Raspberry Pi Zero W
# This script sets up the slideshow application and configures it to start on boot

set -e

echo "Setting up Google Photos Slideshow for Raspberry Pi Zero W..."

# Check if running on Raspberry Pi
if ! grep -q "Raspberry Pi" /proc/cpuinfo 2>/dev/null; then
    echo "Warning: This script is designed for Raspberry Pi. Continuing anyway..."
fi

# Define installation directory
INSTALL_DIR="/home/pi/google-photos-slideshow"
SERVICE_NAME="google-photos-slideshow"

# Create installation directory
echo "Creating installation directory..."
sudo mkdir -p "$INSTALL_DIR"
sudo chown pi:pi "$INSTALL_DIR"

# Copy application files
echo "Copying application files..."
cp -r * "$INSTALL_DIR/"

# Set up Python virtual environment
echo "Setting up Python virtual environment..."
cd "$INSTALL_DIR"
python3 -m venv venv
source venv/bin/activate

# Install Python dependencies with timeout and retry handling
echo "Installing Python dependencies..."
pip install --upgrade pip

# Install dependencies with increased timeout and retries
echo "Installing dependencies (this may take a while on Pi Zero W)..."
pip install --timeout 300 --retries 3 --default-timeout 300 -r requirements.txt

# If that fails, try installing packages individually with system packages where possible
if [ $? -ne 0 ]; then
    echo "Pip install failed, trying alternative installation methods..."
    
    # Install system packages first (faster and more reliable)
    sudo apt-get update
    sudo apt-get install -y python3-pygame python3-pil python3-requests
    
    # Install remaining packages individually with longer timeouts
    pip install --timeout 600 --retries 5 google-auth==2.23.4
    pip install --timeout 600 --retries 5 google-auth-oauthlib==1.1.0
    pip install --timeout 600 --retries 5 google-auth-httplib2==0.1.1
    pip install --timeout 600 --retries 5 google-api-python-client==2.108.0
    pip install --timeout 600 --retries 5 schedule==1.2.0
    pip install --timeout 600 --retries 5 python-dotenv==1.0.0
fi

# Create .env file from example if it doesn't exist
if [ ! -f "$INSTALL_DIR/.env" ]; then
    echo "Creating .env file from example..."
    cp .env.example .env
    echo "Please edit $INSTALL_DIR/.env and set your Google Photos album ID"
fi

# Set up systemd service
echo "Setting up systemd service..."
sudo cp google-photos-slideshow.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable $SERVICE_NAME

# Create log directory
sudo mkdir -p /var/log/google-photos-slideshow
sudo chown pi:pi /var/log/google-photos-slideshow

# Set permissions
chmod +x "$INSTALL_DIR/main.py"

echo ""
echo "Setup completed!"
echo ""
echo "Next steps:"
echo "1. Place your Google Cloud credentials.json file in: $INSTALL_DIR/"
echo "2. Edit the configuration file: $INSTALL_DIR/.env"
echo "3. Set your Google Photos shared album ID in the .env file"
echo "4. Test the application: cd $INSTALL_DIR && source venv/bin/activate && python main.py"
echo "5. Start the service: sudo systemctl start $SERVICE_NAME"
echo "6. Check service status: sudo systemctl status $SERVICE_NAME"
echo ""
echo "The slideshow will automatically start on boot once configured."
echo "Use 'sudo systemctl stop $SERVICE_NAME' to stop the service."
echo "Use 'sudo systemctl disable $SERVICE_NAME' to disable auto-start."
