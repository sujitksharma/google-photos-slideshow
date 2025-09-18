"""
Google Photos API client for accessing shared albums
"""
import os
import json
import logging
from pathlib import Path
from typing import List, Dict, Optional
import requests
from google.auth.transport.requests import Request
from google.oauth2.credentials import Credentials
from google_auth_oauthlib.flow import InstalledAppFlow
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError

import config

logger = logging.getLogger(__name__)

class GooglePhotosClient:
    def __init__(self):
        self.service = None
        self.credentials = None
        self._authenticate()
    
    def _authenticate(self):
        """Authenticate with Google Photos API"""
        creds = None
        
        # Load existing token
        if config.TOKEN_FILE.exists():
            creds = Credentials.from_authorized_user_file(str(config.TOKEN_FILE), config.GOOGLE_PHOTOS_SCOPES)
        
        # If there are no (valid) credentials available, let the user log in
        if not creds or not creds.valid:
            if creds and creds.expired and creds.refresh_token:
                try:
                    creds.refresh(Request())
                except Exception as e:
                    logger.error(f"Failed to refresh credentials: {e}")
                    creds = None
            
            if not creds:
                if not config.CREDENTIALS_FILE.exists():
                    raise FileNotFoundError(
                        f"Credentials file not found at {config.CREDENTIALS_FILE}. "
                        "Please download it from Google Cloud Console."
                    )
                
                flow = InstalledAppFlow.from_client_secrets_file(
                    str(config.CREDENTIALS_FILE), config.GOOGLE_PHOTOS_SCOPES
                )
                creds = flow.run_local_server(port=0)
            
            # Save the credentials for the next run
            with open(config.TOKEN_FILE, 'w') as token:
                token.write(creds.to_json())
        
        self.credentials = creds
        self.service = build('photoslibrary', 'v1', credentials=creds)
        logger.info("Successfully authenticated with Google Photos API")
    
    def get_shared_album(self, album_id: str) -> Optional[Dict]:
        """Get shared album information"""
        try:
            album = self.service.sharedAlbums().get(shareToken=album_id).execute()
            return album
        except HttpError as e:
            logger.error(f"Failed to get shared album: {e}")
            return None
    
    def get_album_photos(self, album_id: str, page_size: int = 100) -> List[Dict]:
        """Get all photos from a shared album"""
        photos = []
        page_token = None
        
        try:
            while True:
                request_body = {
                    'albumId': album_id,
                    'pageSize': page_size
                }
                
                if page_token:
                    request_body['pageToken'] = page_token
                
                response = self.service.mediaItems().search(body=request_body).execute()
                
                if 'mediaItems' in response:
                    photos.extend(response['mediaItems'])
                
                page_token = response.get('nextPageToken')
                if not page_token:
                    break
                    
            logger.info(f"Retrieved {len(photos)} photos from album")
            return photos
            
        except HttpError as e:
            logger.error(f"Failed to get album photos: {e}")
            return []
    
    def download_photo(self, photo: Dict, output_path: Path) -> bool:
        """Download a photo to local storage"""
        try:
            # Get the download URL with appropriate size
            base_url = photo['baseUrl']
            # For Raspberry Pi, we'll use a reasonable size to save bandwidth and storage
            download_url = f"{base_url}=w1920-h1080"
            
            response = requests.get(download_url, stream=True)
            response.raise_for_status()
            
            with open(output_path, 'wb') as f:
                for chunk in response.iter_content(chunk_size=8192):
                    f.write(chunk)
            
            logger.debug(f"Downloaded photo: {output_path.name}")
            return True
            
        except Exception as e:
            logger.error(f"Failed to download photo {photo.get('filename', 'unknown')}: {e}")
            return False
    
    def get_photo_metadata(self, photo: Dict) -> Dict:
        """Extract useful metadata from photo"""
        return {
            'id': photo['id'],
            'filename': photo.get('filename', 'unknown'),
            'creation_time': photo.get('mediaMetadata', {}).get('creationTime'),
            'width': photo.get('mediaMetadata', {}).get('width'),
            'height': photo.get('mediaMetadata', {}).get('height'),
            'mime_type': photo.get('mimeType'),
            'base_url': photo['baseUrl']
        }
