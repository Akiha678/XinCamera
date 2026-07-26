cmake_minimum_required(VERSION 3.22.1)

project("xincamera")

add_library(${CMAKE_PROJECT_NAME} SHARED
    filter/grayscale_filter.cpp
    histogram/luma_histogram.cpp
    xincamera.cpp
)

target_include_directories(${CMAKE_PROJECT_NAME}
    PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}
)

target_link_libraries(${CMAKE_PROJECT_NAME}
    android
    log
)
