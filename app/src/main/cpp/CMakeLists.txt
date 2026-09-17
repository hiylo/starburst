cmake_minimum_required(VERSION 3.22.1)
project("starburst_mnn")

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# MNN native libs (prebuilt, shipped via jniLibs)
add_library(MNN SHARED IMPORTED)
set_target_properties(MNN PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN.so)
add_library(MNN_LLM SHARED IMPORTED)
set_target_properties(MNN_LLM PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllm.so)
add_library(MNN_CL SHARED IMPORTED)
set_target_properties(MNN_CL PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN_CL.so)
add_library(MNN_EXPRESS SHARED IMPORTED)
set_target_properties(MNN_EXPRESS PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN_Express.so)
add_library(MNN_VULKAN SHARED IMPORTED)
set_target_properties(MNN_VULKAN PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNN_Vulkan.so)
add_library(MNN_OPENCV SHARED IMPORTED)
set_target_properties(MNN_OPENCV PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libMNNOpenCV.so)

include_directories(${CMAKE_SOURCE_DIR}/include)

add_library(starburst_mnn SHARED
        starburst_mnn_jni.cpp
)

target_link_libraries(starburst_mnn
        android
        log
        dl
        MNN
        MNN_LLM
        MNN_CL
        MNN_EXPRESS
        MNN_VULKAN
        MNN_OPENCV
)

# Standalone loader for the sherpa-mnn ASR JNI: dlopens libMNN.so and
# libMNN_Express.so with RTLD_GLOBAL so the MNN::Express symbols sherpa needs
# are visible. Deliberately NOT linked against any MNN library to keep this
# target independent from starburst_mnn.
add_library(sherpa_helper SHARED
        sherpa_helper.cpp
)

target_link_libraries(sherpa_helper
        android
        log
        dl
)
