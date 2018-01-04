#include "hash.h"
#include <sodium.h>
#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(std::vector<unsigned char> msg) {
      std::vector<unsigned char> out(crypto_hash_sha256_BYTES);
      crypto_hash_sha256(&out[0], &msg[0], msg.size());
      return out;
    }

  }
}
