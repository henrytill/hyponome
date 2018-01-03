cmake_minimum_required(VERSION 2.8)
project(hyponome CXX)
set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${CMAKE_CURRENT_SOURCE_DIR}/share/cmake/Modules)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Wpedantic")
enable_testing(true)

find_package(CapnProto REQUIRED)
set(CAPNPC_SRC_PREFIX ${CMAKE_CURRENT_SOURCE_DIR}/schemas)
set(CAPNPC_OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/schemas)
include_directories(${CAPNP_INCLUDE_DIRS})
file(MAKE_DIRECTORY ${CAPNPC_OUTPUT_DIR})
add_definitions(${CAPNP_DEFINITIONS})
capnp_generate_cpp(
  CAPNP_SRCS CAPNP_HDRS
  ${CAPNPC_SRC_PREFIX}/file.capnp
  ${CAPNPC_SRC_PREFIX}/file_chunk.capnp
  ${CAPNPC_SRC_PREFIX}/file_info.capnp
  )

add_library(CapnProtoDefs ${CAPNP_SRCS})
target_include_directories(CapnProtoDefs PUBLIC ${CAPNPC_OUTPUT_DIR})

add_subdirectory(bin)
