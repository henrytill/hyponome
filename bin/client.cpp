#include "hasher.capnp.h"
#include "util.h"
#include <algorithm>
#include <capnp/ez-rpc.h>
#include <capnp/message.h>
#include <cstring>
#include <iostream>
#include <string>

int main(int argc, const char *argv[]) {
  if (argc != 3) {
    std::cerr << "usage: " << argv[0] << " HOST[:PORT] INPUT" << std::endl;
    return 1;
  }

  capnp::EzRpcClient client(argv[1], 5923);
  auto &waitScope = client.getWaitScope();

  Hasher::Client cap = client.getMain<Hasher>();

  auto request = cap.hashRequest();
  auto data =
      capnp::Data::Reader(reinterpret_cast<const unsigned char *>(argv[2]), std::strlen(argv[2]));
  request.setData(data);
  auto promise = request.send();
  auto response = promise.wait(waitScope);

  auto hash = response.getHash();
  auto hashHex = hyponome::util::bin2hex(std::vector<unsigned char>(hash.begin(), hash.end()));

  std::cout << hashHex << std::endl;
  return 0;
}
