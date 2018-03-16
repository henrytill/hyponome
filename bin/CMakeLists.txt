add_executable(hyponome-server server.cpp)
target_link_libraries(
  hyponome-server
  Hyponome
  CapnProtoDefs
  ${CAPNP_LIBRARIES}
  )

add_executable(hyponome-client client.cpp)
target_link_libraries(
  hyponome-client
  Hyponome
  CapnProtoDefs
  ${CAPNP_LIBRARIES}
  )

install (TARGETS hyponome-server hyponome-client DESTINATION bin)
