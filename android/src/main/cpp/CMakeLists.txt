cmake_minimum_required(VERSION 3.22.1)
project(whistle_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}
)

find_library(log-lib log)
find_library(android-lib android)

if(${ANDROID_ABI} STREQUAL "arm64-v8a" AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/libs/arm64-v8a/libneedle.a")
    message(STATUS "Whistle Native: Building for arm64-v8a with full on-device needle inference engine")
    add_library(whistle_native SHARED whistle_jni.cpp)
    target_link_libraries(
        whistle_native
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/arm64-v8a/libneedle.a
        ${android-lib}
        ${log-lib}
    )
    target_compile_definitions(whistle_native PRIVATE HAVE_WHISTLE_ENGINE=1)
elseif(${ANDROID_ABI} STREQUAL "armeabi-v7a" AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/libs/armeabi-v7a/libneedle.a")
    message(STATUS "Whistle Native: Building for armeabi-v7a with full on-device needle inference engine")
    add_library(whistle_native SHARED whistle_jni.cpp)
    target_link_libraries(
        whistle_native
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/armeabi-v7a/libneedle.a
        ${android-lib}
        ${log-lib}
    )
    target_compile_definitions(whistle_native PRIVATE HAVE_WHISTLE_ENGINE=1)
elseif(${ANDROID_ABI} STREQUAL "x86_64" AND EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/libs/x86_64/libneedle.a")
    message(STATUS "Whistle Native: Building for x86_64 with on-device needle inference engine and glibc compatibility shim")
    add_library(whistle_native SHARED whistle_jni.cpp glibc_compat.c)
    target_link_options(whistle_native PRIVATE -static-libstdc++)
    target_link_libraries(
        whistle_native
        ${CMAKE_CURRENT_SOURCE_DIR}/libs/x86_64/libneedle.a
        ${android-lib}
        ${log-lib}
    )
    target_compile_definitions(whistle_native PRIVATE HAVE_WHISTLE_ENGINE=1)
else()
    message(STATUS "Whistle Native: Architecture ${ANDROID_ABI} building with graceful fallback stub")
    add_library(whistle_native SHARED whistle_jni.cpp)
    target_link_libraries(
        whistle_native
        ${android-lib}
        ${log-lib}
    )
    target_compile_definitions(whistle_native PRIVATE HAVE_WHISTLE_ENGINE=0)
endif()
