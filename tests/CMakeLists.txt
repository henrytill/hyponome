add_executable(tests_hash
  main.cpp
  tests_hash.cpp)

target_link_libraries(tests_hash
  Hyponome
  CapnProtoDefs
  ${SODIUM_LIBRARY})

add_test(Hash tests_hash)

add_executable(tests_rpc
  main.cpp
  tests_rpc.cpp)

target_link_libraries(tests_rpc
  Hyponome
  CapnProtoDefs
  ${CAPNP_LIBRARIES})

add_test(Rpc tests_rpc)
