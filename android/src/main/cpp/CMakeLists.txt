cmake_minimum_required(VERSION 3.22.1)
project(whistle_native)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

include_directories(
    ${CMAKE_CURRENT_SOURCE_DIR}
)

add_library(
    whistle_native
    SHARED
    whistle_jni.cpp
)

find_library(
    log-lib
    log
)

target_link_libraries(
    whistle_native
    ${log-lib}
)
