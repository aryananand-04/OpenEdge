interface FrameStats {
    width: number;
    height: number;
    fps: number;
    mode: string;
    processingTime: number;
    megapixels?: number;
    estimatedSize?: string;
    type?: string;
}

declare var cv: any;

type ViewMode = 'RAW' | 'GRAY' | 'EDGES';

class EdgeViewer {
    private canvas: HTMLCanvasElement;
    private ctx: CanvasRenderingContext2D;
    private statsDiv: HTMLElement;
    private currentStats: FrameStats;
    private animationId: number | null = null;
    private isUploadedFrame: boolean = false;
    private opencvReady: boolean = false;
    private currentMode: ViewMode = 'EDGES';
    private originalImage: HTMLImageElement | null = null;

    constructor() {
        this.canvas = document.getElementById('frameCanvas') as HTMLCanvasElement;
        this.ctx = this.canvas.getContext('2d')!;
        this.statsDiv = document.getElementById('stats')!;

        this.currentStats = {
            width: 640,
            height: 480,
            fps: 25.0,
            mode: 'Edge Detection',
            processingTime: 15
        };

        this.init();
    }

    private init(): void {
        console.log('🚀 OpenEdge Vision Viewer Starting...');
        this.setupButtons();
        this.loadSampleFrame();
        this.updateStats();
    }

    public onOpenCvReady(): void {
        this.opencvReady = true;
        console.log('✅ OpenCV.js integrated and ready for processing');
    }

    private setupButtons(): void {
        const refreshBtn = document.getElementById('refreshBtn');
        const exportBtn = document.getElementById('exportBtn');
        const animateBtn = document.getElementById('animateBtn');
        const uploadInput = document.getElementById('uploadInput') as HTMLInputElement;
        const canvasWrapper = document.querySelector('.canvas-wrapper');

        // Mode buttons
        const rawBtn = document.getElementById('rawBtn');
        const grayBtn = document.getElementById('grayBtn');
        const edgesBtn = document.getElementById('edgesBtn');

        if (refreshBtn) {
            refreshBtn.addEventListener('click', () => this.generateNewSample());
        }

        if (exportBtn) {
            exportBtn.addEventListener('click', () => this.exportFrame());
        }

        if (animateBtn) {
            animateBtn.addEventListener('click', () => this.toggleAnimation());
        }

        if (uploadInput) {
            uploadInput.addEventListener('change', (e) => this.handleFileUpload(e));
        }

        // Mode switching
        if (rawBtn) {
            rawBtn.addEventListener('click', () => this.switchMode('RAW'));
        }
        if (grayBtn) {
            grayBtn.addEventListener('click', () => this.switchMode('GRAY'));
        }
        if (edgesBtn) {
            edgesBtn.addEventListener('click', () => this.switchMode('EDGES'));
        }

        if (canvasWrapper) {
            canvasWrapper.addEventListener('dragover', (e) => {
                e.preventDefault();
                canvasWrapper.classList.add('drag-over');
            });

            canvasWrapper.addEventListener('dragleave', () => {
                canvasWrapper.classList.remove('drag-over');
            });

            canvasWrapper.addEventListener('drop', (e) => {
                e.preventDefault();
                canvasWrapper.classList.remove('drag-over');
                const files = (e as DragEvent).dataTransfer?.files;
                if (files && files.length > 0) {
                    this.handleDroppedFile(files[0]);
                }
            });
        }
    }

    private switchMode(mode: ViewMode): void {
        if (!this.originalImage) {
            alert('⚠️ Please upload an image first!');
            return;
        }

        if (!this.opencvReady && mode !== 'RAW') {
            alert('⏳ OpenCV.js is still loading. Please wait a moment.');
            return;
        }

        this.currentMode = mode;
        this.updateModeButtons();
        this.processCurrentImage();
    }

