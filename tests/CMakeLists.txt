# https://github.com/philsquared/Catch/blob/master/docs/build-systems.md

include(ExternalProject)

ExternalProject_Add(
  Catch
  URL https://github.com/philsquared/Catch/archive/v1.9.4.tar.gz
  PREFIX ${CMAKE_CURRENT_BINARY_DIR}/Catch
  CONFIGURE_COMMAND ""
  BUILD_COMMAND ""
  INSTALL_COMMAND ""
  )

ExternalProject_Get_Property(Catch source_dir)

set(CATCH_INCLUDE_DIR ${source_dir}/single_include CACHE INTERNAL "Path to Catch's include directory")
include_directories(${CATCH_INCLUDE_DIR} ${COMMON_INCLUDES})

add_executable(
  tests_hash
  main.cpp
  tests_hash.cpp
  )
target_link_libraries(
  tests_hash
  Hyponome
  CapnProtoDefs
  ${SODIUM_LIBRARY}
  )
add_dependencies(tests_hash Catch)
add_test(Hash tests_hash)

add_executable(
  tests_rpc
  main.cpp
  tests_rpc.cpp
  )
target_link_libraries(
  tests_rpc
  Hyponome
  CapnProtoDefs
  ${CAPNP_LIBRARIES}
  )
add_dependencies(tests_rpc Catch)
add_test(Rpc tests_rpc)
