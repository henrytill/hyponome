#include "rpc.h"
#include "hash.h"
#include "hasher.capnp.h"
#include "util.h"
#include <capnp/message.h>
#include <kj/async.h>
#include <kj/common.h>
#include <sodium.h>
#include <stdexcept>
#include <vector>

namespace hyponome {

  HasherImpl::HasherImpl() {
    if (sodium_init() < 0)
      throw std::runtime_error("sodium_init() failed");
  }

  kj::Promise<void> HasherImpl::hash(HashContext context) {
    auto drdr = context.getParams().getData();
    auto data = std::vector<unsigned char>(drdr.begin(), drdr.end());
    auto hash = hash::sha256(data);
    context.getResults().setHash(kj::arrayPtr(&hash[0], hash.size()));
    return kj::READY_NOW;
  }
}
