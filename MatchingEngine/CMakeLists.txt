cmake_minimum_required(VERSION 3.16)
project(MatchingEngine)
set(CMAKE_CXX_STANDARD 17)

# Collect all library sources (everything except main.cpp)
file(GLOB_RECURSE LIB_SOURCES
    CONFIGURE_DEPENDS
    "${CMAKE_CURRENT_SOURCE_DIR}/src/core/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/models/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/kafka/*.cpp"
    "${CMAKE_CURRENT_SOURCE_DIR}/src/service/*.cpp"
)

add_library(MatchingEngineLib STATIC ${LIB_SOURCES})
target_compile_options(MatchingEngineLib PRIVATE -Wall -Wextra -Wpedantic)
target_include_directories(MatchingEngineLib PUBLIC include)

# macOS/Homebrew-specific settings
if(APPLE)
    target_include_directories(MatchingEngineLib PUBLIC
      /opt/homebrew/Cellar/librdkafka/2.10.0/include
      /opt/homebrew/Cellar/librdkafka/2.10.0/include/librdkafka
    )
    target_link_directories(MatchingEngineLib PUBLIC
      /opt/homebrew/lib
      /opt/homebrew/Cellar/librdkafka/2.10.0/lib
    )
    target_include_directories(MatchingEngineLib PUBLIC /opt/homebrew/include)
    set(OPENSSL_ROOT_DIR "/opt/homebrew/opt/openssl@3")
    set(OPENSSL_INCLUDE_DIR "/opt/homebrew/opt/openssl@3/include")
    set(OPENSSL_LIBRARIES "/opt/homebrew/opt/openssl@3/lib")
    target_link_options(MatchingEngineLib PUBLIC -L/opt/homebrew/opt/openssl@3/lib)
endif()

# nlohmann-json for Linux (Docker)
if(NOT APPLE)
    target_include_directories(MatchingEngineLib PUBLIC /usr/include/nlohmann)
endif()

# Add cppkafka subdirectory and link
add_subdirectory(cppkafka)
target_include_directories(MatchingEngineLib PUBLIC cppkafka/include)

# Find and link OpenSSL
find_package(OpenSSL REQUIRED)
target_include_directories(MatchingEngineLib PUBLIC ${OPENSSL_INCLUDE_DIR})

target_link_libraries(MatchingEngineLib
    PUBLIC cppkafka
    PUBLIC OpenSSL::SSL
    PUBLIC OpenSSL::Crypto
)

# Main executable
add_executable(MatchingEngine src/main.cpp)
target_link_libraries(MatchingEngine PRIVATE MatchingEngineLib)
target_compile_options(MatchingEngine PRIVATE -Wall -Wextra -Wpedantic)

# Tests
include(FetchContent)
FetchContent_Declare(
    Catch2
    GIT_REPOSITORY https://github.com/catchorg/Catch2.git
    GIT_TAG v3.7.1
)
FetchContent_MakeAvailable(Catch2)

enable_testing()

add_executable(MatchingEngineTests
    tests/OrderTests.cpp
    tests/OrderBookTests.cpp
    tests/OrderFactoryTests.cpp
    tests/StrategyTests.cpp
    tests/StrategyFactoryTests.cpp
    tests/ModelTests.cpp
    tests/OrderMatchingServiceTests.cpp
    tests/OrderBookStateStoreTests.cpp
)
target_link_libraries(MatchingEngineTests PRIVATE MatchingEngineLib Catch2::Catch2WithMain)
target_compile_options(MatchingEngineTests PRIVATE -Wall -Wextra -Wpedantic)

add_test(NAME MatchingEngineTests COMMAND MatchingEngineTests)
