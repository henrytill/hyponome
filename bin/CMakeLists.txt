add_executable(hyponome-server server.cpp)
target_link_libraries(hyponome-server CapnProtoDefs Hyponome capnp capnp-rpc kj-async kj)

add_executable(hyponome-client client.cpp)
target_link_libraries(hyponome-client CapnProtoDefs Hyponome capnp capnp-rpc kj-async kj)
