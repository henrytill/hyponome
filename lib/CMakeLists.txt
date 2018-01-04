add_library(
  Hyponome 
  hash.cpp 
  util.cpp
  )

target_link_libraries(Hyponome sodium)
