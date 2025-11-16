cmake_minimum_required(VERSION 3.22.1)
project(edgeviewer)

# Support multiple ABIs by resolving the OpenCV prebuilt path using the Android ABI
set(OPENCV_ROOT_CANDIDATE1 ${CMAKE_SOURCE_DIR}/../../opencv)
# project root opencv (e.g. D:/EdgeViewer/opencv)
set(OPENCV_ROOT_CANDIDATE2 ${CMAKE_SOURCE_DIR}/../../../../opencv)

if(EXISTS ${OPENCV_ROOT_CANDIDATE1}/sdk/native/jni/include)
    set(OPENCV_ROOT ${OPENCV_ROOT_CANDIDATE1})
elseif(EXISTS ${OPENCV_ROOT_CANDIDATE2}/sdk/native/jni/include)
    set(OPENCV_ROOT ${OPENCV_ROOT_CANDIDATE2})
else()
    # Fallback: prefer candidate2 if candidate1 is present but incomplete
    if(EXISTS ${OPENCV_ROOT_CANDIDATE2})
        set(OPENCV_ROOT ${OPENCV_ROOT_CANDIDATE2})
    else()
        set(OPENCV_ROOT ${OPENCV_ROOT_CANDIDATE1})
    endif()
endif()

if(EXISTS ${OPENCV_ROOT}/sdk/native/jni/include)
    set(OPENCV_INCLUDE_DIR ${OPENCV_ROOT}/sdk/native/jni/include)
elseif(EXISTS ${OPENCV_ROOT}/jni/include)
    set(OPENCV_INCLUDE_DIR ${OPENCV_ROOT}/jni/include)
else()
    set(OPENCV_INCLUDE_DIR ${OPENCV_ROOT}/include)
endif()

if(EXISTS ${OPENCV_ROOT}/sdk/native/libs/${ANDROID_ABI})
    set(OPENCV_LIB_DIR ${OPENCV_ROOT}/sdk/native/libs/${ANDROID_ABI})
elseif(EXISTS ${OPENCV_ROOT}/libs/${ANDROID_ABI})
    set(OPENCV_LIB_DIR ${OPENCV_ROOT}/libs/${ANDROID_ABI})
else()
    set(OPENCV_LIB_DIR ${OPENCV_ROOT}/libs/${ANDROID_ABI})
endif()

add_library(edgeviewer SHARED native-lib.cpp)

# If the OpenCV shared lib exists for the current ABI, import and link it.
set(OPENCV_SO ${OPENCV_LIB_DIR}/libopencv_java4.so)
if(EXISTS ${OPENCV_SO})
    add_library(opencv_java4 SHARED IMPORTED)
    set_target_properties(opencv_java4 PROPERTIES
        IMPORTED_LOCATION ${OPENCV_SO}
    )

    target_include_directories(edgeviewer PRIVATE ${OPENCV_INCLUDE_DIR})

    find_library(log-lib log)

    target_link_libraries(edgeviewer
        opencv_java4
        ${log-lib}
    )
else()
    message(WARNING "OpenCV library not found at ${OPENCV_SO}. Building without OpenCV.")
    find_library(log-lib log)
    target_link_libraries(edgeviewer ${log-lib})
endif()
