"""
Main application entry point for Google Photos Slideshow
"""
import os
import sys
import logging
import signal
import threading
from pathlib import Path
from dotenv import load_dotenv

# Load environment variables
load_dotenv()

import config
from sync_service import SyncService
from cache_manager import CacheManager
from slideshow import Slideshow

# Set up logging
def setup_logging():
    """Configure logging for the application"""
    log_format = '%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    
    # Console handler
    console_handler = logging.StreamHandler()
    console_handler.setLevel(logging.INFO)
    console_handler.setFormatter(logging.Formatter(log_format))
    
    # File handler
    file_handler = logging.FileHandler(config.LOG_FILE)
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(logging.Formatter(log_format))
    
    # Root logger
    root_logger = logging.getLogger()
    root_logger.setLevel(getattr(logging, config.LOG_LEVEL))
    root_logger.addHandler(console_handler)
    root_logger.addHandler(file_handler)

class GooglePhotosSlideshow:
    def __init__(self):
        self.sync_service = None
        self.slideshow = None
        self.cache_manager = None
        self.running = False
    
    def setup(self):
        """Initialize all components"""
        try:
            # Check if album ID is configured
            if not config.SHARED_ALBUM_ID:
                print("ERROR: Google Photos album ID not configured!")
                print("Please:")
                print("1. Copy .env.example to .env")
                print("2. Set GOOGLE_PHOTOS_ALBUM_ID in the .env file")
                print("3. Make sure you have credentials.json from Google Cloud Console")
                return False
            
            # Check for credentials file
            if not config.CREDENTIALS_FILE.exists():
                print(f"ERROR: Credentials file not found at {config.CREDENTIALS_FILE}")
                print("Please download credentials.json from Google Cloud Console")
                print("and place it in the project directory.")
                return False
            
            # Initialize components
            self.cache_manager = CacheManager()
            self.sync_service = SyncService()
            self.slideshow = Slideshow(self.cache_manager)
            
            return True
            
        except Exception as e:
            logging.error(f"Failed to setup application: {e}")
            return False
    
    def start(self):
        """Start the slideshow application"""
        if not self.setup():
            return False
        
        self.running = True
        
        try:
            # Start sync service
            self.sync_service.start()
            
            # Wait a moment for initial sync to start
            import time
            time.sleep(2)
            
            # Load initial images
            self.slideshow.load_images()
            
            # Start slideshow (this will block)
            logging.info("Starting Google Photos Slideshow")
            self.slideshow.start()
            
        except KeyboardInterrupt:
            logging.info("Received interrupt signal")
        except Exception as e:
            logging.error(f"Application error: {e}")
        finally:
            self.stop()
        
        return True
    
    def stop(self):
        """Stop the slideshow application"""
        if not self.running:
            return
        
        logging.info("Stopping Google Photos Slideshow")
        self.running = False
        
        if self.slideshow:
            self.slideshow.stop()
            self.slideshow.cleanup()
        
        if self.sync_service:
            self.sync_service.stop()
        
        logging.info("Application stopped")

def signal_handler(signum, frame):
    """Handle system signals for graceful shutdown"""
    logging.info(f"Received signal {signum}")
    sys.exit(0)

def main():
    """Main entry point"""
    # Set up logging
    setup_logging()
    
    # Set up signal handlers for graceful shutdown
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    # Create and start the application
    app = GooglePhotosSlideshow()
    
    try:
        success = app.start()
        sys.exit(0 if success else 1)
    except Exception as e:
        logging.error(f"Fatal error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()