    private updateModeButtons(): void {
        const rawBtn = document.getElementById('rawBtn');
        const grayBtn = document.getElementById('grayBtn');
        const edgesBtn = document.getElementById('edgesBtn');

        // Remove active class from all
        [rawBtn, grayBtn, edgesBtn].forEach(btn => {
            btn?.classList.remove('active');
        });

        // Add active class to current mode
        if (this.currentMode === 'RAW' && rawBtn) {
            rawBtn.classList.add('active');
        } else if (this.currentMode === 'GRAY' && grayBtn) {
            grayBtn.classList.add('active');
        } else if (this.currentMode === 'EDGES' && edgesBtn) {
            edgesBtn.classList.add('active');
        }
    }

    private processCurrentImage(): void {
        if (!this.originalImage) return;

        const startTime = performance.now();

        if (this.currentMode === 'RAW') {
            // Display original image
            this.canvas.width = this.originalImage.width;
            this.canvas.height = this.originalImage.height;
            this.ctx.drawImage(this.originalImage, 0, 0);

            const processingTime = Math.round(performance.now() - startTime);
            this.updateStatsForMode('RAW', processingTime);
            console.log('📷 Displaying RAW mode');

        } else if (this.currentMode === 'GRAY') {
            // Display grayscale
            this.showProcessingOverlay(true);

            setTimeout(() => {
                try {
                    const tempCanvas = document.createElement('canvas');
                    tempCanvas.width = this.originalImage!.width;
                    tempCanvas.height = this.originalImage!.height;
                    const tempCtx = tempCanvas.getContext('2d')!;
                    tempCtx.drawImage(this.originalImage!, 0, 0);

                    const src = cv.imread(tempCanvas);
                    const gray = new cv.Mat();

                    cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);

                    this.canvas.width = this.originalImage!.width;
                    this.canvas.height = this.originalImage!.height;
                    cv.imshow(this.canvas, gray);

                    const processingTime = Math.round(performance.now() - startTime);
                    this.updateStatsForMode('GRAY', processingTime);
                    console.log(`⚪ Grayscale processing completed in ${processingTime}ms`);

                    src.delete();
                    gray.delete();
                } catch (error) {
                    console.error('❌ Grayscale processing error:', error);
                } finally {
                    this.showProcessingOverlay(false);
                }
            }, 50);

        } else if (this.currentMode === 'EDGES') {
            // Display edge detection
            this.showProcessingOverlay(true);

            setTimeout(() => {
                try {
                    const tempCanvas = document.createElement('canvas');
                    tempCanvas.width = this.originalImage!.width;
                    tempCanvas.height = this.originalImage!.height;
                    const tempCtx = tempCanvas.getContext('2d')!;
                    tempCtx.drawImage(this.originalImage!, 0, 0);

                    const src = cv.imread(tempCanvas);
                    const gray = new cv.Mat();
                    const blurred = new cv.Mat();
                    const edges = new cv.Mat();

                    cv.cvtColor(src, gray, cv.COLOR_RGBA2GRAY);
                    const ksize = new cv.Size(5, 5);
                    cv.GaussianBlur(gray, blurred, ksize, 1.5);
                    cv.Canny(blurred, edges, 50, 150);

                    this.canvas.width = this.originalImage!.width;
                    this.canvas.height = this.originalImage!.height;
                    cv.imshow(this.canvas, edges);

                    const processingTime = Math.round(performance.now() - startTime);
                    this.updateStatsForMode('EDGES', processingTime);
                    console.log(`🔍 Edge detection completed in ${processingTime}ms`);

                    src.delete();
                    gray.delete();
                    blurred.delete();
                    edges.delete();
                } catch (error) {
                    console.error('❌ Edge detection error:', error);
                } finally {
                    this.showProcessingOverlay(false);
                }
            }, 50);
        }
    }

    private updateStatsForMode(mode: ViewMode, processingTime: number): void {
        if (!this.originalImage) return;

        const width = this.originalImage.width;
        const height = this.originalImage.height;
        const megapixels = (width * height) / 1000000;
        const sizeKB = Math.round((width * height * 4) / 1024);

        let modeText = '';
        if (mode === 'RAW') modeText = 'Raw Image';
        else if (mode === 'GRAY') modeText = 'Grayscale';
        else if (mode === 'EDGES') modeText = 'Edge Detection';

        this.currentStats = {
            width,
            height,
            fps: 0,
            mode: modeText,
            processingTime,
            megapixels,
            estimatedSize: `~${sizeKB} KB`,
            type: 'Uploaded'
        };

        this.updateStats();
    }

    private handleDroppedFile(file: File): void {
        if (!file.type.startsWith('image/')) {
            alert('⚠️ Please drop an image file (PNG, JPG, etc.)');
            return;
        }
        console.log('📦 Dropped file:', file.name);
        this.processImageFile(file);
    }

    private loadSampleFrame(): void {
        const img = new Image();
        img.onload = () => {
            this.canvas.width = img.width;
            this.canvas.height = img.height;
            this.ctx.drawImage(img, 0, 0);
            console.log('✅ Sample frame loaded');
        };
        img.onerror = () => {
            console.log('⚠️ Sample not found, generating placeholder');
            this.drawPlaceholder();
        };
        img.src = 'assets/sample_frame.png';
    }

    private drawPlaceholder(): void {
        this.canvas.width = this.currentStats.width;
        this.canvas.height = this.currentStats.height;

        this.ctx.fillStyle = '#000000';
        this.ctx.fillRect(0, 0, this.canvas.width, this.canvas.height);

        this.drawEdgePattern();

        this.ctx.fillStyle = '#00FFD1';
        this.ctx.font = 'bold 28px Arial';
        this.ctx.textAlign = 'center';
        this.ctx.shadowColor = '#00FFD1';
        this.ctx.shadowBlur = 20;
        this.ctx.fillText('🔬 OpenEdge Vision', this.canvas.width / 2, 50);

        this.ctx.shadowBlur = 0;
        this.ctx.font = '16px Arial';
        this.ctx.fillStyle = '#00BCD4';
        this.ctx.fillText('Upload an image to try RAW, GRAY & EDGES modes!', this.canvas.width / 2, 80);
    }

    private drawEdgePattern(): void {
        this.ctx.strokeStyle = '#00FFD1';
        this.ctx.lineWidth = 2;

        const centerX = this.canvas.width / 2;
        const centerY = this.canvas.height / 2;

        for (let r = 50; r < 200; r += 35) {
            this.ctx.beginPath();
            this.ctx.arc(centerX, centerY, r, 0, Math.PI * 2);
            this.ctx.stroke();
        }

        this.ctx.strokeStyle = '#00E5FF';
        this.ctx.strokeRect(centerX - 160, centerY - 110, 320, 220);
        this.ctx.strokeRect(centerX - 130, centerY - 85, 260, 170);

        for (let i = 0; i < 120; i++) {
            const x = Math.random() * this.canvas.width;
            const y = Math.random() * this.canvas.height;
            const len = 15 + Math.random() * 35;
            const angle = Math.random() * Math.PI * 2;

            this.ctx.beginPath();
            this.ctx.moveTo(x, y);
            this.ctx.lineTo(x + Math.cos(angle) * len, y + Math.sin(angle) * len);
            this.ctx.globalAlpha = 0.4 + Math.random() * 0.6;
            this.ctx.stroke();
        }
        this.ctx.globalAlpha = 1.0;
    }

    private generateNewSample(): void {
        this.isUploadedFrame = false;
        this.originalImage = null;
        this.currentStats = {
            width: 640,
            height: 480,
            fps: 20 + Math.random() * 15,
            processingTime: 10 + Math.floor(Math.random() * 20),
            mode: ['RAW', 'GRAY', 'EDGES'][Math.floor(Math.random() * 3)]
        };
        this.drawPlaceholder();
        this.updateStats();
        console.log('🔄 New sample generated');
    }

    private exportFrame(): void {
        const dataURL = this.canvas.toDataURL('image/png');
        const link = document.createElement('a');
        const modeText = this.currentMode.toLowerCase();
        link.download = `openedge_${modeText}_${Date.now()}.png`;
        link.href = dataURL;
        link.click();
        console.log(`💾 ${this.currentMode} frame exported`);
    }

    private toggleAnimation(): void {
        const btn = document.getElementById('animateBtn');
        if (this.animationId !== null) {
            this.animationId = null;
            if (btn) btn.textContent = '▶ Start Animation';
            console.log('⏸ Animation stopped');
        } else {
            if (btn) btn.textContent = '⏹ Stop Animation';
            this.animationId = 1;
            console.log('▶ Animation started');
            this.animate();
        }
    }

    private animate(): void {
        if (this.animationId === null) return;
        this.generateNewSample();
        setTimeout(() => {
            if (this.animationId !== null) {
                this.animate();
            }
        }, 100);
    }

    private handleFileUpload(event: Event): void {
        const input = event.target as HTMLInputElement;
        const file = input.files?.[0];
        if (!file) return;
        console.log('📤 Uploading:', file.name);
        this.processImageFile(file);
    }

    private showProcessingOverlay(show: boolean): void {
        const overlay = document.getElementById('processingOverlay');
        if (overlay) {
            overlay.style.display = show ? 'flex' : 'none';
        }
    }

    private async processImageFile(file: File): Promise<void> {
        this.isUploadedFrame = true;

        const reader = new FileReader();
        reader.onload = async (e) => {
            const dataURL = e.target?.result as string;
            const img = new Image();

            img.onload = async () => {
                // Store original image
                this.originalImage = img;

                console.log(`📊 Image loaded: ${img.width}x${img.height}`);

                // Reset to EDGES mode and update buttons
                this.currentMode = 'EDGES';
                this.updateModeButtons();

                // Process in current mode
                this.processCurrentImage();
            };

            img.onerror = () => {
                console.error('❌ Failed to load image');
                alert('Failed to load image. Please try another file.');
            };

            img.src = dataURL;
        };

        reader.readAsDataURL(file);
    }

    private updateStats(): void {
        let html = '';
        if (this.isUploadedFrame && this.currentStats.type === 'Uploaded') {
            html = `
                <div class="stat-item">
                    <span class="stat-label">Resolution</span>
                    <span class="stat-value">${this.currentStats.width}x${this.currentStats.height}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">Megapixels</span>
                    <span class="stat-value">${this.currentStats.megapixels?.toFixed(2)} MP</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">Mode</span>
                    <span class="stat-value">${this.currentStats.mode}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">Processing Time</span>
                    <span class="stat-value">${this.currentStats.processingTime}ms</span>
                </div>
            `;
        } else {
            html = `
                <div class="stat-item">
                    <span class="stat-label">Resolution</span>
                    <span class="stat-value">${this.currentStats.width}x${this.currentStats.height}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">FPS</span>
                    <span class="stat-value">${this.currentStats.fps.toFixed(1)}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">Mode</span>
                    <span class="stat-value">${this.currentStats.mode}</span>
                </div>
                <div class="stat-item">
                    <span class="stat-label">Processing</span>
                    <span class="stat-value">${this.currentStats.processingTime}ms</span>
                </div>
            `;
        }
        this.statsDiv.innerHTML = html;
    }

    public updateFrame(imageData: string, stats: FrameStats): void {
        this.isUploadedFrame = false;
        this.currentStats = stats;
        const img = new Image();
        img.onload = () => {
            this.canvas.width = img.width;
            this.canvas.height = img.height;
            this.ctx.drawImage(img, 0, 0);
            this.updateStats();
        };
        img.src = imageData;
    }
}

document.addEventListener('DOMContentLoaded', () => {
    console.log('🚀 OpenEdge Vision Viewer Starting...');
    const viewer = new EdgeViewer();
    (window as any).edgeViewer = viewer;
    console.log('✅ Viewer initialized. Waiting for OpenCV.js to load...');
});