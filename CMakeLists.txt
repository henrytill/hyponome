cmake_minimum_required(VERSION 3.0)

file(STRINGS VERSION VERSION_NUMBER)
MESSAGE(STATUS "VERSION_NUMBER: " ${VERSION_NUMBER})
project(hyponome VERSION ${VERSION_NUMBER} LANGUAGES CXX)

set(CMAKE_MODULE_PATH ${CMAKE_MODULE_PATH} ${CMAKE_CURRENT_SOURCE_DIR}/vendor/cmake/Modules)
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11 -Wall -Wextra -Wpedantic")
enable_testing(true)

option(ENABLE_DOCS "Build documentation" ON)
if(ENABLE_DOCS)
  find_package(Doxygen REQUIRED)
  if(DOXYGEN_FOUND)
    set(DOXYGEN_IN ${CMAKE_CURRENT_SOURCE_DIR}/doc/Doxyfile.in)
    set(DOXYGEN_OUT ${CMAKE_CURRENT_BINARY_DIR}/Doxyfile)

    configure_file(${DOXYGEN_IN} ${DOXYGEN_OUT} @ONLY)

    add_custom_target(doc ALL
      COMMAND ${DOXYGEN_EXECUTABLE} ${DOXYGEN_OUT}
      WORKING_DIRECTORY ${CMAKE_CURRENT_BINARY_DIR}
      COMMENT "Generating API documentation with Doxygen"
      VERBATIM)
  endif(DOXYGEN_FOUND)
else()
  message("Building of documentation is disabled")
endif()

set(CAPNP_LIB_CAPNP-JSON "")
find_package(CapnProto REQUIRED)
set(CAPNPC_SRC_PREFIX ${CMAKE_CURRENT_SOURCE_DIR}/schemas)
set(CAPNPC_OUTPUT_DIR ${CMAKE_CURRENT_BINARY_DIR}/schemas)
include_directories(${CAPNP_INCLUDE_DIRS})
file(MAKE_DIRECTORY ${CAPNPC_OUTPUT_DIR})
add_definitions(${CAPNP_DEFINITIONS})
capnp_generate_cpp(CAPNP_SRCS CAPNP_HDRS
  ${CAPNPC_SRC_PREFIX}/file.capnp
  ${CAPNPC_SRC_PREFIX}/file_chunk.capnp
  ${CAPNPC_SRC_PREFIX}/file_info.capnp
  ${CAPNPC_SRC_PREFIX}/hasher.capnp)

add_library(CapnProtoDefs ${CAPNP_SRCS})
target_include_directories(CapnProtoDefs PUBLIC ${CAPNPC_OUTPUT_DIR})

find_package(sodium REQUIRED)
include_directories(${sodium_INCLUDE_DIR})
find_library(SODIUM_LIBRARY sodium)

include_directories(include)

add_subdirectory(bin)
add_subdirectory(lib)
add_subdirectory(tests)

set(CPACK_GENERATOR "TGZ")
set(CPACK_SOURCE_GENERATOR "TGZ")
set(CPACK_SOURCE_IGNORE_FILES "${CMAKE_BINARY_DIR};/.git/;${CPACK_SOURCE_IGNORE_FILES}")

set(CPACK_PACKAGE_VERSION_MAJOR ${PROJECT_VERSION_MAJOR})
set(CPACK_PACKAGE_VERSION_MINOR ${PROJECT_VERSION_MINOR})
set(CPACK_PACKAGE_VERSION_PATCH ${PROJECT_VERSION_PATCH})

include(CPack)
