cmake_minimum_required(VERSION 3.10)
project(realtime_cut_vad_library_for_android)

set(CMAKE_SHARED_LINKER_FLAGS "${CMAKE_SHARED_LINKER_FLAGS} -Wl,--no-undefined")

add_library(RealtimeCutVadLibraryForAndroid SHARED vad-wrapper.cpp)
include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}  # c_wrapper.h
)
find_library(log-lib log)

target_link_libraries(RealtimeCutVadLibraryForAndroid
        ${log-lib}
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libRealtimeCutVadLibrary.so
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libonnxruntime.so
        ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libwebrtc-audio-processing-1.so
)