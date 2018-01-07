add_library(
  Hyponome
  hash.cpp
  util.cpp
  rpc.cpp
  )

target_link_libraries(Hyponome sodium CapnProtoDefs)
