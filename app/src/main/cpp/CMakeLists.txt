cmake_minimum_required(VERSION 3.22.1)
project(sendspin_satellite LANGUAGES CXX)

add_library(clock_filter STATIC clock_filter.cpp)
target_compile_features(clock_filter PUBLIC cxx_std_20)
target_include_directories(clock_filter PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(pcm_render_fifo STATIC pcm_render_fifo.cpp)
target_compile_features(pcm_render_fifo PUBLIC cxx_std_20)
target_include_directories(pcm_render_fifo PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(audio_volume STATIC audio_volume.cpp)
target_compile_features(audio_volume PUBLIC cxx_std_20)
target_include_directories(audio_volume PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(playback_recovery_state STATIC playback_recovery_state.cpp)
target_compile_features(playback_recovery_state PUBLIC cxx_std_20)
target_include_directories(playback_recovery_state PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(reconnect_policy STATIC reconnect_policy.cpp)
target_compile_features(reconnect_policy PUBLIC cxx_std_20)
target_include_directories(reconnect_policy PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(now_playing_state STATIC now_playing_state.cpp)
target_compile_features(now_playing_state PUBLIC cxx_std_20)
target_include_directories(now_playing_state PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

add_library(artwork_state STATIC artwork_state.cpp)
target_compile_features(artwork_state PUBLIC cxx_std_20)
target_include_directories(artwork_state PUBLIC ${CMAKE_CURRENT_SOURCE_DIR})

if(ANDROID)
    find_package(oboe REQUIRED CONFIG)
    set(BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(SENDSPIN_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(SENDSPIN_ENABLE_CONTROLLER OFF CACHE BOOL "" FORCE)
    set(SENDSPIN_ENABLE_METADATA ON CACHE BOOL "" FORCE)
    set(SENDSPIN_ENABLE_COLOR OFF CACHE BOOL "" FORCE)
    set(SENDSPIN_ENABLE_ARTWORK ON CACHE BOOL "" FORCE)
    set(SENDSPIN_ENABLE_VISUALIZER OFF CACHE BOOL "" FORCE)
    add_subdirectory(third_party/sendspin-cpp EXCLUDE_FROM_ALL)

    add_library(sendspin_native SHARED
        native_clock_jni.cpp
        native_playback_engine.cpp
        oboe_pcm_output.cpp
        sendspin_pcm_listener.cpp)
    target_link_libraries(sendspin_native PRIVATE clock_filter pcm_render_fifo audio_volume playback_recovery_state reconnect_policy now_playing_state artwork_state oboe::oboe sendspin)
    target_compile_features(sendspin_native PRIVATE cxx_std_20)
endif()

if(NOT ANDROID)
    include(CTest)
    if(BUILD_TESTING)
        add_executable(clock_filter_test clock_filter_test.cpp)
        target_link_libraries(clock_filter_test PRIVATE clock_filter)
        add_test(NAME clock_filter_test COMMAND clock_filter_test)

        add_executable(pcm_render_fifo_test pcm_render_fifo_test.cpp)
        target_link_libraries(pcm_render_fifo_test PRIVATE pcm_render_fifo)
        add_test(NAME pcm_render_fifo_test COMMAND pcm_render_fifo_test)

        add_executable(audio_volume_test audio_volume_test.cpp)
        target_link_libraries(audio_volume_test PRIVATE audio_volume)
        add_test(NAME audio_volume_test COMMAND audio_volume_test)

        add_executable(playback_recovery_state_test playback_recovery_state_test.cpp)
        target_link_libraries(playback_recovery_state_test PRIVATE playback_recovery_state)
        add_test(NAME playback_recovery_state_test COMMAND playback_recovery_state_test)

        add_executable(playback_recovery_flow_test playback_recovery_flow_test.cpp)
        target_link_libraries(playback_recovery_flow_test PRIVATE playback_recovery_state pcm_render_fifo)
        add_test(NAME playback_recovery_flow_test COMMAND playback_recovery_flow_test)

        add_executable(reconnect_policy_test reconnect_policy_test.cpp)
        target_link_libraries(reconnect_policy_test PRIVATE reconnect_policy)
        add_test(NAME reconnect_policy_test COMMAND reconnect_policy_test)

        add_executable(now_playing_state_test now_playing_state_test.cpp)
        target_link_libraries(now_playing_state_test PRIVATE now_playing_state)
        add_test(NAME now_playing_state_test COMMAND now_playing_state_test)

        add_executable(artwork_state_test artwork_state_test.cpp)
        target_link_libraries(artwork_state_test PRIVATE artwork_state)
        add_test(NAME artwork_state_test COMMAND artwork_state_test)

        add_executable(playback_soak_test playback_soak_test.cpp)
        target_link_libraries(playback_soak_test PRIVATE reconnect_policy playback_recovery_state pcm_render_fifo)
        add_test(NAME playback_soak_test_short COMMAND playback_soak_test 15)
    endif()
endif()
