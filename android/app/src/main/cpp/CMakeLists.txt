cmake_minimum_required(VERSION 3.22.1)

project(nanzhufeng_mp3 LANGUAGES C CXX)

set(LAME_ROOT "${CMAKE_CURRENT_SOURCE_DIR}/third_party/lame-4.0")
set(LAME_LIBRARY_SOURCES
    "${LAME_ROOT}/libmp3lame/VbrTag.c"
    "${LAME_ROOT}/libmp3lame/bitstream.c"
    "${LAME_ROOT}/libmp3lame/encoder.c"
    "${LAME_ROOT}/libmp3lame/fft.c"
    "${LAME_ROOT}/libmp3lame/gain_analysis.c"
    "${LAME_ROOT}/libmp3lame/id3tag.c"
    "${LAME_ROOT}/libmp3lame/lame.c"
    "${LAME_ROOT}/libmp3lame/newmdct.c"
    "${LAME_ROOT}/libmp3lame/presets.c"
    "${LAME_ROOT}/libmp3lame/psymodel.c"
    "${LAME_ROOT}/libmp3lame/quantize.c"
    "${LAME_ROOT}/libmp3lame/quantize_pvt.c"
    "${LAME_ROOT}/libmp3lame/reservoir.c"
    "${LAME_ROOT}/libmp3lame/set_get.c"
    "${LAME_ROOT}/libmp3lame/tables.c"
    "${LAME_ROOT}/libmp3lame/takehiro.c"
    "${LAME_ROOT}/libmp3lame/util.c"
    "${LAME_ROOT}/libmp3lame/vbrquantize.c"
    "${LAME_ROOT}/libmp3lame/version.c"
    "${LAME_ROOT}/libmp3lame/mpglib_interface.c"
)

add_library(mp3lame SHARED ${LAME_LIBRARY_SOURCES})
target_include_directories(mp3lame
    PUBLIC "${LAME_ROOT}/include"
    PRIVATE
        "${LAME_ROOT}/libmp3lame"
        "${LAME_ROOT}"
)
target_compile_definitions(mp3lame PRIVATE
    STDC_HEADERS
    HAVE_ERRNO_H
    HAVE_FCNTL_H
    HAVE_INTTYPES_H
    HAVE_LIMITS_H
    HAVE_STDINT_H
    HAVE_UNISTD_H
)
target_compile_options(mp3lame PRIVATE
    "-include${CMAKE_CURRENT_SOURCE_DIR}/lame_android_config.h"
)
set_target_properties(mp3lame PROPERTIES
    C_STANDARD 99
    C_STANDARD_REQUIRED YES
)

add_library(nanzhufeng_mp3 SHARED nanzhufeng_mp3_jni.cpp)
target_compile_features(nanzhufeng_mp3 PRIVATE cxx_std_17)

find_library(android_log log)
find_library(android_math m)

target_link_libraries(mp3lame PRIVATE ${android_math})
target_link_libraries(nanzhufeng_mp3 PRIVATE mp3lame ${android_log} ${android_math})
