cmake_minimum_required(VERSION 3.22.1)

project("openedge")

# C++ standard
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Create native library
add_library(
        openedge
        SHARED
        native-lib.cpp
)

# Find and link log library
find_library(
        log-lib
        log
)

target_link_libraries(
        openedge
        ${log-lib}
)