# Quick Start Guide

## 🚀 Get Started in 5 Minutes

### Step 1: Open Project
```bash
# Open in Android Studio
File → Open → Select OpenEdge directory
```

### Step 2: Install Dependencies
- Android Studio will prompt to install missing SDK components
- Accept and wait for Gradle sync

### Step 3: Build
```
Build → Make Project
```

### Step 4: Run
- Connect Android device (USB debugging enabled)
- Or start an emulator (API 24+)
- Click Run button (▶️)

### Step 5: Test Features
1. **Camera Feed**: Should display immediately
2. **Mode Buttons**: Tap to switch between Original/Gray/Blur/Edge
3. **WebView**: Tap "Web" button to open web viewer

## 📱 First Run Checklist

- [ ] Camera permission granted
- [ ] Camera feed visible
- [ ] Mode switching works
- [ ] WebView opens
- [ ] No crashes in logcat

## 🐛 Common Issues

**"NDK not found"**
→ Tools → SDK Manager → SDK Tools → Install NDK

**"OpenCV not found"**
→ Verify `OpenCV-android-sdk/` directory exists

**"Build failed"**
→ File → Invalidate Caches / Restart → Rebuild

## 📊 Expected Performance

- **Frame Rate**: 15-30 FPS
- **Memory**: 50-100 MB
- **CPU**: Medium usage during processing

## 🎯 Next Steps

1. Capture screenshots for README
2. Test on multiple devices
3. Build TypeScript web viewer: `cd web && npm install && npm run build`
4. Commit changes with meaningful messages

---

**Ready to go!** 🎉

