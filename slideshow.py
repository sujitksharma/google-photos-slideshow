"""
Slideshow display functionality using pygame
"""
import os
import sys
import logging
import threading
import time
from pathlib import Path
from typing import List, Optional
import pygame
from PIL import Image

import config

logger = logging.getLogger(__name__)

class Slideshow:
    def __init__(self, cache_manager):
        self.cache_manager = cache_manager
        self.is_running = False
        self.current_image_index = 0
        self.images = []
        self.screen = None
        self.clock = None
        self.preloaded_images = {}
        
        # Initialize pygame
        pygame.init()
        pygame.display.set_caption("Google Photos Slideshow")
        
        # Set up display
        if config.FULLSCREEN:
            self.screen = pygame.display.set_mode((0, 0), pygame.FULLSCREEN)
        else:
            self.screen = pygame.display.set_mode((config.DISPLAY_WIDTH, config.DISPLAY_HEIGHT))
        
        self.screen_width = self.screen.get_width()
        self.screen_height = self.screen.get_height()
        self.clock = pygame.time.Clock()
        
        logger.info(f"Slideshow initialized with display size: {self.screen_width}x{self.screen_height}")
    
    def load_images(self):
        """Load list of cached images"""
        cached_photos = self.cache_manager.get_cached_photos()
        self.images = [photo['path'] for photo in cached_photos]
        
        if not self.images:
            logger.warning("No cached images found for slideshow")
            return False
        
        logger.info(f"Loaded {len(self.images)} images for slideshow")
        return True
    
    def _load_and_scale_image(self, image_path: Path) -> Optional[pygame.Surface]:
        """Load and scale an image to fit the screen"""
        try:
            # Load image with PIL for better format support
            with Image.open(image_path) as pil_image:
                # Convert to RGB if necessary
                if pil_image.mode != 'RGB':
                    pil_image = pil_image.convert('RGB')
                
                # Calculate scaling to fit screen while maintaining aspect ratio
                img_width, img_height = pil_image.size
                scale_x = self.screen_width / img_width
                scale_y = self.screen_height / img_height
                scale = min(scale_x, scale_y)
                
                new_width = int(img_width * scale)
                new_height = int(img_height * scale)
                
                # Resize image
                pil_image = pil_image.resize((new_width, new_height), Image.Resampling.LANCZOS)
                
                # Convert PIL image to pygame surface
                image_string = pil_image.tobytes()
                pygame_image = pygame.image.fromstring(image_string, (new_width, new_height), 'RGB')
                
                return pygame_image
                
        except Exception as e:
            logger.error(f"Failed to load image {image_path}: {e}")
            return None
    
    def _preload_images(self):
        """Preload next few images for smooth transitions"""
        if not self.images:
            return
        
        # Clear old preloaded images to save memory
        self.preloaded_images.clear()
        
        # Preload current and next few images
        for i in range(config.PRELOAD_IMAGES):
            index = (self.current_image_index + i) % len(self.images)
            image_path = self.images[index]
            
            if image_path not in self.preloaded_images:
                surface = self._load_and_scale_image(image_path)
                if surface:
                    self.preloaded_images[image_path] = surface
    
    def _display_image(self, image_surface: pygame.Surface):
        """Display an image centered on screen"""
        # Fill screen with black
        self.screen.fill((0, 0, 0))
        
        # Center the image
        image_rect = image_surface.get_rect()
        image_rect.center = (self.screen_width // 2, self.screen_height // 2)
        
        # Blit image to screen
        self.screen.blit(image_surface, image_rect)
        pygame.display.flip()
    
    def _show_loading_screen(self):
        """Show a loading screen"""
        self.screen.fill((0, 0, 0))
        
        # Create loading text (if font is available)
        try:
            font = pygame.font.Font(None, 74)
            text = font.render("Loading...", True, (255, 255, 255))
            text_rect = text.get_rect(center=(self.screen_width // 2, self.screen_height // 2))
            self.screen.blit(text, text_rect)
        except:
            # If font loading fails, just show a white rectangle
            pygame.draw.rect(self.screen, (255, 255, 255), 
                           (self.screen_width // 2 - 50, self.screen_height // 2 - 25, 100, 50))
        
        pygame.display.flip()
    
    def _show_no_images_screen(self):
        """Show screen when no images are available"""
        self.screen.fill((0, 0, 0))
        
        try:
            font = pygame.font.Font(None, 48)
            lines = [
                "No images available",
                "Waiting for sync...",
                "Press ESC to exit"
            ]
            
            y_offset = self.screen_height // 2 - (len(lines) * 30)
            for line in lines:
                text = font.render(line, True, (255, 255, 255))
                text_rect = text.get_rect(center=(self.screen_width // 2, y_offset))
                self.screen.blit(text, text_rect)
                y_offset += 60
        except:
            # Fallback if font loading fails
            pygame.draw.rect(self.screen, (255, 255, 255), 
                           (self.screen_width // 2 - 100, self.screen_height // 2 - 50, 200, 100))
        
        pygame.display.flip()
    
    def start(self):
        """Start the slideshow"""
        self.is_running = True
        logger.info("Starting slideshow")
        
        last_image_time = 0
        last_reload_time = 0
        
        while self.is_running:
            current_time = time.time()
            
            # Handle pygame events
            for event in pygame.event.get():
                if event.type == pygame.QUIT:
                    self.is_running = False
                elif event.type == pygame.KEYDOWN:
                    if event.key == pygame.K_ESCAPE:
                        self.is_running = False
                    elif event.key == pygame.K_SPACE:
                        # Space bar to pause/resume
                        self._show_loading_screen()
                        time.sleep(1)
                    elif event.key == pygame.K_RIGHT:
                        # Right arrow to next image
                        self.current_image_index = (self.current_image_index + 1) % len(self.images)
                        last_image_time = 0  # Force immediate display
                    elif event.key == pygame.K_LEFT:
                        # Left arrow to previous image
                        self.current_image_index = (self.current_image_index - 1) % len(self.images)
                        last_image_time = 0  # Force immediate display
            
            # Reload image list periodically (every 30 seconds)
            if current_time - last_reload_time > 30:
                old_count = len(self.images)
                self.load_images()
                if len(self.images) != old_count:
                    logger.info(f"Image list updated: {old_count} -> {len(self.images)} images")
                    self.current_image_index = 0
                    self.preloaded_images.clear()
                last_reload_time = current_time
            
            # Display images
            if not self.images:
                self._show_no_images_screen()
            elif current_time - last_image_time >= config.SLIDESHOW_INTERVAL:
                # Time to show next image
                image_path = self.images[self.current_image_index]
                
                # Get image from preloaded cache or load it
                if image_path in self.preloaded_images:
                    image_surface = self.preloaded_images[image_path]
                else:
                    image_surface = self._load_and_scale_image(image_path)
                
                if image_surface:
                    self._display_image(image_surface)
                    logger.debug(f"Displayed image {self.current_image_index + 1}/{len(self.images)}: {image_path.name}")
                    
                    # Move to next image
                    self.current_image_index = (self.current_image_index + 1) % len(self.images)
                    
                    # Preload next images in background
                    threading.Thread(target=self._preload_images, daemon=True).start()
                else:
                    # Skip this image if it failed to load
                    self.current_image_index = (self.current_image_index + 1) % len(self.images)
                
                last_image_time = current_time
            
            # Control frame rate
            self.clock.tick(30)
        
        logger.info("Slideshow stopped")
    
    def stop(self):
        """Stop the slideshow"""
        self.is_running = False
    
    def cleanup(self):
        """Clean up pygame resources"""
        pygame.quit()
