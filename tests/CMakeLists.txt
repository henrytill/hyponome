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
  hash_tests
  main.cpp
  hash_tests.cpp
  )
target_link_libraries(hash_tests CapnProtoDefs)
target_link_libraries(hash_tests sodium)

add_dependencies(hash_tests Catch)
add_test(HashTests hash_tests)
