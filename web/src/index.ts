/**
 * Main entry point for the web viewer
 */

import { FrameViewer, initFrameViewer } from './viewer';

// Initialize viewer when page loads
let viewer: FrameViewer;

window.addEventListener('DOMContentLoaded', () => {
    try {
        viewer = initFrameViewer('frameCanvas');
        console.log('Frame viewer initialized');

        // Example: Load a sample frame if available
        const sampleFrameUrl = './sample_frame.png';
        viewer.loadFrameFromUrl(sampleFrameUrl).catch(() => {
            console.log('Sample frame not found, ready for live frames');
        });

        // Simulate frame updates (for demonstration)
        simulateFrameUpdates();
    } catch (error) {
        console.error('Failed to initialize frame viewer:', error);
    }
});

/**
 * Simulate frame updates for demonstration
 * In production, this would receive frames from Android app via WebSocket/HTTP
 */
function simulateFrameUpdates(): void {
    const canvas = document.getElementById('frameCanvas') as HTMLCanvasElement;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    // Create a simple animated pattern
    let frame = 0;
    setInterval(() => {
        // Draw a simple pattern (replace with actual frame data)
        ctx.fillStyle = `hsl(${(frame * 10) % 360}, 70%, 50%)`;
        ctx.fillRect(0, 0, canvas.width, canvas.height);

        // Create ImageData and display
        const imageData = ctx.getImageData(0, 0, canvas.width, canvas.height);
        viewer.displayFrame(imageData);

        frame++;
    }, 100); // ~10 FPS for demo
}

// Export for use in other modules
export { viewer };

