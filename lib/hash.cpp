#include "hash.h"
#include <sodium.h>
#include <string>
#include <vector>

namespace hyponome {
  namespace hash {

    std::vector<unsigned char> sha256(const std::vector<unsigned char> &msg) {
      std::vector<unsigned char> out(crypto_hash_sha256_BYTES);
      crypto_hash_sha256(&out[0], &msg[0], msg.size());
      return out;
    }

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &msg) {
      std::vector<unsigned char> out(crypto_generichash_BYTES_MAX);
      crypto_generichash(&out[0], out.size(), &msg[0], msg.size(), nullptr, 0);
      return out;
    }

    std::vector<unsigned char> blake2b(const std::vector<unsigned char> &msg,
                                       const std::vector<unsigned char> &key) {
      std::vector<unsigned char> out(crypto_generichash_BYTES_MAX);
      crypto_generichash(&out[0], out.size(), &msg[0], msg.size(), &key[0], key.size());
      return out;
    }
  }
}
