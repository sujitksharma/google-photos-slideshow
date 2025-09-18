"""
Synchronization service for Google Photos album
"""
import logging
import threading
import time
from pathlib import Path
from typing import Set
import tempfile

from google_photos_client import GooglePhotosClient
from cache_manager import CacheManager
import config

logger = logging.getLogger(__name__)

class SyncService:
    def __init__(self):
        self.google_client = GooglePhotosClient()
        self.cache_manager = CacheManager()
        self.is_running = False
        self.sync_thread = None
        self.album_id = config.SHARED_ALBUM_ID
        
        if not self.album_id:
            raise ValueError("SHARED_ALBUM_ID not configured. Please set it in your .env file.")
    
    def start(self):
        """Start the synchronization service"""
        if self.is_running:
            logger.warning("Sync service is already running")
            return
        
        self.is_running = True
        self.sync_thread = threading.Thread(target=self._sync_loop, daemon=True)
        self.sync_thread.start()
        logger.info("Sync service started")
    
    def stop(self):
        """Stop the synchronization service"""
        self.is_running = False
        if self.sync_thread:
            self.sync_thread.join(timeout=10)
        logger.info("Sync service stopped")
    
    def _sync_loop(self):
        """Main synchronization loop"""
        # Perform initial sync
        self.sync_now()
        
        while self.is_running:
            try:
                time.sleep(config.SYNC_INTERVAL)
                if self.is_running:
                    self.sync_now()
            except Exception as e:
                logger.error(f"Error in sync loop: {e}")
                time.sleep(60)  # Wait a minute before retrying
    
    def sync_now(self) -> bool:
        """Perform synchronization now"""
        try:
            logger.info("Starting synchronization...")
            
            # Get current photos from Google Photos
            album_photos = self.google_client.get_album_photos(self.album_id)
            if not album_photos:
                logger.warning("No photos found in album or failed to fetch photos")
                return False
            
            # Get current photo IDs
            current_photo_ids = {photo['id'] for photo in album_photos}
            
            # Clean up photos that are no longer in the album
            self.cache_manager.cleanup_cache(current_photo_ids)
            
            # Download new photos
            new_photos_count = 0
            for photo in album_photos:
                if not self.is_running:
                    break
                
                photo_id = photo['id']
                if not self.cache_manager.is_photo_cached(photo_id):
                    if self._download_and_cache_photo(photo):
                        new_photos_count += 1
            
            # Enforce cache size limits
            self.cache_manager.enforce_cache_limit()
            
            # Update sync timestamp
            self.cache_manager.update_last_sync()
            
            cache_size = self.cache_manager.get_cache_size_mb()
            total_photos = len(self.cache_manager.get_cached_photos())
            
            logger.info(
                f"Sync completed. {new_photos_count} new photos downloaded. "
                f"Total: {total_photos} photos, Cache size: {cache_size:.1f} MB"
            )
            
            return True
            
        except Exception as e:
            logger.error(f"Synchronization failed: {e}")
            return False
    
    def _download_and_cache_photo(self, photo: dict) -> bool:
        """Download and cache a single photo"""
        try:
            photo_id = photo['id']
            metadata = self.google_client.get_photo_metadata(photo)
            
            # Create temporary file for download
            with tempfile.NamedTemporaryFile(suffix='.jpg', delete=False) as temp_file:
                temp_path = Path(temp_file.name)
            
            try:
                # Download photo to temporary location
                if self.google_client.download_photo(photo, temp_path):
                    # Add to cache
                    if self.cache_manager.add_photo_to_cache(photo_id, temp_path, metadata):
                        logger.debug(f"Successfully cached photo: {metadata.get('filename', photo_id)}")
                        return True
                    else:
                        logger.error(f"Failed to cache photo: {metadata.get('filename', photo_id)}")
                
            finally:
                # Clean up temporary file
                if temp_path.exists():
                    temp_path.unlink()
            
            return False
            
        except Exception as e:
            logger.error(f"Failed to download and cache photo: {e}")
            return False
    
    def get_sync_status(self) -> dict:
        """Get current synchronization status"""
        cached_photos = self.cache_manager.get_cached_photos()
        return {
            'is_running': self.is_running,
            'last_sync': self.cache_manager.get_last_sync(),
            'cached_photos_count': len(cached_photos),
            'cache_size_mb': self.cache_manager.get_cache_size_mb(),
            'album_id': self.album_id
        }
