# EdgeViewer — Camera Edge Detection (Android + OpenCV + OpenGL + Web Viewer)

EdgeViewer is a real-time camera processing application built using:

* **Android (Java)**
* **CameraX**
* **C++ (JNI + OpenCV)**
* **OpenGL ES 2.0**
* **TypeScript Web Viewer**

It captures camera frames, sends them to native C++, applies Grayscale or Canny Edge Detection, and renders the processed image using OpenGL ES at real-time FPS.

A simple Web Viewer is also included to preview sample processed frames.

---

## 🚀 Features

### 🎥 Real-Time Camera Feed
Uses **CameraX** for stable, low-latency video capture.

### ⚙️ Native Processing (JNI + OpenCV)
Camera frames are sent to C++ where **OpenCV**:
* Converts **NV21 → BGR**
* Applies **Grayscale** or **Canny Edge Detection**
* Outputs tightly packed buffers

### 🎨 OpenGL Rendering
Processed frames are uploaded to an **OpenGL ES 2.0** texture and displayed at high speed.

### 🔄 Mode Toggle
Toggle button switches between:
* **Edges**
* **Gray**

### ⚡ FPS Counter
Frames per second are displayed at the bottom of the screen.

### 🌐 Web Viewer (TypeScript + Vite)
Simple web viewer that loads:
* `sample_gray.jpg`
* `sample_edges.jpg`
And displays them with two buttons.

---

## 📁 Project Structure
```
EdgeViewer/
│
├── app/                        # Android app
│   ├── src/main/java          
│   ├── src/main/cpp            # native-lib.cpp (JNI + OpenCV)
│   ├── src/main/assets
│   └── CMakeLists.txt
│
├── web/                        # Web viewer
│   ├── index.html
│   ├── main.ts
│   ├── style.css
│   ├── images/
│   │    ├── sample_gray.jpg
│   │    └── sample_edges.jpg
│   ├── vite.config.js
│   ├── tsconfig.json
│   └── package.json
│
├── .github/workflows           # CI build pipeline (optional)
│ 
└── README.md
```
---

## 🛠️ Build Instructions

### 📱 Android App

#### Requirements
* Android Studio Ladybug or later
* NDK 26+ or 27
* CMake installed
* OpenCV Android SDK

#### Build
1.  Clone the repository
    ```bash
    git clone https://github.com/Pushkar2103/EdgeViewer
    cd EdgeViewer
    ```
2.  Open in Android Studio
3.  Wait for Gradle sync
4.  Build & run: `app → Run`

#### Native Code
The project loads the library automatically:
```java
static {
    System.loadLibrary("edgeviewer");
}
```

#### C++ entry points:
```
processFrame()
setViewerMode()
nativeHello()
testOpenCV()
```

### 🌐 Web Viewer
#### Requirements
```Bash
cd web
npm install
npm run dev
```
Open: http://localhost:5173

#### 🧪 Sample Frames
Place your generated sample frames here:
```
web/images/sample_gray.jpg
web/images/sample_edges.jpg
```
---

## Author
Pushkar Gupta
B.Tech CSE, PSIT Kanpur
---
### This project includes:

- Android camera handling
- OpenGL ES texture pipeline
- JNI bridge
- Real-time C++ image processing
- A clean TypeScript frontend
- Full-stack mobile-to-web skillset