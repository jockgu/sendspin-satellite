cmake_minimum_required(VERSION 3.22.1)
project(sendspin_satellite LANGUAGES CXX)

add_library(clock_filter STATIC clock_filter.cpp)
target_compile_features(clock_filter PUBLIC cxx_std_20)
target_include_directories(clock_filter PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(sendspin_native SHARED native_clock_jni.cpp)
target_link_libraries(sendspin_native PRIVATE clock_filter)
target_compile_features(sendspin_native PRIVATE cxx_std_20)

if(NOT ANDROID)
    include(CTest)
    if(BUILD_TESTING)
        add_executable(clock_filter_test clock_filter_test.cpp)
        target_link_libraries(clock_filter_test PRIVATE clock_filter)
        add_test(NAME clock_filter_test COMMAND clock_filter_test)
    endif()
endif()
