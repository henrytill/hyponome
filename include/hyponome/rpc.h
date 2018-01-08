#ifndef HASHER_H
#define HASHER_H

#include "hasher.capnp.h"
#include <kj/async.h>

namespace hyponome {

  ///
  /// \namespace hyponome::rpc
  /// \brief RPC implementation
  ///
  namespace rpc {

    class HasherImpl final : public Hasher::Server {
    public:
      HasherImpl();
      kj::Promise<void> hash(HashContext context) override;
    };
  }
}

#endif

// Local Variables:
// mode: c++
// End:
//
// vim: set filetype=cpp:
