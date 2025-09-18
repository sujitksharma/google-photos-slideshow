"""
Local cache management for Google Photos
"""
import os
import json
import logging
import hashlib
from pathlib import Path
from typing import Dict, List, Set
from datetime import datetime
from PIL import Image

import config

logger = logging.getLogger(__name__)

class CacheManager:
    def __init__(self):
        self.cache_dir = config.CACHE_DIR
        self.metadata_file = self.cache_dir / 'metadata.json'
        self.cache_dir.mkdir(exist_ok=True)
        self.metadata = self._load_metadata()
    
    def _load_metadata(self) -> Dict:
        """Load cache metadata from file"""
        if self.metadata_file.exists():
            try:
                with open(self.metadata_file, 'r') as f:
                    return json.load(f)
            except Exception as e:
                logger.error(f"Failed to load metadata: {e}")
        
        return {
            'photos': {},
            'last_sync': None,
            'total_size': 0
        }
    
    def _save_metadata(self):
        """Save cache metadata to file"""
        try:
            with open(self.metadata_file, 'w') as f:
                json.dump(self.metadata, f, indent=2)
        except Exception as e:
            logger.error(f"Failed to save metadata: {e}")
    
    def _get_file_hash(self, photo_id: str) -> str:
        """Generate a hash for the photo filename"""
        return hashlib.md5(photo_id.encode()).hexdigest()
    
    def _get_cache_path(self, photo_id: str) -> Path:
        """Get the cache file path for a photo"""
        file_hash = self._get_file_hash(photo_id)
        return self.cache_dir / f"{file_hash}.jpg"
    
    def is_photo_cached(self, photo_id: str) -> bool:
        """Check if a photo is already cached"""
        cache_path = self._get_cache_path(photo_id)
        return cache_path.exists() and photo_id in self.metadata['photos']
    
    def add_photo_to_cache(self, photo_id: str, photo_path: Path, metadata: Dict) -> bool:
        """Add a photo to the cache"""
        try:
            cache_path = self._get_cache_path(photo_id)
            
            # Optimize image for Raspberry Pi display
            with Image.open(photo_path) as img:
                # Convert to RGB if necessary
                if img.mode != 'RGB':
                    img = img.convert('RGB')
                
                # Resize if too large (save memory and storage)
                max_size = (config.DISPLAY_WIDTH, config.DISPLAY_HEIGHT)
                img.thumbnail(max_size, Image.Resampling.LANCZOS)
                
                # Save with compression
                img.save(cache_path, 'JPEG', quality=config.IMAGE_QUALITY, optimize=True)
            
            # Update metadata
            file_size = cache_path.stat().st_size
            self.metadata['photos'][photo_id] = {
                'filename': metadata.get('filename', 'unknown'),
                'cache_path': str(cache_path),
                'file_size': file_size,
                'creation_time': metadata.get('creation_time'),
                'cached_at': datetime.now().isoformat(),
                'width': metadata.get('width'),
                'height': metadata.get('height')
            }
            
            self.metadata['total_size'] += file_size
            self._save_metadata()
            
            logger.debug(f"Added photo to cache: {photo_id}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to add photo to cache: {e}")
            return False
    
    def remove_photo_from_cache(self, photo_id: str) -> bool:
        """Remove a photo from the cache"""
        try:
            if photo_id in self.metadata['photos']:
                cache_path = Path(self.metadata['photos'][photo_id]['cache_path'])
                file_size = self.metadata['photos'][photo_id]['file_size']
                
                if cache_path.exists():
                    cache_path.unlink()
                
                self.metadata['total_size'] -= file_size
                del self.metadata['photos'][photo_id]
                self._save_metadata()
                
                logger.debug(f"Removed photo from cache: {photo_id}")
                return True
                
        except Exception as e:
            logger.error(f"Failed to remove photo from cache: {e}")
        
        return False
    
    def get_cached_photos(self) -> List[Dict]:
        """Get list of all cached photos"""
        cached_photos = []
        for photo_id, metadata in self.metadata['photos'].items():
            cache_path = Path(metadata['cache_path'])
            if cache_path.exists():
                cached_photos.append({
                    'id': photo_id,
                    'path': cache_path,
                    'metadata': metadata
                })
        return cached_photos
    
    def get_cache_size_mb(self) -> float:
        """Get current cache size in MB"""
        return self.metadata['total_size'] / (1024 * 1024)
    
    def cleanup_cache(self, current_photo_ids: Set[str]):
        """Remove photos that are no longer in the album"""
        photos_to_remove = []
        for photo_id in self.metadata['photos']:
            if photo_id not in current_photo_ids:
                photos_to_remove.append(photo_id)
        
        for photo_id in photos_to_remove:
            self.remove_photo_from_cache(photo_id)
        
        if photos_to_remove:
            logger.info(f"Cleaned up {len(photos_to_remove)} photos from cache")
    
    def enforce_cache_limit(self):
        """Enforce maximum cache size by removing oldest photos"""
        max_size_bytes = config.MAX_CACHE_SIZE_MB * 1024 * 1024
        
        if self.metadata['total_size'] <= max_size_bytes:
            return
        
        # Sort photos by cached_at timestamp (oldest first)
        photos_by_age = sorted(
            self.metadata['photos'].items(),
            key=lambda x: x[1].get('cached_at', '1970-01-01')
        )
        
        while self.metadata['total_size'] > max_size_bytes and photos_by_age:
            photo_id, _ = photos_by_age.pop(0)
            self.remove_photo_from_cache(photo_id)
        
        logger.info(f"Cache size after cleanup: {self.get_cache_size_mb():.1f} MB")
    
    def update_last_sync(self):
        """Update the last sync timestamp"""
        self.metadata['last_sync'] = datetime.now().isoformat()
        self._save_metadata()
    
    def get_last_sync(self) -> str:
        """Get the last sync timestamp"""
        return self.metadata.get('last_sync', 'Never')
