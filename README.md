# EdgeDetectionApp

This is a solution for a technical assessment demonstrating an Android application that performs real-time edge detection.

The project is built with a focus on integrating multiple technologies, including Android (Java/CameraX), C++ (for native processing), OpenCV, and OpenGL ES.

## Key Features

* **Android App:** Captures camera frames using the CameraX API.
* **JNI Bridge:** Passes camera frames efficiently from the Java/Kotlin layer to the native C++ layer.
* **C++ OpenCV Processing:** Implements a Canny edge detection algorithm in C++ for high-performance processing.
* **OpenGL ES Rendering:** Renders the processed (edge-detected) video feed back to the screen in real-time.
* **Web Viewer:** Includes a simple, decoupled web viewer in the `/web` folder to demonstrate proficiency with TypeScript and DOM manipulation.

## Architecture
1.  **Camera (Java):** `CameraX` provides a stream of `Image` objects.
2.  **JNI (Java -> C++):** `Image` buffers are passed to the `native-lib.cpp` file.
3.  **OpenCV (C++):** The C++ code uses `cv::Canny` to find edges in the Y-plane (luma) of the image.
4.  **OpenGL (Java):** The resulting single-channel edge map is converted to RGBA and uploaded as a texture, which is then rendered on a `GLSurfaceView`.

## Author

* **Rupal Srivastava** (github.com/rupalsrii)