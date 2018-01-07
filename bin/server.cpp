#include "hasher.capnp.h"
#include "rpc.h"
#include <capnp/ez-rpc.h>
#include <capnp/message.h>
#include <iostream>
#include <kj/async.h>

int main(int argc, const char *argv[]) {
  if (argc != 2) {
    std::cerr << "usage: " << argv[0] << " ADDRESS[:PORT]" << std::endl;
    return 1;
  }

  capnp::EzRpcServer server(kj::heap<hyponome::HasherImpl>(), argv[1], 5923);
  auto &waitScope = server.getWaitScope();
  kj::NEVER_DONE.wait(waitScope);
}
