/**
 * FrameViewer - TypeScript class for displaying processed camera frames
 * Displays frame statistics including FPS and resolution
 */

export interface FrameStats {
    fps: number;
    resolution: { width: number; height: number };
    frameCount: number;
    processingTime: number;
}

export class FrameViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private stats: FrameStats;
    private lastTime: number;
    private frameCount: number;
    private fpsUpdateInterval: number = 1000; // Update FPS every second

    constructor(canvasId: string) {
        const canvasElement = document.getElementById(canvasId);
        if (!canvasElement || !(canvasElement instanceof HTMLCanvasElement)) {
            throw new Error(`Canvas element with id "${canvasId}" not found`);
        }

        this.canvas = canvasElement;
        const context = this.canvas.getContext('2d');
        if (!context) {
            throw new Error('Failed to get 2D rendering context');
        }
        this.ctx = context;

        // Initialize stats
        this.stats = {
            fps: 0,
            resolution: {
                width: this.canvas.width,
                height: this.canvas.height
            },
            frameCount: 0,
            processingTime: 0
        };

        this.lastTime = Date.now();
        this.frameCount = 0;

        // Set canvas size
        this.resizeCanvas(640, 480);
    }

    /**
     * Display a processed frame on the canvas
     * @param imageData - ImageData object containing frame data
     */
    displayFrame(imageData: ImageData): void {
        if (imageData.width !== this.canvas.width || imageData.height !== this.canvas.height) {
            this.resizeCanvas(imageData.width, imageData.height);
        }

        // Draw the frame
        this.ctx.putImageData(imageData, 0, 0);

        // Update FPS
        this.updateFPS();

        // Draw statistics overlay
        this.drawStats();
    }

    /**
     * Display frame from ImageBitmap
     * @param bitmap - ImageBitmap object
     */
    displayFrameFromBitmap(bitmap: ImageBitmap): void {
        if (bitmap.width !== this.canvas.width || bitmap.height !== this.canvas.height) {
            this.resizeCanvas(bitmap.width, bitmap.height);
        }

        // Clear and draw
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
        this.ctx.drawImage(bitmap, 0, 0);

        // Update FPS
        this.updateFPS();

        // Draw statistics
        this.drawStats();
    }

    /**
     * Load and display frame from URL
     * @param url - URL of the image
     */
    async loadFrameFromUrl(url: string): Promise<void> {
        const img = new Image();
        img.crossOrigin = 'anonymous';

        return new Promise((resolve, reject) => {
            img.onload = () => {
                if (img.width !== this.canvas.width || img.height !== this.canvas.height) {
                    this.resizeCanvas(img.width, img.height);
                }

                this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
                this.ctx.drawImage(img, 0, 0);

                this.updateFPS();
                this.drawStats();
                resolve();
            };

            img.onerror = () => {
                reject(new Error(`Failed to load image from ${url}`));
            };

            img.src = url;
        });
    }

    /**
     * Resize the canvas
     */
    private resizeCanvas(width: number, height: number): void {
        this.canvas.width = width;
        this.canvas.height = height;
        this.stats.resolution = { width, height };
    }

    /**
     * Update FPS calculation
     */
    private updateFPS(): void {
        this.frameCount++;
        const now = Date.now();
        const elapsed = now - this.lastTime;

        if (elapsed >= this.fpsUpdateInterval) {
            this.stats.fps = Math.round((this.frameCount * 1000) / elapsed);
            this.stats.frameCount = this.frameCount;
            this.frameCount = 0;
            this.lastTime = now;
        }
    }

    /**
     * Draw statistics overlay on the canvas
     */
    private drawStats(): void {
        const padding = 10;
        const lineHeight = 20;
        let y = padding + lineHeight;

        // Semi-transparent background for text
        this.ctx.fillStyle = 'rgba(0, 0, 0, 0.7)';
        this.ctx.fillRect(padding, padding, 200, 80);

        // Text style
        this.ctx.fillStyle = '#00ff00';
        this.ctx.font = '14px monospace';
        this.ctx.textBaseline = 'top';

        // Draw statistics
        this.ctx.fillText(`FPS: ${this.stats.fps}`, padding + 5, y);
        y += lineHeight;
        this.ctx.fillText(
            `Resolution: ${this.stats.resolution.width}x${this.stats.resolution.height}`,
            padding + 5,
            y
        );
        y += lineHeight;
        this.ctx.fillText(`Frames: ${this.stats.frameCount}`, padding + 5, y);
    }

    /**
     * Get current statistics
     */
    getStats(): FrameStats {
        return { ...this.stats };
    }

    /**
     * Clear the canvas
     */
    clear(): void {
        this.ctx.clearRect(0, 0, this.canvas.width, this.canvas.height);
    }

    /**
     * Set processing time (for display)
     */
    setProcessingTime(timeMs: number): void {
        this.stats.processingTime = timeMs;
    }
}

/**
 * Initialize the frame viewer when DOM is ready
 */
export function initFrameViewer(canvasId: string = 'frameCanvas'): FrameViewer {
    if (document.readyState === 'loading') {
        return new Promise<FrameViewer>((resolve) => {
            document.addEventListener('DOMContentLoaded', () => {
                resolve(new FrameViewer(canvasId));
            });
        }) as any;
    } else {
        return new FrameViewer(canvasId);
    }
}

