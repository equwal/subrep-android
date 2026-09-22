cmake_minimum_required(VERSION 3.22)
project(subrep_whisper C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

# Speech recognition is matrix arithmetic. Unoptimised it is ten times slower, so optimise
# whatever Gradle thinks the build type is.
if (CMAKE_BUILD_TYPE STREQUAL "Debug")
    add_compile_options(-O3)
endif ()

set(WHISPER_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../../../../third_party/whisper.cpp)

set(BUILD_SHARED_LIBS      OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS    OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER   OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE            OFF CACHE BOOL "" FORCE)   # cross-compiling: never probe the build host
set(GGML_OPENMP            OFF CACHE BOOL "" FORCE)   # ggml's own thread pool; no libomp to ship
set(GGML_LLAMAFILE         OFF CACHE BOOL "" FORCE)

# Half-precision and dot-product instructions are most of the speed on ARM. Each CPU since the
# Cortex-A55/A75 (2018) has them. Whisper.kt checks before it loads this library, so an older
# CPU gets a message, not a SIGILL.
if (ANDROID_ABI STREQUAL "arm64-v8a")
    set(GGML_CPU_ARM_ARCH "armv8.2-a+fp16+dotprod" CACHE STRING "" FORCE)
endif ()

add_subdirectory(${WHISPER_DIR} whisper EXCLUDE_FROM_ALL)

add_library(subrep_whisper SHARED whisper_jni.c)
target_link_libraries(subrep_whisper PRIVATE whisper log)
target_link_options(subrep_whisper PRIVATE -Wl,--gc-sections -Wl,--exclude-libs,ALL)
